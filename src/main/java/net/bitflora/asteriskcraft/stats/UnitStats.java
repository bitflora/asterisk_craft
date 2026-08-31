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
     * The Protoss eye, and the counterpart to the {@link #OVERLORD}: a Stargate-trained flyer that
     * carries the same detection envelope every other detector has and is itself permanently
     * cloaked, so it goes and looks at things nothing on the other side can shoot at it for.
     *
     * <p>It has no attack — the only Protoss unit that doesn't, and one of the four the rule
     * {@code UnitStatsTest.onlyNonCombatUnitsLackAttackDamage} names. What it pays for the cloak
     * with is being the flimsiest thing either side can field: 20 HP behind 10 shields is under a
     * third of a Scout's pool, so a detector on the other side turns it from untouchable into a
     * single volley, and it is far cheaper than the Overlord because it cannot soak anything.
     *
     * <p>It cruises at the Scout's speed rather than the Overlord's drift: it is a scout that has to
     * get somewhere and look at it, not a sac that follows an army around.
     */
    public static final UnitStat OBSERVER = TABLE.get("observer");

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
     * The Wraith — the Terran fighter, and the race's first unit that leaves the ground. It is
     * built out of two units the mod already had, and both halves are load-bearing.
     *
     * <p><b>From the Scout, the split attack.</b> 2.5 damage to anything on the ground and 10 to
     * anything airborne, written as a base of 2.5 plus a 7.5 anti-air bonus, so one attribute still
     * describes the shot (see {@code UnitStat#antiAirBonus}). The ratio is far steeper than the
     * Scout's 11/21: a Scout is a fighter that can also strafe, while a Wraith that shoots at ground
     * targets is a Wraith being wasted — 2.5 on a 1.5-second cadence is under 1.7 a second, the
     * feeblest sustained damage in the mod. Against a Mutalisk or another Wraith it is 6.7 a second,
     * which beats every Terran gun that is not bolted to the floor.
     *
     * <p><b>From the Ghost, the reactive cloak.</b> Same {@code entity.terran.CloakClock}, same
     * minute up and two minutes locked out. That is what the 60 HP is priced against: a Wraith is
     * tougher than any Terran infantry and still frail for an air unit — a Mutalisk matches it at 60
     * and a Scout beats it at 75-behind-50-shields — because the first hit it takes is supposed to be
     * answered by vanishing rather than by absorbing.
     *
     * <p>Range 8 rather than the Scout's and the Mutalisk's 9, so it out-reaches nothing it will meet
     * in the air; it wins those fights by not being shootable, not by standing further off. Eight is
     * also the floor the shared 4-block cruising altitude leaves — see
     * {@code UnitStatsTest.everyFlyerOutrangesItsOwnCruisingAltitude}.
     *
     * <p>150 wood, 100 cobblestone and 2 iron over 50 seconds, out of a Starport that costs the same
     * shape again. The second Terran unit to spend iron and the second to take 50 seconds, which puts
     * it beside the Ghost as a decision rather than a body.
     */
    public static final UnitStat WRAITH = TABLE.get("wraith");

    /**
     * The Goliath — the Terran walking mech, and the first thing the race has that can look up
     * <em>while moving</em>. A Missile Turret is bolted to the floor and a Wraith has to be in the
     * air itself, so until this a Mutalisk flock over a marching column was answered by walking the
     * column home. It is the third row in the table with an anti-air bonus, and the only one on legs.
     *
     * <p><b>6 to the ground and 10 to the air</b>, written as a base of 6 plus a 4 anti-air bonus so
     * one attribute still describes the shot (see {@code UnitStat#antiAirBonus}). The ratio is the
     * shallowest of the three deliberately: a Scout is 11/21 and a Wraith 2.5/10, both of which say
     * "this is an interceptor". Six a second on the ground is the second-best sustained output the
     * race fields on foot, so a Goliath is a line unit that also happens to answer the sky rather
     * than a specialist that is useless without one — it escorts, it does not need escorting.
     *
     * <p>Range 6, which is the one number that keeps it honest. Flyers cruise at
     * {@link #HOVER_HEIGHT} 4, so six leaves {@code sqrt(36-16) = 4.5} blocks of horizontal reach
     * against something holding altitude: a Goliath has to stand nearly under a Mutalisk to shoot it,
     * where a Missile Turret's 14 covers a whole base from one spot. Mobility is what it is paying
     * for, and short reach is the bill.
     *
     * <p>65 HP behind 1 armour is the sturdiest Terran unit that walks — more than three Marines'
     * worth of health on one body — which is what a mech at 100 cobblestone and 50 wood over 40
     * seconds should buy. It spends no iron, unlike the Ghost and the Wraith: it is meant to be
     * massed out of a Starport rather than committed to one at a time.
     */
    public static final UnitStat GOLIATH = TABLE.get("goliath");

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
     * The Science Vessel — the Terran eye that <em>walks</em>. The Missile Turret above is the
     * race's other detector and it is bolted to the floor, so until this the Terran could see a
     * Dark Templar or a burrowed Lurker exactly where they had already built and nowhere else. This
     * is the Observer's job done the Terran way, and the two units are deliberately opposite in
     * every number: an Observer is 20 HP that survives by being permanently cloaked, while a Vessel
     * is <b>100 HP behind 1 armour</b> and survives by being hard to kill. It is the toughest thing
     * either race flies.
     *
     * <p><b>No attack at all</b> — the {@code attack_damage} cell is blank, which is what puts it in
     * {@code UnitStatsTest}'s non-combatant list beside the Observer, the Overlord and the two
     * workers. What it does instead is on a timer rather than in the table: every 45 seconds it
     * poisons one enemy in its bubble or, seeing none, grants Resistance to an ally. Those constants
     * live on {@code entity.terran.ScienceVesselEntity}, not here, for the reason
     * {@code CloakClock.CLOAK_TICKS} does — see docs/balance-table.md. Poison floors its victim at
     * 1 HP, so a Vessel genuinely cannot secure a kill; "no attack" is honest rather than a rounding
     * error.
     *
     * <p><b>Detection is 10, and it is the first detector off the shared 16 / 20 / 60 envelope.</b>
     * Every other one is rooted or, in the Observer's case, invisible — a bubble you can neither
     * move nor shoot. A Vessel is 100 HP flying in the open at the front of an army, so the shorter
     * reach is what it pays for being able to walk its detection into an ambush instead of waiting
     * for one. The sweep and reveal halves stay on the common 20 / 60 so a revealed unit behaves
     * identically whichever eye found it.
     *
     * <p>100 cobblestone and 225 wood over 80 seconds, out of the Starport behind the Wraith and the
     * Goliath. The steepest price and the longest build time on the roster, in both resources rather
     * than either: this is a commitment, not a body, and it is the only Terran unit whose value is
     * entirely in what it lets the rest of the army do.
     */
    public static final UnitStat SCIENCE_VESSEL = TABLE.get("science_vessel");

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
