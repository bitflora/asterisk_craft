package net.bitflora.asteriskcraft.race;

import net.bitflora.asteriskcraft.AsteriskCraft;
import net.bitflora.asteriskcraft.building.BuildingTemplates;
import net.bitflora.asteriskcraft.building.ProductionKind;
import net.bitflora.asteriskcraft.building.WarpScaffold;
import net.bitflora.asteriskcraft.faction.Race;
import net.bitflora.asteriskcraft.race.RaceProfile.BaseDefence;
import net.bitflora.asteriskcraft.race.RaceProfile.StartingStack;
import net.bitflora.asteriskcraft.race.RaceProfile.TechBuilding;
import net.bitflora.asteriskcraft.stats.UnitStats;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * The per-race table. This is the only place in the mod that knows a Hive is what a Zerg army
 * builds and a Drone is what it mines with; every other caller asks {@link #of(Race)} for a
 * {@link RaceProfile} and reads what it needs off that.
 *
 * <p>Adding a race is an entry here plus its assets — no change to the AI director, the base block
 * entity, world generation or targeting. That is the same shape {@code faction.WildKind} gives the
 * targeting rules (see CLAUDE.md): one table, edited in one place.
 *
 * <p>Note what is <em>not</em> here. Unit numbers stay in {@link UnitStats}, the single balance
 * table; a roster only names which of those entries a race can produce. Cheap racial traits
 * (shields, HP regen, infestation, what it hunts in the wild) stay on {@link Race} itself, so a
 * pure rule can consult them with no registries loaded.
 */
public final class Races {

    /**
     * Base staying power. The sturdiest buildings in the mod, as the thing you lose the game with
     * should be. The Zerg base carries no shields, which is not a Zerg mechanic; it does take just
     * as long to grow as a Nexus takes to warp, since both are bought as an expansion kit and the
     * one at world generation is stood up by {@code GameBootstrap} either way.
     */
    private static final int PROTOSS_BASE_HEALTH = 325;
    private static final int PROTOSS_BASE_SHIELD = 325;
    /** Two minutes — an expansion Nexus is a long commitment. */
    private static final int PROTOSS_BASE_WARP_TICKS = 20 * 120;
    private static final int ZERG_BASE_HEALTH = 300;
    /** The Nexus's two minutes: an expansion Hive is the same size of commitment. */
    private static final int ZERG_BASE_WARP_TICKS = 20 * 120;

    /** 3x a single building's original 9 slots. */
    private static final int PROTOSS_BANK_SLOTS = 27;
    /** 3x a single Hive's original 27 slots. */
    private static final int ZERG_BANK_SLOTS = 81;

    /**
     * The Terran base is as sturdy as the other two — the thing you lose the game with should be —
     * but carries no shields, which is not a Terran mechanic.
     */
    private static final int TERRAN_BASE_HEALTH = 325;
    /** The Nexus's and the Hive's two minutes: an expansion Command Center is the same commitment. */
    private static final int TERRAN_BASE_WARP_TICKS = 20 * 120;
    /** The Protoss shape: three rows, and only a handful of item types ever in it. */
    private static final int TERRAN_BANK_SLOTS = 27;

    /**
     * What the Zerg bank is stocked with when the swarm is the computer player: the old
     * 128/128/48-per-Hive x3 total, from when each Hive held its own independent stock.
     */
    private static final int ZERG_STARTING_LOGS = 128 * 3;
    private static final int ZERG_STARTING_COBBLE = 128 * 3;
    private static final int ZERG_STARTING_IRON = 48 * 3;

    /**
     * What a <em>human's</em> bank opens with, whichever race they drew. Deliberately a fraction of
     * the computer's stockpile and deliberately the same for both races: the AI is handed enough to
     * bankroll a scripted opening it cannot deviate from, while a player starts with about one
     * building's worth and mines their way up from there. Symmetric on purpose — the two sides
     * differ in what they build, not in what they are given to build it with.
     */
    private static final int PLAYER_STARTING_LOGS = 100;
    private static final int PLAYER_STARTING_COBBLE = 100;

    private static final List<StartingStack> PLAYER_BANK = List.of(
            new StartingStack(() -> Items.OAK_LOG, PLAYER_STARTING_LOGS),
            new StartingStack(() -> Items.COBBLESTONE, PLAYER_STARTING_COBBLE));

    public static final RaceProfile PROTOSS = new RaceProfile(
            Race.PROTOSS,
            AsteriskCraft.NEXUS_CORE::get,
            BuildingTemplates.NEXUS,
            PROTOSS_BASE_HEALTH,
            PROTOSS_BASE_SHIELD,
            PROTOSS_BASE_WARP_TICKS,
            PROTOSS_BANK_SLOTS,
            UnitRoster.builder()
                    .worker(UnitStats.PROBE, AsteriskCraft.PROBE)
                    .unit(UnitStats.ZEALOT, AsteriskCraft.ZEALOT)
                    .unit(UnitStats.DRAGOON, AsteriskCraft.DRAGOON)
                    .unit(UnitStats.SCOUT, AsteriskCraft.SCOUT)
                    // Ungated, exactly as the Scout is: the Stargate's command card is what a
                    // human buys it through, and a roster gate would strand the computer, whose
                    // script has no card to open. See RacesTest.
                    .unit(UnitStats.OBSERVER, AsteriskCraft.OBSERVER)
                    .unit(UnitStats.DARK_TEMPLAR, AsteriskCraft.DARK_TEMPLAR)
                    .build(),
            () -> ProductionKind.PROTOSS_BASE,
            AsteriskCraft.id("build_scripts/protoss.txt"),
            // No creep: the Protoss leave the ground they land on alone.
            null,
            // No support fill either — the end-stone-brick platform is its own plinth, and nothing
            // foreign should be stamped under Protoss stonework.
            null,
            // Warp glass, the look this whole mechanic was written for.
            () -> WarpScaffold.PANE,
            // Soul fire off a building the warp rift is still pouring into place.
            ParticleTypes.SOUL_FIRE_FLAME,
            List.of(new BaseDefence(AsteriskCraft.PHOTON_CANNON, 1)),
            // No tech buildings: nothing on the Protoss roster is gated behind one yet, so the
            // computer's bases stand as they always did.
            List.of(),
            // No escort: the Protoss detector is the Photon Cannon, which is already in the line
            // above, and nothing else about a Nexus wants a unit hovering over it at world start.
            List.of(),
            // No opening army either: the Protoss player starts with a Pylon and a Cannon kit and
            // buys their first Zealot off the Nexus card.
            List.of(),
            List.of(new StartingStack(() -> Items.OAK_LOG, 128 * 3),
                    new StartingStack(() -> Items.COBBLESTONE, 128 * 3)),
            PLAYER_BANK,
            // One Pylon, and the Cannon kit that needs one in range before it will go down
            // (PsiField). No Gateway kit: the base's command card sells those, so the player pays
            // for their first one.
            List.of(AsteriskCraft.PYLON_KIT::get, AsteriskCraft.PHOTON_CANNON_KIT::get));

    public static final RaceProfile ZERG = new RaceProfile(
            Race.ZERG,
            AsteriskCraft.HIVE_CORE::get,
            BuildingTemplates.HIVE,
            ZERG_BASE_HEALTH,
            0,
            ZERG_BASE_WARP_TICKS,
            ZERG_BANK_SLOTS,
            UnitRoster.builder()
                    .worker(UnitStats.DRONE, AsteriskCraft.DRONE)
                    // Everything with teeth waits on a Spawning Pool, and the flyer on a Spire:
                    // the swarm's tech ladder, and the mod's only one. The Lurker is in the first
                    // group because it is a Hydralisk that has dug in. The Overlord is in neither
                    // — it carries detection rather than a weapon, and an army that had to buy a
                    // building before it could see a Dark Templar would simply lose to one.
                    .unit(UnitStats.ZERGLING, AsteriskCraft.ZERGLING, AsteriskCraft.SPAWNING_POOL_CORE)
                    .unit(UnitStats.HYDRALISK, AsteriskCraft.HYDRALISK, AsteriskCraft.SPAWNING_POOL_CORE)
                    .unit(UnitStats.MUTALISK, AsteriskCraft.MUTALISK, AsteriskCraft.SPIRE_CORE)
                    .unit(UnitStats.OVERLORD, AsteriskCraft.OVERLORD)
                    .unit(UnitStats.ULTRALISK, AsteriskCraft.ULTRALISK, AsteriskCraft.SPAWNING_POOL_CORE)
                    .unit(UnitStats.LURKER, AsteriskCraft.LURKER, AsteriskCraft.SPAWNING_POOL_CORE)
                    .build(),
            // The Hive is the swarm's whole production building — there is no Zerg factory to
            // build, so its card morphs combat units directly as well as training Drones.
            () -> ProductionKind.ZERG_BASE,
            AsteriskCraft.id("build_scripts/zerg.txt"),
            () -> Blocks.MYCELIUM.defaultBlockState(),
            // The mound keeps a mycelium footing under it — same material as the creep it sits in,
            // so unlike the Protoss stonework there is nothing foreign to see.
            () -> Blocks.MYCELIUM.defaultBlockState(),
            // Slime rather than the Protoss glass: a Hive is grown out of the Drone that died to
            // start it, so what it stands as while it grows should be flesh rather than a pane.
            // Still one hit to break, so an attacker can smash it for the same penalty.
            () -> Blocks.SLIME_BLOCK.defaultBlockState(),
            // Spores drifting off something alive and unfinished.
            ParticleTypes.SPORE_BLOSSOM_AIR,
            List.of(new BaseDefence(AsteriskCraft.SUNKEN_COLONY, 1),
                    new BaseDefence(AsteriskCraft.SPORE_COLONY, 1)),
            // The two buildings the roster above gates on, planted finished with each of the
            // computer's Hives — which is what a player razes to shut its waves down a tier.
            List.of(new TechBuilding(BuildingTemplates.SPAWNING_POOL, AsteriskCraft.SPAWNING_POOL_CORE::get),
                    new TechBuilding(BuildingTemplates.SPIRE, AsteriskCraft.SPIRE_CORE::get)),
            // One Overlord per Hive, on both sides. The Spore Colony above it detects too, but it is
            // rooted to this spot — the Overlord is what carries detection out with an army, which
            // is the whole answer to a Dark Templar met in the field.
            List.of(new BaseDefence(AsteriskCraft.OVERLORD, 1)),
            // No opening army: the Hive is the swarm's whole production building, so a Zerg player
            // is already one click from a Zergling and starts with a Sunken Colony to root instead.
            List.of(),
            List.of(new StartingStack(() -> Items.OAK_LOG, ZERG_STARTING_LOGS),
                    new StartingStack(() -> Items.COBBLESTONE, ZERG_STARTING_COBBLE),
                    new StartingStack(() -> Items.IRON_INGOT, ZERG_STARTING_IRON)),
            PLAYER_BANK,
            // The swarm's opener is one Sunken Colony to root by the Hive. No Spore: the first
            // thing that can hurt you comes on the ground, and the card sells the answer to the
            // rest.
            List.of(AsteriskCraft.SUNKEN_COLONY_SPAWN_EGG_ALLY::get));

    /**
     * The Terran: a Command Center, the SCVs that mine for it, the infantry they build, and the two
     * structures those infantry are bought alongside. The entries below that still read as "nothing"
     * are genuinely nothing rather than omissions — the race has no prerequisite of the Pylon/creep
     * kind, so there is no ground cover to lay and no support to fill.
     *
     * <p><b>Their base defence is a Bunker with a pair of live Marines in it, and a Missile Turret
     * over the top of them</b> — and the split is the race, not an inconsistency with the other two.
     * The Protoss plant a Photon Cannon that answers everything and the Zerg plant one colony per
     * layer of the sky; the Terran answer the ground with a wall they have to crew and the air with a
     * gun they do not. Pull the Marines out and the ground half of the defence leaves with them,
     * which is still the only base in this table whose defence can be dismantled by the defender.
     */
    public static final RaceProfile TERRAN = new RaceProfile(
            Race.TERRAN,
            AsteriskCraft.COMMAND_CENTER_CORE::get,
            BuildingTemplates.COMMAND_CENTER,
            TERRAN_BASE_HEALTH,
            0,
            TERRAN_BASE_WARP_TICKS,
            TERRAN_BANK_SLOTS,
            UnitRoster.builder()
                    .worker(UnitStats.SCV, AsteriskCraft.SCV)
                    // The Bunker is deliberately absent, exactly as the Photon Cannon is from the
                    // Protoss roster and the colonies from the Zerg one: a roster is what a
                    // building can be told to *train*, and a structure bought as a kit is never
                    // trained. Listing it would let a build script name it and get one for nothing,
                    // since a kit-bought unit carries UnitCost.NONE and no build time.
                    .unit(UnitStats.MARINE, AsteriskCraft.MARINE)
                    .unit(UnitStats.FIREBAT, AsteriskCraft.FIREBAT)
                    .unit(UnitStats.GHOST, AsteriskCraft.GHOST)
                    // The race's two gated units, and the only two on any roster that share a
                    // gate: both need a Starport standing, the way a Mutalisk needs a Spire. The
                    // infantry above are gated by a Barracks in practice — that is where their
                    // buttons live — but not by the roster, since the Command Center's card could
                    // sell one again without lying.
                    .unit(UnitStats.WRAITH, AsteriskCraft.WRAITH, AsteriskCraft.STARPORT_CORE)
                    // The Goliath is not a flyer; it is the answer to one. It comes out of the air
                    // building anyway because that is what the Starport is *for* on this roster —
                    // the tier a player buys when the sky becomes the problem — and it means one
                    // razed building takes the whole answer with it, air half and ground half.
                    .unit(UnitStats.GOLIATH, AsteriskCraft.GOLIATH, AsteriskCraft.STARPORT_CORE)
                    // The third and last thing behind the Starport gate, and the only unit on any
                    // roster with no attack at all. It is on the roster rather than sold as a kit
                    // because it is genuinely trained — the Bunker's exclusion above is about
                    // things bought as buildings, not about things that cannot shoot.
                    .unit(UnitStats.SCIENCE_VESSEL, AsteriskCraft.SCIENCE_VESSEL, AsteriskCraft.STARPORT_CORE)
                    .build(),
            () -> ProductionKind.TERRAN_BASE,
            AsteriskCraft.id("build_scripts/terran.txt"),
            // No creep, and no support fill: the Terran set down on the ground as they find it.
            null,
            null,
            // The Terran build rather than warp, but a half-built Command Center still has to stand
            // as something for the two minutes an SCV spends welding it together. Dark grey glass:
            // the frame of a building, not the Protoss light pouring into one.
            () -> Blocks.GRAY_STAINED_GLASS.defaultBlockState(),
            // The smoke of a welding torch, not an energy effect — the SCV is building this by hand.
            ParticleTypes.CAMPFIRE_COSY_SMOKE,
            // A Bunker and the two Marines that crew it. GameBootstrap puts the Marines inside once
            // both are standing (see its garrison pass) — order here is irrelevant, since it boards
            // afterwards rather than as it spawns.
            List.of(new BaseDefence(AsteriskCraft.BUNKER, 1), new BaseDefence(AsteriskCraft.MARINE, 2),
                    new BaseDefence(AsteriskCraft.MISSILE_TURRET, 1)),
            // The Starport, planted finished with each of the computer's Command Centers — the Zerg
            // Spire's opposite number, and gated for the same reason: the Wraith and the Goliath are
            // the Terran units the roster makes conditional, so a build script that names one gets
            // nothing until this is standing. It is also what a player razes to shut the air waves down a tier.
            // The Barracks is deliberately not here: the race's infantry are ungated on the roster,
            // so a computer Command Center already trains them without one.
            List.of(new TechBuilding(BuildingTemplates.STARPORT, AsteriskCraft.STARPORT_CORE::get)),
            // No escort: the Terran have no flyer, so there is nothing a Command Center wants
            // hovering over it. Their detection is the Missile Turret above, which is rooted — so
            // unlike the swarm's Overlord, nothing carries it out with an army.
            List.of(),
            // Two Marines standing at the Command Center, the human's only. The Terran are the one
            // race whose base card sells no combat unit — infantry come out of a Barracks — so a
            // Terran player who was given nothing but SCVs would be defenceless for as long as the
            // kit below takes to build. These are the crew for the Bunker in that kit, too.
            List.of(new BaseDefence(AsteriskCraft.MARINE, 2)),
            List.of(new StartingStack(() -> Items.OAK_LOG, 128 * 3),
                    new StartingStack(() -> Items.COBBLESTONE, 128 * 3)),
            PLAYER_BANK,
            // A Bunker kit and a Barracks kit: placing them is the Terran opening move the way a Pylon
            // is the Protoss one. The Barracks is what makes the pair an opening rather than a wall —
            // the base card trains only SCVs, so without one a Terran player has no way to replace
            // the two Marines above, and the Bunker has nothing to be crewed with once they die.
            List.of(AsteriskCraft.BUNKER_KIT::get, AsteriskCraft.BARRACKS_KIT::get));

    private static final Map<Race, RaceProfile> BY_RACE = new EnumMap<>(Map.of(
            Race.PROTOSS, PROTOSS,
            Race.ZERG, ZERG,
            Race.TERRAN, TERRAN));

    private Races() {
    }

    public static RaceProfile of(Race race) {
        RaceProfile profile = BY_RACE.get(race);
        if (profile == null) {
            throw new IllegalStateException("no profile registered for race " + race);
        }
        return profile;
    }

    /** Every profile, for the table-completeness test and a future datapack override layer. */
    public static List<RaceProfile> all() {
        return List.copyOf(BY_RACE.values());
    }
}
