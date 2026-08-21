package net.bitflora.asteriskcraft.director;

import net.bitflora.asteriskcraft.race.RaceProfile;
import net.bitflora.asteriskcraft.race.Races;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Sanity bounds on the computer player's economy tuning: the worker-floor cadence and leash live on
 * {@link AiDirector} and are race-independent, while everything the director spends and stores comes
 * off the race it happens to be playing.
 *
 * <p>Unit costs themselves live in {@code net.bitflora.asteriskcraft.stats.UnitStats} — see
 * {@code stats.UnitStatsTest}. (Costs are predicate-based and tag-dependent, so the exact
 * "mirrors the other race" relationship is verified in-game, not here — see the JUnit-bootstrap
 * note in {@code entity.WorkerEconomyTest}.)
 */
class AiEconomyTest {

    @Test
    void workerUpkeepIsPeriodicAndLeashed() {
        assertTrue(AiDirector.WORKER_CHECK_INTERVAL > 0, "worker upkeep must be periodic, not every tick");
        assertTrue(AiDirector.WORKER_LEASH > 0, "workers need a leash radius to be counted as the army's");
    }

    @Test
    void everyRaceCanBankWhatItsWorkersMine() {
        // The director spends out of one shared bank per race, so a race with no storage would
        // stall its own economy the moment a worker came home.
        for (RaceProfile profile : Races.all()) {
            assertTrue(profile.bankSlots() > 0, profile.race() + " needs storage for mined resources");
        }
    }
}
