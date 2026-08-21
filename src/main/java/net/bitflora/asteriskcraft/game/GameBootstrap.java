package net.bitflora.asteriskcraft.game;

import net.bitflora.asteriskcraft.AsteriskCraft;
import net.bitflora.asteriskcraft.building.ArmyBank;
import net.bitflora.asteriskcraft.building.BuildingTemplates;
import net.bitflora.asteriskcraft.building.BaseBlockEntity;
import net.bitflora.asteriskcraft.building.CoreCensus;
import net.bitflora.asteriskcraft.building.UnitSpawns;
import net.bitflora.asteriskcraft.command.CommandAttachments;
import net.bitflora.asteriskcraft.entity.WorkerEntity;
import net.minecraft.world.entity.Mob;
import net.bitflora.asteriskcraft.race.RaceProfile;
import net.bitflora.asteriskcraft.faction.Faction;
import net.bitflora.asteriskcraft.faction.FactionAttachments;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.core.Vec3i;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.function.IntBinaryOperator;
import java.util.function.IntPredicate;

/**
 * Places the player's Nexus (seeded with starting resources) the first time someone joins a
 * new world. We do this on first join (rather than at server start) because the player's
 * chunk is guaranteed loaded with a settled surface height — placing at server start
 * can land the structure at the world floor before terrain is ready. From the player's
 * point of view the world still "starts" with a Nexus already standing (R1).
 */
@EventBusSubscriber(modid = AsteriskCraft.MODID)
public final class GameBootstrap {
    private static final int PLAYER_BASE_OFFSET = 6;
    private static final int STARTING_LOGS = 100;
    private static final int STARTING_COBBLESTONE = 100;
    private static final int INITIAL_WORKERS = 4;
    // How far past the player base's own footprint to clear trees, so the starting base is never
    // stamped into — or left standing under — a tree, just like the AI's.
    private static final int PLAYER_BASE_CLEAR_MARGIN = 2;

    // Each AI base is planted in its own random direction around the player's, at a random distance
    // in this range. The max stays inside typical simulation distance so wave units and workers
    // never freeze in unloaded chunks (V3 keeps chunk tickets out of scope; see docs/shaping.md).
    private static final int AI_BASE_COUNT = 3;
    private static final int AI_BASE_MIN_DISTANCE = 95;
    private static final int AI_BASE_MAX_DISTANCE = 120;
    // WORLD_SURFACE reports the topmost solid block in a column, which can be far underground
    // if that column happens to be a cave/ravine breach — retry with a fresh angle/distance
    // rather than bury a Hive at the bottom of a sinkhole.
    private static final int AI_BASE_PLACEMENT_ATTEMPTS = 8;
    private static final int SURFACE_CLEARANCE = 12;
    // Base spread is drawn from an RNG seeded off the world seed (so the same seed reproduces the same
    // layout), XORed with this fixed salt to decorrelate the spread from vanilla terrain features that
    // also key off the seed.
    private static final long AI_BASE_PLACEMENT_SALT = 0x5A3E_2B17L;
    // Keep the AI's bases from clustering: a candidate must be at least this many blocks from every
    // base already placed this bootstrap.
    private static final int AI_BASE_MIN_SEPARATION = 15;
    // If a candidate would sit in open water, re-roll a fresh spot this many times before finally
    // settling for a watery one rather than looping forever.
    private static final int WATER_RETRY_LIMIT = 3;
    private static final int INITIAL_WORKERS_PER_AI_BASE = 2;
    // How far a race's ground cover ("creep" for the Zerg) reaches around a new AI base: every
    // exposed natural-ground surface block within this radius is overrun. A race that lays none
    // (RaceProfile.creep) skips the sweep entirely.
    private static final int AI_BASE_COVER_RADIUS = 10;

    /**
     * Radius of the unbroken stone ring laid around each AI base. Sits outside the scattered garden
     * nodes (which reach out to 6) and inside the ground-cover disc, so the ring is on ground the
     * base has already cleared.
     */
    private static final int AI_BASE_STONE_RING_RADIUS = 8;
    private static final Set<Block> COVERABLE_GROUND = Set.of(
            Blocks.GRASS_BLOCK, Blocks.STONE, Blocks.SAND, Blocks.RED_SAND, Blocks.DIRT, Blocks.GRAVEL);
    // How far down to scan past a tree's logs and undergrowth when locating the real ground beneath
    // it — comfortably taller than any vanilla tree (a jungle giant on a slope is the worst case) so
    // we always reach the floor rather than falling back to the top of the scan.
    private static final int MAX_TREE_SCAN = 64;
    // How far up a submerged column may be followed to the top of the water or ice over it when
    // taking a footprint's height — deeper than any vanilla lake or river, but bounded so an ocean
    // trench can't run the climb away.
    private static final int MAX_WATER_RISE = 32;

    private GameBootstrap() {
    }

    @SubscribeEvent
    public static void onPlayerJoin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        ServerLevel overworld = player.level().getServer().overworld();
        MatchSetup setup = MatchSetup.of(overworld);

        // Set on every login (not just first-join bootstrap) so players who joined before
        // enemy-vs-player combat existed also pick up their faction — and so a match whose sides
        // were set differently is picked up on the next login rather than only at bootstrap.
        FactionAttachments.set(player, setup.playerFaction());

        if (!overworld.getData(GameAttachments.BOOTSTRAPPED)) {
            placeStartingBase(overworld, player, setup);
        }

        for (BlockPos base : CoreCensus.standing(overworld, setup.playerFaction())) {
            player.sendSystemMessage(Component.translatable(
                    "message.asteriskcraft.base_location", base.getX(), base.getY(), base.getZ()));
        }
    }

    /**
     * Clears the departing player's selection glow. The selection attachment itself isn't
     * serialized (it resets to empty on relog), but {@link net.bitflora.asteriskcraft.command.PlayerSelection#add}
     * sets a vanilla glowing tag that persists on the entity — so without this sweep, units
     * selected before logout would glow forever with no selection state left to clear them.
     */
    @SubscribeEvent
    public static void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player
                && player.level() instanceof ServerLevel level) {
            CommandAttachments.selection(player).clear(level);
        }
    }

    /**
     * Clears the dying player's selection glow, same rationale as {@link #onPlayerLogout}: death
     * replaces the player entity with a fresh instance on respawn, and the new instance gets a
     * fresh empty {@link net.bitflora.asteriskcraft.command.PlayerSelection} — so without this,
     * units selected before death keep their vanilla glowing tag with no selection state left to
     * clear them. Fired from {@link net.minecraft.world.entity.LivingEntity#die} before any
     * teardown, so {@code player} and {@code player.level()} are still valid here.
     */
    @SubscribeEvent
    public static void onPlayerDeath(LivingDeathEvent event) {
        if (event.getEntity() instanceof ServerPlayer player
                && player.level() instanceof ServerLevel level) {
            CommandAttachments.selection(player).clear(level);
        }
    }

    private static void placeStartingBase(ServerLevel level, ServerPlayer player, MatchSetup setup) {
        RaceProfile profile = setup.playerProfile();
        int x = player.blockPosition().getX() + PLAYER_BASE_OFFSET;
        int z = player.blockPosition().getZ() + PLAYER_BASE_OFFSET;
        // The base's footprint comes from its structure template, so redesigning the building in
        // Blockbench/a structure block automatically widens the ground work below.
        Vec3i size = BuildingTemplates.size(level, profile.baseTemplate());
        if (size == null) {
            // A missing template is a broken install, not a crash: bail before touching the terrain
            // and leave the world un-bootstrapped, so a fixed jar still places the base on the next join.
            AsteriskCraft.LOGGER.error("AsteriskCraft: could not place the starting base");
            return;
        }
        int footprint = BuildingTemplates.footprintRadius(size);
        // Scan past any tree canopy to the real ground (WORLD_SURFACE counts leaves/logs as the surface,
        // which used to leave the base perched high up in a tree) and clear the trees over the footprint.
        clearTrees(level, x, z, footprint + PLAYER_BASE_CLEAR_MARGIN);
        int y = platformHeight(level, x, z, footprint);
        BlockPos origin = new BlockPos(x, y, z);

        BuildingTemplates.Placed placed = BuildingTemplates.place(level, origin, profile.baseTemplate(),
                profile.coreBlock().get(), support(profile));
        if (placed == null) {
            AsteriskCraft.LOGGER.error("AsteriskCraft: the starting base template holds no core block");
            return;
        }
        // No warp scaffold: the starting base is standing finished when the world begins (R1).
        BlockPos core = placed.core();

        if (level.getBlockEntity(core) instanceof BaseBlockEntity base) {
            base.setFaction(setup.playerFaction());
            // The starting base is simply standing there when the world begins (R1) — it was never
            // warped in from a kit, so it doesn't spend its first two minutes half-built and idle.
            base.skipWarpIn();
            seedPlayerBase(base);
        }
        spawnStartingWorkers(level, core, profile, setup.playerFaction());

        placeAiBase(level, x, z, setup);

        level.setData(GameAttachments.BOOTSTRAPPED, true);

        // The Command Crystal enables unit select/order mode while held (R5).
        player.getInventory().add(new ItemStack(AsteriskCraft.CURSOR.get()));
        player.getInventory().add(new ItemStack(AsteriskCraft.GATEWAY_KIT.get()));
        player.getInventory().add(new ItemStack(AsteriskCraft.PHOTON_CANNON_KIT.get()));
        player.sendSystemMessage(Component.translatable("message.asteriskcraft.enemy_location", AI_BASE_COUNT));

        AsteriskCraft.LOGGER.info("AsteriskCraft: placed starting base core at {}", core);
    }

    /**
     * What a race's base fills gaps beneath its template with, or null to leave the ground as it
     * lies — the Protoss stonework is its own plinth, and a foreign apron poking out from under it
     * read as a mistake.
     */
    private static @Nullable BlockState support(RaceProfile profile) {
        return profile.support() == null ? null : profile.support().get();
    }

    /**
     * Stamps the computer player's bases (plus a small resource garden and starter workers), each in
     * its own random direction and distance from the player's, so the enemy presence surrounds them
     * instead of sitting in one predictable cluster.
     *
     * <p>Everything race-specific here — which template is stamped, what ground cover is laid, what
     * static defence is planted, which worker is spawned — comes off the AI's {@link RaceProfile},
     * so this reads the same whichever race the computer drew.
     */
    private static void placeAiBase(ServerLevel level, int playerX, int playerZ, MatchSetup setup) {
        RaceProfile profile = setup.aiProfile();
        // Seed off the world seed so the same seed always reproduces the same layout (the player's
        // base location is itself seed-derived), instead of drawing from the level's shared,
        // non-reproducible RNG.
        RandomSource random = RandomSource.create(level.getSeed() ^ AI_BASE_PLACEMENT_SALT);
        Vec3i baseSize = BuildingTemplates.size(level, profile.baseTemplate());
        if (baseSize == null) {
            AsteriskCraft.LOGGER.error("AsteriskCraft: could not place the {} bases", profile.race());
            return;
        }
        int footprint = BuildingTemplates.footprintRadius(baseSize);
        List<BlockPos> cores = new ArrayList<>();
        for (int i = 0; i < AI_BASE_COUNT; i++) {
            BlockPos chosen = null;
            BlockPos fallback = null; // last candidate seen; used if nothing fully passes
            int waterRejects = 0;
            for (int attempt = 0; attempt < AI_BASE_PLACEMENT_ATTEMPTS; attempt++) {
                float angle = random.nextFloat() * Mth.TWO_PI;
                int distance = Mth.nextInt(random, AI_BASE_MIN_DISTANCE, AI_BASE_MAX_DISTANCE);
                int x = playerX + Math.round(Mth.cos(angle) * distance);
                int z = playerZ + Math.round(Mth.sin(angle) * distance);
                int y = platformHeight(level, x, z, footprint);
                BlockPos candidate = new BlockPos(x, y, z);
                fallback = candidate;

                // Hard constraints: re-roll within the attempt budget.
                if (!hasOpenSky(level, x, y, z)) {
                    continue;
                }
                if (!farFromOthers(candidate, cores)) {
                    continue;
                }
                // Water: re-roll up to WATER_RETRY_LIMIT times, then accept a watery spot.
                if (isSurroundedByWater(level, x, z) && waterRejects < WATER_RETRY_LIMIT) {
                    waterRejects++;
                    continue;
                }
                chosen = candidate;
                break;
            }
            if (chosen == null) {
                chosen = fallback; // never null: fallback is set on the first iteration
            }
            BlockPos core = placeAiBaseAt(level, chosen.getX(), chosen.getY(), chosen.getZ(),
                    profile, setup.aiFaction());
            if (core != null) {
                cores.add(core);
            }
            seedResourceGarden(level, chosen.getX(), chosen.getZ());
            seedStoneRing(level, chosen.getX(), chosen.getZ());
        }
        seedAiArmyBank(level, profile, setup.aiFaction());
        AsteriskCraft.LOGGER.info("AsteriskCraft: placed {} {} bases scattered around {},{}",
                cores.size(), profile.race(), playerX, playerZ);
    }

    /**
     * True if the column above (x, y, z) is open to the sky, distinguishing real outdoor ground
     * from the floor of a cave or ravine that happens to breach the surface (which the ground scan
     * would otherwise report as valid, burying the structure underground).
     *
     * <p>The probe starts from the higher of the platform and this column's own ground, because
     * {@link #medianGround} can seat the platform <i>below</i> the center column — on a rise, a
     * probe from the platform up would start inside the dirt the building is about to be cut into
     * and reject a perfectly open spot as a cave.
     */
    private static boolean hasOpenSky(ServerLevel level, int x, int y, int z) {
        int from = Math.max(y, groundHeight(level, x, z));
        for (int dy = 2; dy <= SURFACE_CLEARANCE; dy++) {
            // Trees and undergrowth overhead are fine — they get cleared before the mound is stamped
            // (see clearTrees) — so only a real solid overhang (cave/ravine roof) rejects the spot.
            if (isGround(level.getBlockState(new BlockPos(x, from + dy, z)))) {
                return false;
            }
        }
        return true;
    }

    /** True if this block is part of a tree (log or leaves), which the placement scans past and clears. */
    private static boolean isVegetation(BlockState state) {
        return state.is(BlockTags.LOGS) || state.is(BlockTags.LEAVES);
    }

    /**
     * True if this block is solid natural ground a building can rest on — the positive test the
     * downward ground scan stops at. Stating what ground <i>is</i> rather than listing the foliage it
     * is not is what makes a jungle work: the canopy is draped in vines and cocoa, neither of which is
     * a log or leaves, and a scan that stopped at "the first block that isn't air or a tree" stopped
     * on a vine ~30 blocks up and left the Hive floating over the trees. Vines, cocoa, glow lichen,
     * undergrowth, bamboo, snow layers and water are all either non-occluding or not full cubes, so
     * {@code isSolidRender} rejects the lot of them without naming any. Logs <i>are</i> solid-render,
     * so the tag exclusion is load-bearing.
     */
    static boolean isGround(BlockState state) {
        return state.isSolidRender() && !isVegetation(state);
    }

    /**
     * First ground Y at or below {@code top}, or {@code top} itself if the scan reaches {@code minY}
     * without finding one. Pure and probe-driven (no level, no tags) so the scan geometry is
     * unit-testable, same pattern as {@link net.bitflora.asteriskcraft.building.BuildingTemplates#planSitePrep}.
     */
    static int scanToGround(int top, int minY, IntPredicate isGround) {
        for (int y = top; y >= minY; y--) {
            if (isGround.test(y)) {
                return y;
            }
        }
        return top;
    }

    /**
     * Topmost solid ground block in a column, scanning down past a tree to the floor beneath it. The
     * scan starts from MOTION_BLOCKING_NO_LEAVES rather than WORLD_SURFACE because that heightmap has
     * already discounted the canopy and the vines hanging off it, leaving only the trunk to walk down.
     */
    private static int groundHeight(ServerLevel level, int x, int z) {
        int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) - 1;
        return scanToGround(top, top - MAX_TREE_SCAN,
                y -> isGround(level.getBlockState(new BlockPos(x, y, z))));
    }

    /**
     * Clears everything standing between the ground and the sky in the creep disc around a Hive, so
     * the mound is never stamped into a tree and no canopy is left floating over the finished base.
     * Called from {@link #placeHive} before the layout is placed. It clears anything that isn't
     * {@link #isGround} — logs and leaves, but also the vines and cocoa a jungle drapes over them,
     * which a logs-and-leaves-only sweep left dangling in mid-air over a cleared base. Genuine solid
     * overhangs are ground, so they survive; the column's own floor is below the band and untouched.
     *
     * <p>{@link #isSurfaceCover Water and ice} survive too, and that carve-out is load-bearing rather
     * than cosmetic: neither is {@link #isGround}, so this sweep used to drain a lake to its bed
     * across the whole disc — and on the player's base it runs <em>before</em> the height is measured,
     * so {@link #surfaceHeight} then found nothing left to climb and reported the bed. A base on a
     * frozen lake was stamped at the bottom of it. Whatever this clears must be terrain a building
     * would stand over, never terrain it is meant to stand <i>on</i>.
     */
    private static void clearTrees(ServerLevel level, int cx, int cz, int radius) {
        BlockState air = Blocks.AIR.defaultBlockState();
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                if (dx * dx + dz * dz > radius * radius) {
                    continue;
                }
                int x = cx + dx;
                int z = cz + dz;
                // WORLD_SURFACE, not the ground scan's MOTION_BLOCKING_NO_LEAVES: the leaves and
                // vines this is here to remove are exactly what the latter heightmap looks past.
                int top = level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z) - 1;
                int ground = groundHeight(level, x, z);
                for (int cy = top; cy > ground; cy--) {
                    BlockPos pos = new BlockPos(x, cy, z);
                    BlockState state = level.getBlockState(pos);
                    if (!state.isAir() && !isGround(state) && !isSurfaceCover(level, pos)) {
                        level.setBlock(pos, air, Block.UPDATE_ALL);
                    }
                }
            }
        }
    }

    /**
     * True if {@code candidate} is at least {@link #AI_BASE_MIN_SEPARATION} blocks (horizontally) from
     * every base placed so far this bootstrap, so the AI bases never cluster on top of each other.
     * Package-private so {@code GameBootstrapTest} can exercise this pure spread rule.
     */
    static boolean farFromOthers(BlockPos candidate, List<BlockPos> cores) {
        for (BlockPos other : cores) {
            int dx = candidate.getX() - other.getX();
            int dz = candidate.getZ() - other.getZ();
            if (dx * dx + dz * dz < AI_BASE_MIN_SEPARATION * AI_BASE_MIN_SEPARATION) {
                return false;
            }
        }
        return true;
    }

    /**
     * True if the footprint's perimeter is mostly open water — sampled at eight compass points a few
     * blocks out, each at its own surface height. There is no placement-time fluid check elsewhere;
     * the codebase's only fluid pattern is {@code getFluidState().isEmpty()} (see SiegeBlockGoal).
     *
     * <p>Each point is sampled at the block <b>above</b> the ground, not the ground itself: the
     * ground scan passes straight through water down to the bed, so probing the block it lands on
     * would report dry land in the middle of a lake. Sampling from a raw WORLD_SURFACE height was the
     * other way to get this wrong — under a forest it probes a leaf block and never sees water at all.
     */
    private static boolean isSurroundedByWater(ServerLevel level, int cx, int cz) {
        int[][] ring = {{3, 0}, {-3, 0}, {0, 3}, {0, -3}, {3, 3}, {3, -3}, {-3, 3}, {-3, -3}};
        int water = 0;
        for (int[] o : ring) {
            int x = cx + o[0];
            int z = cz + o[1];
            int y = groundHeight(level, x, z) + 1;
            if (!level.getFluidState(new BlockPos(x, y, z)).isEmpty()) {
                water++;
            }
        }
        return water >= 5; // majority of the ring is fluid -> treat as surrounded
    }

    /**
     * Median surface height across a building's footprint, so the building settles into the terrain
     * instead of standing on stilts on its tallest column. Site prep squares up whatever the median
     * leaves behind in both directions: the head-room clear cuts back the uphill ground that now
     * rises above the platform, and the support fill (for the buildings that ask for one) closes the
     * gap under the downhill columns. On flat terrain this is unchanged from the old highest-column
     * rule.
     *
     * <p>Median rather than mean because it is the outliers that decide where a base ends up
     * standing, and a footprint's outliers are exactly the terrain it should ignore: one pillar, one
     * tree-stump column or one ravine mouth clipping a corner drags a mean off the ground the rest of
     * the square is actually sitting on, while the median stays on the level the bulk of the columns
     * agree about. The square is {@code (2 * radius + 1)^2} columns, always odd, so the median is a
     * single sampled height and never an interpolation.
     *
     * <p>This is the Y of the ground itself — the block the building stands <i>on</i>, not the one it
     * occupies; see {@link #platformHeight} for the layer above it that actually gets stamped.
     *
     * <p>Pure and probe-driven (no level) so the geometry is unit-testable, same pattern as
     * {@link #scanToGround} and
     * {@link net.bitflora.asteriskcraft.building.BuildingTemplates#planSitePrep}. The probe is called
     * with footprint-relative offsets and walks the full square, matching the square the template is
     * actually stamped over rather than the disc {@link #clearTrees} sweeps.
     */
    static int medianGround(int radius, IntBinaryOperator columnHeight) {
        int span = 2 * radius + 1;
        int[] heights = new int[span * span];
        int count = 0;
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                heights[count++] = columnHeight.applyAsInt(dx, dz);
            }
        }
        Arrays.sort(heights);
        return heights[count / 2];
    }

    /**
     * The origin Y to stamp a building at: one above {@link #medianGround}, because
     * {@link net.bitflora.asteriskcraft.building.BuildingTemplates#place} lays the template's bottom
     * layer <i>at</i> the origin's height. Passing the median itself sank every building a block, its
     * platform replacing the surface it was meant to be resting on.
     */
    private static int platformHeight(ServerLevel level, int cx, int cz, int radius) {
        return medianGround(radius, (dx, dz) -> surfaceHeight(level, cx + dx, cz + dz)) + 1;
    }

    /**
     * Walks up from {@code ground} for as long as the block above is cover, stopping after
     * {@code limit} steps, and returns the last Y — i.e. the top of the water or ice lying over a
     * submerged column, or {@code ground} untouched on dry land. Pure and probe-driven for the same
     * reason {@link #scanToGround} is.
     */
    static int riseThroughCover(int ground, int limit, IntPredicate isCover) {
        int y = ground;
        while (y - ground < limit && isCover.test(y + 1)) {
            y++;
        }
        return y;
    }

    /**
     * True if this block is a water or ice surface a building should stand <i>on</i> rather than sink
     * through. Ice needs stating because {@link #isGround} rejects it: {@link Blocks#ICE} and
     * {@link Blocks#FROSTED_ICE} are declared {@code noOcclusion()} in vanilla, so
     * {@code isSolidRender} says no and the ground scan walks straight through a frozen lake to its
     * bed (the two hard ices, packed and blue, do occlude and are already ground — the tag covers all
     * four either way, and matching on the tag rather than the two soft blocks means a modded ice is
     * in as well). The fluid test is a state question, not a block one, so it stays separate: a
     * waterlogged block is not "water" here.
     */
    private static boolean isSurfaceCover(ServerLevel level, BlockPos pos) {
        return !level.getFluidState(pos).isEmpty() || level.getBlockState(pos).is(BlockTags.ICE);
    }

    /**
     * The height a column contributes to {@link #medianGround}: its ground, but one under water or
     * ice reported at the top of that cover rather than at the bed beneath it — a base on a lake or a
     * frozen one stands on the surface, the way anything walking up to it does.
     * {@link #groundHeight} deliberately scans straight through both down to the bed (which is what
     * {@link #isSurroundedByWater} needs), and folding that raw value in would drag a base built along
     * a shoreline down under the waterline.
     *
     * <p>The climb tests water and ice together rather than one after the other, because a frozen
     * lake is both: ice on top with water beneath it. Rising through the fluid alone stopped a block
     * short, under the ice, and rising through the ice alone never left the lakebed.
     */
    private static int surfaceHeight(ServerLevel level, int x, int z) {
        int ground = groundHeight(level, x, z);
        return riseThroughCover(ground, MAX_WATER_RISE, y -> isSurfaceCover(level, new BlockPos(x, y, z)));
    }

    /** True if this surface block is natural ground a base's cover should overrun. */
    static boolean isCoverableGround(BlockState state) {
        return COVERABLE_GROUND.contains(state.getBlock());
    }

    /**
     * Spreads a race's ground cover — Zerg "creep" — over the natural ground surface within
     * {@link #AI_BASE_COVER_RADIUS} of a new AI base: every exposed grass/stone/sand/red-sand/dirt/
     * gravel surface block (air directly above) becomes the race's cover block. A race that lays
     * none ({@link RaceProfile#creep()} null) returns immediately, leaving the terrain untouched.
     *
     * <p>Called from {@link #placeAiBaseAt} before the resource garden is seeded, so the garden's
     * ore/log nodes — placed afterward — are not themselves overrun. Goes through
     * {@link #groundHeight} rather than a raw heightmap so the cover reaches the forest floor; read
     * off WORLD_SURFACE it targeted a leaf block, which isn't coverable, and a wooded base got no
     * cover at all.
     */
    private static void coverGround(ServerLevel level, int cx, int cz, RaceProfile profile) {
        if (profile.creep() == null) {
            return;
        }
        BlockState cover = profile.creep().get();
        for (int dx = -AI_BASE_COVER_RADIUS; dx <= AI_BASE_COVER_RADIUS; dx++) {
            for (int dz = -AI_BASE_COVER_RADIUS; dz <= AI_BASE_COVER_RADIUS; dz++) {
                if (dx * dx + dz * dz > AI_BASE_COVER_RADIUS * AI_BASE_COVER_RADIUS) {
                    continue;
                }
                int x = cx + dx;
                int z = cz + dz;
                int y = groundHeight(level, x, z);
                BlockPos pos = new BlockPos(x, y, z);
                if (isCoverableGround(level.getBlockState(pos)) && level.getBlockState(pos.above()).isAir()) {
                    level.setBlock(pos, cover, Block.UPDATE_ALL);
                }
            }
        }
    }

    private static @Nullable BlockPos placeAiBaseAt(ServerLevel level, int x, int y, int z,
            RaceProfile profile, Faction faction) {
        BlockPos origin = new BlockPos(x, y, z);
        clearTrees(level, x, z, AI_BASE_COVER_RADIUS);
        // Ground cover first, building second: coverGround rewrites exposed surface blocks, and the
        // Hive's own template has dirt speckled through its mycelium that the sweep would otherwise
        // eat. A race that lays no cover skips this.
        coverGround(level, x, z, profile);
        // The support fill matters here because medianGround settles a pre-placed base at its
        // footprint's median height: the downhill half of a slope still falls away beneath it and
        // would otherwise overhang open air.
        BuildingTemplates.Placed placed = BuildingTemplates.place(level, origin, profile.baseTemplate(),
                profile.coreBlock().get(), support(profile));
        if (placed == null) {
            return null;
        }
        // No warp scaffold either: an AI base is pre-placed, with no warp phase to fill in over.
        BlockPos core = placed.core();
        if (level.getBlockEntity(core) instanceof BaseBlockEntity base) {
            base.setFaction(faction);
            spawnStartingWorkers(level, core, profile, faction, INITIAL_WORKERS_PER_AI_BASE);
            // Static defence planted with each base, so an early rush meets something with teeth
            // even when the army is out on a wave. Which units, and how many, is the race's own.
            for (RaceProfile.BaseDefence defence : profile.baseDefences()) {
                for (int i = 0; i < defence.count(); i++) {
                    UnitSpawns.spawn(level, core, defence.type().get(), faction, false);
                }
            }
        }
        return core;
    }

    /**
     * Seeds the computer player's shared army bank once (not per-base — every base is a linked
     * chest onto the same pool) with enough resources to bankroll its first workers and the
     * director's early waves. What that is comes off the race's own profile.
     */
    private static void seedAiArmyBank(ServerLevel level, RaceProfile profile, Faction faction) {
        NonNullList<ItemStack> bank = ArmyBank.of(level, faction);
        int slot = 0;
        for (RaceProfile.StartingStack stack : profile.startingBank()) {
            slot = seedStacks(bank, slot, stack.item().get(), stack.amount());
        }
    }

    /**
     * Fills consecutive bank slots starting at {@code slot} with {@code amount} of {@code item},
     * each slot capped at the item's max stack size (64 for these resources) — a naive single
     * split assuming the leftover fits in one slot silently produces oversized stacks once a
     * total exceeds twice the max stack size, which then fails to (de)serialize.
     */
    private static int seedStacks(NonNullList<ItemStack> bank, int slot, net.minecraft.world.item.Item item, int amount) {
        int maxStackSize = new ItemStack(item).getMaxStackSize();
        int remaining = amount;
        while (remaining > 0) {
            int count = Math.min(remaining, maxStackSize);
            bank.set(slot++, new ItemStack(item, count));
            remaining -= count;
        }
        return slot;
    }

    /**
     * Exposes a handful of surface harvestable blocks near an AI base so its workers can keep
     * mining. Seeded at {@link #groundHeight} so the nodes land on the floor within reach of a
     * worker rather than up in a canopy.
     */
    private static void seedResourceGarden(ServerLevel level, int cx, int cz) {
        BlockState[] nodes = {
                Blocks.STONE.defaultBlockState(), Blocks.STONE.defaultBlockState(), Blocks.STONE.defaultBlockState(),
                Blocks.OAK_LOG.defaultBlockState(), Blocks.OAK_LOG.defaultBlockState(), Blocks.OAK_LOG.defaultBlockState(),
                Blocks.IRON_ORE.defaultBlockState(), Blocks.IRON_ORE.defaultBlockState(), Blocks.IRON_ORE.defaultBlockState()};
        int[][] offsets = {{5, 0}, {6, 2}, {5, -2}, {-5, 0}, {-6, 2}, {-5, -2}, {0, 5}, {2, 6}, {-2, 5}};
        for (int i = 0; i < offsets.length; i++) {
            int x = cx + offsets[i][0];
            int z = cz + offsets[i][1];
            int y = groundHeight(level, x, z);
            level.setBlock(new BlockPos(x, y, z), nodes[i], Block.UPDATE_ALL);
        }
    }

    /**
     * Lays an unbroken ring of stone around an AI base at {@link #AI_BASE_STONE_RING_RADIUS}, so
     * its workers have a mineable seam that lasts rather than exhausting the handful of scattered
     * {@link #seedResourceGarden} nodes and then standing idle.
     *
     * <p>A column joins the ring when its distance from the centre <i>rounds</i> to the radius,
     * which is what makes the circle continuous: a strict {@code dist == radius} test leaves gaps
     * wherever the circle passes between block centres. Placed at {@link #groundHeight} like the
     * garden nodes, so the ring follows the terrain instead of hanging over a slope.
     */
    private static void seedStoneRing(ServerLevel level, int cx, int cz) {
        BlockState stone = Blocks.STONE.defaultBlockState();
        for (int dx = -AI_BASE_STONE_RING_RADIUS; dx <= AI_BASE_STONE_RING_RADIUS; dx++) {
            for (int dz = -AI_BASE_STONE_RING_RADIUS; dz <= AI_BASE_STONE_RING_RADIUS; dz++) {
                if (!isOnStoneRing(dx, dz)) {
                    continue;
                }
                int x = cx + dx;
                int z = cz + dz;
                int y = groundHeight(level, x, z);
                level.setBlock(new BlockPos(x, y, z), stone, Block.UPDATE_ALL);
            }
        }
    }

    /** True if this offset from a base's centre belongs to the stone ring. Pure, so it is unit-testable. */
    static boolean isOnStoneRing(int dx, int dz) {
        return Math.round(Math.sqrt(dx * dx + dz * dz)) == AI_BASE_STONE_RING_RADIUS;
    }

    /**
     * Warps in a base's starting workers, so a new world begins with a small worker line already
     * gathering instead of an idle base. Mirrors {@code BaseBlockEntity.spawnWorker}: each worker is
     * tagged with its side and homed on the core it came from.
     */
    private static void spawnStartingWorkers(ServerLevel level, BlockPos core, RaceProfile profile, Faction faction) {
        spawnStartingWorkers(level, core, profile, faction, INITIAL_WORKERS);
    }

    private static void spawnStartingWorkers(ServerLevel level, BlockPos core, RaceProfile profile,
            Faction faction, int count) {
        for (int i = 0; i < count; i++) {
            Mob unit = UnitSpawns.spawn(level, core, profile.worker().type(), faction, false);
            if (unit instanceof WorkerEntity worker) {
                worker.setHomePos(core);
            }
        }
    }

    /**
     * Seeds the player's army bank — reached through their base, which is a linked chest onto it —
     * with enough resources to bankroll their first workers.
     */
    private static void seedPlayerBase(BaseBlockEntity base) {
        base.setItem(0, new ItemStack(Items.OAK_LOG, 64));
        base.setItem(1, new ItemStack(Items.OAK_LOG, STARTING_LOGS - 64));
        base.setItem(2, new ItemStack(Items.COBBLESTONE, 64));
        base.setItem(3, new ItemStack(Items.COBBLESTONE, STARTING_COBBLESTONE - 64));
    }
}
