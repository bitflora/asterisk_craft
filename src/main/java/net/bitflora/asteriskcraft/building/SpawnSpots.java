package net.bitflora.asteriskcraft.building;

import net.bitflora.asteriskcraft.entity.Rooted;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.phys.AABB;

import java.util.function.Predicate;

/** Finds an open, safely-footed spot near a building core to place a freshly produced unit. */
public final class SpawnSpots {
    private SpawnSpots() {
    }

    /** Rings searched outward from the core; the building itself occupies the middle. */
    private static final int MIN_RING = 2;
    /**
     * Reaches clear of every building footprint in the mod (the widest, the Hive, is 8x7 and puts
     * its core 4 blocks from one edge), so a unit too wide to fit anywhere on or against a building
     * still has open ground to land on rather than falling through to the last-resort spot.
     */
    private static final int MAX_RING = 6;

    /** Vertical band searched at each ring, relative to the core: a few down, one up. */
    private static final int MIN_DY = -3;
    private static final int MAX_DY = 1;

    /**
     * An open spot near {@code pos} where an entity of {@code type} fits with no part of it inside a
     * block. The type matters: units wider than a block (the Sunken Colony and the Mutalisk, both
     * 1.4) overlap the neighbouring columns, so a spot whose own column is clear can still bury them
     * in the very building they were produced at — and a rooted Sunken Colony can never walk out of
     * it, so it simply suffocates against the Hive.
     */
    public static BlockPos findGroundSpot(ServerLevel level, BlockPos pos, EntityType<?> type) {
        return findGroundSpot(pos, candidate -> fits(level, candidate, type));
    }

    /**
     * The search order — nearest ring first, then downward through the vertical band — separated
     * from the world lookups so it is unit-testable without a live {@link ServerLevel}. Falls back
     * to just above {@code pos} when nothing in range fits.
     */
    static BlockPos findGroundSpot(BlockPos pos, Predicate<BlockPos> fits) {
        for (int r = MIN_RING; r <= MAX_RING; r++) {
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != r) {
                        continue;
                    }
                    for (int dy = MIN_DY; dy <= MAX_DY; dy++) {
                        BlockPos candidate = pos.offset(dx, dy, dz);
                        if (fits.test(candidate)) {
                            return candidate;
                        }
                    }
                }
            }
        }
        return pos.above();
    }

    /**
     * True if a {@code type} standing centred on {@code spot} has solid footing, its whole body —
     * not just the column it stands in — is clear of blocks, and nothing already standing there is
     * unable to get out of the way.
     */
    private static boolean fits(ServerLevel level, BlockPos spot, EntityType<?> type) {
        // The centre column is checked for air specifically, not merely for a lack of collision, so
        // a spot that is only swimmable (water) or on fire stays rejected as it always was.
        if (!level.getBlockState(spot).isAir()
                || !level.getBlockState(spot.above()).isAir()
                || !level.getBlockState(spot.below()).isSolidRender()) {
            return false;
        }
        // Deflated by a hair: a unit exactly as wide as its block sits flush against the neighbouring
        // column's face, which would otherwise read as a collision with it.
        AABB body = type.getSpawnAABB(spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5).deflate(1.0E-4);
        if (!level.noBlockCollision(null, body)) {
            return false;
        }
        // A spot is also taken if a rooted unit is already standing in it. Blocks are not the only
        // thing that can occupy ground, and the search is deterministic — every caller asking for a
        // spot near the same core walks the same rings in the same order and would otherwise be
        // handed the identical block. Two mobile units shove each other apart within a tick, so this
        // never mattered before; two rooted ones stand inside each other forever, which is exactly
        // what a Hive's Sunken and Spore Colony did. Only {@link Rooted} counts, so a Drone milling
        // around the Hive still can't push production out to the far rings.
        return level.getEntities((Entity) null, body, Rooted::isRooted).isEmpty();
    }
}
