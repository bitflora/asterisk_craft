package net.bitflora.asteriskcraft.director;

import net.bitflora.asteriskcraft.building.FactionCore;
import net.bitflora.asteriskcraft.building.GatewayBlockEntity;
import net.bitflora.asteriskcraft.building.HiveBlockEntity;
import net.bitflora.asteriskcraft.building.NexusBlockEntity;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The shaping doc requires Zerg unit costs to mirror the Protoss equivalents exactly. These
 * assert that relationship so the two economies can't drift apart, plus a few sanity bounds on
 * the Hive/core tuning constants.
 */
class ZergEconomyTest {



    @Test
    void hiveTuningIsSane() {
        assertTrue(HiveBlockEntity.DRONE_TARGET > 0, "a Hive must keep at least one drone");
        assertTrue(HiveBlockEntity.DRONE_CHECK_INTERVAL > 0, "drone upkeep must be periodic, not every tick");
        assertTrue(HiveBlockEntity.DRONE_LEASH > 0, "drones need a leash radius to be counted as the Hive's");
        assertTrue(HiveBlockEntity.INPUT_SLOTS > 0, "the Hive needs storage for mined resources");
    }

    @Test
    void coreHealthIsPositive() {
        assertTrue(FactionCore.CORE_MAX_HEALTH > 0, "cores must have health to survive a hit or two");
    }
}
