package net.bitflora.asteriskcraft.stats;

import com.mojang.serialization.Codec;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.function.Predicate;

/**
 * The resource kinds a {@link UnitCost} line can be paid in. Protoss costs name a specific one;
 * every Zerg cost uses {@link #ANY}, which any item in the shared Hive bank satisfies (see the
 * Costs table in docs/shaping.md).
 *
 * <p>The predicates are lambdas on purpose: nothing here touches {@code Items}/{@code ItemTags} at
 * class-init beyond simple static field references, so the whole {@code stats} package loads in a
 * unit test where item/block <em>tags</em> are unbound — a test may read a cost's amounts freely;
 * only {@link CostPayment} ever invokes a predicate.
 *
 * <p>{@link #STONE}'s label is deliberately the literal {@code "cobblestone"}, not "stone" — it is
 * interpolated into user-facing messages (e.g. {@code message.asteriskcraft.nexus.cannot_afford}),
 * so changing it changes what the player reads.
 */
public enum Resource implements StringRepresentable {
    WOOD("wood", stack -> stack.is(ItemTags.LOGS)),
    STONE("cobblestone", stack -> stack.is(Items.COBBLESTONE)),
    IRON("iron", stack -> stack.is(Items.IRON_INGOT)),
    /** Any item at all — every Zerg cost, since the Hive bank isn't picky about what it's paid in. */
    ANY("resources", stack -> true);

    public static final Codec<Resource> CODEC = StringRepresentable.fromEnum(Resource::values);

    private final String label;
    private final Predicate<ItemStack> matches;

    Resource(String label, Predicate<ItemStack> matches) {
        this.label = label;
        this.matches = matches;
    }

    public Predicate<ItemStack> matches() {
        return this.matches;
    }

    /** Player-facing name; also this enum's serialized name. */
    public String label() {
        return this.label;
    }

    @Override
    public String getSerializedName() {
        return this.label;
    }
}
