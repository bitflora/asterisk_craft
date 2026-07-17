package net.bitflora.asteriskcraft.director;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Guards the Zerg attack-wave escalation curves. Both are pure functions of the wave number, so
 * they can be checked without a world: waves must grow, come sooner, and stay within sane bounds.
 */
class WaveScheduleTest {

    @Test
    void firstWaveHasAGracePeriod() {
        assertTrue(ZergDirector.FIRST_WAVE_DELAY > 0, "there must be a delay before the first wave");
    }

    @Test
    void waveSizeStartsAtBaseAndGrowsMonotonically() {
        assertEquals(ZergDirector.BASE_WAVE_SIZE, ZergDirector.waveSize(0));
        int previous = ZergDirector.waveSize(0);
        for (int n = 1; n <= 30; n++) {
            int current = ZergDirector.waveSize(n);
            assertTrue(current >= previous, "wave size must never shrink");
            assertTrue(current <= ZergDirector.MAX_WAVE_SIZE, "wave size must be capped");
            previous = current;
        }
    }

    @Test
    void waveSizeReachesTheCap() {
        assertEquals(ZergDirector.MAX_WAVE_SIZE, ZergDirector.waveSize(1000));
    }

    @Test
    void intervalStartsAtMaxAndShrinksToMin() {
        assertEquals(ZergDirector.WAVE_INTERVAL_MAX, ZergDirector.intervalFor(0));
        int previous = ZergDirector.intervalFor(0);
        for (int n = 1; n <= 30; n++) {
            int current = ZergDirector.intervalFor(n);
            assertTrue(current <= previous, "the interval must never grow");
            assertTrue(current >= ZergDirector.WAVE_INTERVAL_MIN, "the interval must not fall below the floor");
            previous = current;
        }
    }

    @Test
    void intervalBottomsOutAtMin() {
        assertEquals(ZergDirector.WAVE_INTERVAL_MIN, ZergDirector.intervalFor(1000));
    }
}
