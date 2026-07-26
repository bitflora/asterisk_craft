package net.bitflora.asteriskcraft.game;

import net.bitflora.asteriskcraft.AsteriskCraft;
import net.bitflora.asteriskcraft.building.ArmyBank;
import net.bitflora.asteriskcraft.building.BuildingTemplates;
import net.bitflora.asteriskcraft.building.HiveBlockEntity;
import net.bitflora.asteriskcraft.building.NexusBlockEntity;
import net.bitflora.asteriskcraft.building.UnitSpawns;
import net.bitflora.asteriskcraft.command.CommandAttachments;
import net.bitflora.asteriskcraft.entity.protoss.ProbeEntity;
import net.bitflora.asteriskcraft.entity.zerg.DroneEntity;
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
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Places the player's Nexus (seeded with starting resources) the first time someone joins a
 * new world. We do this on first join (rather than at server start) because the player's
 * chunk is guaranteed loaded with a settled surface height — placing at server start
 * can land the structure at the world floor before terrain is ready. From the player's
 * point of view the world still "starts" with a Nexus already standing (R1).
 */
@EventBusSubscriber(modid = AsteriskCraft.MODID)
public final class GameBootstrap {
    private static final int NEXUS_OFFSET = 6;
    private static final int STARTING_LOGS = 100;
    private static final int STARTING_COBBLESTONE = 100;
    private static final int INITIAL_PROBES = 4;
    // How far past the Nexus's own footprint to clear trees, so the starting base is never stamped
    // into — or left standing under — a tree, just like the Hives.
    private static final int NEXUS_CLEAR_MARGIN = 2;

    // Each AI Hive is planted in its own random direction around the Nexus, at a random distance
    // in this range. The max stays inside typical simulation distance so wave units and Drones
    // never freeze in unloaded chunks (V3 keeps chunk tickets out of scope; see docs/shaping.md).
    private static final int HIVE_COUNT = 3;
    private static final int HIVE_MIN_DISTANCE = 95;
    private static final int HIVE_MAX_DISTANCE = 120;
    // WORLD_SURFACE reports the topmost solid block in a column, which can be far underground
    // if that column happens to be a cave/ravine breach — retry with a fresh angle/distance
    // rather than bury a Hive at the bottom of a sinkhole.
    private static final int HIVE_PLACEMENT_ATTEMPTS = 8;
    private static final int SURFACE_CLEARANCE = 12;
    // Hive spread is drawn from an RNG seeded off the world seed (so the same seed reproduces the same
    // base), XORed with this fixed salt to decorrelate the spread from vanilla terrain features that
    // also key off the seed.
    private static final long HIVE_PLACEMENT_SALT = 0x5A3E_2B17L;
    // Keep Hives from clustering: a candidate must be at least this many blocks from every Hive
    // already placed this bootstrap.
    private static final int HIVE_MIN_SEPARATION = 15;
    // If a candidate would sit in open water, re-roll a fresh spot this many times before finally
    // settling for a watery one rather than looping forever.
    private static final int WATER_RETRY_LIMIT = 3;
    // Total seeded into the shared Zerg army bank once, matching the old 128/128/48-per-Hive x3
    // total (each Hive used to hold its own independent stock before they shared one pool).
    private static final int STARTING_ZERG_LOGS = 128 * 3;
    private static final int STARTING_ZERG_COBBLE = 128 * 3;
    private static final int STARTING_ZERG_IRON = 48 * 3;
    private static final int INITIAL_DRONES_PER_HIVE = 2;
    // Static defence planted with each Hive, so an early rush into a Zerg base meets something with
    // teeth even when its army is out on a wave.
    private static final int SUNKEN_COLONIES_PER_HIVE = 1;
    // Zerg "creep": every exposed natural-ground surface block within this radius of a Hive is
    // overrun with mycelium.
    private static final int HIVE_INFEST_RADIUS = 10;
    private static final Set<Block> INFESTABLE_GROUND = Set.of(
            Blocks.GRASS_BLOCK, Blocks.STONE, Blocks.SAND, Blocks.RED_SAND, Blocks.DIRT, Blocks.GRAVEL);
    // How far down to scan past a tree's logs/leaves when locating the real ground beneath it —
    // comfortably taller than any vanilla tree so we always reach the floor.
    private static final int MAX_TREE_SCAN = 40;

    private GameBootstrap() {
    }

    @SubscribeEvent
    public static void onPlayerJoin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        ServerLevel overworld = player.level().getServer().overworld();

        // Set on every login (not just first-join bootstrap) so players who joined before
        // enemy-vs-player combat existed also pick up their faction.
        FactionAttachments.set(player, Faction.PROTOSS);

        if (!overworld.getData(GameAttachments.BOOTSTRAPPED)) {
            placeStartingBase(overworld, player);
        }

        BlockPos nexus = overworld.getData(GameAttachments.NEXUS_POS);
        player.sendSystemMessage(Component.translatable(
                "message.asteriskcraft.nexus_location", nexus.getX(), nexus.getY(), nexus.getZ()));
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

    private static void placeStartingBase(ServerLevel level, ServerPlayer player) {
        int x = player.blockPosition().getX() + NEXUS_OFFSET;
        int z = player.blockPosition().getZ() + NEXUS_OFFSET;
        // The Nexus's footprint comes from its structure template, so redesigning the building in
        // Blockbench/a structure block automatically widens the ground work below.
        Vec3i size = BuildingTemplates.size(level, BuildingTemplates.NEXUS);
        if (size == null) {
            // A missing template is a broken install, not a crash: bail before touching the terrain
            // and leave the world un-bootstrapped, so a fixed jar still places the base on the next join.
            AsteriskCraft.LOGGER.error("AsteriskCraft: could not place the starting Nexus");
            return;
        }
        int footprint = BuildingTemplates.footprintRadius(size);
        // Scan past any tree canopy to the real ground (WORLD_SURFACE counts leaves/logs as the surface,
        // which used to leave the Nexus perched high up in a tree) and clear the trees over the footprint.
        clearTrees(level, x, z, footprint + NEXUS_CLEAR_MARGIN);
        int y = highestGround(level, x, z, footprint);
        BlockPos origin = new BlockPos(x, y, z);

        // No support fill under the Nexus: its end-stone-brick platform is the base, and a quartz
        // apron poking out from under it read as a mistake.
        BlockPos core = BuildingTemplates.place(level, origin, BuildingTemplates.NEXUS,
                AsteriskCraft.NEXUS_CORE.get(), null);
        if (core == null) {
            AsteriskCraft.LOGGER.error("AsteriskCraft: the Nexus template holds no core block");
            return;
        }

        level.setData(GameAttachments.NEXUS_POS, core);
        if (level.getBlockEntity(core) instanceof NexusBlockEntity nexus) {
            seedNexus(nexus);
        }
        spawnStartingProbes(level, core);

        placeZergBase(level, x, z);

        level.setData(GameAttachments.BOOTSTRAPPED, true);

        // The Command Crystal enables unit select/order mode while held (R5).
        player.getInventory().add(new ItemStack(AsteriskCraft.CURSOR.get()));
        player.getInventory().add(new ItemStack(AsteriskCraft.GATEWAY_KIT.get()));
        player.getInventory().add(new ItemStack(AsteriskCraft.PHOTON_CANNON_KIT.get()));
        player.sendSystemMessage(Component.translatable("message.asteriskcraft.zerg_location"));

        AsteriskCraft.LOGGER.info("AsteriskCraft: placed Nexus core at {}", core);
    }

    /**
     * Stamps the AI Zerg's Hives (plus a small resource garden and starter Drones), each in its
     * own random direction and distance from the Nexus, so the enemy presence surrounds the
     * player instead of sitting in one predictable cluster.
     */
    private static void placeZergBase(ServerLevel level, int nexusX, int nexusZ) {
        // Seed off the world seed so the same seed always reproduces the same Hive layout (the Nexus
        // location is itself seed-derived), instead of drawing from the level's shared, non-reproducible RNG.
        RandomSource random = RandomSource.create(level.getSeed() ^ HIVE_PLACEMENT_SALT);
        Vec3i hiveSize = BuildingTemplates.size(level, BuildingTemplates.HIVE);
        if (hiveSize == null) {
            AsteriskCraft.LOGGER.error("AsteriskCraft: could not place the Zerg Hives");
            return;
        }
        int footprint = BuildingTemplates.footprintRadius(hiveSize);
        List<BlockPos> cores = new ArrayList<>();
        for (int i = 0; i < HIVE_COUNT; i++) {
            BlockPos chosen = null;
            BlockPos fallback = null; // last candidate seen; used if nothing fully passes
            int waterRejects = 0;
            for (int attempt = 0; attempt < HIVE_PLACEMENT_ATTEMPTS; attempt++) {
                float angle = random.nextFloat() * Mth.TWO_PI;
                int distance = Mth.nextInt(random, HIVE_MIN_DISTANCE, HIVE_MAX_DISTANCE);
                int x = nexusX + Math.round(Mth.cos(angle) * distance);
                int z = nexusZ + Math.round(Mth.sin(angle) * distance);
                int y = highestGround(level, x, z, footprint);
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
            BlockPos core = placeHive(level, chosen.getX(), chosen.getY(), chosen.getZ());
            if (core != null) {
                cores.add(core);
            }
            seedResourceGarden(level, chosen.getX(), chosen.getZ());
        }
        level.setData(GameAttachments.HIVE_POSITIONS, List.copyOf(cores));
        seedZergArmyBank(level);
        AsteriskCraft.LOGGER.info("AsteriskCraft: placed {} Zerg Hives scattered around {},{}",
                cores.size(), nexusX, nexusZ);
    }

    /**
     * True if the column above (x, y, z) is open to the sky, distinguishing real outdoor ground
     * from the floor of a cave or ravine that happens to breach the surface (which WORLD_SURFACE
     * would otherwise report as valid, burying the structure underground).
     */
    private static boolean hasOpenSky(ServerLevel level, int x, int y, int z) {
        for (int dy = 2; dy <= SURFACE_CLEARANCE; dy++) {
            BlockState state = level.getBlockState(new BlockPos(x, y + dy, z));
            // Tree logs/leaves overhead are fine — they get cleared before the mound is stamped
            // (see clearTrees) — so only a real solid overhang (cave/ravine roof) rejects the spot.
            if (state.isSolidRender() && !isVegetation(state)) {
                return false;
            }
        }
        return true;
    }

    /** True if this block is part of a tree (log or leaves), which the Hive placement scans past and clears. */
    private static boolean isVegetation(BlockState state) {
        return state.is(BlockTags.LOGS) || state.is(BlockTags.LEAVES);
    }

    /**
     * Topmost solid ground block in a column, scanning down past tree logs and leaves so a Hive lands
     * on the ground beneath a tree instead of on its canopy (the WORLD_SURFACE heightmap counts the
     * canopy as the surface, which is why Hives were ending up in trees).
     */
    private static int groundHeight(ServerLevel level, int x, int z) {
        int top = level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z) - 1;
        for (int gy = top; gy > top - MAX_TREE_SCAN; gy--) {
            BlockState state = level.getBlockState(new BlockPos(x, gy, z));
            if (!state.isAir() && !isVegetation(state)) {
                return gy;
            }
        }
        return top;
    }

    /**
     * Clears tree logs and leaves in the creep disc around a Hive, so the mound is never stamped into
     * a tree and no canopy is left floating over the finished base. Called from {@link #placeHive}
     * before the layout is placed.
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
                int top = level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z) - 1;
                int ground = groundHeight(level, x, z);
                for (int cy = top; cy > ground; cy--) {
                    BlockPos pos = new BlockPos(x, cy, z);
                    if (isVegetation(level.getBlockState(pos))) {
                        level.setBlock(pos, air, Block.UPDATE_ALL);
                    }
                }
            }
        }
    }

    /**
     * True if {@code candidate} is at least {@link #HIVE_MIN_SEPARATION} blocks (horizontally) from
     * every Hive placed so far this bootstrap, so the three Hives never cluster on top of each other.
     * Package-private so {@code GameBootstrapTest} can exercise this pure spread rule.
     */
    static boolean farFromOthers(BlockPos candidate, List<BlockPos> cores) {
        for (BlockPos other : cores) {
            int dx = candidate.getX() - other.getX();
            int dz = candidate.getZ() - other.getZ();
            if (dx * dx + dz * dz < HIVE_MIN_SEPARATION * HIVE_MIN_SEPARATION) {
                return false;
            }
        }
        return true;
    }

    /**
     * True if the footprint's perimeter is mostly open water — sampled at eight compass points a few
     * blocks out, each at its own surface height. There is no placement-time fluid check elsewhere;
     * the codebase's only fluid pattern is {@code getFluidState().isEmpty()} (see SiegeBlockGoal).
     */
    private static boolean isSurroundedByWater(ServerLevel level, int cx, int cz) {
        int[][] ring = {{3, 0}, {-3, 0}, {0, 3}, {0, -3}, {3, 3}, {3, -3}, {-3, 3}, {-3, -3}};
        int water = 0;
        for (int[] o : ring) {
            int x = cx + o[0];
            int z = cz + o[1];
            int y = level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z) - 1;
            if (!level.getFluidState(new BlockPos(x, y, z)).isEmpty()) {
                water++;
            }
        }
        return water >= 5; // majority of the ring is fluid -> treat as surrounded
    }

    /**
     * Highest solid surface (top-block Y) across a building's footprint, so it rests on top of the
     * highest ground and never sinks below its own neighbours in a dip or a super-flat step (which
     * reads as a hole). On flat terrain this equals the center column's {@code WORLD_SURFACE - 1}.
     */
    private static int highestGround(ServerLevel level, int cx, int cz, int radius) {
        int max = Integer.MIN_VALUE;
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                max = Math.max(max, groundHeight(level, cx + dx, cz + dz));
            }
        }
        return max;
    }

    /** True if this surface block is natural ground the Hive's creep should overrun with mycelium. */
    static boolean isInfestableGround(BlockState state) {
        return INFESTABLE_GROUND.contains(state.getBlock());
    }

    /**
     * Spreads Zerg "creep" over the natural ground surface within {@link #HIVE_INFEST_RADIUS} of a
     * Hive: every exposed grass/stone/sand/red-sand/dirt/gravel surface block (air directly above)
     * becomes mycelium. Called from {@link #placeHive} before the resource garden is seeded, so the
     * garden's ore/log nodes — placed afterward — are not themselves overrun.
     */
    private static void infestGround(ServerLevel level, int cx, int cz) {
        BlockState mycelium = Blocks.MYCELIUM.defaultBlockState();
        for (int dx = -HIVE_INFEST_RADIUS; dx <= HIVE_INFEST_RADIUS; dx++) {
            for (int dz = -HIVE_INFEST_RADIUS; dz <= HIVE_INFEST_RADIUS; dz++) {
                if (dx * dx + dz * dz > HIVE_INFEST_RADIUS * HIVE_INFEST_RADIUS) {
                    continue;
                }
                int x = cx + dx;
                int z = cz + dz;
                int y = level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z) - 1;
                BlockPos pos = new BlockPos(x, y, z);
                if (isInfestableGround(level.getBlockState(pos)) && level.getBlockState(pos.above()).isAir()) {
                    level.setBlock(pos, mycelium, Block.UPDATE_ALL);
                }
            }
        }
    }

    private static @Nullable BlockPos placeHive(ServerLevel level, int x, int y, int z) {
        BlockPos origin = new BlockPos(x, y, z);
        clearTrees(level, x, z, HIVE_INFEST_RADIUS);
        // Creep first, mound second: infestGround rewrites exposed surface blocks, and the Hive's
        // own template has dirt speckled through its mycelium that the sweep would otherwise eat.
        infestGround(level, x, z);
        // The mound keeps a mycelium footing under it — same material as the creep it sits in, so
        // unlike the Protoss stonework there is nothing foreign to see.
        BlockPos core = BuildingTemplates.place(level, origin, BuildingTemplates.HIVE,
                AsteriskCraft.HIVE_CORE.get(), Blocks.MYCELIUM.defaultBlockState());
        if (core == null) {
            return null;
        }
        if (level.getBlockEntity(core) instanceof HiveBlockEntity hive) {
            hive.setFaction(Faction.ZERG);
            for (int i = 0; i < INITIAL_DRONES_PER_HIVE; i++) {
                DroneEntity drone = UnitSpawns.spawn(level, core, AsteriskCraft.DRONE.get(), Faction.ZERG, false);
                if (drone != null) {
                    drone.setHomePos(core);
                }
            }
            for (int i = 0; i < SUNKEN_COLONIES_PER_HIVE; i++) {
                UnitSpawns.spawn(level, core, AsteriskCraft.SUNKEN_COLONY.get(), Faction.ZERG, false);
            }
        }
        return core;
    }

    /**
     * Seeds the shared Zerg army bank once (not per-Hive — all three Hives are linked chests
     * onto the same pool) with enough resources to bankroll the first Drones and the director's
     * early waves.
     */
    private static void seedZergArmyBank(ServerLevel level) {
        NonNullList<ItemStack> bank = ArmyBank.of(level, Faction.ZERG);
        int slot = 0;
        slot = seedStacks(bank, slot, Items.OAK_LOG, STARTING_ZERG_LOGS);
        slot = seedStacks(bank, slot, Items.COBBLESTONE, STARTING_ZERG_COBBLE);
        seedStacks(bank, slot, Items.IRON_INGOT, STARTING_ZERG_IRON);
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

    /** Exposes a handful of surface harvestable blocks near a Hive so its Drones can keep mining. */
    private static void seedResourceGarden(ServerLevel level, int cx, int cz) {
        BlockState[] nodes = {
                Blocks.STONE.defaultBlockState(), Blocks.STONE.defaultBlockState(), Blocks.STONE.defaultBlockState(),
                Blocks.OAK_LOG.defaultBlockState(), Blocks.OAK_LOG.defaultBlockState(), Blocks.OAK_LOG.defaultBlockState(),
                Blocks.IRON_ORE.defaultBlockState(), Blocks.IRON_ORE.defaultBlockState(), Blocks.IRON_ORE.defaultBlockState()};
        int[][] offsets = {{5, 0}, {6, 2}, {5, -2}, {-5, 0}, {-6, 2}, {-5, -2}, {0, 5}, {2, 6}, {-2, 5}};
        for (int i = 0; i < offsets.length; i++) {
            int x = cx + offsets[i][0];
            int z = cz + offsets[i][1];
            int y = level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z) - 1;
            level.setBlock(new BlockPos(x, y, z), nodes[i], Block.UPDATE_ALL);
        }
    }

    /**
     * Warps in the player's starting Probes next to the freshly placed Nexus, so a new world
     * begins with a small worker line already gathering instead of an idle base. Mirrors
     * {@code NexusBlockEntity.spawnProbe}: each Probe is tagged Protoss and homed on the core.
     */
    private static void spawnStartingProbes(ServerLevel level, BlockPos core) {
        for (int i = 0; i < INITIAL_PROBES; i++) {
            ProbeEntity probe = UnitSpawns.spawn(level, core, AsteriskCraft.PROBE.get(), Faction.PROTOSS, false);
            if (probe != null) {
                probe.setHomePos(core);
            }
        }
    }

    /** Seeds the Nexus's own input slots with enough resources to bankroll the first Probes. */
    private static void seedNexus(NexusBlockEntity nexus) {
        nexus.setItem(0, new ItemStack(Items.OAK_LOG, 64));
        nexus.setItem(1, new ItemStack(Items.OAK_LOG, STARTING_LOGS - 64));
        nexus.setItem(2, new ItemStack(Items.COBBLESTONE, 64));
        nexus.setItem(3, new ItemStack(Items.COBBLESTONE, STARTING_COBBLESTONE - 64));
    }
}
