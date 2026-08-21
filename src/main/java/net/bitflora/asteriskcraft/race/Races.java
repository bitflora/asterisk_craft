package net.bitflora.asteriskcraft.race;

import net.bitflora.asteriskcraft.AsteriskCraft;
import net.bitflora.asteriskcraft.building.BuildingTemplates;
import net.bitflora.asteriskcraft.building.ProductionKind;
import net.bitflora.asteriskcraft.faction.Faction;
import net.bitflora.asteriskcraft.faction.Race;
import net.bitflora.asteriskcraft.race.RaceProfile.BaseDefence;
import net.bitflora.asteriskcraft.race.RaceProfile.StartingStack;
import net.bitflora.asteriskcraft.stats.UnitStats;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import org.jetbrains.annotations.Nullable;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * The per-race table. This is the only place in the mod that knows a Hive is what a Zerg army
 * builds and a Drone is what it mines with; every other caller asks {@link #of(Faction)} for a
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
     * What the Zerg bank is stocked with when the swarm is the computer player: the old
     * 128/128/48-per-Hive x3 total, from when each Hive held its own independent stock.
     */
    private static final int ZERG_STARTING_LOGS = 128 * 3;
    private static final int ZERG_STARTING_COBBLE = 128 * 3;
    private static final int ZERG_STARTING_IRON = 48 * 3;

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
            List.of(new StartingStack(() -> Items.OAK_LOG, 128 * 3),
                    new StartingStack(() -> Items.COBBLESTONE, 128 * 3)));

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
                    .unit(UnitStats.ULTRALISK, AsteriskCraft.ULTRALISK)
                    .unit(UnitStats.LURKER, AsteriskCraft.LURKER)
                    .build(),
            // No command card yet: the Hive has never had a GUI, since the swarm has only ever been
            // the computer player. This is the seam a player-Zerg build would fill.
            null,
            AsteriskCraft.id("build_scripts/zerg.txt"),
            () -> Blocks.MYCELIUM.defaultBlockState(),
            // The mound keeps a mycelium footing under it — same material as the creep it sits in,
            // so unlike the Protoss stonework there is nothing foreign to see.
            () -> Blocks.MYCELIUM.defaultBlockState(),
            List.of(new BaseDefence(AsteriskCraft.SUNKEN_COLONY, 1),
                    new BaseDefence(AsteriskCraft.SPORE_COLONY, 1)),
            List.of(new StartingStack(() -> Items.OAK_LOG, ZERG_STARTING_LOGS),
                    new StartingStack(() -> Items.COBBLESTONE, ZERG_STARTING_COBBLE),
                    new StartingStack(() -> Items.IRON_INGOT, ZERG_STARTING_IRON)));

    private static final Map<Race, RaceProfile> BY_RACE = new EnumMap<>(Map.of(
            Race.PROTOSS, PROTOSS,
            Race.ZERG, ZERG));

    private Races() {
    }

    public static RaceProfile of(Race race) {
        RaceProfile profile = BY_RACE.get(race);
        if (profile == null) {
            throw new IllegalStateException("no profile registered for race " + race);
        }
        return profile;
    }

    /**
     * The profile of whichever race this side plays, or null for {@link Faction#NEUTRAL} — the
     * unfactioned world is nobody's army and has no base, bank or roster to describe.
     */
    public static @Nullable RaceProfile of(Faction faction) {
        Race race = faction.race();
        return race == null ? null : of(race);
    }

    /**
     * As {@link #of(Faction)}, for the callers that have already established the side is a real
     * army — a match's two sides, a building's owner. Throws rather than returning null so a
     * NEUTRAL leaking into one of those paths fails loudly instead of NPE-ing somewhere later.
     */
    public static RaceProfile required(Faction faction) {
        RaceProfile profile = of(faction);
        if (profile == null) {
            throw new IllegalArgumentException(faction + " plays no race");
        }
        return profile;
    }

    /** Every profile, for the table-completeness test and a future datapack override layer. */
    public static List<RaceProfile> all() {
        return List.copyOf(BY_RACE.values());
    }
}
