package net.bitflora.asteriskcraft.race;

import net.bitflora.asteriskcraft.AsteriskCraft;
import net.bitflora.asteriskcraft.building.BuildingTemplates;
import net.bitflora.asteriskcraft.building.ProductionKind;
import net.bitflora.asteriskcraft.faction.Race;
import net.bitflora.asteriskcraft.race.RaceProfile.BaseDefence;
import net.bitflora.asteriskcraft.race.RaceProfile.StartingStack;
import net.bitflora.asteriskcraft.stats.UnitStats;
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
     * should be. The Zerg base carries no shields (not a Zerg mechanic) and no warp-in, since it
     * is pre-placed at world generation rather than raised from a kit.
     */
    private static final int PROTOSS_BASE_HEALTH = 325;
    private static final int PROTOSS_BASE_SHIELD = 325;
    /** Two minutes — an expansion Nexus is a long commitment. */
    private static final int PROTOSS_BASE_WARP_TICKS = 20 * 120;
    private static final int ZERG_BASE_HEALTH = 300;

    /** 3x a single building's original 9 slots. */
    private static final int PROTOSS_BANK_SLOTS = 27;
    /** 3x a single Hive's original 27 slots. */
    private static final int ZERG_BANK_SLOTS = 81;

    /**
     * The Terran base is as sturdy as the other two — the thing you lose the game with should be —
     * but carries no shields (not a Terran mechanic) and no warp-in, since nothing raises one from
     * a kit yet.
     */
    private static final int TERRAN_BASE_HEALTH = 325;
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
                    .unit(UnitStats.DARK_TEMPLAR, AsteriskCraft.DARK_TEMPLAR)
                    .build(),
            () -> ProductionKind.PROTOSS_BASE,
            AsteriskCraft.id("build_scripts/protoss.txt"),
            // No creep: the Protoss leave the ground they land on alone.
            null,
            // No support fill either — the end-stone-brick platform is its own plinth, and nothing
            // foreign should be stamped under Protoss stonework.
            null,
            List.of(new BaseDefence(AsteriskCraft.PHOTON_CANNON, 1)),
            // No escort: the Protoss detector is the Photon Cannon, which is already in the line
            // above, and nothing else about a Nexus wants a unit hovering over it at world start.
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
            0,
            ZERG_BANK_SLOTS,
            UnitRoster.builder()
                    .worker(UnitStats.DRONE, AsteriskCraft.DRONE)
                    .unit(UnitStats.ZERGLING, AsteriskCraft.ZERGLING)
                    .unit(UnitStats.HYDRALISK, AsteriskCraft.HYDRALISK)
                    .unit(UnitStats.MUTALISK, AsteriskCraft.MUTALISK)
                    .unit(UnitStats.OVERLORD, AsteriskCraft.OVERLORD)
                    .unit(UnitStats.ULTRALISK, AsteriskCraft.ULTRALISK)
                    .unit(UnitStats.LURKER, AsteriskCraft.LURKER)
                    .build(),
            // The Hive is the swarm's whole production building — there is no Zerg factory to
            // build, so its card morphs combat units directly as well as training Drones.
            () -> ProductionKind.ZERG_BASE,
            AsteriskCraft.id("build_scripts/zerg.txt"),
            () -> Blocks.MYCELIUM.defaultBlockState(),
            // The mound keeps a mycelium footing under it — same material as the creep it sits in,
            // so unlike the Protoss stonework there is nothing foreign to see.
            () -> Blocks.MYCELIUM.defaultBlockState(),
            List.of(new BaseDefence(AsteriskCraft.SUNKEN_COLONY, 1),
                    new BaseDefence(AsteriskCraft.SPORE_COLONY, 1)),
            // One Overlord per Hive, on both sides. The Spore Colony above it detects too, but it is
            // rooted to this spot — the Overlord is what carries detection out with an army, which
            // is the whole answer to a Dark Templar met in the field.
            List.of(new BaseDefence(AsteriskCraft.OVERLORD, 1)),
            List.of(new StartingStack(() -> Items.OAK_LOG, ZERG_STARTING_LOGS),
                    new StartingStack(() -> Items.COBBLESTONE, ZERG_STARTING_COBBLE),
                    new StartingStack(() -> Items.IRON_INGOT, ZERG_STARTING_IRON)),
            PLAYER_BANK,
            // The swarm's opener is one Sunken Colony to root by the Hive. No Spore: the first
            // thing that can hurt you comes on the ground, and the card sells the answer to the
            // rest.
            List.of(AsteriskCraft.SUNKEN_COLONY_SPAWN_EGG_ALLY::get));

    /**
     * The Terran, as far as they go today: a Command Center, the SCVs that mine for it, and the
     * Marines they build. The entries below that still read as "nothing" are genuinely nothing yet
     * rather than omissions — there is no second Terran building to hand out as a starting kit, and
     * no prerequisite of the Pylon/creep kind, so no ground cover to lay.
     *
     * <p><b>Their base defence is a pair of live Marines rather than a structure</b>, which is the
     * one place the race reads differently from the other two in this table: the Protoss plant a
     * Photon Cannon and the Zerg a Sunken and a Spore, both rooted, while the Terran have nothing
     * static to plant and post a guard instead. A guard can be pulled off to fight elsewhere, and
     * can chase what it is shooting at, so a Terran base is defended more loosely than either of
     * the others — a real difference in how the race holds ground, not a placeholder.
     */
    public static final RaceProfile TERRAN = new RaceProfile(
            Race.TERRAN,
            AsteriskCraft.COMMAND_CENTER_CORE::get,
            BuildingTemplates.COMMAND_CENTER,
            TERRAN_BASE_HEALTH,
            0,
            0,
            TERRAN_BANK_SLOTS,
            UnitRoster.builder()
                    .worker(UnitStats.SCV, AsteriskCraft.SCV)
                    .unit(UnitStats.MARINE, AsteriskCraft.MARINE)
                    .build(),
            () -> ProductionKind.TERRAN_BASE,
            AsteriskCraft.id("build_scripts/terran.txt"),
            // No creep, and no support fill: the Terran set down on the ground as they find it.
            null,
            null,
            // Two Marines standing on the Command Center. The race's only answer to "what is
            // guarding this base at world start" — see the class doc above.
            List.of(new BaseDefence(AsteriskCraft.MARINE, 2)),
            // No escort: the Terran have no flyer and no detector, so there is nothing a Command
            // Center wants hovering over it that the two Marines below it are not already doing.
            List.of(),
            List.of(new StartingStack(() -> Items.OAK_LOG, 128 * 3),
                    new StartingStack(() -> Items.COBBLESTONE, 128 * 3)),
            PLAYER_BANK,
            List.of());

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
