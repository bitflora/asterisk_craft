package net.bitflora.asteriskcraft.building;

import net.bitflora.asteriskcraft.AsteriskCraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;

import java.util.List;
import java.util.function.Supplier;

/**
 * Client-safe description of a production building: which block it is (for the menu's
 * {@code stillValid} check), how many input slots it exposes, and the buttons the screen
 * should draw. Carries no cost predicates — those stay server-side in the block entity.
 * Serialized to the open-menu buffer by {@link #ordinal()} and rebuilt on the client.
 *
 * <p>Options are laid out column-major: {@link #columns} unit columns, each holding
 * {@link #optionsPerColumn()} stacked buttons. The Nexus uses this to give every unit its
 * own column with a Wood button above a Stone button — deliberately two separate buttons
 * instead of one that pays with a mix of both, see {@code NexusBlockEntity#trainOption} —
 * while keeping the button label itself just the unit's icon and name; the resource a given
 * button pays with is only in its tooltip, not the label.
 */
public enum ProductionKind {
    NEXUS(() -> AsteriskCraft.NEXUS_CORE.get(), NexusBlockEntity.INPUT_SLOTS, 3, List.of(
            new OptionView(
                    Component.translatable("entity.asteriskcraft.probe"),
                    Icon.ofTexture(AsteriskCraft.id("textures/gui/icons/probe.png"), 115, 111),
                    Component.translatable("gui.asteriskcraft.cost.probe_wood")),
            new OptionView(
                    Component.translatable("entity.asteriskcraft.probe"),
                    Icon.ofTexture(AsteriskCraft.id("textures/gui/icons/probe.png"), 115, 111),
                    Component.translatable("gui.asteriskcraft.cost.probe_stone")),
            new OptionView(
                    Component.translatable("block.asteriskcraft.gateway_core"),
                    Icon.ofItem(new ItemStack(AsteriskCraft.GATEWAY_KIT.get())),
                    Component.translatable("gui.asteriskcraft.cost.gateway_wood")),
            new OptionView(
                    Component.translatable("block.asteriskcraft.gateway_core"),
                    Icon.ofItem(new ItemStack(AsteriskCraft.GATEWAY_KIT.get())),
                    Component.translatable("gui.asteriskcraft.cost.gateway_stone")),
            new OptionView(
                    Component.translatable("entity.asteriskcraft.photon_cannon"),
                    Icon.ofItem(new ItemStack(AsteriskCraft.PHOTON_CANNON_KIT.get())),
                    Component.translatable("gui.asteriskcraft.cost.photon_cannon_wood")),
            new OptionView(
                    Component.translatable("entity.asteriskcraft.photon_cannon"),
                    Icon.ofItem(new ItemStack(AsteriskCraft.PHOTON_CANNON_KIT.get())),
                    Component.translatable("gui.asteriskcraft.cost.photon_cannon_stone")))),
    GATEWAY(() -> AsteriskCraft.GATEWAY_CORE.get(), NexusBlockEntity.INPUT_SLOTS, 1, List.of(
            new OptionView(
                    Component.translatable("entity.asteriskcraft.zealot"),
                    Icon.ofTexture(AsteriskCraft.id("textures/gui/icons/zealot.png"), 116, 121),
                    Component.translatable("gui.asteriskcraft.cost.zealot")),
            new OptionView(
                    Component.translatable("entity.asteriskcraft.dragoon"),
                    Icon.ofTexture(AsteriskCraft.id("textures/gui/icons/dragoon.png"), 113, 112),
                    Component.translatable("gui.asteriskcraft.cost.dragoon"))));

    /** One train button: unit label, an icon, and a cost tooltip (the tooltip carries any resource-specific detail). */
    public record OptionView(Component label, Icon icon, Component costTooltip) {
    }

    /**
     * A button's icon: either a real registered item's render (used for the Gateway/Photon
     * Cannon kits, which are actual items) or a hand-picked command-card style texture (used
     * for units that have no item form of their own — Probe, Zealot, Dragoon).
     */
    public sealed interface Icon {
        record FromItem(ItemStack stack) implements Icon {
        }

        record FromTexture(Identifier location, int width, int height) implements Icon {
        }

        static Icon ofItem(ItemStack stack) {
            return new FromItem(stack);
        }

        static Icon ofTexture(Identifier location, int width, int height) {
            return new FromTexture(location, width, height);
        }
    }

    private final Supplier<Block> block;
    private final int inputSlotCount;
    private final int columns;
    private final List<OptionView> options;

    ProductionKind(Supplier<Block> block, int inputSlotCount, int columns, List<OptionView> options) {
        this.block = block;
        this.inputSlotCount = inputSlotCount;
        this.columns = columns;
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

    /** Number of unit columns the option buttons are grouped into (see class docs). */
    public int columns() {
        return this.columns;
    }

    /** Buttons stacked within a single column; {@link #options()} is ordered column-major. */
    public int optionsPerColumn() {
        return this.options.size() / this.columns;
    }

    public static ProductionKind byId(int id) {
        ProductionKind[] values = values();
        return (id >= 0 && id < values.length) ? values[id] : NEXUS;
    }
}
