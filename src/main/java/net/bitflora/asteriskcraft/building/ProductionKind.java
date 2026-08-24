package net.bitflora.asteriskcraft.building;

import net.bitflora.asteriskcraft.AsteriskCraft;
import net.bitflora.asteriskcraft.race.Races;
import net.bitflora.asteriskcraft.stats.CostText;
import net.bitflora.asteriskcraft.stats.Resource;
import net.bitflora.asteriskcraft.stats.UnitStats;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
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
 * Columns don't need equal height — the Protoss base gives Probe/Pylon/Gateway/Photon Cannon a Wood
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
                    Icon.ofIcon("probe"),
                    CostText.tooltip(UnitStats.PROBE.cost(), 0), 0,
                    new Action.TrainWorker(0)),
            new OptionView(
                    Icon.ofIcon("probe"),
                    CostText.tooltip(UnitStats.PROBE.cost(), 1), 0,
                    new Action.TrainWorker(1)),
            // The Pylon reads first among the buildings because everything to its right needs one
            // standing before its kit will go down — see PsiField.
            new OptionView(
                    Icon.ofItem(AsteriskCraft.PYLON_KIT),
                    CostText.tooltip(BaseBlockEntity.PYLON_COST, Resource.WOOD), 1,
                    new Action.GiveKit(AsteriskCraft.PYLON_KIT::get, Resource.WOOD,
                            BaseBlockEntity.PYLON_COST, "message.asteriskcraft.base.pylon_ready")),
            new OptionView(
                    Icon.ofItem(AsteriskCraft.PYLON_KIT),
                    CostText.tooltip(BaseBlockEntity.PYLON_COST, Resource.STONE), 1,
                    new Action.GiveKit(AsteriskCraft.PYLON_KIT::get, Resource.STONE,
                            BaseBlockEntity.PYLON_COST, "message.asteriskcraft.base.pylon_ready")),
            new OptionView(
                    Icon.ofItem(AsteriskCraft.GATEWAY_KIT),
                    CostText.tooltip(BaseBlockEntity.BUILDING_COST, Resource.WOOD), 2,
                    new Action.GiveKit(AsteriskCraft.GATEWAY_KIT::get, Resource.WOOD,
                            BaseBlockEntity.BUILDING_COST, "message.asteriskcraft.base.gateway_ready")),
            new OptionView(
                    Icon.ofItem(AsteriskCraft.GATEWAY_KIT),
                    CostText.tooltip(BaseBlockEntity.BUILDING_COST, Resource.STONE), 2,
                    new Action.GiveKit(AsteriskCraft.GATEWAY_KIT::get, Resource.STONE,
                            BaseBlockEntity.BUILDING_COST, "message.asteriskcraft.base.gateway_ready")),
            new OptionView(
                    Icon.ofItem(AsteriskCraft.PHOTON_CANNON_KIT),
                    CostText.tooltip(BaseBlockEntity.BUILDING_COST, Resource.WOOD), 3,
                    new Action.GiveKit(AsteriskCraft.PHOTON_CANNON_KIT::get, Resource.WOOD,
                            BaseBlockEntity.BUILDING_COST, "message.asteriskcraft.base.photon_cannon_ready")),
            new OptionView(
                    Icon.ofItem(AsteriskCraft.PHOTON_CANNON_KIT),
                    CostText.tooltip(BaseBlockEntity.BUILDING_COST, Resource.STONE), 3,
                    new Action.GiveKit(AsteriskCraft.PHOTON_CANNON_KIT::get, Resource.STONE,
                            BaseBlockEntity.BUILDING_COST, "message.asteriskcraft.base.photon_cannon_ready")),
            new OptionView(
                    Icon.ofItem(AsteriskCraft.NEXUS_KIT),
                    CostText.tooltip(BaseBlockEntity.BASE_KIT_COST, Resource.STONE), 4,
                    new Action.GiveKit(AsteriskCraft.NEXUS_KIT::get, Resource.STONE,
                            BaseBlockEntity.BASE_KIT_COST, "message.asteriskcraft.base.base_kit_ready")))),
    GATEWAY(() -> AsteriskCraft.GATEWAY_CORE.get(), Races.PROTOSS.bankSlots(), List.of(
            new OptionView(
                    Icon.ofIcon("zealot"),
                    CostText.tooltip(UnitStats.ZEALOT.cost(), 0), 0, Action.FACTORY),
            new OptionView(
                    Icon.ofIcon("dragoon"),
                    CostText.tooltip(UnitStats.DRAGOON.cost(), 0), 1, Action.FACTORY),
            new OptionView(
                    Icon.ofIcon("scout"),
                    CostText.tooltip(UnitStats.SCOUT.cost(), 0), 2, Action.FACTORY),
            new OptionView(
                    Icon.ofIcon("dark_templar"),
                    CostText.tooltip(UnitStats.DARK_TEMPLAR.cost(), 0), 3, Action.FACTORY))),
    /**
     * The Hive's card. Wider than the Protoss base's because the swarm has no factory building:
     * a Hive morphs its combat units itself, so everything an army needs is on this one card.
     *
     * <p>Two deliberate asymmetries with {@link #PROTOSS_BASE}. Every button is a single one rather
     * than a Wood/Stone pair — the swarm pays for everything out of one pool — which is also what
     * keeps this grid inside the panel's six-column budget. And its input slot count is a flat 27,
     * three rows, rather than the Zerg bank's full {@code Races.ZERG.bankSlots()} of 81:
     * {@link ProductionMenu} lays its height out around three rows, nine would run straight through
     * the button row and the player inventory, and the bank fills from the front with only three
     * item types ever in it — so three rows is the working set, not a truncation of it. (Written as
     * a literal because an enum constant may not forward-reference a static field of its own enum.)
     */
    ZERG_BASE(() -> AsteriskCraft.HIVE_CORE.get(), 27, List.of(
            // One button, not the Nexus's pair: every Zerg cost is a single "any resource"
            // alternative (UnitCost.of(ANY, ...)), so there is no Wood-or-Stone choice to offer.
            new OptionView(
                    Icon.ofIcon("drone"),
                    CostText.tooltip(UnitStats.DRONE.cost(), 0), 0,
                    new Action.TrainWorker(0)),
            new OptionView(
                    Icon.ofIcon("zergling"),
                    CostText.tooltip(UnitStats.ZERGLING.cost(), 0), 1,
                    new Action.TrainUnit(UnitStats.ZERGLING.id())),
            new OptionView(
                    Icon.ofIcon("ultralisk"),
                    CostText.tooltip(UnitStats.ULTRALISK.cost(), 0), 1,
                    new Action.TrainUnit(UnitStats.ULTRALISK.id())),
            new OptionView(
                    Icon.ofIcon("hydralisk"),
                    CostText.tooltip(UnitStats.HYDRALISK.cost(), 0), 2,
                    new Action.TrainUnit(UnitStats.HYDRALISK.id())),
            new OptionView(
                    Icon.ofIcon("lurker"),
                    CostText.tooltip(UnitStats.LURKER.cost(), 0), 2,
                    new Action.TrainUnit(UnitStats.LURKER.id())),
            new OptionView(
                    Icon.ofIcon("mutalisk"),
                    CostText.tooltip(UnitStats.MUTALISK.cost(), 0), 3,
                    new Action.TrainUnit(UnitStats.MUTALISK.id())),
            // The air column's second button: the swarm's detector, and the one unit on this card
            // that cannot fight at all.
            new OptionView(
                    Icon.ofIcon("overlord"),
                    CostText.tooltip(UnitStats.OVERLORD.cost(), 0), 3,
                    new Action.TrainUnit(UnitStats.OVERLORD.id())),
            // The colonies are entities rather than block layouts, so they are bought exactly the
            // way the Photon Cannon is: an ally-side spawn item handed over for a building's price.
            new OptionView(
                    Icon.ofItem(AsteriskCraft.SUNKEN_COLONY_SPAWN_EGG_ALLY),
                    CostText.tooltip(BaseBlockEntity.BUILDING_COST, Resource.STONE), 4,
                    new Action.GiveKit(AsteriskCraft.SUNKEN_COLONY_SPAWN_EGG_ALLY::get, Resource.STONE,
                            BaseBlockEntity.BUILDING_COST, "message.asteriskcraft.base.sunken_colony_ready")),
            new OptionView(
                    Icon.ofItem(AsteriskCraft.SPORE_COLONY_SPAWN_EGG_ALLY),
                    CostText.tooltip(BaseBlockEntity.BUILDING_COST, Resource.STONE), 4,
                    new Action.GiveKit(AsteriskCraft.SPORE_COLONY_SPAWN_EGG_ALLY::get, Resource.STONE,
                            BaseBlockEntity.BUILDING_COST, "message.asteriskcraft.base.spore_colony_ready")),
            new OptionView(
                    Icon.ofItem(AsteriskCraft.HIVE_KIT),
                    CostText.tooltip(BaseBlockEntity.BASE_KIT_COST, Resource.STONE), 5,
                    new Action.GiveKit(AsteriskCraft.HIVE_KIT::get, Resource.STONE,
                            BaseBlockEntity.BASE_KIT_COST, "message.asteriskcraft.base.hive_kit_ready")))),
    /**
     * The Command Center's card. The Terran have no factory building, so — like the Hive and unlike
     * the Nexus — the base is where the army comes from as well as the economy.
     *
     * <p>The two columns are deliberately different shapes, and the difference is the rule rather
     * than an inconsistency. The worker column is a Wood button above a Stone one, the
     * {@link #PROTOSS_BASE} split: an SCV costs 50 of <em>either</em> resource, and the pair is what
     * turns that into a choice the player makes rather than one the payment code guesses.
     * Alternative 0 is Wood and 1 is Stone, matching the order in {@code UnitStats.SCV}'s cost. The
     * Marine gets a single button, because {@link Action.TrainUnit} pays through
     * {@code CostPayment.payAny} — the Wood/Stone split is a worker-only affordance, and a combat
     * unit takes whichever the army has.
     */
    TERRAN_BASE(() -> AsteriskCraft.COMMAND_CENTER_CORE.get(), Races.TERRAN.bankSlots(), List.of(
            new OptionView(
                    Icon.ofIcon("scv"),
                    CostText.tooltip(UnitStats.SCV.cost(), 0), 0,
                    new Action.TrainWorker(0)),
            new OptionView(
                    Icon.ofIcon("scv"),
                    CostText.tooltip(UnitStats.SCV.cost(), 1), 0,
                    new Action.TrainWorker(1)),
            new OptionView(
                    Icon.ofIcon("marine"),
                    CostText.tooltip(UnitStats.MARINE.cost(), 0), 1,
                    new Action.TrainUnit(UnitStats.MARINE.id()))));

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

        /**
         * Queue one combat unit of the base's own race, named by its
         * {@code stats.UnitStat#id()} — the same spelling a build script uses, so a card and a
         * script name a unit the same way. Paid with {@code CostPayment.payAny}, so one button
         * covers whichever resource the army has; the Wood/Stone split is a worker-only affordance.
         *
         * <p>A race whose roster does not answer to this id simply has a dead button, which is a
         * mis-authored table rather than a runtime case.
         */
        record TrainUnit(String rosterId) implements Action {
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
     * Cannon kits, which are actual items) or a command-card texture from
     * {@code textures/gui/icons/} (used for units that have no item form of their own — Probe,
     * Zealot, Dragoon, Scout, Dark Templar).
     *
     * <p>Those textures are generated by {@code tools/gen_command_icons.py} and are all
     * {@code ProductionScreen.ICON_SIZE} square, which is why {@link #ofIcon} names a unit
     * rather than carrying a path and a source resolution.
     *
     * <p>An item icon holds a {@link Supplier}, not a resolved {@code ItemStack}, for the same
     * reason {@link Action.GiveKit} beside it does: this enum's constants are built at class-init,
     * and an {@code ItemStack} cannot be constructed before data components are bound.
     */
    public sealed interface Icon {
        record FromItem(Supplier<? extends Item> item) implements Icon {
        }

        record FromTexture(Identifier location) implements Icon {
        }

        static Icon ofItem(Supplier<? extends Item> item) {
            return new FromItem(item);
        }

        /** The command-card icon for {@code name}, e.g. {@code "zealot"}. */
        static Icon ofIcon(String name) {
            return new FromTexture(AsteriskCraft.id("textures/gui/icons/" + name + ".png"));
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
