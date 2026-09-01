package net.bitflora.asteriskcraft.stats;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * {@link UnitCost#flatTotal()} is how the computer reads a price: the kind of each line ignored,
 * because the {@code director.AiDirector} never opens a command card and would otherwise stall a
 * build script beside a bank full of the wrong resource. Pure arithmetic over a hand-built cost —
 * paying it out of a real container needs a non-empty {@code ItemStack}, which the JUnit bootstrap
 * can't construct (see {@code building.ResourceBankTest}).
 */
class UnitCostFlatTotalTest {

    @Test
    void anAndCostSumsEveryLineWhateverKindItNames() {
        UnitCost cost = UnitCost.all(
                UnitCost.line(Resource.STONE, 25), UnitCost.line(Resource.WOOD, 75),
                UnitCost.line(Resource.IRON, 3));

        assertEquals(103, cost.flatTotal());
    }

    @Test
    void anOrCostTakesItsCheapestAlternative() {
        UnitCost cost = UnitCost.either(
                List.of(UnitCost.line(Resource.WOOD, 80)),
                List.of(UnitCost.line(Resource.STONE, 50)));

        assertEquals(50, cost.flatTotal(),
                "alternative order is a command card's button order, which means nothing to a payer"
                        + " that isn't choosing a kind");
    }

    @Test
    void anUnpurchasableCostHasNoTotal() {
        assertEquals(-1, UnitCost.NONE.flatTotal());
    }
}
