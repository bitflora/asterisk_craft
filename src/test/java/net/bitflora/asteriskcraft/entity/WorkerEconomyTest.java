package net.bitflora.asteriskcraft.entity;

import net.bitflora.asteriskcraft.building.DepletedNodeBlockEntity;
import net.bitflora.asteriskcraft.building.UnitQueue;
import net.bitflora.asteriskcraft.race.RaceProfile;
import net.bitflora.asteriskcraft.race.Races;
import net.bitflora.asteriskcraft.entity.WorkerEntity.ResourceType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Guards the resource-economy design constants (R7). The block-to-item yield mapping
 * itself depends on datapack block tags, which are not bound in the unit-test
 * bootstrap, so that mapping is exercised via the in-game/runClient flow instead.
 *
 * <p>Every race's worker shares this economy — {@code WorkerEntity} is the whole harvest loop and
 * a Probe or a Drone is a thin subclass over it — so these rules are tested once, here.
 *
 * <p>A worker's cost itself lives in {@code net.bitflora.asteriskcraft.stats.UnitStats} — see
 * {@code stats.UnitStatsTest}.
 */
class WorkerEconomyTest {

    @Test
    void harvestYieldIsFlatPerTrip() {
        // Every resource type (wood/cobblestone/iron) yields the same flat amount per trip.
        assertEquals(3, WorkerEntity.YIELD_PER_TRIP);
    }

    @Test
    void anAssignedWorkerMinesOnlyItsOwnResource() {
        // The rule the harvest search filters on: an assignment is a filter, not a preference, so a
        // worker put on iron never picks up a nearer log just because its own nodes are spent.
        assertTrue(WorkerEntity.mines(ResourceType.IRON, ResourceType.IRON));
        assertFalse(WorkerEntity.mines(ResourceType.IRON, ResourceType.WOOD));
        assertFalse(WorkerEntity.mines(ResourceType.IRON, ResourceType.STONE));
    }

    @Test
    void anUnassignedWorkerMinesAnything() {
        // A freshly built worker has been put on nothing yet; its first pick is what assigns it.
        for (ResourceType type : ResourceType.values()) {
            assertTrue(WorkerEntity.mines(null, type), type + " should be open to an unassigned worker");
        }
    }

    @Test
    void waitingOutADepletedNodeAlwaysEnds() {
        // Why holding an assignment can't stall a worker forever: mining never removes a block, it
        // swaps it for a node that regrows on a finite cooldown, so there is always something to
        // wait for. (Which block a node regrows into is tag-dependent — see the class javadoc.)
        assertTrue(DepletedNodeBlockEntity.REGEN_TICKS > 0, "a depleted node must come back");
    }

    @Test
    void productionTimingIsSane() {
        for (RaceProfile profile : Races.all()) {
            assertTrue(profile.worker().buildTicks() > 0,
                    profile.race() + "'s worker must take time to build");
        }
        assertTrue(UnitQueue.MAX >= 1, "the queue must hold at least one worker");
    }
}
