package net.bitflora.asteriskcraft.building;

import net.bitflora.asteriskcraft.faction.Faction;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * The Pylon prerequisite: the single answer to "may a building be warped in here", the way
 * {@code faction/FactionAttachments.isHostile} is the single answer to "may this be shot".
 * Every call site that places a Protoss building asks this and nothing else — today
 * {@link BuildingKitItem} (the Gateway) and {@code entity/FactionSpawnEggItem} (the Photon Cannon,
 * which is an entity and so is placed by an egg rather than a kit).
 *
 * <p>Which buildings are exempt is <b>not</b> decided here: it is a flag on the placing item, so a
 * Nexus and a Pylon simply never ask. Nothing in this class names a building.
 *
 * <p>Pylons are found by walking the block entities of the chunks around a point rather than by
 * sweeping every block in range. That is what lets {@code client/PsiFieldOverlay} paint the whole
 * powered area at once: a sweep answers about one position and costs a cube of block reads, while
 * one walk hands back every Pylon and then answers about a thousand ground squares for free.
 *
 * <p>Whether a Pylon counts is read off {@link PylonBlock#ONLINE} — a blockstate, so a client can
 * apply exactly the same test the server does. Its <em>owner</em> is on the block entity and is not
 * synced, which is the one thing only the server can check; see {@link #onlinePylons}.
 */
public final class PsiField {
    private PsiField() {
    }

    /** How far a Pylon's power reaches, measured core to core. */
    public static final int RADIUS = 10;

    /**
     * Whether any of {@code pylons} stands within {@link #RADIUS} of {@code origin}.
     *
     * <p>Pure, so the geometry is unit-testable without a live level — the same split
     * {@link BuildingTemplates#planSitePrep} and {@link SpawnSpots} use.
     */
    public static boolean covered(BlockPos origin, Collection<BlockPos> pylons) {
        for (BlockPos pylon : pylons) {
            if (withinBlocks(pylon, origin, RADIUS)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Inclusive range, unlike {@code BlockPos.closerThan}, which is strictly less-than — "within 10
     * blocks" has to include the block exactly 10 away, or the reach is quietly one short of what
     * every message and doc says it is.
     */
    private static boolean withinBlocks(BlockPos a, BlockPos b, int blocks) {
        return a.distSqr(b) <= (double) blocks * blocks;
    }

    /**
     * Every online Pylon core within {@code searchRadius} of {@code center}, optionally narrowed to
     * one owner.
     *
     * <p>Pass a null {@code owner} to ask the question a <em>client</em> can answer: ownership lives
     * on the block entity and is never synced, so the client-side placement outline and overlay are
     * deliberately owner-blind. That makes them the more permissive of the two, which is the right
     * way round — the preview may say yes where the server says no, never the reverse. The two
     * cannot actually disagree yet, since nothing but the player places a Pylon
     * ({@code director/AiDirector} builds no buildings at all).
     */
    public static List<BlockPos> onlinePylons(Level level, BlockPos center, int searchRadius, @Nullable Faction owner) {
        List<BlockPos> found = new ArrayList<>();
        int minChunkX = SectionPos.blockToSectionCoord(center.getX() - searchRadius);
        int maxChunkX = SectionPos.blockToSectionCoord(center.getX() + searchRadius);
        int minChunkZ = SectionPos.blockToSectionCoord(center.getZ() - searchRadius);
        int maxChunkZ = SectionPos.blockToSectionCoord(center.getZ() + searchRadius);
        for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
            for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
                // Never load a chunk to answer this: a Pylon in unloaded ground could not have been
                // found by sweeping blocks either, so the two agree on the edge case as well.
                LevelChunk chunk = level.getChunkSource().getChunkNow(chunkX, chunkZ);
                if (chunk == null) {
                    continue;
                }
                for (BlockEntity blockEntity : chunk.getBlockEntities().values()) {
                    if (!(blockEntity instanceof PylonBlockEntity pylon)) {
                        continue;
                    }
                    BlockPos pos = pylon.getBlockPos();
                    if (!isOnline(pylon.getBlockState()) || !withinBlocks(pos, center, searchRadius)) {
                        continue;
                    }
                    if (owner == null || pylon.buildingFaction() == owner) {
                        found.add(pos.immutable());
                    }
                }
            }
        }
        return found;
    }

    /** Whether an online Pylon of any owner reaches {@code origin} — all a client can see. */
    public static boolean covered(Level level, BlockPos origin) {
        return covered(origin, onlinePylons(level, origin, RADIUS, null));
    }

    /** The authoritative test: an online Pylon in range that belongs to {@code faction}. */
    public static boolean covered(Level level, BlockPos origin, Faction faction) {
        return covered(origin, onlinePylons(level, origin, RADIUS, faction));
    }

    /** Whether a blockstate is a Pylon core that has finished warping in. */
    public static boolean isOnline(BlockState state) {
        return state.hasProperty(PylonBlock.ONLINE) && state.getValue(PylonBlock.ONLINE);
    }
}
