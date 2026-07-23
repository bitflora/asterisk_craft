package net.bitflora.asteriskcraft.game;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pure-logic guard for the Hive spread rule: candidates must land at least HIVE_MIN_SEPARATION (15)
 * blocks from every Hive already placed, so the three AI Hives never cluster on top of each other.
 * The rest of placement (heightmap-based highestGround, fluid-based water avoidance, end-to-end
 * stamping) needs a live ServerLevel the JUnit bootstrap doesn't provide, and is runClient-verified.
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
}
