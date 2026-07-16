package net.bitflora.asteriskcraft.building;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/**
 * Shared chest-scan + transactional extraction used by production buildings to pay
 * item costs out of nearby containers. Costs can require several distinct resources
 * (e.g. wood AND cobblestone) paid atomically in one transaction.
 */
public final class ResourceBank {

    /** One resource requirement: a predicate matching the item and the amount needed. */
    public record Cost(Predicate<ItemStack> matches, int amount) {
    }

    private ResourceBank() {
    }

    /** Extracts exactly {@code amount} items matching {@code matches} from containers near {@code center}. */
    public static boolean extract(ServerLevel level, BlockPos center, int radius, Predicate<ItemStack> matches, int amount) {
        return extractAll(level, center, radius, List.of(new Cost(matches, amount)));
    }

    /**
     * Extracts every {@link Cost} in the list atomically: either all requirements are met and
     * extracted together, or nothing is taken.
     */
    public static boolean extractAll(ServerLevel level, BlockPos center, int radius, List<Cost> costs) {
        List<ResourceHandler<ItemResource>> handlers = new ArrayList<>();
        for (BlockPos scanPos : BlockPos.betweenClosed(
                center.offset(-radius, -3, -radius),
                center.offset(radius, 3, radius))) {
            ResourceHandler<ItemResource> handler = level.getCapability(Capabilities.Item.BLOCK, scanPos.immutable(), null);
            if (handler != null) {
                handlers.add(handler);
            }
        }

        for (Cost cost : costs) {
            int available = 0;
            for (ResourceHandler<ItemResource> handler : handlers) {
                for (int i = 0; i < handler.size(); i++) {
                    ItemResource res = handler.getResource(i);
                    if (!res.isEmpty() && cost.matches().test(res.toStack(1))) {
                        available += handler.getAmountAsInt(i);
                    }
                }
            }
            if (available < cost.amount()) {
                return false;
            }
        }

        try (Transaction tx = Transaction.openRoot()) {
            for (Cost cost : costs) {
                int remaining = cost.amount();
                for (ResourceHandler<ItemResource> handler : handlers) {
                    for (int i = 0; i < handler.size() && remaining > 0; i++) {
                        ItemResource res = handler.getResource(i);
                        if (!res.isEmpty() && cost.matches().test(res.toStack(1))) {
                            remaining -= handler.extract(i, res, remaining, tx);
                        }
                    }
                }
                if (remaining != 0) {
                    return false;
                }
            }
            tx.commit();
            return true;
        }
    }
}
