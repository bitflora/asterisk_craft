package net.bitflora.asteriskcraft.building;

import net.minecraft.world.SimpleContainer;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * Guards for {@link ResourceBank}'s container extraction.
 *
 * <p>Only the "cannot afford" path is unit-testable here: constructing a non-empty
 * {@link net.minecraft.world.item.ItemStack} throws "Components not bound yet" in the
 * JUnit bootstrap (item data components aren't bound, the same limitation that leaves
 * block/item tags unbound — see {@link net.bitflora.asteriskcraft.entity.ProbeEconomyTest}).
 * The full all-or-nothing extraction (exact match empties slots, one-short leaves the
 * container untouched, multi-cost atomicity, surplus removes only the cost) is therefore
 * exercised via {@code runClient}: load resources into a Nexus/Gateway and train.
 */
class ResourceBankTest {

    @Test
    void emptyContainerCannotAfford() {
        SimpleContainer container = new SimpleContainer(9); // all slots ItemStack.EMPTY
        assertFalse(ResourceBank.extract(container, stack -> true, 1),
                "an empty container must never be able to pay a cost");
    }
}
