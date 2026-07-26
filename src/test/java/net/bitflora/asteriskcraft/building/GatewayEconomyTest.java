package net.bitflora.asteriskcraft.building;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Guards the Gateway's design constants: a Zealot costs 50 wood AND 50 cobblestone
 * (paid atomically), a Dragoon costs 100 wood AND 50 cobblestone, a Scout costs 150 cobblestone AND
 * 20 iron, and production/warp-in take time.
 */
class GatewayEconomyTest {

    @Test
    void productionTimingIsSane() {
        assertTrue(GatewayBlockEntity.BUILD_TICKS > 0, "units must take time to build");
        assertTrue(GatewayBlockEntity.MAX_QUEUE >= 1, "the queue must hold at least one unit");
        assertTrue(GatewayBlockEntity.WARP_TICKS > 0, "the Gateway must take time to warp in");
    }

    @Test
    void unitCostsMatchDesign() {
        assertEquals(50, GatewayBlockEntity.ZEALOT_WOOD_COST);
        assertEquals(50, GatewayBlockEntity.ZEALOT_COBBLE_COST);
        assertEquals(100, GatewayBlockEntity.DRAGOON_WOOD_COST);
        assertEquals(50, GatewayBlockEntity.DRAGOON_COBBLE_COST);
        assertEquals(150, GatewayBlockEntity.SCOUT_COBBLE_COST);
        assertEquals(20, GatewayBlockEntity.SCOUT_IRON_COST);
    }
}
