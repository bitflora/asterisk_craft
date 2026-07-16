package net.bitflora.asteriskcraft.building;

import net.bitflora.asteriskcraft.AsteriskCraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Guards the Nexus multiblock definition (R1): a 5x5 platform with the interactive
 * core block on top of the center pedestal. Runs under the bootstrapped NeoForge
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
    void nexusHasAFullFiveByFivePlatform() {
        Map<BlockPos, BlockState> layout = BuildingLayouts.nexus();
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                assertTrue(layout.containsKey(new BlockPos(dx, 0, dz)),
                        "missing platform block at " + dx + "," + dz);
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
}
