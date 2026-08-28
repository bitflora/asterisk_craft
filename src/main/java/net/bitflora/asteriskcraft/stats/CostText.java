package net.bitflora.asteriskcraft.stats;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

import java.util.List;

/**
 * Builds a player-facing tooltip {@link Component} from a {@link UnitCost} (or a plain building-kit
 * cost). A single translation key can't express a variable-length cost — one line, or several
 * joined with "+" — so the GUI builds the tooltip from the numbers instead of trying to
 * parameterize one string per unit. This is the only place a {@link UnitCost}'s numbers reach the
 * player; {@code stats.UnitStats} stays the one place they're authored.
 */
public final class CostText {

    private CostText() {
    }

    /** "Cost: 50 wood" / "Cost: 150 cobblestone + 20 iron" for one alternative of a unit's cost. */
    public static Component tooltip(UnitCost cost, int alternative) {
        return prefixed(lines(cost.alternatives().get(alternative)));
    }

    /** "50 wood + 50 cobblestone" — the bare amounts, with no "Cost: " prefix, for embedding in a sentence. */
    public static Component costOnly(UnitCost cost, int alternative) {
        return lines(cost.alternatives().get(alternative));
    }

    private static MutableComponent prefixed(Component body) {
        return Component.translatable("gui.asteriskcraft.cost.prefix").copy().append(body);
    }

    private static Component lines(List<ResourceAmount> bundle) {
        MutableComponent result = null;
        for (ResourceAmount amount : bundle) {
            Component next = line(amount.amount(), amount.resource());
            result = result == null ? next.copy() : result.append(" + ").append(next);
        }
        return result == null ? Component.empty() : result;
    }

    private static Component line(int amount, Resource resource) {
        return Component.translatable("gui.asteriskcraft.cost.line", amount,
                Component.translatable("gui.asteriskcraft.resource." + resource.getSerializedName()));
    }
}
