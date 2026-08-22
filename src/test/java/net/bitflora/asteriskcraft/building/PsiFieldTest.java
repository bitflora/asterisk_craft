package net.bitflora.asteriskcraft.building;

import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The Pylon prerequisite's geometry, exercised through the pure overload that takes the Pylons
 * outright instead of a level — the same split {@code BuildingTemplates.planSitePrep} and {@link SpawnSpots}
 * use, so the rule is testable without a running server. Which blocks actually count as a powered
 * Pylon is a blockstate question and is verified in-game instead.
 */
class PsiFieldTest {

    private static boolean coveredBy(BlockPos origin, BlockPos... pylons) {
        return PsiField.covered(origin, List.of(pylons));
    }

    @Test
    void noPylonAnywhereIsNotCovered() {
        assertFalse(coveredBy(BlockPos.ZERO));
    }

    @Test
    void aPylonUnderfootCovers() {
        assertTrue(coveredBy(BlockPos.ZERO, BlockPos.ZERO));
    }

    @Test
    void reachesExactlyToTheRadius() {
        assertTrue(coveredBy(BlockPos.ZERO, new BlockPos(PsiField.RADIUS, 0, 0)));
        assertFalse(coveredBy(BlockPos.ZERO, new BlockPos(PsiField.RADIUS + 1, 0, 0)));
    }

    @Test
    void reachesVerticallyAsWellAsHorizontally() {
        assertTrue(coveredBy(BlockPos.ZERO, new BlockPos(0, PsiField.RADIUS, 0)));
        assertTrue(coveredBy(BlockPos.ZERO, new BlockPos(0, -PsiField.RADIUS, 0)));
    }

    /** The range is a sphere, not a box: a Pylon at the corner of the enclosing cube is out. */
    @Test
    void aCornerOfTheEnclosingCubeIsOutOfRange() {
        BlockPos corner = new BlockPos(PsiField.RADIUS, PsiField.RADIUS, PsiField.RADIUS);
        assertFalse(coveredBy(BlockPos.ZERO, corner));
    }

    @Test
    void oneOfSeveralPylonsInRangeIsEnough() {
        assertTrue(coveredBy(BlockPos.ZERO,
                new BlockPos(500, 70, 500),
                new BlockPos(3, 1, -2)));
    }

    @Test
    void rangeIsMeasuredFromTheOriginNotTheWorldCentre() {
        BlockPos origin = new BlockPos(1000, 64, -1000);
        assertTrue(coveredBy(origin, origin.offset(4, 2, -3)));
        assertFalse(coveredBy(origin, origin.offset(20, 0, 0)));
    }
}
