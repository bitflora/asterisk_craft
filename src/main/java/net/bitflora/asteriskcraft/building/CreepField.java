package net.bitflora.asteriskcraft.building;

import net.bitflora.asteriskcraft.entity.CreepSource;
import net.bitflora.asteriskcraft.faction.Faction;
import net.bitflora.asteriskcraft.faction.FactionAttachments;
import net.bitflora.asteriskcraft.faction.Race;
import net.bitflora.asteriskcraft.race.RaceProfile;
import net.bitflora.asteriskcraft.race.Races;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * The Zerg creep prerequisite: the single answer to "may a Zerg building be placed here", the way
 * {@link PsiField} is the Protoss one. Deliberately a separate, Zerg-named class rather than a
 * generalisation of {@code PsiField} — the two mechanisms happen to share a shape today, but nothing
 * requires them to stay that way, and neither may name the other's race.
 *
 * <p>Placement is allowed within {@link #RADIUS} of <em>any</em> creep source (a Hive, or a Sunken or
 * Spore Colony) <em>or</em> standing on the race's own creep block (mycelium) outright — a razed
 * base leaves its creep behind, and that ground stays buildable for whoever reaches it, unowned.
 * Deliberately faction-blind on both clauses, unlike the Pylon: creep is shared territory in
 * StarCraft, not an army-specific resource, so an enemy colony egg (used for testing, or a future
 * Zerg-mirror PvP match) may go down on or beside creep an ally spread just as freely as its own.
 * Which buildings are exempt is, as with the Pylon, a flag on the placing item and not a case here;
 * nothing in this class names a building.
 *
 * <p>Sources are found by walking chunk block entities and nearby entities around a point, exactly
 * like {@link PsiField#onlinePylons} and for the same reason: one walk answers the whole ground grid
 * {@code client/CreepFieldOverlay} paints, rather than a sweep per square.
 */
public final class CreepField {
    private CreepField() {
    }

    /** How far a creep source's range reaches, measured core to core. */
    public static final int RADIUS = 20;

    /**
     * Whether any of {@code sources} stands within {@link #RADIUS} of {@code origin}.
     *
     * <p>Pure, so the geometry is unit-testable without a live level — see {@link PsiField#covered}.
     */
    public static boolean inRange(BlockPos origin, Collection<BlockPos> sources) {
        for (BlockPos source : sources) {
            if (withinBlocks(source, origin, RADIUS)) {
                return true;
            }
        }
        return false;
    }

    /** Inclusive range — see {@link PsiField}'s identical note on why. */
    private static boolean withinBlocks(BlockPos a, BlockPos b, int blocks) {
        return a.distSqr(b) <= (double) blocks * blocks;
    }

    /**
     * Every creep source belonging to {@code owner} within {@code searchRadius} of {@code center}; a
     * null {@code owner} means any faction. {@link #covered} always passes null — creep is
     * deliberately faction-blind, so both the server's authoritative check and the client overlay ask
     * the same owner-agnostic question. The {@code owner} parameter survives for callers that
     * genuinely want one faction's sources, and because it's the same shape as
     * {@link PsiField#onlinePylons}.
     *
     * <p>Two branches, neither naming a building: a {@link FactionCore} block entity that
     * {@link FactionCore#projectsCreep()} (the Hive, without saying so — a Nexus is a
     * {@link FactionCore} too but its race has no creep, so it is excluded even though Zerg
     * placement is the only caller today), and a {@link CreepSource} entity of the right faction
     * (the two colonies).
     */
    public static List<BlockPos> creepSources(Level level, BlockPos center, int searchRadius, @Nullable Faction owner) {
        List<BlockPos> found = new ArrayList<>();
        int minChunkX = SectionPos.blockToSectionCoord(center.getX() - searchRadius);
        int maxChunkX = SectionPos.blockToSectionCoord(center.getX() + searchRadius);
        int minChunkZ = SectionPos.blockToSectionCoord(center.getZ() - searchRadius);
        int maxChunkZ = SectionPos.blockToSectionCoord(center.getZ() + searchRadius);
        for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
            for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
                // Never load a chunk to answer this — see PsiField#onlinePylons.
                LevelChunk chunk = level.getChunkSource().getChunkNow(chunkX, chunkZ);
                if (chunk == null) {
                    continue;
                }
                for (BlockEntity blockEntity : chunk.getBlockEntities().values()) {
                    if (!(blockEntity instanceof FactionCore core) || !core.projectsCreep()) {
                        continue;
                    }
                    BlockPos pos = blockEntity.getBlockPos();
                    if (!withinBlocks(pos, center, searchRadius)) {
                        continue;
                    }
                    if (owner == null || core.buildingFaction() == owner) {
                        found.add(pos.immutable());
                    }
                }
            }
        }
        AABB box = new AABB(center).inflate(searchRadius);
        List<Mob> colonies = level.getEntitiesOfClass(Mob.class, box, mob -> mob instanceof CreepSource);
        for (Mob colony : colonies) {
            if (owner == null || FactionAttachments.get(colony) == owner) {
                found.add(colony.blockPosition());
            }
        }
        return found;
    }

    /**
     * Whether the ground {@code origin} sits on is {@code race}'s own creep block. Reads the block
     * directly below {@code origin}, which is correct for both placement call sites and the overlay:
     * all three hand in the position <em>above</em> the surface, exactly what
     * {@code Heightmap.Types.MOTION_BLOCKING_NO_LEAVES} reports. Unowned deliberately — creep left
     * behind by a fallen base stays buildable for whoever reaches it.
     */
    public static boolean onCreep(BlockGetter level, BlockPos origin, @Nullable Race race) {
        RaceProfile profile = race == null ? null : Races.of(race);
        if (profile == null || profile.creep() == null) {
            return false;
        }
        BlockState creep = profile.creep().get();
        return level.getBlockState(origin.below()).is(creep.getBlock());
    }

    /**
     * The authoritative test: either clause. {@code race} only picks whose ground counts as creep
     * for the {@link #onCreep} clause — sources are searched for {@code any} owner, since creep
     * coverage doesn't care which army's colony or Hive spread it.
     */
    public static boolean covered(Level level, BlockPos origin, @Nullable Race race) {
        if (onCreep(level, origin, race)) {
            return true;
        }
        return inRange(origin, creepSources(level, origin, RADIUS, null));
    }

    /** Column test for the overlay, given a pre-fetched source list — see {@link #creepSources}. */
    public static boolean covered(BlockGetter level, BlockPos origin, @Nullable Race race,
            Collection<BlockPos> sources) {
        if (onCreep(level, origin, race)) {
            return true;
        }
        return inRange(origin, sources);
    }
}
