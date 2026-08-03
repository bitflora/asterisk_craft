package net.bitflora.asteriskcraft.entity.ai;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Guards the 40-second no-movement clock behind {@link StuckWanderGoal}. As with
 * {@link MoveProgressTest}, the clock is a separate class precisely so these cases can be checked
 * without a live ServerLevel — the goal itself needs a real Mob with a navigation, a Sensing, and a
 * GoalSelector driving start/tick/stop ordering, so its behaviour is verified via {@code runClient}
 * (same reasoning as {@code ProbeEconomyTest}).
 */
class StuckClockTest {

    /** Runs {@code ticks} ticks with the unit pinned at one spot and not busy. */
    private static boolean runStationary(StuckClock clock, int ticks) {
        boolean stuck = false;
        for (int i = 0; i < ticks; i++) {
            stuck = clock.tick(0.0, 64.0, 0.0, false);
        }
        return stuck;
    }

    @Test
    void tripsAfterFortySecondsStationary() {
        StuckClock clock = new StuckClock();
        clock.reset(0.0, 64.0, 0.0);

        assertFalse(runStationary(clock, StuckClock.STUCK_TICKS - StuckClock.SAMPLE_TICKS),
                "a unit must be given the full 40s before it is judged stuck");
        assertTrue(runStationary(clock, StuckClock.SAMPLE_TICKS), "the verdict lands on the 40s boundary");
        assertEquals(StuckClock.STUCK_TICKS, clock.stalledTicks());
    }

    @Test
    void marchingNeverTrips() {
        StuckClock clock = new StuckClock();
        clock.reset(0.0, 64.0, 0.0);

        // A slow unit still covers far more than the epsilon in a one-second sample.
        for (int tick = 1; tick <= StuckClock.STUCK_TICKS * 2; tick++) {
            assertFalse(clock.tick(tick * 0.1, 64.0, 0.0, false), "a unit still moving is never stuck");
        }
        assertEquals(0, clock.stalledTicks());
    }

    @Test
    void fightingInPlaceNeverTrips() {
        // The case that would otherwise pull a unit out of a long fight: a Hydralisk shooting from
        // range stands perfectly still for as long as its target lives.
        StuckClock clock = new StuckClock();
        clock.reset(0.0, 64.0, 0.0);

        for (int i = 0; i < StuckClock.STUCK_TICKS * 2; i++) {
            assertFalse(clock.tick(0.0, 64.0, 0.0, true), "standing still with a reason is not being stuck");
        }
        assertEquals(0, clock.stalledTicks());
    }

    @Test
    void oneStepFreeRestartsTheWholeCount() {
        StuckClock clock = new StuckClock();
        clock.reset(0.0, 64.0, 0.0);

        runStationary(clock, StuckClock.STUCK_TICKS - StuckClock.SAMPLE_TICKS);
        assertTrue(clock.stalledTicks() > 0);

        // One sample's worth of real movement clears the history: patience is measured from the last
        // time the unit got somewhere, not from when it was first blocked.
        for (int i = 0; i < StuckClock.SAMPLE_TICKS; i++) {
            clock.tick(5.0, 64.0, 0.0, false);
        }
        assertEquals(0, clock.stalledTicks());
        assertFalse(runStationary(clock, StuckClock.STUCK_TICKS - StuckClock.SAMPLE_TICKS),
                "the unit gets a fresh 40s after breaking loose");
    }

    @Test
    void creepingUnderTheEpsilonStillTrips() {
        // The epsilon is load-bearing: a unit shuffling on the spot in a crowd, or sliding a hair
        // down a slope, drifts every sample without ever getting anywhere. Measuring each sample from
        // where the unit actually is (not from a stale anchor) is what stops the drift accumulating
        // into false progress.
        StuckClock clock = new StuckClock();
        clock.reset(0.0, 64.0, 0.0);

        double x = 0.0;
        boolean stuck = false;
        for (int sample = 0; sample < StuckClock.STUCK_TICKS / StuckClock.SAMPLE_TICKS; sample++) {
            x += 0.4; // under the 0.5-block threshold, but 16 blocks over the full 40s
            for (int i = 0; i < StuckClock.SAMPLE_TICKS; i++) {
                stuck = clock.tick(x, 64.0, 0.0, false);
            }
        }
        assertTrue(stuck, "drift smaller than the epsilon must not be credited as movement");
    }

    @Test
    void verdictOnlyLandsOnSampleBoundaries() {
        StuckClock clock = new StuckClock();
        clock.reset(0.0, 64.0, 0.0);

        runStationary(clock, StuckClock.STUCK_TICKS - StuckClock.SAMPLE_TICKS);
        for (int i = 0; i < StuckClock.SAMPLE_TICKS - 1; i++) {
            assertFalse(clock.tick(0.0, 64.0, 0.0, false), "no verdict is due before the sample closes");
        }
        assertTrue(clock.tick(0.0, 64.0, 0.0, false));
    }

    @Test
    void firstTickAnchorsRatherThanCountingFromTheOrigin() {
        // Without an initial anchor the first sample would measure from (0,0,0) — a unit spawned far
        // from origin would read as having sprinted there, and one at origin as never having moved.
        StuckClock clock = new StuckClock();

        assertFalse(clock.tick(1000.0, 64.0, -2000.0, false));
        for (int i = 0; i < StuckClock.SAMPLE_TICKS; i++) {
            assertFalse(clock.tick(1000.0, 64.0, -2000.0, false));
        }
        assertEquals(StuckClock.SAMPLE_TICKS, clock.stalledTicks(),
                "counting starts from where the unit actually was on the first tick");
    }
}
