package net.bitflora.asteriskcraft.building;

import net.bitflora.asteriskcraft.AsteriskCraft;
import net.bitflora.asteriskcraft.race.Races;
import net.bitflora.asteriskcraft.stats.CostText;
import com.mojang.serialization.Codec;
import net.bitflora.asteriskcraft.stats.UnitCost;
import net.bitflora.asteriskcraft.stats.UnitStats;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
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
 * but only a single button for the Nexus Kit. Buttons show only their icon — no name label — so
 * the hover tooltip is the only place a button says what it is: it names the unit or building,
 * describes it in a line, and gives its cost (including which resource a Wood/Stone pair's button
 * pays with).
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
            unit("probe", UnitStats.PROBE.cost(), 0, 0,
                    new Action.TrainWorker(0)),
            unit("probe", UnitStats.PROBE.cost(), 1, 0,
                    new Action.TrainWorker(1)),
            // The Pylon reads first among the buildings because everything to its right needs one
            // standing before its kit will go down — see PsiField.
            kit("pylon", AsteriskCraft.PYLON_KIT, BaseBlockEntity.PYLON_COST, 0, 1,
                    new Action.GiveKit(AsteriskCraft.PYLON_KIT::get, BaseBlockEntity.PYLON_COST, 0,
                            "message.asteriskcraft.base.pylon_ready")),
            kit("pylon", AsteriskCraft.PYLON_KIT, BaseBlockEntity.PYLON_COST, 1, 1,
                    new Action.GiveKit(AsteriskCraft.PYLON_KIT::get, BaseBlockEntity.PYLON_COST, 1,
                            "message.asteriskcraft.base.pylon_ready")),
            kit("gateway", AsteriskCraft.GATEWAY_KIT, BaseBlockEntity.BUILDING_COST, 0, 2,
                    new Action.GiveKit(AsteriskCraft.GATEWAY_KIT::get, BaseBlockEntity.BUILDING_COST, 0,
                            "message.asteriskcraft.base.gateway_ready")),
            kit("gateway", AsteriskCraft.GATEWAY_KIT, BaseBlockEntity.BUILDING_COST, 1, 2,
                    new Action.GiveKit(AsteriskCraft.GATEWAY_KIT::get, BaseBlockEntity.BUILDING_COST, 1,
                            "message.asteriskcraft.base.gateway_ready")),
            // The Stargate sits between the Gateway and the Cannon because that is the order it is
            // bought in, and it gets a single button where its neighbours get a Wood/Stone pair: its
            // price is one bundle of all three resources, so there is no choice to offer.
            kit("stargate", AsteriskCraft.STARGATE_KIT, BaseBlockEntity.STARGATE_COST, 0, 3,
                    new Action.GiveKit(AsteriskCraft.STARGATE_KIT::get, BaseBlockEntity.STARGATE_COST, 0,
                            "message.asteriskcraft.base.stargate_ready")),
            kit("photon_cannon", AsteriskCraft.PHOTON_CANNON_KIT, BaseBlockEntity.BUILDING_COST, 0, 4,
                    new Action.GiveKit(AsteriskCraft.PHOTON_CANNON_KIT::get, BaseBlockEntity.BUILDING_COST, 0,
                            "message.asteriskcraft.base.photon_cannon_ready")),
            kit("photon_cannon", AsteriskCraft.PHOTON_CANNON_KIT, BaseBlockEntity.BUILDING_COST, 1, 4,
                    new Action.GiveKit(AsteriskCraft.PHOTON_CANNON_KIT::get, BaseBlockEntity.BUILDING_COST, 1,
                            "message.asteriskcraft.base.photon_cannon_ready")),
            kit("nexus", AsteriskCraft.NEXUS_KIT, BaseBlockEntity.BASE_KIT_COST, 0, 5,
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
            unit("zealot", UnitStats.ZEALOT.cost(), 0, 0,
                    Action.FACTORY),
            unit("dragoon", UnitStats.DRAGOON.cost(), 0, 1,
                    Action.FACTORY),
            unit("dark_templar", UnitStats.DARK_TEMPLAR.cost(), 0, 2,
                    Action.FACTORY),
            unit("archon", UnitStats.ARCHON.cost(), 0, 3,
                    Action.FACTORY))),
    /**
     * The Stargate's card: everything Protoss that flies, and nothing that walks. The race's second
     * factory — an army that wants to answer a Mutalisk, or to see a Dark Templar coming, buys a
     * building for it rather than adding buttons to the card it already had.
     *
     * <p>Two units, and they are the two halves of what air is for: the Scout intercepts, the
     * Observer looks. One button each, in their own columns, the {@link #TERRAN_BARRACKS}'s shape
     * rather than the {@link #GATEWAY}'s — and each names its unit by the same {@code UnitStat.id()}
     * a build script spells, because this card is read by {@link FactoryBlockEntity}, which resolves
     * it against the Protoss roster, so nothing here names a Java class.
     */
    PROTOSS_STARGATE(() -> AsteriskCraft.STARGATE_CORE.get(), Races.PROTOSS.bankSlots(), List.of(
            unit("scout", UnitStats.SCOUT.cost(), 0, 0,
                    new Action.TrainUnit(UnitStats.SCOUT.id())),
            // One button, not a Wood/Stone pair: the Observer's cost is a single bundle that must be
            // paid in full, so there is no alternative to choose between.
            unit("observer", UnitStats.OBSERVER.cost(), 0, 1,
                    new Action.TrainUnit(UnitStats.OBSERVER.id())))),
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
            unit("drone", UnitStats.DRONE.cost(), 0, 0,
                    new Action.TrainWorker(0)),
            unit("zergling", UnitStats.ZERGLING.cost(), 0, 1,
                    new Action.TrainUnit(UnitStats.ZERGLING.id())),
            unit("ultralisk", UnitStats.ULTRALISK.cost(), 0, 1,
                    new Action.TrainUnit(UnitStats.ULTRALISK.id())),
            unit("hydralisk", UnitStats.HYDRALISK.cost(), 0, 2,
                    new Action.TrainUnit(UnitStats.HYDRALISK.id())),
            unit("lurker", UnitStats.LURKER.cost(), 0, 2,
                    new Action.TrainUnit(UnitStats.LURKER.id())),
            unit("mutalisk", UnitStats.MUTALISK.cost(), 0, 3,
                    new Action.TrainUnit(UnitStats.MUTALISK.id())),
            // The air column's second button: the swarm's detector, and the one unit on this card
            // that cannot fight at all.
            unit("overlord", UnitStats.OVERLORD.cost(), 0, 3,
                    new Action.TrainUnit(UnitStats.OVERLORD.id())),
            // The colonies are entities rather than block layouts, so they are bought exactly the
            // way the Photon Cannon is: an ally-side spawn item handed over for a building's price.
            kit("sunken_colony", AsteriskCraft.SUNKEN_COLONY_SPAWN_EGG_ALLY, BaseBlockEntity.BUILDING_COST, 1, 4,
                    new Action.GiveKit(AsteriskCraft.SUNKEN_COLONY_SPAWN_EGG_ALLY::get,
                            BaseBlockEntity.BUILDING_COST, 1, "message.asteriskcraft.base.sunken_colony_ready")),
            kit("spore_colony", AsteriskCraft.SPORE_COLONY_SPAWN_EGG_ALLY, BaseBlockEntity.BUILDING_COST, 1, 4,
                    new Action.GiveKit(AsteriskCraft.SPORE_COLONY_SPAWN_EGG_ALLY::get,
                            BaseBlockEntity.BUILDING_COST, 1, "message.asteriskcraft.base.spore_colony_ready")),
            // The swarm's two grown structures share a column, and it is the only Zerg column whose
            // buildings are block layouts rather than entities: both are stamped from a template by
            // a Drone that dies doing it, and both may only go down on creep (CreepField).
            kit("spawning_pool", AsteriskCraft.SPAWNING_POOL_KIT, BaseBlockEntity.SPAWNING_POOL_COST, 0, 5,
                    new Action.GiveKit(AsteriskCraft.SPAWNING_POOL_KIT::get,
                            BaseBlockEntity.SPAWNING_POOL_COST, 0,
                            "message.asteriskcraft.base.spawning_pool_ready")),
            kit("spire", AsteriskCraft.SPIRE_KIT, BaseBlockEntity.SPIRE_COST, 0, 5,
                    new Action.GiveKit(AsteriskCraft.SPIRE_KIT::get, BaseBlockEntity.SPIRE_COST, 0,
                            "message.asteriskcraft.base.spire_ready")),
            kit("hive", AsteriskCraft.HIVE_KIT, BaseBlockEntity.BASE_KIT_COST, 0, 6,
                    new Action.GiveKit(AsteriskCraft.HIVE_KIT::get, BaseBlockEntity.BASE_KIT_COST, 0,
                            "message.asteriskcraft.base.hive_kit_ready")))),
    /**
     * The Command Center's card. The Terran have no factory building, so — like the Hive and unlike
     * the Nexus — the base is where the army comes from as well as the economy.
     *
     * <p>It trains one unit — the SCV — and sells five buildings, which makes it the
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
            unit("scv", UnitStats.SCV.cost(), 0, 0,
                    new Action.TrainWorker(0)),
            unit("scv", UnitStats.SCV.cost(), 1, 0,
                    new Action.TrainWorker(1)),
            // The Barracks reads first among the buildings for the reason the Pylon does on the
            // Nexus's card: the whole army to the right of it now comes out of one. A single button
            // rather than a Wood/Stone pair, because its price is a bundle of both and there is
            // nothing for a second button to choose between.
            kit("barracks", AsteriskCraft.BARRACKS_KIT, BaseBlockEntity.BARRACKS_COST, 0, 1,
                    new Action.GiveKit(AsteriskCraft.BARRACKS_KIT::get, BaseBlockEntity.BARRACKS_COST, 0,
                            "message.asteriskcraft.base.barracks_ready")),
            // The Starport sits under the Barracks in the same column, the pairing the swarm's
            // Spawning Pool and Spire make: one building is where the race's ground army comes from
            // and the other where its air will, and a column is what says they are the same kind of
            // commitment. One button, because its price is a bundle with nothing to choose between.
            kit("starport", AsteriskCraft.STARPORT_KIT, BaseBlockEntity.STARPORT_COST, 0, 1,
                    new Action.GiveKit(AsteriskCraft.STARPORT_KIT::get, BaseBlockEntity.STARPORT_COST, 0,
                            "message.asteriskcraft.base.starport_ready")),
            // The Bunker is an entity rather than a block layout, so it is bought exactly the way the
            // Photon Cannon and the Zerg colonies are: an ally-side spawn item handed over for a
            // building's price. A single button, because its cost names one resource.
            kit("bunker", AsteriskCraft.BUNKER_KIT, BaseBlockEntity.BUNKER_COST, 0, 2,
                    new Action.GiveKit(AsteriskCraft.BUNKER_KIT::get, BaseBlockEntity.BUNKER_COST, 0,
                            "message.asteriskcraft.base.bunker_ready")),
            // The Missile Turret shares the structure column with the Bunker, and the pairing is the
            // point: the Bunker is what the race puts in front of the ground and the turret is what
            // it puts under the air, and neither covers for the other.
            kit("missile_turret", AsteriskCraft.MISSILE_TURRET_KIT, BaseBlockEntity.MISSILE_TURRET_COST, 0, 2,
                    new Action.GiveKit(AsteriskCraft.MISSILE_TURRET_KIT::get,
                            BaseBlockEntity.MISSILE_TURRET_COST, 0,
                            "message.asteriskcraft.base.missile_turret_ready")),
            // The expansion kit gets its own column, exactly as the Nexus's and the Hive's do — but
            // not their price: a Command Center is the same 400, payable in either resource rather
            // than in cobblestone alone, so it is the one expansion kit with a Wood/Stone pair.
            kit("command_center", AsteriskCraft.COMMAND_CENTER_KIT, BaseBlockEntity.COMMAND_CENTER_COST, 0, 3,
                    new Action.GiveKit(AsteriskCraft.COMMAND_CENTER_KIT::get,
                            BaseBlockEntity.COMMAND_CENTER_COST, 0,
                            "message.asteriskcraft.base.command_center_kit_ready")),
            kit("command_center", AsteriskCraft.COMMAND_CENTER_KIT, BaseBlockEntity.COMMAND_CENTER_COST, 1, 3,
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
            unit("marine", UnitStats.MARINE.cost(), 0, 0,
                    new Action.TrainUnit(UnitStats.MARINE.id())),
            unit("firebat", UnitStats.FIREBAT.cost(), 0, 1,
                    new Action.TrainUnit(UnitStats.FIREBAT.id())),
            unit("ghost", UnitStats.GHOST.cost(), 0, 2,
                    new Action.TrainUnit(UnitStats.GHOST.id())))),
    /**
     * The Starport's card: the Terran <em>answer to the sky</em>, which is not the same thing as the
     * Terran air units. It began as {@link #PROTOSS_STARGATE}'s shape — the one thing that flies,
     * bought as a building rather than as a fourth button on the {@link #TERRAN_BARRACKS}' card —
     * and the Goliath widened it, because a mech that walks is still something a player comes here
     * for. What the two buttons have in common is the problem they solve, not the layer they fight
     * on, and razing this building takes both halves of that answer at once.
     *
     * <p>One button per column, as the Barracks' card is laid out: the three are alternatives rather
     * than tiers, and a column of two would read as one.
     *
     * <p>The Science Vessel is the third, and it widens the card's claim once more without breaking
     * it: it neither flies at anything nor shoots anything, but a player comes here for it for the
     * same reason they come here for the Goliath — the sky has become the problem, and this is what
     * you buy when the problem is something you cannot see rather than something you cannot reach.
     *
     * <p>Each button names its unit by the same {@code UnitStat.id()} a build script spells, so
     * {@link FactoryBlockEntity} resolves it against the Terran roster and this card names no Java
     * class.
     */
    TERRAN_STARPORT(() -> AsteriskCraft.STARPORT_CORE.get(), Races.TERRAN.bankSlots(), List.of(
            unit("wraith", UnitStats.WRAITH.cost(), 0, 0,
                    new Action.TrainUnit(UnitStats.WRAITH.id())),
            unit("goliath", UnitStats.GOLIATH.cost(), 0, 1,
                    new Action.TrainUnit(UnitStats.GOLIATH.id())),
            unit("science_vessel", UnitStats.SCIENCE_VESSEL.cost(), 0, 2,
                    new Action.TrainUnit(UnitStats.SCIENCE_VESSEL.id()))));

    /**
     * One train button: an icon, the name and one-line description shown when the player hovers
     * it, a cost tooltip, the unit column it stacks into (see class docs), and what pressing it
     * does.
     *
     * <p>A button carries no visible label, so the hover tooltip is the only place it ever says
     * what it makes — which is why the name and the description are separate fields rather than
     * baked into {@code costTooltip}: a locked button replaces the cost line with what it is
     * waiting for and must still say what it is (see {@link #lockedTooltip}).
     */
    public record OptionView(Icon icon, Component name, Component description, Component costTooltip,
                             int column, Action action) {

        /** Name, description, then cost — what an available button says on hover. */
        public Component tooltip() {
            return heading().append("\n").append(this.costTooltip);
        }

        /**
         * Name, description, then {@code requires} in place of the cost, for a button greyed out
         * by a missing prerequisite: a disabled button cannot answer with the action-bar message
         * the server would have sent, and dropping the first two lines would leave it unable to
         * say what it was even offering.
         */
        public Component lockedTooltip(Component requires) {
            return heading().append("\n").append(requires.copy().withStyle(ChatFormatting.RED));
        }

        private MutableComponent heading() {
            return this.name.copy().withStyle(ChatFormatting.WHITE)
                    .append("\n")
                    .append(this.description.copy().withStyle(ChatFormatting.GRAY));
        }
    }

    /**
     * A unit button. Everything about it is keyed off the unit's {@code UnitStat.id()} — the same
     * spelling a build script and {@link Action.TrainUnit} use — so the icon it blits, the name it
     * shows and the description it shows are one string here rather than three. The name reuses
     * the entity's own translation key instead of a card-specific one, since a second spelling of
     * "Zealot" is a second thing to keep in step.
     */
    private static OptionView unit(String id, UnitCost cost, int alternative, int column, Action action) {
        return new OptionView(Icon.ofIcon(id),
                Component.translatable("entity.asteriskcraft." + id),
                Component.translatable(DESCRIPTION_PREFIX + id),
                CostText.tooltip(cost, alternative), column, action);
    }

    /**
     * A building button, whose icon is the kit item's own render. It takes a card id ("pylon")
     * rather than deriving one from the item, because the item is a <em>kit</em> or an ally-side
     * spawn egg and its name ("Allied Spore Colony Spawn Egg") is not what a command card should
     * call the thing it builds.
     */
    private static OptionView kit(String id, Supplier<? extends Item> item, UnitCost cost, int alternative,
                                  int column, Action action) {
        return new OptionView(Icon.ofItem(item),
                Component.translatable(NAME_PREFIX + id),
                Component.translatable(DESCRIPTION_PREFIX + id),
                CostText.tooltip(cost, alternative), column, action);
    }

    /**
     * Where a building button's name and every button's description are authored. Public because
     * {@code ProductionCardTextTest} checks that en_us.json actually answers to every key a card
     * asks for — a missing one is invisible at compile time and renders as the raw key in-game.
     */
    public static final String NAME_PREFIX = "gui.asteriskcraft.card.";
    public static final String DESCRIPTION_PREFIX = "gui.asteriskcraft.card.desc.";

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
