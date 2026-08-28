package net.bitflora.asteriskcraft.building;

import net.bitflora.asteriskcraft.AsteriskCraft;
import net.bitflora.asteriskcraft.race.Races;
import net.bitflora.asteriskcraft.stats.CostText;
import com.mojang.serialization.Codec;
import net.bitflora.asteriskcraft.stats.UnitCost;
import net.bitflora.asteriskcraft.stats.UnitStats;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.StringRepresentable;
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
public enum ProductionKind implements StringRepresentable {
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
                    CostText.tooltip(BaseBlockEntity.PYLON_COST, 0), 1,
                    new Action.GiveKit(AsteriskCraft.PYLON_KIT::get, BaseBlockEntity.PYLON_COST, 0,
                            "message.asteriskcraft.base.pylon_ready")),
            new OptionView(
                    Icon.ofItem(AsteriskCraft.PYLON_KIT),
                    CostText.tooltip(BaseBlockEntity.PYLON_COST, 1), 1,
                    new Action.GiveKit(AsteriskCraft.PYLON_KIT::get, BaseBlockEntity.PYLON_COST, 1,
                            "message.asteriskcraft.base.pylon_ready")),
            new OptionView(
                    Icon.ofItem(AsteriskCraft.GATEWAY_KIT),
                    CostText.tooltip(BaseBlockEntity.BUILDING_COST, 0), 2,
                    new Action.GiveKit(AsteriskCraft.GATEWAY_KIT::get, BaseBlockEntity.BUILDING_COST, 0,
                            "message.asteriskcraft.base.gateway_ready")),
            new OptionView(
                    Icon.ofItem(AsteriskCraft.GATEWAY_KIT),
                    CostText.tooltip(BaseBlockEntity.BUILDING_COST, 1), 2,
                    new Action.GiveKit(AsteriskCraft.GATEWAY_KIT::get, BaseBlockEntity.BUILDING_COST, 1,
                            "message.asteriskcraft.base.gateway_ready")),
            // The Stargate sits between the Gateway and the Cannon because that is the order it is
            // bought in, and it gets a single button where its neighbours get a Wood/Stone pair: its
            // price is one bundle of all three resources, so there is no choice to offer.
            new OptionView(
                    Icon.ofItem(AsteriskCraft.STARGATE_KIT),
                    CostText.tooltip(BaseBlockEntity.STARGATE_COST, 0), 3,
                    new Action.GiveKit(AsteriskCraft.STARGATE_KIT::get, BaseBlockEntity.STARGATE_COST, 0,
                            "message.asteriskcraft.base.stargate_ready")),
            new OptionView(
                    Icon.ofItem(AsteriskCraft.PHOTON_CANNON_KIT),
                    CostText.tooltip(BaseBlockEntity.BUILDING_COST, 0), 4,
                    new Action.GiveKit(AsteriskCraft.PHOTON_CANNON_KIT::get, BaseBlockEntity.BUILDING_COST, 0,
                            "message.asteriskcraft.base.photon_cannon_ready")),
            new OptionView(
                    Icon.ofItem(AsteriskCraft.PHOTON_CANNON_KIT),
                    CostText.tooltip(BaseBlockEntity.BUILDING_COST, 1), 4,
                    new Action.GiveKit(AsteriskCraft.PHOTON_CANNON_KIT::get, BaseBlockEntity.BUILDING_COST, 1,
                            "message.asteriskcraft.base.photon_cannon_ready")),
            new OptionView(
                    Icon.ofItem(AsteriskCraft.NEXUS_KIT),
                    CostText.tooltip(BaseBlockEntity.BASE_KIT_COST, 0), 5,
                    new Action.GiveKit(AsteriskCraft.NEXUS_KIT::get, BaseBlockEntity.BASE_KIT_COST, 0,
                            "message.asteriskcraft.base.base_kit_ready")))),
    /**
     * The Gateway's card: the Protoss ground army. The Scout is deliberately not on it — the air
     * unit trains at {@link #PROTOSS_STARGATE}, which is what the player has already paid for by
     * the time they can build one.
     *
     * <p>Its buttons are positional, dispatched through {@code GatewayBlockEntity.UnitType} rather
     * than named by roster id, which is why they carry {@link Action#FACTORY}. That is the old
     * shape and the reason the Gateway is not a {@link FactoryBlock}; see {@code FactoryBlockEntity}.
     */
    GATEWAY(() -> AsteriskCraft.GATEWAY_CORE.get(), Races.PROTOSS.bankSlots(), List.of(
            new OptionView(
                    Icon.ofIcon("zealot"),
                    CostText.tooltip(UnitStats.ZEALOT.cost(), 0), 0, Action.FACTORY),
            new OptionView(
                    Icon.ofIcon("dragoon"),
                    CostText.tooltip(UnitStats.DRAGOON.cost(), 0), 1, Action.FACTORY),
            new OptionView(
                    Icon.ofIcon("dark_templar"),
                    CostText.tooltip(UnitStats.DARK_TEMPLAR.cost(), 0), 2, Action.FACTORY))),
    /**
     * The Stargate's card: the Protoss air unit, and nothing else. The race's second factory, and
     * the Scout is the whole of it — where the Gateway trains everything that walks, the Stargate
     * trains the one thing that flies, so an army that wants to answer a Mutalisk buys a building
     * for it rather than adding a fourth button to a card it already had.
     *
     * <p>Its button names its unit by the same {@code UnitStat.id()} a build script spells, the
     * {@link #TERRAN_BARRACKS}'s shape rather than the {@link #GATEWAY}'s: this card is read by
     * {@link FactoryBlockEntity}, which resolves it against the Protoss roster, so nothing here
     * names a Java class.
     */
    PROTOSS_STARGATE(() -> AsteriskCraft.STARGATE_CORE.get(), Races.PROTOSS.bankSlots(), List.of(
            new OptionView(
                    Icon.ofIcon("scout"),
                    CostText.tooltip(UnitStats.SCOUT.cost(), 0), 0,
                    new Action.TrainUnit(UnitStats.SCOUT.id())))),
    /**
     * The Hive's card. Wider than the Protoss base's because the swarm has no factory building:
     * a Hive morphs its combat units itself, so everything an army needs is on this one card.
     *
     * <p>Two deliberate asymmetries with {@link #PROTOSS_BASE}. Every button is a single one rather
     * than a Wood/Stone pair — the swarm pays for everything out of one pool — which is what keeps a
     * card this long inside the panel's width at all; at seven columns it is the widest there is,
     * and {@code ProductionCardLayoutTest} is what says whether the next one still fits. And its
     * input slot count is a flat 27,
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
                    CostText.tooltip(BaseBlockEntity.BUILDING_COST, 1), 4,
                    new Action.GiveKit(AsteriskCraft.SUNKEN_COLONY_SPAWN_EGG_ALLY::get,
                            BaseBlockEntity.BUILDING_COST, 1, "message.asteriskcraft.base.sunken_colony_ready")),
            new OptionView(
                    Icon.ofItem(AsteriskCraft.SPORE_COLONY_SPAWN_EGG_ALLY),
                    CostText.tooltip(BaseBlockEntity.BUILDING_COST, 1), 4,
                    new Action.GiveKit(AsteriskCraft.SPORE_COLONY_SPAWN_EGG_ALLY::get,
                            BaseBlockEntity.BUILDING_COST, 1, "message.asteriskcraft.base.spore_colony_ready")),
            // The swarm's two grown structures share a column, and it is the only Zerg column whose
            // buildings are block layouts rather than entities: both are stamped from a template by
            // a Drone that dies doing it, and both may only go down on creep (CreepField).
            new OptionView(
                    Icon.ofItem(AsteriskCraft.SPAWNING_POOL_KIT),
                    CostText.tooltip(BaseBlockEntity.SPAWNING_POOL_COST, 0), 5,
                    new Action.GiveKit(AsteriskCraft.SPAWNING_POOL_KIT::get,
                            BaseBlockEntity.SPAWNING_POOL_COST, 0,
                            "message.asteriskcraft.base.spawning_pool_ready")),
            new OptionView(
                    Icon.ofItem(AsteriskCraft.SPIRE_KIT),
                    CostText.tooltip(BaseBlockEntity.SPIRE_COST, 0), 5,
                    new Action.GiveKit(AsteriskCraft.SPIRE_KIT::get, BaseBlockEntity.SPIRE_COST, 0,
                            "message.asteriskcraft.base.spire_ready")),
            new OptionView(
                    Icon.ofItem(AsteriskCraft.HIVE_KIT),
                    CostText.tooltip(BaseBlockEntity.BASE_KIT_COST, 0), 6,
                    new Action.GiveKit(AsteriskCraft.HIVE_KIT::get, BaseBlockEntity.BASE_KIT_COST, 0,
                            "message.asteriskcraft.base.hive_kit_ready")))),
    /**
     * The Command Center's card. The Terran have no factory building, so — like the Hive and unlike
     * the Nexus — the base is where the army comes from as well as the economy.
     *
     * <p>It trains one unit — the SCV — and sells four buildings, which makes it the
     * {@link #PROTOSS_BASE}'s shape rather than the {@link #ZERG_BASE}'s: the race's infantry moved
     * to {@link #TERRAN_BARRACKS} the moment there was a Barracks to put them in, and a base that
     * still built Marines would have made that building optional.
     *
     * <p>The worker column is a Wood button above a Stone one, the {@link #PROTOSS_BASE} split: an
     * SCV costs 50 of <em>either</em> resource, and the pair is what turns that into a choice the
     * player makes rather than one the payment code guesses. Alternative 0 is Wood and 1 is Stone,
     * matching the order in {@code UnitStats.SCV}'s cost.
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
            // The Barracks reads first among the buildings for the reason the Pylon does on the
            // Nexus's card: the whole army to the right of it now comes out of one. A single button
            // rather than a Wood/Stone pair, because its price is a bundle of both and there is
            // nothing for a second button to choose between.
            new OptionView(
                    Icon.ofItem(AsteriskCraft.BARRACKS_KIT),
                    CostText.tooltip(BaseBlockEntity.BARRACKS_COST, 0), 1,
                    new Action.GiveKit(AsteriskCraft.BARRACKS_KIT::get, BaseBlockEntity.BARRACKS_COST, 0,
                            "message.asteriskcraft.base.barracks_ready")),
            // The Bunker is an entity rather than a block layout, so it is bought exactly the way the
            // Photon Cannon and the Zerg colonies are: an ally-side spawn item handed over for a
            // building's price. A single button, because its cost names one resource.
            new OptionView(
                    Icon.ofItem(AsteriskCraft.BUNKER_KIT),
                    CostText.tooltip(BaseBlockEntity.BUNKER_COST, 0), 2,
                    new Action.GiveKit(AsteriskCraft.BUNKER_KIT::get, BaseBlockEntity.BUNKER_COST, 0,
                            "message.asteriskcraft.base.bunker_ready")),
            // The Missile Turret shares the structure column with the Bunker, and the pairing is the
            // point: the Bunker is what the race puts in front of the ground and the turret is what
            // it puts under the air, and neither covers for the other.
            new OptionView(
                    Icon.ofItem(AsteriskCraft.MISSILE_TURRET_KIT),
                    CostText.tooltip(BaseBlockEntity.MISSILE_TURRET_COST, 0), 2,
                    new Action.GiveKit(AsteriskCraft.MISSILE_TURRET_KIT::get,
                            BaseBlockEntity.MISSILE_TURRET_COST, 0,
                            "message.asteriskcraft.base.missile_turret_ready")),
            // The expansion kit gets its own column, exactly as the Nexus's and the Hive's do — but
            // not their price: a Command Center is the same 400, payable in either resource rather
            // than in cobblestone alone, so it is the one expansion kit with a Wood/Stone pair.
            new OptionView(
                    Icon.ofItem(AsteriskCraft.COMMAND_CENTER_KIT),
                    CostText.tooltip(BaseBlockEntity.COMMAND_CENTER_COST, 0), 3,
                    new Action.GiveKit(AsteriskCraft.COMMAND_CENTER_KIT::get,
                            BaseBlockEntity.COMMAND_CENTER_COST, 0,
                            "message.asteriskcraft.base.command_center_kit_ready")),
            new OptionView(
                    Icon.ofItem(AsteriskCraft.COMMAND_CENTER_KIT),
                    CostText.tooltip(BaseBlockEntity.COMMAND_CENTER_COST, 1), 3,
                    new Action.GiveKit(AsteriskCraft.COMMAND_CENTER_KIT::get,
                            BaseBlockEntity.COMMAND_CENTER_COST, 1,
                            "message.asteriskcraft.base.command_center_kit_ready")))),
    /**
     * The Barracks' card: the whole Terran army, and nothing else. The race's counterpart to
     * {@link #GATEWAY} — where the Protoss have always had to build one building before they could
     * build a soldier, and the Zerg deliberately never do (a Hive morphs its own).
     *
     * <p>One button per unit, in its own column, because {@link Action.TrainUnit} pays through
     * {@code CostPayment.payAny}: the Wood/Stone split is a worker-only affordance and a combat unit
     * takes whichever the army has. That holds even for the Firebat, whose cost is a bundle of both
     * resources rather than a choice between them — {@code payAny} walks alternatives, and a bundle
     * is one alternative with two lines in it.
     *
     * <p>Unlike the Gateway's, these buttons are <b>not</b> positional: they name their units by the
     * same {@code UnitStat.id()} a build script spells, so {@link FactoryBlockEntity} resolves them
     * against its own race's roster and this card names no Java class.
     */
    TERRAN_BARRACKS(() -> AsteriskCraft.BARRACKS_CORE.get(), Races.TERRAN.bankSlots(), List.of(
            new OptionView(
                    Icon.ofIcon("marine"),
                    CostText.tooltip(UnitStats.MARINE.cost(), 0), 0,
                    new Action.TrainUnit(UnitStats.MARINE.id())),
            new OptionView(
                    Icon.ofIcon("firebat"),
                    CostText.tooltip(UnitStats.FIREBAT.cost(), 0), 1,
                    new Action.TrainUnit(UnitStats.FIREBAT.id())),
            new OptionView(
                    Icon.ofIcon("ghost"),
                    CostText.tooltip(UnitStats.GHOST.cost(), 0), 2,
                    new Action.TrainUnit(UnitStats.GHOST.id()))));

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

        /**
         * Hand the player a building kit item for one alternative of {@code cost}. The same
         * {@link UnitCost} a unit is priced with, and for the same reason: a building's price is a
         * bundle of lines that must all be paid (a Barracks: 75 wood <em>and</em> 75 cobblestone) or
         * a set of interchangeable ones the card gives a button each (a Gateway: wood <em>or</em>
         * cobblestone), and a plain amount-and-resource could say neither.
         *
         * <p>What a kit does <em>not</em> carry is a build time: the countdown belongs to the
         * structure the kit stamps ({@code BuildingDefense}'s warp-in), not to the base that sold
         * it, so a player is free to hold a kit and place it later.
         */
        record GiveKit(Supplier<Item> kit, UnitCost cost, int alternative, String readyKey) implements Action {
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

    /**
     * Serialized form, for the one place a card is named in data rather than in code: a
     * {@link FactoryBlock}'s own block codec, which has to be able to write out which card the block
     * it is describing opens. The menu itself still travels by {@link #ordinal()} — see
     * {@link #byId} — since that buffer is written and read by this same build.
     */
    public static final Codec<ProductionKind> CODEC = StringRepresentable.fromEnum(ProductionKind::values);

    @Override
    public String getSerializedName() {
        return name().toLowerCase(java.util.Locale.ROOT);
    }

    public static ProductionKind byId(int id) {
        ProductionKind[] values = values();
        return (id >= 0 && id < values.length) ? values[id] : PROTOSS_BASE;
    }
}
