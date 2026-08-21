package net.bitflora.asteriskcraft.entity.ai.zerg;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.OptionalDouble;

/**
 * Where a spike coming up out of the ground actually breaks the surface, shared by the two attacks
 * that plant {@code entity.zerg.FangStrikeEntity}s — the Sunken Colony's single strike and the
 * Lurker's row.
 *
 * <p>Ported from vanilla's {@code Evoker$EvokerAttackSpellGoal.createSpellEntity}: walk down the
 * column from {@code maxY} to the first block whose top face is sturdy, then sit the spike on that
 * block's collision height so it doesn't sink into a slab or a stair.
 *
 * <p>Worth being one function rather than two copies because the Lurker asks it once <em>per
 * spine</em>: a row crossing a step plants each spine on its own footing, and the ones that run out
 * over a ledge or a ravine simply don't get planted — which is why this returns an empty answer
 * instead of a fallback height.
 */
public final class GroundStrike {
    /** How far below the spike's spawn column to keep searching for solid footing before giving up. */
    private static final int GROUND_SEARCH_DEPTH = 4;

    private GroundStrike() {
    }

    /**
     * The Y a spike should stand at in the column through {@code (x, z)}, or empty if there is no
     * footing there — the target is airborne, or the column falls away into a void.
     *
     * @param minY the lower of attacker and target feet: how far down the search is allowed to reach
     * @param maxY the higher of the two, plus one: where the search starts
     */
    public static OptionalDouble footingY(Level level, double x, double z, double minY, double maxY) {
        BlockPos pos = BlockPos.containing(x, maxY, z);
        int floor = Mth.floor(minY) - GROUND_SEARCH_DEPTH;

        while (pos.getY() >= floor) {
            BlockPos below = pos.below();
            BlockState belowState = level.getBlockState(below);
            if (belowState.isFaceSturdy(level, below, Direction.UP)) {
                return OptionalDouble.of(pos.getY() + topOffset(level, pos));
            }
            pos = pos.below();
        }
        return OptionalDouble.empty();
    }

    /** How high the block the spike stands <em>in</em> reaches, so it doesn't erupt inside a slab. */
    private static double topOffset(Level level, BlockPos pos) {
        if (level.isEmptyBlock(pos)) {
            return 0.0;
        }
        VoxelShape shape = level.getBlockState(pos).getCollisionShape(level, pos);
        return shape.isEmpty() ? 0.0 : shape.max(Direction.Axis.Y);
    }
}
