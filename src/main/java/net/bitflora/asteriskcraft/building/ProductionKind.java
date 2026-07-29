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
 * <p>Options are laid out column-major: each {@link OptionView} names the unit column it
 * belongs to (see {@link #columns}), stacked top-to-bottom in list order within that column.
 * Columns don't need equal height — the Nexus gives Probe/Gateway/Photon Cannon a Wood button
 * above a Stone button (two separate buttons instead of one that pays with a mix of both, see
 * {@code NexusBlockEntity#trainOption}) but only a single button for the Nexus Kit. Buttons
 * show only their icon — no name label — the icon identifies the unit, and the hover tooltip
 * gives its cost (including which resource a Wood/Stone pair's button pays with).
 */
public enum ProductionKind {
    NEXUS(() -> AsteriskCraft.NEXUS_CORE.get(), NexusBlockEntity.INPUT_SLOTS, List.of(
            new OptionView(
                    Icon.ofTexture(AsteriskCraft.id("textures/gui/icons/probe.png"), 115, 111),
                    Component.translatable("gui.asteriskcraft.cost.probe_wood"), 0),
            new OptionView(
                    Icon.ofTexture(AsteriskCraft.id("textures/gui/icons/probe.png"), 115, 111),
                    Component.translatable("gui.asteriskcraft.cost.probe_stone"), 0),
            new OptionView(
                    Icon.ofItem(new ItemStack(AsteriskCraft.GATEWAY_KIT.get())),
                    Component.translatable("gui.asteriskcraft.cost.gateway_wood"), 1),
            new OptionView(
                    Icon.ofItem(new ItemStack(AsteriskCraft.GATEWAY_KIT.get())),
                    Component.translatable("gui.asteriskcraft.cost.gateway_stone"), 1),
            new OptionView(
                    Icon.ofItem(new ItemStack(AsteriskCraft.PHOTON_CANNON_KIT.get())),
                    Component.translatable("gui.asteriskcraft.cost.photon_cannon_wood"), 2),
            new OptionView(
                    Icon.ofItem(new ItemStack(AsteriskCraft.PHOTON_CANNON_KIT.get())),
                    Component.translatable("gui.asteriskcraft.cost.photon_cannon_stone"), 2),
            new OptionView(
                    Icon.ofItem(new ItemStack(AsteriskCraft.NEXUS_KIT.get())),
                    Component.translatable("gui.asteriskcraft.cost.nexus_kit"), 3))),
    GATEWAY(() -> AsteriskCraft.GATEWAY_CORE.get(), NexusBlockEntity.INPUT_SLOTS, List.of(
            new OptionView(
                    Icon.ofTexture(AsteriskCraft.id("textures/gui/icons/zealot.png"), 116, 121),
                    Component.translatable("gui.asteriskcraft.cost.zealot"), 0),
            new OptionView(
                    Icon.ofTexture(AsteriskCraft.id("textures/gui/icons/dragoon.png"), 113, 112),
                    Component.translatable("gui.asteriskcraft.cost.dragoon"), 1),
            new OptionView(
                    Icon.ofTexture(AsteriskCraft.id("textures/gui/icons/scout.png"), 112, 111),
                    Component.translatable("gui.asteriskcraft.cost.scout"), 2)));

    /** One train button: an icon, a cost tooltip, and the unit column it stacks into (see class docs). */
    public record OptionView(Icon icon, Component costTooltip, int column) {
    }

    /**
     * A button's icon: either a real registered item's render (used for the Gateway/Photon
     * Cannon kits, which are actual items) or a hand-picked command-card style texture (used
     * for units that have no item form of their own — Probe, Zealot, Dragoon, Scout).
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
    private final List<OptionView> options;
    private final int columns;
    private final int[] rowsPerColumn;

    ProductionKind(Supplier<Block> block, int inputSlotCount, List<OptionView> options) {
        this.block = block;
        this.inputSlotCount = inputSlotCount;
        this.options = options;
        int columnCount = 0;
        for (OptionView option : options) {
            columnCount = Math.max(columnCount, option.column() + 1);
        }
        this.columns = columnCount;
        this.rowsPerColumn = new int[columnCount];
        for (OptionView option : options) {
            this.rowsPerColumn[option.column()]++;
        }
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

    /** Buttons stacked in the given column; columns may hold different counts. */
    public int rowsInColumn(int column) {
        return this.rowsPerColumn[column];
    }

    /** Tallest column, used to vertically center shorter columns against it. */
    public int maxRows() {
        int max = 0;
        for (int rows : this.rowsPerColumn) {
            max = Math.max(max, rows);
        }
        return max;
    }

    public static ProductionKind byId(int id) {
        ProductionKind[] values = values();
        return (id >= 0 && id < values.length) ? values[id] : NEXUS;
    }
}
