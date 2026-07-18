package net.bitflora.asteriskcraft.building;

import net.minecraft.core.NonNullList;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.item.ItemStack;

/**
 * A "linked chest" onto a shared {@link ArmyBank} list: every building implementing this reads
 * and writes the same backing data as every other building in its army, instead of each holding
 * its own independent inventory. Implementors supply {@link #armyItems()} (which building's army
 * this instance belongs to) and {@link #markArmyBankChanged()} (typically {@code setChanged()});
 * {@code stillValid(Player)} is deliberately left for each building to implement itself (usually
 * {@code Container.stillValidBlockEntity(this, player)}), so menu-distance/removal validity stays
 * per-building even though the underlying data doesn't.
 */
public interface ArmyLinkedContainer extends Container {

    /** The live shared item list for this building's army — the same reference for every sibling. */
    NonNullList<ItemStack> armyItems();

    /** Marks the owning block entity dirty so the shared bank gets saved. */
    void markArmyBankChanged();

    @Override
    default int getContainerSize() {
        return armyItems().size();
    }

    @Override
    default boolean isEmpty() {
        return armyItems().stream().allMatch(ItemStack::isEmpty);
    }

    @Override
    default ItemStack getItem(int slot) {
        return armyItems().get(slot);
    }

    @Override
    default ItemStack removeItem(int slot, int amount) {
        ItemStack removed = ContainerHelper.removeItem(armyItems(), slot, amount);
        if (!removed.isEmpty()) {
            markArmyBankChanged();
        }
        return removed;
    }

    @Override
    default ItemStack removeItemNoUpdate(int slot) {
        return ContainerHelper.takeItem(armyItems(), slot);
    }

    @Override
    default void setItem(int slot, ItemStack stack) {
        NonNullList<ItemStack> items = armyItems();
        items.set(slot, stack);
        stack.limitSize(this.getMaxStackSize(stack));
        markArmyBankChanged();
    }

    @Override
    default void clearContent() {
        armyItems().clear();
    }
}
