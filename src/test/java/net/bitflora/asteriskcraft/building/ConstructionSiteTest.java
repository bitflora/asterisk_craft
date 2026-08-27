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
        return ConstructionSite.decide(true, true, inRange, started, 0);
    }

    @Test
    void aBuildingNobodyWasAskedToPutUpJustGoesUp() {
        // Every Protoss and Zerg warp-in, and every structure world generation stamps. The gate has
        // to be free for them or adding it would change three races' behaviour to give one a feature.
        assertEquals(Progress.BUILDING, ConstructionSite.decide(false, false, false, false, 0));
        assertEquals(Progress.BUILDING,
                ConstructionSite.decide(false, false, false, false, ConstructionSite.LOST_TOLERANCE_TICKS * 10));
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
        assertEquals(Progress.ABANDONED, ConstructionSite.decide(true, false, false, false, lost));
        assertEquals(Progress.ABANDONED, ConstructionSite.decide(true, false, false, true, lost));
    }

    @Test
    void aBuilderMissingOnlyBrieflyIsNotTreatedAsDead() {
        // A reload hands back a level whose entities have not caught up, so the builder resolves to
        // nothing for a moment. Razing on the first miss would demolish every site across a reload.
        assertEquals(Progress.BUILDING,
                ConstructionSite.decide(true, false, false, true, ConstructionSite.LOST_TOLERANCE_TICKS));
        assertEquals(Progress.WAITING,
                ConstructionSite.decide(true, false, false, false, ConstructionSite.LOST_TOLERANCE_TICKS));
    }
}
