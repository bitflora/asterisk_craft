package net.bitflora.asteriskcraft.building;

import net.bitflora.asteriskcraft.faction.Faction;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.Containers;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import java.util.List;

/**
 * What a razed {@link FactionCore} spills onto the ground. A core can't just drop its own
 * inventory — it doesn't have one; it is a linked chest onto the whole army's shared
 * {@link ArmyBank} (see {@link ArmyLinkedContainer}), and dumping that would bankrupt an army
 * over one building. Instead a share of the pool is knocked loose, sized so that the loss bites
 * harder the fewer cores are left:
 *
 * <p>{@code 1 / (cores that faction still has standing + 2)}. For the Zerg's three Hives that is
 * a quarter, then a third, then a half — each raid taking an equal quarter of the original pool
 * and leaving the last quarter to the survivors. The rule is faction-generic and counts real
 * standing cores ({@link CoreCensus}), so an expansion Nexus genuinely cushions the loss of the
 * first one.
 *
 * <p>The arithmetic is kept pure ({@link #denominator}/{@link #share}) so it is unit-testable
 * without a world.
 */
public final class CoreSpoils {

    private CoreSpoils() {
    }

    /** The divisor for a core falling while {@code remainingCores} of its faction still stand. */
    public static int denominator(int remainingCores) {
        return Math.max(0, remainingCores) + 2;
    }

    /** How much of {@code total} one share is — rounded down, so small holdings survive intact. */
    public static int share(int total, int denominator) {
        return denominator <= 0 ? 0 : total / denominator;
    }

    /**
     * Removes one share of {@code faction}'s pooled resources from {@code bank} and drops it where
     * the core stood. Called from a core block entity's {@code preRemoveSideEffects}, by which
     * point the block has already been replaced with air — so the items land in the open, exactly
     * as a broken chest's do.
     */
    public static void spill(Level level, Faction faction, BlockPos pos, Container bank) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }
        CoreCensus.unregister(serverLevel, pos);
        int remaining = CoreCensus.count(serverLevel, faction, pos);
        List<ItemStack> spoils = ResourceBank.extractShare(bank, denominator(remaining));
        for (ItemStack stack : spoils) {
            Containers.dropItemStack(serverLevel, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, stack);
        }
    }
}
