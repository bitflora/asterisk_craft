package net.bitflora.asteriskcraft.entity.ai;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Guards the no-progress clock behind {@code CommandedMoveGoal}'s give-up rule. {@link MoveProgress}
 * exists as a separate class precisely so these cases can be checked without a live ServerLevel — the
 * goal itself can't be unit-tested, since it needs a real Mob with a navigation, a level for the
 * command attachment, and a GoalSelector driving start/tick/stop ordering. That part is verified via
 * {@code runClient} (same reasoning as {@code ProbeEconomyTest}).
 *
 * <p>Every test here is a regression the previous implementation actually failed: it compared each
 * tick against an all-time-best distance, so a unit shoved backwards in a crowd could never register
 * progress again and abandoned its order while genuinely marching.
 */
class MoveProgressTest {

    /** Runs a whole sampling window, returning whether it closed as stalled. */
    private static boolean runWindow(MoveProgress progress, double distSqr) {
        boolean stalled = false;
        for (int i = 0; i < MoveProgress.WINDOW_TICKS; i++) {
            stalled = progress.tick(distSqr);
        }
        return stalled;
    }

    @Test
    void steadyApproachNeverStalls() {
        MoveProgress progress = new MoveProgress();
        progress.reset(400.0);

        for (int window = 1; window <= 20; window++) {
            assertFalse(runWindow(progress, 400.0 - window * 10.0),
                    "a unit still closing on its destination must never be judged stalled");
        }
        assertEquals(0, progress.stalledWindows());
    }

    @Test
    void standingStillStallsOnEveryWindow() {
        MoveProgress progress = new MoveProgress();
        progress.reset(100.0);

        for (int window = 1; window <= 5; window++) {
            assertTrue(runWindow(progress, 100.0), "a unit that has not moved must report a stalled window");
            assertEquals(window, progress.stalledWindows());
        }
    }

    @Test
    void stallIsOnlyJudgedOnWindowBoundaries() {
        MoveProgress progress = new MoveProgress();
        progress.reset(100.0);

        for (int i = 0; i < MoveProgress.WINDOW_TICKS - 1; i++) {
            assertFalse(progress.tick(100.0), "no verdict is due before the window closes");
        }
        assertTrue(progress.tick(100.0), "the verdict lands exactly on the window boundary");
    }

    @Test
    void pushedBackThenRecoveredCountsAsProgress() {
        // The case that broke Dragoons: jostled backwards by the squad, then making ground again.
        // Against an all-time best of 19 the recovery to 22 would never beat the record, and the unit
        // would keep accumulating stalled windows all the way to giving up.
        MoveProgress progress = new MoveProgress();
        progress.reset(30.0);

        assertFalse(runWindow(progress, 19.0), "closing from 30 to 19 is progress");
        assertTrue(runWindow(progress, 22.0), "being shoved backwards is one stalled window");
        assertFalse(runWindow(progress, 17.0), "recovering past the shove is progress again");
        assertEquals(0, progress.stalledWindows(),
                "recovering must clear the stall count, not leave it counting down to give-up");
    }

    @Test
    void resetRebaselinesToTheNewDestination() {
        // A second move order sends the unit somewhere farther away. Without a re-baseline the new,
        // larger distance reads as instant no-progress against the old destination's record.
        MoveProgress progress = new MoveProgress();
        progress.reset(4.0);
        runWindow(progress, 4.0);
        assertEquals(1, progress.stalledWindows());

        progress.reset(400.0);
        assertEquals(0, progress.stalledWindows(), "a new destination clears the old stall history");
        assertFalse(runWindow(progress, 380.0), "progress toward the new destination is measured against it");
    }

    @Test
    void driftSmallerThanTheEpsilonStillStalls() {
        // The epsilon is load-bearing: a unit shuffling on the spot in a crowd creeps a hair closer
        // every window without ever getting anywhere, and must not be credited for it.
        MoveProgress progress = new MoveProgress();
        progress.reset(50.0);

        assertTrue(runWindow(progress, 50.0 - MoveProgress.EPSILON_SQR / 2.0),
                "closing less than the epsilon is not progress");
    }
}
