package net.bitflora.asteriskcraft.building;

import net.bitflora.asteriskcraft.AsteriskCraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;

import java.util.List;
import java.util.function.Supplier;

/**
 * Client-safe description of a production building: which block it is (for the menu's
 * {@code stillValid} check), how many input slots it exposes, and the buttons the screen
 * should draw. Carries no cost predicates — those stay server-side in the block entity.
 * Serialized to the open-menu buffer by {@link #ordinal()} and rebuilt on the client.
 */
public enum ProductionKind {
    NEXUS(() -> AsteriskCraft.NEXUS_CORE.get(), 9, List.of(
            new OptionView(
                    Component.translatable("entity.asteriskcraft.probe"),
                    new ItemStack(Items.GOLDEN_PICKAXE),
                    Component.translatable("gui.asteriskcraft.cost.probe")))),
    GATEWAY(() -> AsteriskCraft.GATEWAY_CORE.get(), 9, List.of(
            new OptionView(
                    Component.translatable("entity.asteriskcraft.zealot"),
                    new ItemStack(Items.IRON_SWORD),
                    Component.translatable("gui.asteriskcraft.cost.zealot")),
            new OptionView(
                    Component.translatable("entity.asteriskcraft.dragoon"),
                    new ItemStack(Items.BOW),
                    Component.translatable("gui.asteriskcraft.cost.dragoon"))));

    /** One train button: unit label, an icon, and a cost tooltip. */
    public record OptionView(Component label, ItemStack icon, Component costTooltip) {
    }

    private final Supplier<Block> block;
    private final int inputSlotCount;
    private final List<OptionView> options;

    ProductionKind(Supplier<Block> block, int inputSlotCount, List<OptionView> options) {
        this.block = block;
        this.inputSlotCount = inputSlotCount;
        this.options = options;
    }

    public Block block() {
        return this.block.get();
    }

    public int inputSlotCount() {
        return this.inputSlotCount;
    }

    public List<OptionView> options() {
        return this.options;
    }

    public static ProductionKind byId(int id) {
        ProductionKind[] values = values();
        return (id >= 0 && id < values.length) ? values[id] : NEXUS;
    }
}
