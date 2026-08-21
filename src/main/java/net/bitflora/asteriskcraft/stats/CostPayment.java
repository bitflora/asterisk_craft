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
     * Pays one specific alternative — for a building whose UI gives the player one button per
     * alternative (e.g. the Nexus's Wood/Stone Probe buttons).
     */
    public static boolean pay(Container container, UnitCost cost, int alternative) {
        return ResourceBank.extractAll(container, cost.bankCosts(alternative));
    }
}
