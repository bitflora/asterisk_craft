package net.bitflora.asteriskcraft.entity.protoss;

import net.bitflora.asteriskcraft.building.NexusBlockEntity;
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
    void harvestYieldIsFlatPerTrip() {
        // Every resource type (wood/cobblestone/iron) yields the same flat amount per trip.
        assertEquals(3, ProbeEntity.YIELD_PER_TRIP);
    }

    @Test
    void productionTimingIsSane() {
        assertTrue(NexusBlockEntity.BUILD_TICKS > 0, "probes must take time to build");
        assertTrue(NexusBlockEntity.MAX_QUEUE >= 1, "the queue must hold at least one probe");
    }
}
