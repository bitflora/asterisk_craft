package net.bitflora.asteriskcraft.stats;

import net.bitflora.asteriskcraft.faction.Race;

import java.util.List;
import java.util.stream.Stream;

/**
 * The single balance table for every unit in the mod: combat stats and production cost.
 *
 * <p><b>The numbers are not here.</b> They live one row per unit in
 * {@code src/main/resources/asteriskcraft/balance/unit_stats.csv}, read at this class's init by
 * {@link UnitStatTable} — because a balance pass is a question about a <em>column</em> ("what does
 * every ranged unit cost per point of damage?") and a builder chain per unit can only be read a
 * unit at a time. Open the CSV in a spreadsheet, sort by whatever you are comparing, save. Edit the
 * file, relaunch — nothing recompiles, and an installed build can be tuned from
 * {@code <gamedir>/config/asteriskcraft/unit_stats.csv} without being rebuilt at all.
 *
 * <p><b>docs/balance-table.md is the per-column reference</b>: what each field means, what it is
 * measured in, which columns come in all-or-nothing groups, the cost syntax, and the numbers that
 * deliberately live somewhere else.
 *
 * <p>What stays here is everything a grid cannot hold: the named constant each call site reaches
 * for, and the design rationale for why a number is what it is. The two halves are checked against
 * each other at startup — a field naming a row the CSV doesn't define fails immediately, and
 * {@link UnitStat.Builder#build()} plus {@code UnitStatsTest} police the numbers themselves.
 *
 * <p>Attributes get registered from these entries via {@link UnitAttributes#apply}; costs get paid
 * via {@link CostPayment}. See each entity's {@code createAttributes()} for how it consumes its
 * entry, and {@code building/ProductionKind} / {@code race/UnitRoster} for how the
 * costs reach the GUI and the enemy build script.
 */
public final class UnitStats {

    private static final UnitStatTable TABLE = UnitStatTable.load();

    /**
     * The one altitude every flyer in the mod cruises at, so a player learns a single number rather
     * than one per unit. It stays a constant here rather than becoming only a column, because it is
     * a rule about the set and not a fact about any one row; each flyer's {@code hover_height} cell
     * must equal it, which {@code UnitStatsTest} enforces.
     */
    public static final int HOVER_HEIGHT = 4;

    // --- Protoss ---

    public static final UnitStat PROBE = TABLE.get("probe");

    public static final UnitStat ZEALOT = TABLE.get("zealot");

    /**
     * The mod's other two-node-wide unit, and it has the same problem the Ultralisk does: stopping
     * 0.55 short of a ledge while MoveControl only fires a jump within sqrt(width) of the waypoint.
     * The stalling that CommandedMoveGoal's detour rules and MoveFormation's spacing were both
     * written against is partly this. A walker steps up a block anyway — hence its step height.
     */
    public static final UnitStat DRAGOON = TABLE.get("dragoon");

    /**
     * Anti-air is what a Scout is for: 21 to a Mutalisk against 11 to anything on the ground, so a
     * pair of Scouts out-trades the flock they intercept while staying an indifferent answer to a
     * ground army the Zealots should be handling.
     */
    public static final UnitStat SCOUT = TABLE.get("scout");

    /**
     * The first unit in the mod to carry {@code faction.Cloaked}, and priced for it. Hits harder than
     * anything else the Gateway makes and cannot be fought back at while undetected — so it is
     * deliberately the most fragile thing on the Protoss roster behind the Probe: a single Sunken
     * Colony spike (20) takes half of it, and the shield does not cover the gap. Expensive in build
     * time rather than in any one resource, so massing them costs tempo the player feels.
     */
    public static final UnitStat DARK_TEMPLAR = TABLE.get("dark_templar");

    /**
     * Kit-bought at a base for {@code BaseBlockEntity.BUILDING_COST}, not trained directly — hence a
     * cost of none, and hence no build time either.
     *
     * <p>Its follow range equals its gun's, so it never acquires what it can't shoot. Detection
     * reaches two blocks past the gun on purpose: a cannon lights a cloaked attacker up for the
     * whole base a moment before it can shoot at it itself.
     */
    public static final UnitStat PHOTON_CANNON = TABLE.get("photon_cannon");

    // --- Zerg ---

    /**
     * Transcribed from {@link #PROBE} verbatim, including its shield pool — a Drone is never Protoss
     * today, so {@code ShieldAttachments}' faction gate zeroes it in play regardless, but that pass
     * was behaviour-preserving. Follow-up: drop to 0 once a Drone truly can't be Protoss.
     */
    public static final UnitStat DRONE = TABLE.get("drone");

    public static final UnitStat ZERGLING = TABLE.get("zergling");

    public static final UnitStat HYDRALISK = TABLE.get("hydralisk");

    public static final UnitStat MUTALISK = TABLE.get("mutalisk");

    /**
     * The swarm's eye in the sky, and the mod's first <em>mobile</em> detector: everything that
     * detects before it — the Photon Cannon, the Spore Colony — is rooted, so detection could only
     * ever cover ground an army had already built on. An Overlord carries the same 16-block bubble
     * wherever it drifts, which is what lets the swarm push into a Dark Templar rather than wait for
     * one to wander into a colony. It shares that envelope with both rooted detectors, so a player
     * learns one radius, not two.
     *
     * <p>It has no attack at all — the only unit besides the two workers that doesn't, which is the
     * rule {@code UnitStatsTest.onlyNonCombatUnitsLackAttackDamage} states. That is what it pays for
     * its detection with: it is slow (well under the Mutalisk — a drifting sac, not a raider), huge
     * and entirely dependent on an escort.
     */
    public static final UnitStat OVERLORD = TABLE.get("overlord");

    /**
     * The Zerg heavy: the only unit on either side that a Zealot ball can't simply out-trade, and the
     * reason to keep Dragoons or a Cannon around. Its minute-long build time is what keeps it rare —
     * a wave carrying one takes a minute longer to assemble than the same wave without.
     *
     * <p>It steps over a full block instead of jumping it. Two pathfinding nodes wide, it stops 0.6
     * short of a ledge, and MoveControl only fires a jump inside sqrt(width) of the waypoint — so on
     * broken ground it would stall against every rise. See {@link UnitStat#stepHeight}.
     *
     * <p>It reuses the Zergling's geometry, scaled up — broad and long far more than tall, and more
     * than its hitbox is: see its registration in {@code AsteriskCraft} for why the height stays
     * under 2.0, and {@code client.zerg.UltraliskRenderer} for the silhouette that sits over it.
     */
    public static final UnitStat ULTRALISK = TABLE.get("ultralisk");

    /**
     * The Zerg answer to the Dark Templar, and the roster's first <em>conditionally</em> cloaked unit:
     * it is hidden and armed only while burrowed, and spends three seconds helpless in the dirt at
     * each end of that. Priced as a heavy siege piece rather than an assassin — 63 HP behind 1 armour
     * is more than a Hydralisk carries, because a unit that cannot move while it fights cannot
     * disengage from a mistake, and a 40-second build time is what keeps a wall of them from being
     * the obvious answer to everything.
     *
     * <p>Its 6 blocks of range is the Hydralisk's, deliberately: what the Lurker buys with the burrow
     * is not reach but a row of spines that each land in full, so it out-trades anything that walks
     * up the line at it and loses to anything that shoots it from 8.
     */
    public static final UnitStat LURKER = TABLE.get("lurker");

    /** Pre-placed by {@code GameBootstrap}, not trained — hence no cost and no build time. Its follow
     * range equals its reach: a rooted attacker can't close the gap. */
    public static final UnitStat SUNKEN_COLONY = TABLE.get("sunken_colony");

    /**
     * The Zerg answer to the air, and the only thing on either side that fires <em>exclusively</em> at
     * it: acquisition is narrowed by {@code entity.Altitude#isAirborne} rather than by unit type, so
     * what it may shoot is decided by where a target is, not what it is. Pre-placed beside every Hive
     * like the Sunken, hence no cost.
     *
     * <p>Tuned as a wall rather than a gun: the toughest unit in the mod at 200 HP (a rooted structure
     * that cannot answer a ground army at all has to survive being ignored), but only 7.5 a shot on a
     * one-second cadence — 7.5 DPS, less than half the Sunken's, because a flyer it out-ranges cannot
     * shoot back at all and the exchange should still take a moment.
     *
     * <p>It is also the Zerg detector, on the shared 16-block envelope — a Hive perimeter lights up
     * cloaked attackers from both of its rooted defences.
     */
    public static final UnitStat SPORE_COLONY = TABLE.get("spore_colony");

    /**
     * The swarm's dividend on an overrun village: raised from a villager's corpse rather than trained,
     * which is why it costs nothing and has no build time — {@code combat.InfestationHandler} is its
     * only producer.
     *
     * <p>It is the roster's first <em>suicide</em> unit, so its 250 damage is not a per-swing number
     * but the one detonation it ever gets: eight Zerglings' worth of damage delivered once, to
     * everything inside three blocks and to whatever building it was standing against. Priced against
     * that in the only currency it has — 30 HP and no armour, less than a Hydralisk, so a defended
     * base kills it on the approach and an undefended one loses a third of a Nexus.
     *
     * <p>It walks a little faster than a Zergling: a bomb that can be out-run is a bomb that never
     * goes off.
     */
    public static final UnitStat INFESTED_VILLAGER = TABLE.get("infested_villager");

    // --- Terran ---

    /**
     * The SCV. Numerically the Probe with its shields taken away — the Terran carry none — which
     * leaves it the cheapest thing in the mod that can still be killed by a single Zergling.
     * Deliberately priced identically to the other two workers: what a race differs in is what it
     * builds with its economy, not what the economy itself costs to staff.
     *
     * <p>It is also the one worker in the mod that is armed. It never goes looking for a fight — see
     * {@code entity.terran.ScvEntity} for the retaliate-only goal set — but a Terran economy that
     * can answer a stray Zergling is a real difference between the races, not a rounding error, and
     * it is paid for in the build time: an SCV takes twice as long to come out as a Probe.
     */
    public static final UnitStat SCV = TABLE.get("scv");

    /**
     * The Marine. The Terran line infantry, and the first thing the race has that goes looking for a
     * fight: a rifle at five blocks on a one-second cadence, for three damage a shot.
     *
     * <p>Deliberately the frailest combat unit in the mod at 20 HP — under a Zergling's, and two
     * Zealot swings — because everything else about it says "bring more of them". It is the cheapest
     * unit either picky race can buy, it comes out in 24 seconds, and a lone one loses to almost
     * anything on the field. Massed, its damage stacks the way no single Terran unit's does, which
     * is the whole shape of the race until it has more in it.
     *
     * <p>An SCV's walk with the rifle's weight on it: a shade slower than the worker that built it,
     * matching the Hydralisk it will most often be shooting at.
     */
    public static final UnitStat MARINE = TABLE.get("marine");

    /**
     * The Firebat: the Terran answer to a <em>clump</em>, where the Marine is the answer to a body.
     *
     * <p>Everything in these numbers is the trade for that. It has to stand at two blocks, which is
     * inside a Zergling's reach and well inside a Hydralisk's, so it is given 25 HP and the armour 1
     * a Bunker has — flat reduction, worth most against exactly the massed small hits it walks into.
     * In return, 8 damage on a 1.5-second cadence lands on <em>everything</em> in the cone rather
     * than on one target, so a Firebat that reaches a pack out-trades its own cost several times
     * over and one that is kited never gets to swing.
     *
     * <p>Twice the Marine's price, in both resources rather than either — the first Terran cost that
     * is a bundle rather than a choice, which is what says this is a specialist and not a body.
     * Same 24 seconds, so the two come off one Command Center at the same rate.
     *
     * <p>The 2-block {@code range} is not a misnomer: {@link UnitStat.Ranged} is "how far it holds
     * at, and how often it fires", which is exactly true of a flamethrower, and it is what lets the
     * unit reuse {@code entity.ai.UnitRangedAttackGoal} and pick up the Bunker firing-slit bonus for
     * free.
     */
    public static final UnitStat FIREBAT = TABLE.get("firebat");

    /**
     * The Ghost: the Terran answer to being <em>out-ranged</em>, where the Marine answers a body and
     * the Firebat answers a clump.
     *
     * <p>It is the most expensive thing the race trains and the flimsiest — 23 HP and no armour at
     * all, so anything that reaches it kills it — and it buys two things with that. Seven blocks is
     * two further than a rifle, which is the difference between trading with a Hydralisk and being
     * shot at by one. And it cloaks the instant it is hit (see {@code entity.terran.CloakClock}),
     * which means the first Ghost in a fight usually gets to walk out of it.
     *
     * <p>Five damage on a 1.5-second cadence is deliberately <em>worse</em> per second than a
     * Marine's three on 1.0 — it is not a damage unit, it is a reach unit, and one that traded its
     * survivability for that reach has to lose the damage race to something standing in the open.
     *
     * <p>The first Terran cost naming three resources, and the only unit in the mod that costs iron
     * — a metal the player has to go underground for, on a unit that also takes 50 seconds to come
     * out of a Command Center. Both are the same statement: a Ghost is a decision, not a body.
     */
    public static final UnitStat GHOST = TABLE.get("ghost");

    /**
     * The Bunker. The first unit in the mod that holds other units, and the only structure with no
     * weapon of its own: everything it does to an enemy is done by whatever is inside it.
     *
     * <p>Its numbers are all shell. 175 HP is the toughest thing either race fields short of a base,
     * and the armour 1 is the point of the building — it is flat reduction, so it is worth most
     * against exactly the massed small hits a Terran player is trying to survive. There is no
     * shield (the Terran carry none) and no attack damage, which makes it the first entry here that
     * is both rooted and unarmed; {@code UnitStatsTest} had to split those two ideas apart to say so.
     *
     * <p>Kit-bought at a Command Center for {@code BaseBlockEntity.BUNKER_COST}, not trained — hence
     * no cost, and hence no build ticks. The thirty seconds it takes to stand up is the construction
     * countdown on {@code entity.terran.BunkerEntity} itself, the same place the Photon Cannon keeps
     * its ten.
     */
    public static final UnitStat BUNKER = TABLE.get("bunker");

    /**
     * The Missile Turret — the Terran answer to the two things the race could not do at all. It is
     * their first detector, and their first weapon that reaches a flyer holding altitude; before it,
     * a Terran base watched a Mutalisk flock and a cloaked Dark Templar with exactly the same
     * helplessness.
     *
     * <p>Deliberately the Spore Colony's opposite number rather than its copy. The Zerg answer is a
     * wall — 200 HP dribbling 7.5 a shot — while this is a gun with a thin skin: 100 HP, no armour,
     * and 20 damage a second, the hardest-hitting static defence in the mod and the same DPS as a
     * Dark Templar. It kills what it hits and dies to anything that reaches it, which is the whole
     * reason the Bunker stands next to it: the turret handles the air and the infantry inside the
     * Bunker handle the ground, and neither covers for the other.
     *
     * <p>Air-only, and "air" here is positional ({@code entity.Altitude}), not a unit type — the same
     * rule the Spore Colony uses, so a Mutalisk that lands stops being a target mid-fight. Range 14
     * and follow range 14, because a rooted attacker must never acquire past its own reach.
     *
     * <p>Detection is the shared 16 / 20 / 60 envelope every detector uses: a player learns one
     * radius, not three.
     *
     * <p>Kit-bought at a Command Center for {@code BaseBlockEntity.MISSILE_TURRET_COST}, not trained
     * — hence no cost and no build ticks. The thirty seconds it takes to stand up is the construction
     * countdown on {@code entity.terran.MissileTurretEntity}, beside the Bunker's.
     */
    public static final UnitStat MISSILE_TURRET = TABLE.get("missile_turret");

    /**
     * The Protoss roster, for balance grouping and the "Protoss stays picky" cost invariant. Derived
     * from the CSV's {@code race} column rather than hand-listed, so a unit added to the file cannot
     * be left out of its own race's roster.
     */
    public static final List<UnitStat> PROTOSS_ROSTER = TABLE.roster(Race.PROTOSS);

    /** The Zerg roster, for balance grouping and the "Zerg pays in any item" cost invariant. */
    public static final List<UnitStat> ZERG_ROSTER = TABLE.roster(Race.ZERG);

    /**
     * The Terran roster. It holds the same "names a real resource, never ANY" invariant the Protoss
     * one does.
     */
    public static final List<UnitStat> TERRAN_ROSTER = TABLE.roster(Race.TERRAN);

    private UnitStats() {
    }

    /** The whole roster, for invariant tests. */
    public static List<UnitStat> all() {
        return Stream.of(PROTOSS_ROSTER, ZERG_ROSTER, TERRAN_ROSTER).flatMap(List::stream).toList();
    }
}
