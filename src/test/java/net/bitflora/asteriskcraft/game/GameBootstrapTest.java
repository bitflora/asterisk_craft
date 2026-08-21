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
 *
 * <p>The height those columns add up to is covered the same way: {@code averageGround} for the
 * footprint arithmetic and {@code riseThroughFluid} for the climb to a water surface, both fed by
 * probe lambdas. Which blocks count as fluid is a live-level question and stays runClient-verified.
 */
class GameBootstrapTest {

    /**
     * The stone ring around a Hive must have no gap a Drone's seam could fall through: every
     * ring column needs a ring neighbour on each side going round, which is what a rounded-distance
     * test buys over a strict {@code dist == radius} one.
     */
    @Test
    void stoneRingIsContinuous() {
        int radius = 8;
        int count = 0;
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                if (!GameBootstrap.isOnStoneRing(dx, dz)) {
                    continue;
                }
                count++;
                boolean hasNeighbour = false;
                for (int nx = -1; nx <= 1 && !hasNeighbour; nx++) {
                    for (int nz = -1; nz <= 1; nz++) {
                        if ((nx != 0 || nz != 0) && GameBootstrap.isOnStoneRing(dx + nx, dz + nz)) {
                            hasNeighbour = true;
                            break;
                        }
                    }
                }
                assertTrue(hasNeighbour, "ring column " + dx + "," + dz + " is isolated");
            }
        }
        assertTrue(count > 40, "a radius-8 ring should be ~50 blocks of stone, got " + count);
    }

    /** The ring sits clear of the Hive mound and of the scattered garden nodes at radius 5-6. */
    @Test
    void stoneRingClearsTheHiveAndGarden() {
        assertFalse(GameBootstrap.isOnStoneRing(0, 0), "the ring must not cover the Hive core");
        assertFalse(GameBootstrap.isOnStoneRing(5, 0), "the ring must not sit on a garden node");
        assertFalse(GameBootstrap.isOnStoneRing(6, 2), "the ring must not sit on a garden node");
        assertTrue(GameBootstrap.isOnStoneRing(8, 0), "the ring passes through the cardinal offsets");
        assertTrue(GameBootstrap.isOnStoneRing(0, -8), "the ring passes through the cardinal offsets");
    }

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
                "must be rejected if too close to ANY placed base");
    }

    @Test
    void coverableGroundCoversNaturalSurfaceTypes() {
        assertTrue(GameBootstrap.isCoverableGround(Blocks.GRASS_BLOCK.defaultBlockState()));
        assertTrue(GameBootstrap.isCoverableGround(Blocks.STONE.defaultBlockState()));
        assertTrue(GameBootstrap.isCoverableGround(Blocks.SAND.defaultBlockState()));
        assertTrue(GameBootstrap.isCoverableGround(Blocks.RED_SAND.defaultBlockState()));
        assertTrue(GameBootstrap.isCoverableGround(Blocks.DIRT.defaultBlockState()));
        assertTrue(GameBootstrap.isCoverableGround(Blocks.GRAVEL.defaultBlockState()));
    }

    @Test
    void groundCoverSkipsNonGroundAndAlreadyCovered() {
        assertFalse(GameBootstrap.isCoverableGround(Blocks.MYCELIUM.defaultBlockState()));
        assertFalse(GameBootstrap.isCoverableGround(Blocks.WATER.defaultBlockState()));
        assertFalse(GameBootstrap.isCoverableGround(Blocks.OAK_LOG.defaultBlockState()));
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
    void flatGroundAveragesToItsOwnHeight() {
        // The Nexus's footprint radius. Flat terrain must come out exactly where it always did,
        // so switching from the highest column to the mean is a no-op away from slopes.
        assertEquals(70, GameBootstrap.averageGround(4, (dx, dz) -> 70));
    }

    @Test
    void slopeSettlesAtItsMidpointNotItsPeak() {
        // A ramp climbing one block per step across the footprint: 60 on the low edge, 68 on the high
        // edge. The building belongs halfway up, cut into the hill — not perched at 68 on stilts.
        assertEquals(64, GameBootstrap.averageGround(4, (dx, dz) -> 64 + dx),
                "a building on a slope must sit at its footprint's mean height, not its highest column");
    }

    @Test
    void oneTallSpikeBarelyMovesTheResult() {
        // Otherwise-flat ground at 64 with a single pillar towering over one corner. The old rule
        // hoisted the whole building to 120; the mean shifts by less than a block.
        // (5240 / 81 columns = 64.69 -> 65, which also pins the scan to the full 9x9 square.)
        assertEquals(65, GameBootstrap.averageGround(4, (dx, dz) -> dx == 4 && dz == 4 ? 120 : 64));
    }

    @Test
    void averageRoundsToTheNearestBlock() {
        // 9 columns, one of them raised: 64.44 rounds down, 64.56 rounds up.
        assertEquals(64, GameBootstrap.averageGround(1, (dx, dz) -> dx == 1 && dz == 1 ? 68 : 64));
        assertEquals(65, GameBootstrap.averageGround(1, (dx, dz) -> dx == 1 && dz == 1 ? 69 : 64));
    }

    @Test
    void averageRoundsToTheNearestBlockBelowSeaLevel() {
        // Deepslate-level terrain: the rounding must stay nearest-wins with negative heights rather
        // than truncating toward zero, which would drift the building a block uphill every time.
        assertEquals(-59, GameBootstrap.averageGround(1, (dx, dz) -> dx == 1 && dz == 1 ? -55 : -60));
        assertEquals(-60, GameBootstrap.averageGround(1, (dx, dz) -> dx == 1 && dz == 1 ? -56 : -60));
    }

    @Test
    void dryColumnsContributeTheirOwnGround() {
        assertEquals(64, GameBootstrap.riseThroughFluid(64, 32, y -> false),
                "dry land must report its ground untouched");
    }

    @Test
    void submergedColumnsContributeTheirWaterSurface() {
        // Lakebed at 55 under water up to 62: averaging in the bed would drag a shoreline base under.
        assertEquals(62, GameBootstrap.riseThroughFluid(55, 32, y -> y <= 62));
    }

    @Test
    void fluidClimbHonorsItsLimit() {
        // Bottomless fluid column — stop after the budget rather than run away up the world.
        assertEquals(59, GameBootstrap.riseThroughFluid(55, 4, y -> true));
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
