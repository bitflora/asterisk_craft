package net.bitflora.asteriskcraft.building;

import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The creep prerequisite's geometry, exercised through the pure overload that takes the creep
 * sources outright instead of a level — the same split {@link PsiFieldTest} uses for the Pylon
 * prerequisite, so the rule is testable without a running server. Which block actually counts as
 * creep, and which entities/block entities actually count as a source, are level-dependent questions
 * verified in-game instead.
 */
class CreepFieldTest {

    private static boolean inRangeOf(BlockPos origin, BlockPos... sources) {
        return CreepField.inRange(origin, List.of(sources));
    }

    @Test
    void noSourceAnywhereIsNotInRange() {
        assertFalse(inRangeOf(BlockPos.ZERO));
    }

    @Test
    void aSourceUnderfootIsInRange() {
        assertTrue(inRangeOf(BlockPos.ZERO, BlockPos.ZERO));
    }

    @Test
    void reachesExactlyToTheRadius() {
        assertTrue(inRangeOf(BlockPos.ZERO, new BlockPos(CreepField.RADIUS, 0, 0)));
        assertFalse(inRangeOf(BlockPos.ZERO, new BlockPos(CreepField.RADIUS + 1, 0, 0)));
    }

    @Test
    void reachesVerticallyAsWellAsHorizontally() {
        assertTrue(inRangeOf(BlockPos.ZERO, new BlockPos(0, CreepField.RADIUS, 0)));
        assertTrue(inRangeOf(BlockPos.ZERO, new BlockPos(0, -CreepField.RADIUS, 0)));
    }

    /** The range is a sphere, not a box: a source at the corner of the enclosing cube is out. */
    @Test
    void aCornerOfTheEnclosingCubeIsOutOfRange() {
        BlockPos corner = new BlockPos(CreepField.RADIUS, CreepField.RADIUS, CreepField.RADIUS);
        assertFalse(inRangeOf(BlockPos.ZERO, corner));
    }

    @Test
    void oneOfSeveralSourcesInRangeIsEnough() {
        assertTrue(inRangeOf(BlockPos.ZERO,
                new BlockPos(500, 70, 500),
                new BlockPos(3, 1, -2)));
    }

    @Test
    void rangeIsMeasuredFromTheOriginNotTheWorldCentre() {
        BlockPos origin = new BlockPos(1000, 64, -1000);
        assertTrue(inRangeOf(origin, origin.offset(4, 2, -3)));
        assertFalse(inRangeOf(origin, origin.offset(30, 0, 0)));
    }
}
