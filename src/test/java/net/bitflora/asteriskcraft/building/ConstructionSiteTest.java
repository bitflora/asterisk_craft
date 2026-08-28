package net.bitflora.asteriskcraft.building;

import net.bitflora.asteriskcraft.building.ConstructionSite.Progress;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The rule deciding whether a building under construction may make progress. Only the pure half is
 * exercised here — resolving a builder, measuring how far away it is and emitting its welding plume
 * all need a live {@code ServerLevel}, and are verified with {@code runClient}.
 */
class ConstructionSiteTest {

    /** Convenience for the common shape: a builder that is alive and either here or not. */
    private static Progress withBuilder(boolean inRange, boolean started) {
        return ConstructionSite.decide(true, true, inRange, started, 0, 0);
    }

    @Test
    void aBuildingNobodyWasAskedToPutUpJustGoesUp() {
        // Every Protoss warp-in, and every structure world generation stamps. The gate has to be
        // free for them or adding it would change three races' behaviour to give one a feature.
        assertEquals(Progress.BUILDING, ConstructionSite.decide(false, false, false, false, 0, 0));
        assertEquals(Progress.BUILDING,
                ConstructionSite.decide(false, false, false, false, ConstructionSite.LOST_TOLERANCE_TICKS * 10,
                        ConstructionSite.ARRIVAL_TIMEOUT_TICKS * 10));
    }

    @Test
    void aBuilderSpentByArrivingCannotThenRazeWhatItPaidFor() {
        // A race that consumes its builders (the Zerg Drone) kills the worker on arrival and drops it
        // from the site, which lands the site in the not-required state above. That state must never
        // reach ABANDONED however long the worker stays unresolvable, or the swarm would lose a
        // colony forty ticks after the Drone that became it died.
        for (int missed = 0; missed <= ConstructionSite.LOST_TOLERANCE_TICKS * 2; missed++) {
            assertEquals(Progress.BUILDING, ConstructionSite.decide(false, false, false, true, missed, 0),
                    "a consumed builder must leave the build running, not abandoned");
        }
    }

    @Test
    void nothingIsBuiltUntilTheBuilderArrives() {
        assertEquals(Progress.WAITING, withBuilder(false, false));
    }

    @Test
    void arrivingStartsTheBuild() {
        assertEquals(Progress.BUILDING, withBuilder(true, false));
    }

    @Test
    void aBuildThatHasStartedSurvivesTheBuilderWalkingOff() {
        // Pulling a worker away with a move order is a redirection, not a cancellation: the structure
        // is already up to its knees and finishes on its own.
        assertEquals(Progress.BUILDING, withBuilder(false, true));
    }

    @Test
    void killingTheBuilderRazesTheSiteWhetherOrNotItHadStarted() {
        int lost = ConstructionSite.LOST_TOLERANCE_TICKS + 1;
        assertEquals(Progress.ABANDONED, ConstructionSite.decide(true, false, false, false, lost, 0));
        assertEquals(Progress.ABANDONED, ConstructionSite.decide(true, false, false, true, lost, 0));
    }

    @Test
    void aBuilderThatNeverArrivesCancelsTheSite() {
        // The worker is alive and well — walled off, stuck, or ordered elsewhere — and simply never
        // gets there. The site gives up rather than standing half-warped for the rest of the match.
        int late = ConstructionSite.ARRIVAL_TIMEOUT_TICKS + 1;
        assertEquals(Progress.ABANDONED, ConstructionSite.decide(true, true, false, false, 0, late));
    }

    @Test
    void theArrivalTimeoutStopsCountingOnceTheBuildHasStarted() {
        // Once the builder has been here the build runs unattended, so a worker sent off on a long
        // errand must not have the structure cancelled out from under it.
        assertEquals(Progress.BUILDING, ConstructionSite.decide(true, true, false, true, 0,
                ConstructionSite.ARRIVAL_TIMEOUT_TICKS * 10));
    }

    @Test
    void aBuilderStillOnItsWayIsGivenTheFullThirtySeconds() {
        assertEquals(Progress.WAITING,
                ConstructionSite.decide(true, true, false, false, 0, ConstructionSite.ARRIVAL_TIMEOUT_TICKS));
    }

    @Test
    void aBuilderMissingOnlyBrieflyIsNotTreatedAsDead() {
        // A reload hands back a level whose entities have not caught up, so the builder resolves to
        // nothing for a moment. Razing on the first miss would demolish every site across a reload.
        assertEquals(Progress.BUILDING,
                ConstructionSite.decide(true, false, false, true, ConstructionSite.LOST_TOLERANCE_TICKS, 0));
        assertEquals(Progress.WAITING,
                ConstructionSite.decide(true, false, false, false, ConstructionSite.LOST_TOLERANCE_TICKS, 0));
    }
}
