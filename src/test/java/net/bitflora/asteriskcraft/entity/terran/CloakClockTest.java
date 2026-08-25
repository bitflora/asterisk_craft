package net.bitflora.asteriskcraft.entity.terran;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * The Ghost's reactive cloak, tested without a world. {@link CloakClock} is why the entity's cloak
 * is one hook and one synced int, so the cases worth pinning are the ones the entity leans on: that
 * a hit only ever cloaks a Ghost that is ready, that a cloak rolls straight into its lockout with no
 * gap in between, and that the lockout genuinely has to run out before the trick works again.
 */
class CloakClockTest {

    /** Runs the clock until it settles, and reports how many ticks that took. */
    private static int tickUntilSettled(CloakClock clock) {
        int ticks = 0;
        while (clock.tick()) {
            ticks++;
        }
        return ticks;
    }

    /** Runs the clock for exactly {@code ticks} ticks. */
    private static void tick(CloakClock clock, int ticks) {
        for (int i = 0; i < ticks; i++) {
            clock.tick();
        }
    }

    @Test
    void aFreshGhostIsUncloakedAndReady() {
        CloakClock clock = new CloakClock();

        assertFalse(clock.isCloaked());
        assertTrue(clock.isReady());
        assertFalse(clock.tick(), "an untouched Ghost's clock never moves");
    }

    @Test
    void beingHurtRaisesTheCloak() {
        CloakClock clock = new CloakClock();

        assertTrue(clock.onDamaged(), "the hit is what engages the cloak");
        assertTrue(clock.isCloaked());
        assertFalse(clock.isReady());
    }

    @Test
    void beingHurtAgainWhileCloakedDoesNotExtendIt() {
        CloakClock clock = new CloakClock();
        clock.onDamaged();
        tick(clock, 100);
        int remaining = clock.value();

        assertFalse(clock.onDamaged(), "a Ghost already hidden has nothing to engage");
        assertEquals(remaining, clock.value(), "the cloak's time must not be refreshed by a hit");
    }

    @Test
    void theCloakRunsOutIntoTheLockoutWithNoGap() {
        CloakClock clock = new CloakClock();
        clock.onDamaged();

        // Every tick of the cloak, and not one more.
        tick(clock, CloakClock.CLOAK_TICKS - 1);
        assertTrue(clock.isCloaked(), "the last tick of the cloak is still a tick of the cloak");

        clock.tick();
        assertFalse(clock.isCloaked());
        assertFalse(clock.isReady(), "there is never a tick where it is both uncloaked and re-armed");
    }

    @Test
    void beingHurtDuringTheLockoutDoesNothing() {
        CloakClock clock = new CloakClock();
        clock.onDamaged();
        tick(clock, CloakClock.CLOAK_TICKS);
        int locked = clock.value();

        assertFalse(clock.onDamaged());
        assertFalse(clock.isCloaked());
        assertEquals(locked, clock.value(), "a hit must not reset the lockout either");
    }

    @Test
    void theWholeCycleCostsItsCloakPlusItsLockout() {
        CloakClock clock = new CloakClock();
        clock.onDamaged();

        assertEquals(CloakClock.CLOAK_TICKS + CloakClock.COOLDOWN_TICKS, tickUntilSettled(clock));
        assertTrue(clock.isReady(), "and the Ghost is armed again at the end of it");
        assertTrue(clock.onDamaged(), "so the next hit cloaks it again");
    }

    @Test
    void aRestoredStateResumesWhereItLeftOff() {
        CloakClock saved = new CloakClock();
        saved.onDamaged();
        tick(saved, 250);

        CloakClock loaded = new CloakClock();
        loaded.restore(saved.value());

        assertEquals(saved.value(), loaded.value());
        assertTrue(loaded.isCloaked(), "a Ghost saved hidden comes back hidden");
        assertEquals(CloakClock.CLOAK_TICKS - 250 + CloakClock.COOLDOWN_TICKS,
                tickUntilSettled(loaded), "and owes only the time it had left");
    }

    @Test
    void aRestoredStateIsClampedToSomethingTheClockCouldHaveProduced() {
        CloakClock clock = new CloakClock();

        clock.restore(Integer.MAX_VALUE);
        assertEquals(CloakClock.CLOAK_TICKS, clock.value());

        clock.restore(Integer.MIN_VALUE);
        assertEquals(-CloakClock.COOLDOWN_TICKS, clock.value());
    }
}
