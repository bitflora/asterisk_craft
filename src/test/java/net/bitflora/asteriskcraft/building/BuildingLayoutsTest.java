package net.bitflora.asteriskcraft.building;

import net.bitflora.asteriskcraft.AsteriskCraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Guards the Nexus multiblock definition (R1): a 3x3 quartz pedestal with four corner
 * pillars, and the interactive core block on top of the center gold pedestal. Runs under the bootstrapped NeoForge
 * environment so mod-registered blocks and vanilla blocks resolve.
 */
class BuildingLayoutsTest {

    @Test
    void nexusCoreSitsOnTopOfCenter() {
        Map<BlockPos, BlockState> layout = BuildingLayouts.nexus();
        BlockState core = layout.get(new BlockPos(0, 2, 0));
        assertNotNull(core, "expected a block at the core position (0,2,0)");
        assertEquals(AsteriskCraft.NEXUS_CORE.get(), core.getBlock(),
                "the top-center block must be the interactive Nexus core");
    }

    @Test
    void nexusHasAThreeByThreePedestalWithNoOuterRing() {
        Map<BlockPos, BlockState> layout = BuildingLayouts.nexus();
        BlockState quartz = Blocks.SMOOTH_QUARTZ.defaultBlockState();
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                boolean inPedestal = Math.abs(dx) <= 1 && Math.abs(dz) <= 1;
                boolean isCorner = Math.abs(dx) == 2 && Math.abs(dz) == 2;
                BlockState at = layout.get(new BlockPos(dx, 0, dz));
                if (inPedestal) {
                    assertEquals(quartz, at, "3x3 pedestal must be quartz at " + dx + "," + dz);
                } else if (isCorner) {
                    assertNotNull(at, "corner footing must remain at " + dx + "," + dz);
                } else {
                    assertNull(at, "outer-ring block should be removed at " + dx + "," + dz);
                }
            }
        }
    }

    @Test
    void nexusHasFourCornerPillars() {
        Map<BlockPos, BlockState> layout = BuildingLayouts.nexus();
        int[][] corners = {{-2, -2}, {-2, 2}, {2, -2}, {2, 2}};
        for (int[] c : corners) {
            assertNotNull(layout.get(new BlockPos(c[0], 1, c[1])),
                    "expected a pillar above corner " + c[0] + "," + c[1]);
        }
    }

    @Test
    void gatewayCoreSitsAtItsDeclaredOffset() {
        Map<BlockPos, BlockState> layout = BuildingLayouts.gateway();
        BlockState core = layout.get(BuildingLayouts.GATEWAY_CORE_OFFSET);
        assertNotNull(core, "expected a block at the declared Gateway core offset");
        assertEquals(AsteriskCraft.GATEWAY_CORE.get(), core.getBlock(),
                "the declared offset must hold the interactive Gateway core");
    }

    @Test
    void gatewayHasAFullThreeByThreePlatform() {
        Map<BlockPos, BlockState> layout = BuildingLayouts.gateway();
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                assertTrue(layout.containsKey(new BlockPos(dx, 0, dz)),
                        "missing platform block at " + dx + "," + dz);
            }
        }
    }

    // --- place() geometry, exercised through the pure planPlacement (no live ServerLevel needed) ---
    // The probe is a Predicate<BlockPos> reporting whether an EXISTING world block is solid-render;
    // support fill treats anything non-solid (air OR fluids) as needing quartz footing.

    private static final BlockPos ORIGIN = new BlockPos(0, 64, 0);
    /** Flat ground: every block strictly below the origin is solid, so support fill stops immediately. */
    private static final Predicate<BlockPos> FLAT_GROUND = pos -> pos.getY() < ORIGIN.getY();
    /** Support material fed to planPlacement; the fill mechanism is material-agnostic. */
    private static final BlockState SUPPORT = Blocks.SMOOTH_QUARTZ.defaultBlockState();

    @Test
    void headroomIsClearedAboveFootprint() {
        Map<BlockPos, BlockState> edits = BuildingLayouts.planPlacement(ORIGIN, Map.of(), FLAT_GROUND, 6, SUPPORT);
        BlockState air = Blocks.AIR.defaultBlockState();
        for (int dx = -3; dx <= 3; dx++) {
            for (int dz = -3; dz <= 3; dz++) {
                for (int dy = 1; dy <= 5; dy++) {
                    assertEquals(air, edits.get(ORIGIN.offset(dx, dy, dz)),
                            "head-room not cleared at " + dx + "," + dy + "," + dz);
                }
            }
        }
    }

    @Test
    void flatGroundGetsNoSupportFill() {
        Map<BlockPos, BlockState> edits = BuildingLayouts.planPlacement(ORIGIN, Map.of(), FLAT_GROUND, 6, SUPPORT);
        assertFalse(edits.containsValue(Blocks.SMOOTH_QUARTZ.defaultBlockState()),
                "flat ground should need no quartz support (matches pre-change behavior)");
    }

    @Test
    void dipIsFilledDownToGround() {
        // Column at world (3,0) has its ground three blocks below the platform; everything else is flat.
        Predicate<BlockPos> probe = pos -> (pos.getX() == 3 && pos.getZ() == 0)
                ? pos.getY() <= 60 : pos.getY() <= 63;
        Map<BlockPos, BlockState> edits = BuildingLayouts.planPlacement(ORIGIN, Map.of(), probe, 6, SUPPORT);
        BlockState quartz = Blocks.SMOOTH_QUARTZ.defaultBlockState();
        assertEquals(quartz, edits.get(new BlockPos(3, 63, 0)));
        assertEquals(quartz, edits.get(new BlockPos(3, 62, 0)));
        assertEquals(quartz, edits.get(new BlockPos(3, 61, 0)));
        assertNull(edits.get(new BlockPos(3, 60, 0)), "fill must stop at the first solid ground block");
    }

    @Test
    void supportFillIsBoundedByMaxDepth() {
        // Column at world (3,3) is bottomless void; fill must cap at maxSupportDepth, never a runaway shaft.
        Predicate<BlockPos> probe = pos -> !(pos.getX() == 3 && pos.getZ() == 3) && pos.getY() <= 63;
        int maxDepth = 6;
        Map<BlockPos, BlockState> edits = BuildingLayouts.planPlacement(ORIGIN, Map.of(), probe, maxDepth, SUPPORT);
        BlockState quartz = Blocks.SMOOTH_QUARTZ.defaultBlockState();
        for (int dy = -1; dy >= -maxDepth; dy--) {
            assertEquals(quartz, edits.get(ORIGIN.offset(3, dy, 3)),
                    "expected quartz support at dy=" + dy);
        }
        assertNull(edits.get(ORIGIN.offset(3, -maxDepth - 1, 3)), "fill must not exceed maxSupportDepth");
    }

    @Test
    void fluidPocketUnderPlatformIsFilled() {
        // A fluid pocket reads as non-solid, so the two water blocks below (2,2) get quartz footing.
        Predicate<BlockPos> probe = pos -> (pos.getX() == 2 && pos.getZ() == 2)
                ? pos.getY() <= 61 : pos.getY() <= 63;
        Map<BlockPos, BlockState> edits = BuildingLayouts.planPlacement(ORIGIN, Map.of(), probe, 6, SUPPORT);
        BlockState quartz = Blocks.SMOOTH_QUARTZ.defaultBlockState();
        assertEquals(quartz, edits.get(new BlockPos(2, 63, 2)));
        assertEquals(quartz, edits.get(new BlockPos(2, 62, 2)));
        assertNull(edits.get(new BlockPos(2, 61, 2)), "fill stops at the solid floor under the water");
    }

    @Test
    void supportMaterialIsHonored() {
        // Zerg Hives fill their footing with mycelium (a mycelium base), not the Protoss quartz.
        BlockState mycelium = Blocks.MYCELIUM.defaultBlockState();
        Predicate<BlockPos> probe = pos -> pos.getY() <= 62; // one air block below the platform
        Map<BlockPos, BlockState> edits = BuildingLayouts.planPlacement(ORIGIN, Map.of(), probe, 6, mycelium);
        assertEquals(mycelium, edits.get(new BlockPos(0, 63, 0)), "support fill must use the given material");
        assertFalse(edits.containsValue(Blocks.SMOOTH_QUARTZ.defaultBlockState()),
                "no quartz should appear when mycelium is the support material");
    }

    @Test
    void layoutBlocksWinOverClearedPositions() {
        Map<BlockPos, BlockState> edits =
                BuildingLayouts.planPlacement(ORIGIN, BuildingLayouts.hive(), FLAT_GROUND, 6, SUPPORT);
        // The core sits at dy=1, inside the head-room band that was cleared to air — the layout must win.
        BlockPos core = ORIGIN.offset(BuildingLayouts.HIVE_CORE_OFFSET.getX(),
                BuildingLayouts.HIVE_CORE_OFFSET.getY(), BuildingLayouts.HIVE_CORE_OFFSET.getZ());
        assertEquals(AsteriskCraft.HIVE_CORE.get(), edits.get(core).getBlock(),
                "Hive core must override the head-room air clear at its position");
        assertEquals(Blocks.SLIME_BLOCK, edits.get(ORIGIN).getBlock(),
                "the slime pedestal must be stamped at the origin");
    }
}
