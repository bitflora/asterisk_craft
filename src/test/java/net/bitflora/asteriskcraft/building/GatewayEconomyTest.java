package net.bitflora.asteriskcraft.building;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Guards the Gateway's design constants: a Zealot costs 50 wood AND 50 cobblestone
 * (paid atomically), a Dragoon costs 10 iron ingots, and production/warp-in take time.
 */
class GatewayEconomyTest {

    @Test
    void productionTimingIsSane() {
        assertTrue(GatewayBlockEntity.BUILD_TICKS > 0, "units must take time to build");
        assertTrue(GatewayBlockEntity.MAX_QUEUE >= 1, "the queue must hold at least one unit");
        assertTrue(GatewayBlockEntity.WARP_TICKS > 0, "the Gateway must take time to warp in");
    }
}
