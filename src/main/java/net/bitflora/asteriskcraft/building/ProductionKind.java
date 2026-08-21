package net.bitflora.asteriskcraft.building;

import net.bitflora.asteriskcraft.AsteriskCraft;
import net.bitflora.asteriskcraft.race.Races;
import net.bitflora.asteriskcraft.stats.CostText;
import net.bitflora.asteriskcraft.stats.Resource;
import net.bitflora.asteriskcraft.stats.UnitStats;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;

import java.util.List;
import java.util.function.Supplier;

/**
 * Client-safe description of a production building: which block it is (for the menu's
 * {@code stillValid} check), how many input slots it exposes, and the buttons the screen
 * should draw. Serialized to the open-menu buffer by {@link #ordinal()} and rebuilt on the client.
 *
 * <p>Tooltip text is built from {@code stats.UnitStats} via {@link CostText}, so the numbers
 * shown here can never drift from what {@code CostPayment} actually charges. Referencing
 * {@code stats.Resource} is client-safe on its own — its predicates are lambdas never evaluated at
 * class-init — but nothing here evaluates one; only the server-side {@code CostPayment} does.
 *
 * <p>Options are laid out column-major: each {@link OptionView} names the unit column it
 * belongs to (see {@link #columns}), stacked top-to-bottom in list order within that column.
 * Columns don't need equal height — the Protoss base gives Probe/Gateway/Photon Cannon a Wood
 * button above a Stone button (two separate buttons instead of one that pays with a mix of both)
 * but only a single button for the Nexus Kit. Buttons show only their icon — no name label — the
 * icon identifies the unit, and the hover tooltip gives its cost (including which resource a
 * Wood/Stone pair's button pays with).
 *
 * <p>A base's card also carries what each button <em>does</em>, as an {@link Action}, so
 * {@link BaseBlockEntity#trainOption} executes it without knowing which race's card it is holding.
 * That is what makes a second race's command card a new entry in this enum rather than a new branch
 * in the block entity. A unit factory keeps its own dispatch: the Gateway's buttons are positional
 * by design (see {@code GatewayBlockEntity.UnitType}), so they carry {@link Action#FACTORY}.
 * Actions are inert data on the client — the item a kit names is a supplier, and only the server
 * ever resolves it.
 */
public enum ProductionKind {
    PROTOSS_BASE(() -> AsteriskCraft.NEXUS_CORE.get(), Races.PROTOSS.bankSlots(), List.of(
            new OptionView(
                    Icon.ofTexture(AsteriskCraft.id("textures/gui/icons/probe.png"), 115, 111),
                    CostText.tooltip(UnitStats.PROBE.cost(), 0), 0,
                    new Action.TrainWorker(0)),
            new OptionView(
                    Icon.ofTexture(AsteriskCraft.id("textures/gui/icons/probe.png"), 115, 111),
                    CostText.tooltip(UnitStats.PROBE.cost(), 1), 0,
                    new Action.TrainWorker(1)),
            new OptionView(
                    Icon.ofItem(new ItemStack(AsteriskCraft.GATEWAY_KIT.get())),
                    CostText.tooltip(BaseBlockEntity.BUILDING_COST, Resource.WOOD), 1,
                    new Action.GiveKit(AsteriskCraft.GATEWAY_KIT::get, Resource.WOOD,
                            BaseBlockEntity.BUILDING_COST, "message.asteriskcraft.base.gateway_ready")),
            new OptionView(
                    Icon.ofItem(new ItemStack(AsteriskCraft.GATEWAY_KIT.get())),
                    CostText.tooltip(BaseBlockEntity.BUILDING_COST, Resource.STONE), 1,
                    new Action.GiveKit(AsteriskCraft.GATEWAY_KIT::get, Resource.STONE,
                            BaseBlockEntity.BUILDING_COST, "message.asteriskcraft.base.gateway_ready")),
            new OptionView(
                    Icon.ofItem(new ItemStack(AsteriskCraft.PHOTON_CANNON_KIT.get())),
                    CostText.tooltip(BaseBlockEntity.BUILDING_COST, Resource.WOOD), 2,
                    new Action.GiveKit(AsteriskCraft.PHOTON_CANNON_KIT::get, Resource.WOOD,
                            BaseBlockEntity.BUILDING_COST, "message.asteriskcraft.base.photon_cannon_ready")),
            new OptionView(
                    Icon.ofItem(new ItemStack(AsteriskCraft.PHOTON_CANNON_KIT.get())),
                    CostText.tooltip(BaseBlockEntity.BUILDING_COST, Resource.STONE), 2,
                    new Action.GiveKit(AsteriskCraft.PHOTON_CANNON_KIT::get, Resource.STONE,
                            BaseBlockEntity.BUILDING_COST, "message.asteriskcraft.base.photon_cannon_ready")),
            new OptionView(
                    Icon.ofItem(new ItemStack(AsteriskCraft.NEXUS_KIT.get())),
                    CostText.tooltip(BaseBlockEntity.BASE_KIT_COST, Resource.STONE), 3,
                    new Action.GiveKit(AsteriskCraft.NEXUS_KIT::get, Resource.STONE,
                            BaseBlockEntity.BASE_KIT_COST, "message.asteriskcraft.base.base_kit_ready")))),
    GATEWAY(() -> AsteriskCraft.GATEWAY_CORE.get(), Races.PROTOSS.bankSlots(), List.of(
            new OptionView(
                    Icon.ofTexture(AsteriskCraft.id("textures/gui/icons/zealot.png"), 116, 121),
                    CostText.tooltip(UnitStats.ZEALOT.cost(), 0), 0, Action.FACTORY),
            new OptionView(
                    Icon.ofTexture(AsteriskCraft.id("textures/gui/icons/dragoon.png"), 113, 112),
                    CostText.tooltip(UnitStats.DRAGOON.cost(), 0), 1, Action.FACTORY),
            new OptionView(
                    Icon.ofTexture(AsteriskCraft.id("textures/gui/icons/scout.png"), 112, 111),
                    CostText.tooltip(UnitStats.SCOUT.cost(), 0), 2, Action.FACTORY),
            new OptionView(
                    Icon.ofTexture(AsteriskCraft.id("textures/gui/icons/dark_templar.png"), 117, 114),
                    CostText.tooltip(UnitStats.DARK_TEMPLAR.cost(), 0), 3, Action.FACTORY)));

    /**
     * One train button: an icon, a cost tooltip, the unit column it stacks into (see class docs),
     * and what pressing it does.
     */
    public record OptionView(Icon icon, Component costTooltip, int column, Action action) {
    }

    /**
     * What a button does when pressed. A base's card is executed generically from this, which is
     * why a base never needs to know which race's card it is holding.
     */
    public sealed interface Action {
        /**
         * Queue one worker, paying cost alternative {@code alternative} — the Wood button is 0 and
         * the Stone button 1 (see {@code UnitCost}, whose alternative order is load-bearing for
         * exactly this reason). <em>Which</em> worker is the base's race's, not this option's, so
         * one entry covers every race that copies this layout.
         */
        record TrainWorker(int alternative) implements Action {
        }

        /** Hand the player a building kit item for {@code cost} of one resource. */
        record GiveKit(Supplier<Item> kit, Resource resource, int cost, String readyKey) implements Action {
        }

        /**
         * This button belongs to a unit factory, which dispatches its own options by index.
         * Carrying a marker rather than leaving the field null keeps every option answerable.
         */
        record Factory() implements Action {
        }

        Action FACTORY = new Factory();
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
        return (id >= 0 && id < values.length) ? values[id] : PROTOSS_BASE;
    }
}
