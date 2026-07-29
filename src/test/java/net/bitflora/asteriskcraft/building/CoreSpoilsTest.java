package net.bitflora.asteriskcraft.building;

import net.minecraft.world.SimpleContainer;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Guards for the share a razed core spills — {@code 1 / (standing cores + 2)} of the army's
 * pooled resources.
 *
 * <p>Only the arithmetic is unit-testable: the extraction and the world drop need non-empty
 * {@link net.minecraft.world.item.ItemStack}s, which throw "Components not bound yet" in the
 * JUnit bootstrap (see {@link ResourceBankTest}), and {@link CoreSpoils#spill} additionally
 * needs a live {@link net.minecraft.server.level.ServerLevel} for the {@link CoreCensus}. Both
 * are verified via {@code runClient} by razing Hives and watching the pool shrink.
 */
class CoreSpoilsTest {

    @Test
    void theShareGrowsAsAFactionRunsOutOfCores() {
        assertEquals(4, CoreSpoils.denominator(2), "a quarter while two other Hives still stand");
        assertEquals(3, CoreSpoils.denominator(1));
        assertEquals(2, CoreSpoils.denominator(0), "the last core of a faction spills half the pool");
    }

    @Test
    void razingAllThreeHivesTakesAnEqualQuarterEachTime() {
        int pool = 100;
        int firstDrop = CoreSpoils.share(pool, CoreSpoils.denominator(2));
        pool -= firstDrop;
        int secondDrop = CoreSpoils.share(pool, CoreSpoils.denominator(1));
        pool -= secondDrop;
        int thirdDrop = CoreSpoils.share(pool, CoreSpoils.denominator(0));
        pool -= thirdDrop;

        assertEquals(25, firstDrop);
        assertEquals(25, secondDrop);
        assertEquals(25, thirdDrop);
        assertEquals(25, pool, "a quarter of the original pool survives the last Hive");
    }

    @Test
    void theShareRoundsDownSoSmallHoldingsSurvive() {
        assertEquals(2, CoreSpoils.share(10, 4));
        assertEquals(0, CoreSpoils.share(3, 4), "fewer items than the divisor means nothing is spilled");
    }

    @Test
    void aNonsenseDivisorSpillsNothingRatherThanDividingByZero() {
        assertEquals(0, CoreSpoils.share(100, 0));
        assertEquals(2, CoreSpoils.denominator(-1), "a negative core count is clamped, not propagated");
    }

    @Test
    void anEmptyBankSpillsNothing() {
        SimpleContainer bank = new SimpleContainer(9); // all slots ItemStack.EMPTY
        assertTrue(ResourceBank.extractShare(bank, 4).isEmpty());
    }
}
