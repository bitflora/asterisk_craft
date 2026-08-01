package net.bitflora.asteriskcraft.game;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pure-logic guards for Hive placement: the spread rule (candidates must land at least
 * HIVE_MIN_SEPARATION (15) blocks from every Hive already placed, so the three AI Hives never
 * cluster on top of each other) and the column scan that decides how high off the ground a Hive
 * ends up.
 *
 * <p>The scan is covered in two halves, because neither half alone would have caught the jungle bug
 * that motivated it: {@code scanToGround} for the descent geometry (probe-driven, so no
 * ServerLevel is needed) and {@code isGround} for which blocks stop that descent. The
 * {@code isGround} cases stick to blocks whose ruling comes from {@code isSolidRender} alone —
 * <b>logs cannot be asserted here</b>, since they are excluded via BlockTags.LOGS and block tags are
 * not bound in the JUnit bootstrap (same limitation ProbeEconomyTest documents); that exclusion is
 * runClient-verified. What remains of placement (fluid-based water avoidance, end-to-end stamping)
 * needs a live ServerLevel and is likewise runClient-verified.
 */
class GameBootstrapTest {

    @Test
    void rejectsHivesWithinMinSeparation() {
        List<BlockPos> placed = List.of(new BlockPos(0, 64, 0));
        // 10 blocks away horizontally (< 15), regardless of Y — must be rejected.
        assertFalse(GameBootstrap.farFromOthers(new BlockPos(10, 70, 0), placed),
                "a candidate within 15 blocks of a placed Hive must be rejected");
    }

    @Test
    void acceptsHivesBeyondMinSeparation() {
        List<BlockPos> placed = List.of(new BlockPos(0, 64, 0));
        // 20 blocks away horizontally (>= 15) — must be accepted.
        assertTrue(GameBootstrap.farFromOthers(new BlockPos(20, 64, 0), placed),
                "a candidate at least 15 blocks from every placed Hive must be accepted");
    }

    @Test
    void acceptsWhenNoHivesPlacedYet() {
        assertTrue(GameBootstrap.farFromOthers(new BlockPos(0, 64, 0), List.of()),
                "the first Hive has nothing to conflict with");
    }

    @Test
    void rejectsWhenTooCloseToAnyOfSeveral() {
        List<BlockPos> placed = List.of(
                new BlockPos(0, 64, 0), new BlockPos(100, 64, 0), new BlockPos(0, 64, 100));
        // Far from the first two, but only 12 blocks from the third -> rejected.
        assertFalse(GameBootstrap.farFromOthers(new BlockPos(0, 64, 88), placed),
                "must be rejected if too close to ANY placed Hive");
    }

    @Test
    void infestableGroundCoversNaturalSurfaceTypes() {
        assertTrue(GameBootstrap.isInfestableGround(Blocks.GRASS_BLOCK.defaultBlockState()));
        assertTrue(GameBootstrap.isInfestableGround(Blocks.STONE.defaultBlockState()));
        assertTrue(GameBootstrap.isInfestableGround(Blocks.SAND.defaultBlockState()));
        assertTrue(GameBootstrap.isInfestableGround(Blocks.RED_SAND.defaultBlockState()));
        assertTrue(GameBootstrap.isInfestableGround(Blocks.DIRT.defaultBlockState()));
        assertTrue(GameBootstrap.isInfestableGround(Blocks.GRAVEL.defaultBlockState()));
    }

    @Test
    void infestationSkipsNonGroundAndAlreadyMycelium() {
        assertFalse(GameBootstrap.isInfestableGround(Blocks.MYCELIUM.defaultBlockState()));
        assertFalse(GameBootstrap.isInfestableGround(Blocks.WATER.defaultBlockState()));
        assertFalse(GameBootstrap.isInfestableGround(Blocks.OAK_LOG.defaultBlockState()));
    }

    @Test
    void groundScanDescendsPastEverythingAboveTheFloor() {
        // Ground at 64, nothing but canopy and undergrowth from 65 up to the scan's start at 90.
        assertEquals(64, GameBootstrap.scanToGround(90, 90 - 64, y -> y <= 64),
                "the scan must walk down to the first ground block, not stop at the top of the column");
    }

    @Test
    void groundScanStopsImmediatelyOnOpenTerrain() {
        // The common case: no tree, so the scan's start already is the ground.
        assertEquals(70, GameBootstrap.scanToGround(70, 70 - 64, y -> y <= 70));
    }

    @Test
    void groundScanFallsBackToTopWhenNoGroundInRange() {
        // Nothing qualifies anywhere in the budget — fall back to the top rather than run off downward.
        assertEquals(90, GameBootstrap.scanToGround(90, 90 - 64, y -> false));
    }

    @Test
    void groundScanHonorsItsLowerBound() {
        // Ground sits one block below the budget, so it must not be found.
        assertEquals(90, GameBootstrap.scanToGround(90, 26, y -> y <= 25));
    }

    @Test
    void solidNaturalBlocksCountAsGround() {
        assertTrue(GameBootstrap.isGround(Blocks.GRASS_BLOCK.defaultBlockState()));
        assertTrue(GameBootstrap.isGround(Blocks.STONE.defaultBlockState()));
        assertTrue(GameBootstrap.isGround(Blocks.DIRT.defaultBlockState()));
        assertTrue(GameBootstrap.isGround(Blocks.SAND.defaultBlockState()));
        assertTrue(GameBootstrap.isGround(Blocks.MYCELIUM.defaultBlockState()));
    }

    @Test
    void canopyAndUndergrowthAreNotGround() {
        // Vines are the block that put Hives up in the trees: a jungle canopy is draped in them, and
        // they are neither logs nor leaves, so a tree-parts-only test walked straight into one.
        assertFalse(GameBootstrap.isGround(Blocks.VINE.defaultBlockState()),
                "a hanging vine must never be mistaken for the ground under a jungle");
        assertFalse(GameBootstrap.isGround(Blocks.COCOA.defaultBlockState()));
        assertFalse(GameBootstrap.isGround(Blocks.GLOW_LICHEN.defaultBlockState()));
        assertFalse(GameBootstrap.isGround(Blocks.SHORT_GRASS.defaultBlockState()));
        assertFalse(GameBootstrap.isGround(Blocks.JUNGLE_LEAVES.defaultBlockState()));
        assertFalse(GameBootstrap.isGround(Blocks.AIR.defaultBlockState()));
        assertFalse(GameBootstrap.isGround(Blocks.WATER.defaultBlockState()));
    }
}
