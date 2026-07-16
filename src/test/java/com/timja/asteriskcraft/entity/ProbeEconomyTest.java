package com.timja.asteriskcraft.entity;

import com.timja.asteriskcraft.building.NexusBlockEntity;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Guards the resource-economy design constants (R7). The block-to-item yield mapping
 * itself depends on datapack block tags, which are not bound in the unit-test
 * bootstrap, so that mapping is exercised via the in-game/runClient flow instead.
 */
class ProbeEconomyTest {

    @Test
    void probeCostMatchesDesign() {
        // A Probe costs 50 wood OR 50 cobblestone (see plan).
        assertEquals(50, NexusBlockEntity.PROBE_COST);
    }

    @Test
    void harvestYieldIsPositiveAndEven() {
        // Iron ore yields YIELD_PER_TRIP / 2 refined ingots, so it must stay even and > 0.
        assertTrue(ProbeEntity.YIELD_PER_TRIP > 0, "yield must be positive");
        assertEquals(0, ProbeEntity.YIELD_PER_TRIP % 2, "yield must be even so iron halves cleanly");
    }

    @Test
    void productionTimingIsSane() {
        assertTrue(NexusBlockEntity.BUILD_TICKS > 0, "probes must take time to build");
        assertTrue(NexusBlockEntity.MAX_QUEUE >= 1, "the queue must hold at least one probe");
    }
}
