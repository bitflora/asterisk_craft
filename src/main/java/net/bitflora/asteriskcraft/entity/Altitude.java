package net.bitflora.asteriskcraft.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The one place that decides what "in the air" means for targeting: a target counts as air when it is
 * {@link #AIR_CLEARANCE} or more blocks above solid footing.
 *
 * <p><b>Not the same thing as {@link Flyer#isAir}</b>, and the two must never be substituted for each
 * other. {@code Flyer} is a <em>unit-type</em> marker — a Mutalisk is an air unit whether or not it
 * happens to be high up, which is what an anti-air damage bonus multiplies against. This is a
 * <em>positional</em> test, re-evaluated every time it is asked: a Mutalisk that drops to tree height
 * is not in the air by this rule, and a Zealot falling off a cliff is.
 *
 * <p>Only {@link #AIR_CLEARANCE} blocks are probed — four lookups, cheap enough to sit inside a
 * targeting predicate. Footing is {@code isFaceSturdy(..., Direction.UP)}, the same "is this real
 * ground" test {@code entity.ai.zerg.SunkenSpikeGoal} uses when it hunts for a surface to plant a
 * spike on, so a slab, a fence top or a leaf ledge is judged consistently across the mod. Water is not
 * sturdy, so something swimming over deep water reads as airborne — the honest consequence of a purely
 * positional rule, deliberately not special-cased.
 */
public final class Altitude {
    /** Blocks of clear space beneath a target before it counts as an air target. */
    public static final int AIR_CLEARANCE = 4;

    private Altitude() {
    }

    /** Whether {@code entity} is {@link #AIR_CLEARANCE} or more blocks above the nearest solid footing. */
    public static boolean isAirborne(Entity entity) {
        return isAirborne(entity.level(), entity.getX(), entity.getBoundingBox().minY, entity.getZ());
    }

    /**
     * The rule itself, over a bare {@link BlockGetter} so it is unit-testable without a live level.
     * Measured from the target's <em>feet</em> ({@code getBoundingBox().minY}), never its eyes or its
     * block position, so a tall mob isn't airborne merely for being tall.
     */
    static boolean isAirborne(BlockGetter blocks, double x, double feetY, double z) {
        BlockPos.MutableBlockPos pos = BlockPos.containing(x, feetY, z).mutable();
        int feetBlockY = Mth.floor(feetY);
        for (int depth = 1; depth <= AIR_CLEARANCE; depth++) {
            pos.setY(feetBlockY - depth);
            BlockState state = blocks.getBlockState(pos);
            if (state.isFaceSturdy(blocks, pos, Direction.UP)) {
                return false;
            }
        }
        return true;
    }
}
