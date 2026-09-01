package net.bitflora.asteriskcraft.stats;

import net.bitflora.asteriskcraft.building.ResourceBank;
import net.minecraft.world.Container;

/**
 * Pays a {@link UnitCost} out of a {@link Container}, atomically, via {@link ResourceBank}. The
 * single payment path for every production building — replaces the three near-identical shapes
 * that used to live in {@code GatewayBlockEntity}, the Nexus block entity and the Zerg
 * director.
 */
public final class CostPayment {

    private CostPayment() {
    }

    /** Pays the first affordable alternative. False if none is affordable (or the cost is NONE). */
    public static boolean payAny(Container container, UnitCost cost) {
        for (int i = 0; i < cost.alternatives().size(); i++) {
            if (ResourceBank.extractAll(container, cost.bankCosts(i))) {
                return true;
            }
        }
        return false;
    }

    /**
     * Pays a cost as a flat quantity of anything — {@link UnitCost#flatTotal()} taken out of the
     * container without regard to what kind of item covers it. This is how the computer buys: the
     * {@code director.AiDirector} never opens a command card and never chooses between a cost's
     * alternatives, so making it hunt its bank for the specific logs a Zealot names only ever
     * stalls a build script beside a pile of cobblestone it is standing on. The player still pays
     * {@link #pay} and {@link #payAny}, kind by kind.
     *
     * <p>False if the cost isn't purchasable at all, or the container can't cover the total.
     */
    public static boolean payFlat(Container container, UnitCost cost) {
        int total = cost.flatTotal();
        if (total < 0) {
            return false;
        }
        return ResourceBank.extract(container, Resource.ANY.matches(), total);
    }

    /**
     * Pays one specific alternative — for a building whose UI gives the player one button per
     * alternative (e.g. the Nexus's Wood/Stone Probe buttons).
     */
    public static boolean pay(Container container, UnitCost cost, int alternative) {
        return ResourceBank.extractAll(container, cost.bankCosts(alternative));
    }
}
