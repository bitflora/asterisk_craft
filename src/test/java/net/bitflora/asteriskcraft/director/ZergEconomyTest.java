package net.bitflora.asteriskcraft.director;

import net.bitflora.asteriskcraft.building.FactionCore;
import net.bitflora.asteriskcraft.building.HiveBlockEntity;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Sanity bounds on the Zerg economy tuning constants after they were centralised: the worker-floor
 * cadence and leash live on {@link ZergDirector}, Hive storage on {@link HiveBlockEntity}. Worker
 * cost itself now lives in {@code net.bitflora.asteriskcraft.stats.UnitStats} — see
 * {@code stats.UnitStatsTest}. (Unit costs are predicate-based and tag-dependent, so the exact
 * "mirrors Protoss" relationship is verified in-game, not here — see the JUnit-bootstrap note in
 * {@code ProbeEconomyTest}.)
 */
class ZergEconomyTest {

    @Test
    void economyTuningIsSane() {
        assertTrue(ZergDirector.WORKER_CHECK_INTERVAL > 0, "worker upkeep must be periodic, not every tick");
        assertTrue(ZergDirector.DRONE_LEASH > 0, "drones need a leash radius to be counted as the army's");
        assertTrue(HiveBlockEntity.INPUT_SLOTS > 0, "the Hive needs storage for mined resources");
    }

    @Test
    void coreHealthIsPositive() {
        assertTrue(FactionCore.CORE_MAX_HEALTH > 0, "cores must have health to survive a hit or two");
    }
}
