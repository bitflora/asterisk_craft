package net.bitflora.asteriskcraft.entity.zerg;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * The Lurker's burrow rule, tested without a world. {@link BurrowClock} is the reason the entity,
 * three goals and a renderer can all be one-liners over a single number, so the cases worth pinning
 * are the ones those callers rely on: that the dig genuinely costs its full duration in both
 * directions, that the armed/hidden state and the mobile state are the two ends and never overlap,
 * and that reversing mid-dig unwinds from where the unit had got to rather than restarting or
 * snapping.
 */
class BurrowClockTest {

    /** Runs the clock until it settles, and reports how many ticks that took. */
    private static int tickUntilSettled(BurrowClock clock) {
        int ticks = 0;
        while (clock.tick()) {
            ticks++;
        }
        return ticks;
    }

    @Test
    void aFreshLurkerStandsOnTheSurface() {
        BurrowClock clock = new BurrowClock();

        assertTrue(clock.isSurfaced());
        assertFalse(clock.isBurrowed());
        assertFalse(clock.isDigging());
        assertEquals(0.0f, clock.fraction());
    }

    @Test
    void diggingInAndOutEachCostTheFullTransition() {
        BurrowClock clock = new BurrowClock();

        clock.setWantsBurrowed(true);
        assertEquals(BurrowClock.TRANSITION_TICKS, tickUntilSettled(clock));
        assertTrue(clock.isBurrowed());
        assertEquals(1.0f, clock.fraction());

        clock.setWantsBurrowed(false);
        assertEquals(BurrowClock.TRANSITION_TICKS, tickUntilSettled(clock));
        assertTrue(clock.isSurfaced());
    }

    @Test
    void midDigItIsNeitherMobileNorArmed() {
        BurrowClock clock = new BurrowClock();
        clock.setWantsBurrowed(true);

        for (int tick = 1; tick < BurrowClock.TRANSITION_TICKS; tick++) {
            clock.tick();
            assertTrue(clock.isDigging(), "tick " + tick + " should be mid-dig");
            assertFalse(clock.isSurfaced(), "a half-buried Lurker must not count as able to move");
            assertFalse(clock.isBurrowed(), "a half-buried Lurker must not count as armed or hidden");
        }
    }

    @Test
    void reversingMidDigUnwindsFromWhereItGotTo() {
        BurrowClock clock = new BurrowClock();
        clock.setWantsBurrowed(true);
        int partial = BurrowClock.TRANSITION_TICKS / 4;
        for (int tick = 0; tick < partial; tick++) {
            clock.tick();
        }

        clock.setWantsBurrowed(false);

        // Only as far back as it had come — an order reversed a quarter of the way in costs a
        // quarter of the transition, not a whole one and not nothing.
        assertEquals(partial, tickUntilSettled(clock));
        assertTrue(clock.isSurfaced());
    }

    @Test
    void aSettledClockReportsNothingToDo() {
        BurrowClock clock = new BurrowClock();
        clock.setWantsBurrowed(true);
        tickUntilSettled(clock);

        // The entity only writes synced data on a tick that moved, so a settled clock saying "false"
        // is what keeps a dug-in Lurker off the network entirely.
        assertFalse(clock.tick());
    }

    @Test
    void theDigBarkFiresOnceAtEachChangeOfDirection() {
        BurrowClock clock = new BurrowClock();

        clock.setWantsBurrowed(true);
        assertTrue(clock.isAboutToStartDigging(), "leaving the surface starts a dig");
        clock.tick();
        assertFalse(clock.isAboutToStartDigging(), "already on the way down");

        tickUntilSettled(clock);
        clock.setWantsBurrowed(false);
        assertTrue(clock.isAboutToStartDigging(), "leaving the bottom starts a dig too");
        clock.tick();
        assertFalse(clock.isAboutToStartDigging());
    }

    @Test
    void aRestoredClockComesBackWhereItWasSaved() {
        BurrowClock clock = new BurrowClock();
        clock.restore(BurrowClock.TRANSITION_TICKS, true);

        // A Lurker that was buried when the world was saved must still be buried on load — not
        // standing in the open, and not digging itself back in.
        assertTrue(clock.isBurrowed());
        assertTrue(clock.wantsBurrowed());
        assertFalse(clock.tick());
    }

    @Test
    void aRestoredDepthIsClampedToTheTransition() {
        BurrowClock clock = new BurrowClock();

        clock.restore(Integer.MAX_VALUE, true);
        assertTrue(clock.isBurrowed());

        clock.restore(-50, false);
        assertTrue(clock.isSurfaced());
    }
}
