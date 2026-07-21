package net.bitflora.asteriskcraft.director.script;

import net.minecraft.util.RandomSource;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Bounds and normalization guarantees for the quantity range type. */
class IntRangeTest {

    @Test
    void reversedBoundsAreNormalized() {
        assertEquals(new IntRange(2, 7), new IntRange(7, 2));
    }

    @Test
    void singleRollsItsExactValue() {
        RandomSource random = RandomSource.create(1234L);
        IntRange range = IntRange.single(5);
        for (int i = 0; i < 100; i++) {
            assertEquals(5, range.roll(random));
        }
    }

    @Test
    void rangeRollStaysWithinInclusiveBounds() {
        RandomSource random = RandomSource.create(9876L);
        IntRange range = new IntRange(3, 6);
        boolean sawMin = false;
        boolean sawMax = false;
        for (int i = 0; i < 2000; i++) {
            int value = range.roll(random);
            assertTrue(value >= 3 && value <= 6, "roll out of bounds: " + value);
            sawMin |= value == 3;
            sawMax |= value == 6;
        }
        assertTrue(sawMin && sawMax, "both endpoints should be reachable");
    }
}
