package net.bitflora.asteriskcraft.building;

import net.minecraft.world.Container;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

/**
 * Shared cost model for production buildings: pays item costs atomically out of a building's
 * own input {@link Container}. Costs can require several distinct resources (e.g. wood AND
 * cobblestone) paid together in one all-or-nothing operation.
 */
public final class ResourceBank {

    /** One resource requirement: a predicate matching the item and the amount needed. */
    public record Cost(Predicate<ItemStack> matches, int amount) {
    }

    private ResourceBank() {
    }

    /** Single-cost convenience for {@link #extractAll(Container, List)}. */
    public static boolean extract(Container container, Predicate<ItemStack> matches, int amount) {
        return extractAll(container, List.of(new Cost(matches, amount)));
    }

    /**
     * Atomically extracts every {@link Cost} from a single {@link Container}: either every
     * requirement is available and all are removed, or nothing is taken. Availability is
     * checked in full before any removal, so the compute-then-mutate is atomic within a tick
     * without needing a transaction object.
     *
     * <p>The check <b>simulates</b> the removals rather than totalling each cost independently,
     * because two costs may match the same stack — {@code Resource.ANY} matches the iron that an
     * iron line also wants (the Spire's price is both). Counting them separately said yes to a bank
     * that could only cover one of them, and the removal pass then took what it could and reported
     * success, charging the player for a building at less than its price. The simulation walks the
     * costs in the order they are paid, so it gives exactly the answer the removal pass will.
     */
    public static boolean extractAll(Container container, List<Cost> costs) {
        int[] remainingInSlot = new int[container.getContainerSize()];
        for (int i = 0; i < remainingInSlot.length; i++) {
            remainingInSlot[i] = container.getItem(i).getCount();
        }
        for (Cost cost : costs) {
            int owed = cost.amount();
            for (int i = 0; i < container.getContainerSize() && owed > 0; i++) {
                ItemStack stack = container.getItem(i);
                if (!stack.isEmpty() && remainingInSlot[i] > 0 && cost.matches().test(stack)) {
                    int take = Math.min(owed, remainingInSlot[i]);
                    remainingInSlot[i] -= take;
                    owed -= take;
                }
            }
            if (owed > 0) {
                return false;
            }
        }

        for (Cost cost : costs) {
            int remaining = cost.amount();
            for (int i = 0; i < container.getContainerSize() && remaining > 0; i++) {
                ItemStack stack = container.getItem(i);
                if (!stack.isEmpty() && cost.matches().test(stack)) {
                    int take = Math.min(remaining, stack.getCount());
                    container.removeItem(i, take);
                    remaining -= take;
                }
            }
        }
        container.setChanged();
        return true;
    }

    /**
     * Removes one share of everything in {@code container} — {@code floor(total / denominator)} of
     * each distinct item — and returns the removed stacks. Used to spill part of an army's pooled
     * resources when one of its cores is destroyed ({@link CoreSpoils}).
     *
     * <p>Items are pooled by {@link ItemStack#getItem()} across slots, so a resource split over
     * several stacks is shared as one total (data components are deliberately ignored — the bank
     * only ever holds plain vanilla resources). The removal itself is per slot, so every returned
     * stack is already within its max stack size and needs no further splitting.
     */
    public static List<ItemStack> extractShare(Container container, int denominator) {
        if (denominator <= 0) {
            return List.of();
        }

        Map<Item, Integer> totals = new LinkedHashMap<>();
        for (int i = 0; i < container.getContainerSize(); i++) {
            ItemStack stack = container.getItem(i);
            if (!stack.isEmpty()) {
                totals.merge(stack.getItem(), stack.getCount(), Integer::sum);
            }
        }

        Map<Item, Integer> owed = new LinkedHashMap<>();
        totals.forEach((item, total) -> {
            int share = total / denominator;
            if (share > 0) {
                owed.put(item, share);
            }
        });
        if (owed.isEmpty()) {
            return List.of();
        }

        List<ItemStack> removed = new ArrayList<>();
        for (int i = 0; i < container.getContainerSize(); i++) {
            ItemStack stack = container.getItem(i);
            if (stack.isEmpty()) {
                continue;
            }
            Item item = stack.getItem();
            int remaining = owed.getOrDefault(item, 0);
            if (remaining <= 0) {
                continue;
            }
            ItemStack taken = container.removeItem(i, Math.min(remaining, stack.getCount()));
            if (!taken.isEmpty()) {
                owed.put(item, remaining - taken.getCount());
                removed.add(taken);
            }
        }
        container.setChanged();
        return removed;
    }
}
