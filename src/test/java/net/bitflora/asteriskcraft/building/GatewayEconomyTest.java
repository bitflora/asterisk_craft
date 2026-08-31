package net.bitflora.asteriskcraft.building;

import net.bitflora.asteriskcraft.stats.UnitStats;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Guards the Gateway's production timing. Unit costs and build times themselves now live in
 * {@code net.bitflora.asteriskcraft.stats.UnitStats} — see {@code stats.UnitStatsTest}.
 */
class GatewayEconomyTest {

    @Test
    void productionTimingIsSane() {
        assertTrue(GatewayBlockEntity.MAX_QUEUE >= 1, "the queue must hold at least one unit");
        assertTrue(GatewayBlockEntity.WARP_TICKS > 0, "the Gateway must take time to warp in");
    }

    @Test
    void everyGatewayUnitTakesTimeToBuild() {
        // The Gateway has no build-time constant of its own any more: it counts down whichever unit
        // is at the head of its queue, so a zero here would pop that unit out on the tick it was
        // ordered. One assertion per UnitType the Gateway can produce.
        assertTrue(UnitStats.ZEALOT.buildTicks() > 0, "a Zealot must take time to build");
        assertTrue(UnitStats.DRAGOON.buildTicks() > 0, "a Dragoon must take time to build");
        assertTrue(UnitStats.DARK_TEMPLAR.buildTicks() > 0, "a Dark Templar must take time to build");
        assertTrue(UnitStats.ARCHON.buildTicks() > 0, "an Archon must take time to build");
    }
}
