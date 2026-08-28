package net.bitflora.asteriskcraft.stats;

import net.bitflora.asteriskcraft.building.ResourceBank;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * {@link Resource#ANY} matches every item in a bank, iron included, so a bundle that asks for both a
 * flat pile and a specific resource has to spend the specific one first — otherwise the pile takes
 * the very iron the next line still needs, and the payment either fails on a bank that could afford
 * it or (before {@code ResourceBank}'s check learned to simulate) quietly charges less than the
 * price. The Spire is the first cost in the mod shaped that way.
 *
 * <p>Checked on the emitted {@link ResourceBank.Cost} order rather than by paying out of a real
 * container: a non-empty {@code ItemStack} can't be constructed in the JUnit bootstrap (see
 * {@code building.ResourceBankTest}), so the payment itself is exercised via {@code runClient}.
 */
class UnitCostPaymentOrderTest {

    @Test
    void anyLinesArePaidAfterTheLinesThatNameAResource() {
        UnitCost authoredAnyFirst = UnitCost.all(
                UnitCost.line(Resource.ANY, 250), UnitCost.line(Resource.IRON, 10));

        List<ResourceBank.Cost> costs = authoredAnyFirst.bankCosts(0);

        assertEquals(2, costs.size());
        assertSame(Resource.IRON.matches(), costs.get(0).matches(),
                "the iron line has to be paid before the flat pile that would swallow it");
        assertSame(Resource.ANY.matches(), costs.get(1).matches());
    }

    @Test
    void aCostWithoutAnAnyLineKeepsTheOrderItWasAuthoredIn() {
        UnitCost cost = UnitCost.all(
                UnitCost.line(Resource.STONE, 150), UnitCost.line(Resource.WOOD, 150),
                UnitCost.line(Resource.IRON, 10));

        List<ResourceBank.Cost> costs = cost.bankCosts(0);

        assertSame(Resource.STONE.matches(), costs.get(0).matches());
        assertSame(Resource.WOOD.matches(), costs.get(1).matches());
        assertSame(Resource.IRON.matches(), costs.get(2).matches());
    }
}
