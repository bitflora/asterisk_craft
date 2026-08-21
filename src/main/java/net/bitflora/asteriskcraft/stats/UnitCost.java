package net.bitflora.asteriskcraft.stats;

import net.bitflora.asteriskcraft.building.ResourceBank;

import java.util.ArrayList;
import java.util.List;

/**
 * What a unit costs. Each element of {@link #alternatives()} is a bundle every line of which must
 * be paid together; paying <em>any one</em> bundle produces the unit. So a one-bundle cost is an
 * AND (a Zealot: 50 wood AND 50 cobblestone) and a multi-bundle cost is an OR (a Probe: 50 wood OR
 * 50 cobblestone).
 *
 * <p><strong>Alternative order is load-bearing</strong> where a building gives one button per
 * alternative: the Nexus's Probe buttons are Wood then Stone, i.e. alternatives 0 and 1 in that
 * order (see {@code ProductionKind.Action.TrainWorker} and {@code ProductionKind.PROTOSS_BASE}).
 * {@code stats.UnitStatsTest} pins it.
 */
public record UnitCost(List<List<ResourceAmount>> alternatives) {

    /** Not directly purchasable: a Photon Cannon comes from a kit bought at the Nexus
     *  ({@code BaseBlockEntity.BUILDING_COST}); a Sunken Colony is pre-placed by GameBootstrap. */
    public static final UnitCost NONE = new UnitCost(List.of());

    public static ResourceAmount line(Resource resource, int amount) {
        return new ResourceAmount(resource, amount);
    }

    /** One bundle: every line must be paid together (AND). */
    public static UnitCost all(ResourceAmount... lines) {
        return new UnitCost(List.of(List.of(lines)));
    }

    /** Several interchangeable single-line bundles (OR) — one line each. */
    @SafeVarargs
    public static UnitCost either(List<ResourceAmount>... bundles) {
        return new UnitCost(List.of(bundles));
    }

    /** A one-line, one-bundle cost. */
    public static UnitCost of(Resource resource, int amount) {
        return all(line(resource, amount));
    }

    public boolean isPurchasable() {
        return !this.alternatives.isEmpty();
    }

    /** Amount of {@code resource} in the given alternative; 0 if that alternative doesn't use it. */
    public int amountOf(int alternative, Resource resource) {
        for (ResourceAmount line : this.alternatives.get(alternative)) {
            if (line.resource() == resource) {
                return line.amount();
            }
        }
        return 0;
    }

    /**
     * Amount of {@code resource} in the single alternative — the terse form for a unit test.
     * Throws if this cost has more than one alternative; use {@link #amountOf(int, Resource)} for
     * an interchangeable (either/or) cost.
     */
    public int amountOf(Resource resource) {
        if (this.alternatives.size() != 1) {
            throw new IllegalStateException(
                    "amountOf(Resource) needs exactly one alternative, has " + this.alternatives.size());
        }
        return amountOf(0, resource);
    }

    /** Adapter to the payment layer. This is the only place a {@link Resource} predicate is built. */
    public List<ResourceBank.Cost> bankCosts(int alternative) {
        List<ResourceBank.Cost> costs = new ArrayList<>();
        for (ResourceAmount line : this.alternatives.get(alternative)) {
            costs.add(new ResourceBank.Cost(line.resource().matches(), line.amount()));
        }
        return costs;
    }
}
