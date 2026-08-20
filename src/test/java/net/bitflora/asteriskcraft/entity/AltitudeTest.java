package net.bitflora.asteriskcraft.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The Spore Colony's air rule, which is the only genuinely new logic behind it: a target is airborne
 * when it stands {@link Altitude#AIR_CLEARANCE} or more blocks above solid footing.
 *
 * <p>Driven through the package-private {@link BlockGetter} overload rather than a live entity, so
 * the boundary can be walked one block at a time. Blocks (unlike tags) do resolve in the NeoForge
 * JUnit bootstrap, so {@link Blocks#STONE} is a real sturdy-faced state here.
 */
class AltitudeTest {

    private static final double FEET_Y = 64.0;

    /** A column that is air everywhere except one solid layer at {@code solidY}. */
    private record Column(int solidY) implements BlockGetter {
        @Override
        public BlockState getBlockState(BlockPos pos) {
            return pos.getY() == this.solidY ? Blocks.STONE.defaultBlockState() : Blocks.AIR.defaultBlockState();
        }

        @Override
        public FluidState getFluidState(BlockPos pos) {
            return getBlockState(pos).getFluidState();
        }

        @Nullable
        @Override
        public BlockEntity getBlockEntity(BlockPos pos) {
            return null;
        }

        @Override
        public int getHeight() {
            return 384;
        }

        @Override
        public int getMinY() {
            return -64;
        }
    }

    /** Everything is air — nothing solid within reach in any direction. */
    private record Void_() implements BlockGetter {
        @Override
        public BlockState getBlockState(BlockPos pos) {
            return Blocks.AIR.defaultBlockState();
        }

        @Override
        public FluidState getFluidState(BlockPos pos) {
            return Blocks.AIR.defaultBlockState().getFluidState();
        }

        @Nullable
        @Override
        public BlockEntity getBlockEntity(BlockPos pos) {
            return null;
        }

        @Override
        public int getHeight() {
            return 384;
        }

        @Override
        public int getMinY() {
            return -64;
        }
    }

    /** Airborne-ness with exactly {@code clearBlocks} empty blocks between the feet and solid ground. */
    private static boolean airborneOver(int clearBlocks) {
        return Altitude.isAirborne(new Column((int) FEET_Y - clearBlocks - 1), 0.5, FEET_Y, 0.5);
    }

    @Test
    void somethingStandingOnTheGroundIsNotAirborne() {
        assertFalse(airborneOver(0), "solid directly underfoot");
    }

    @Test
    void theBoundaryIsExactlyFourBlocksOfClearance() {
        assertFalse(airborneOver(1), "one block up is a hop, not flight");
        assertFalse(airborneOver(3), "three clear blocks is one short of the rule");
        assertTrue(airborneOver(Altitude.AIR_CLEARANCE), "four clear blocks is the rule itself");
        assertTrue(airborneOver(10), "well clear of the ground");
    }

    @Test
    void nothingSolidUnderneathAtAllCountsAsAirborne() {
        // A flyer out over a void or an ocean trench: there is no footing to be near.
        assertTrue(Altitude.isAirborne(new Void_(), 0.5, FEET_Y, 0.5));
    }

    @Test
    void aNonSturdyBlockUnderfootIsNotFooting() {
        // Footing is isFaceSturdy(UP), not "any block": a torch (or water, or a fence top) beneath a
        // hovering unit must not ground it.
        BlockGetter torchAtFeetMinusOne = new BlockGetter() {
            @Override
            public BlockState getBlockState(BlockPos pos) {
                return pos.getY() == (int) FEET_Y - 1
                        ? Blocks.TORCH.defaultBlockState()
                        : Blocks.AIR.defaultBlockState();
            }

            @Override
            public FluidState getFluidState(BlockPos pos) {
                return getBlockState(pos).getFluidState();
            }

            @Nullable
            @Override
            public BlockEntity getBlockEntity(BlockPos pos) {
                return null;
            }

            @Override
            public int getHeight() {
                return 384;
            }

            @Override
            public int getMinY() {
                return -64;
            }
        };
        assertTrue(Altitude.isAirborne(torchAtFeetMinusOne, 0.5, FEET_Y, 0.5));
    }

    @Test
    void fractionalFeetHeightStillFindsTheGroundBeneath() {
        // Feet hovering part-way up their own block — mid-step, or a mob with a bobbing render
        // offset. The floor of the feet Y is what the probe starts from, so the block below still
        // grounds it rather than the fraction pushing the whole scan up a level.
        assertFalse(Altitude.isAirborne(new Column((int) FEET_Y - 1), 0.5, FEET_Y + 0.4, 0.5));
    }
}
