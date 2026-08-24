package net.bitflora.asteriskcraft.stats;

import java.util.List;
import java.util.stream.Stream;

import static net.bitflora.asteriskcraft.stats.Resource.ANY;
import static net.bitflora.asteriskcraft.stats.Resource.IRON;
import static net.bitflora.asteriskcraft.stats.Resource.STONE;
import static net.bitflora.asteriskcraft.stats.Resource.WOOD;
import static net.bitflora.asteriskcraft.stats.UnitCost.line;

/**
 * The single balance table for every unit in the mod: combat stats and production cost. Edit this
 * file, {@code ./gradlew compileJava}, relaunch — this is the whole tweak loop.
 *
 * <p>Attributes get registered from these entries via {@link UnitAttributes#apply}; costs get paid
 * via {@link CostPayment}. See each entity's {@code createAttributes()} for how it consumes its
 * entry, and {@code building/ProductionKind} / {@code race/UnitRoster} for how the
 * costs reach the GUI and the enemy build script.
 */
public final class UnitStats {

    // --- Protoss ---

    public static final UnitStat PROBE = UnitStat.builder("probe")
            .health(10.0).shield(10).speed(0.35).followRange(48.0)
            .cost(UnitCost.either(List.of(line(WOOD, 50)), List.of(line(STONE, 50)))).buildTicks(200)
            .build();

    public static final UnitStat ZEALOT = UnitStat.builder("zealot")
            .health(50.0).shield(30).armor(0.5).speed(0.25)
            .attackDamage(4.0).attackAnimTicks(10)
            .cost(UnitCost.all(line(WOOD, 50), line(STONE, 50))).buildTicks(200)
            .build();

    public static final UnitStat DRAGOON = UnitStat.builder("dragoon")
            .health(50.0).shield(40).armor(0.5).speed(0.25)
            // The mod's other two-node-wide unit, and it has the same problem the Ultralisk does:
            // stopping 0.55 short of a ledge while MoveControl only fires a jump within sqrt(width)
            // of the waypoint. The stalling that CommandedMoveGoal's detour rules and MoveFormation's
            // spacing were both written against is partly this. A walker steps up a block anyway.
            .stepHeight(1.1)
            .attackDamage(10.0).ranged(8.0f, 40)
            .cost(UnitCost.all(line(WOOD, 100), line(STONE, 50))).buildTicks(200)
            .build();

    public static final UnitStat SCOUT = UnitStat.builder("scout")
            .health(75.0).shield(50).armor(0.5).speed(0.25)
            // Anti-air is what a Scout is for: 21 to a Mutalisk against 11 to anything on the
            // ground, so a pair of Scouts out-trades the flock they intercept while staying an
            // indifferent answer to a ground army the Zealots should be handling.
            .attackDamage(11.0).antiAirBonus(10.0).ranged(9.0f, 30)
            .flight(0.6, 6, 64.0f)
            .cost(UnitCost.all(line(STONE, 150), line(IRON, 20))).buildTicks(200)
            .build();

    /**
     * The first unit in the mod to carry {@code faction.Cloaked}, and priced for it. Hits harder than
     * anything else the Gateway makes and cannot be fought back at while undetected — so it is
     * deliberately the most fragile thing on the Protoss roster behind the Probe: a single Sunken
     * Colony spike (20) takes half of it, and the shield does not cover the gap. Expensive in build
     * time rather than in any one resource, so massing them costs tempo the player feels.
     */
    public static final UnitStat DARK_TEMPLAR = UnitStat.builder("dark_templar")
            .health(40.0).shield(20).armor(1.0).speed(0.25)
            .attackDamage(20.0).attackAnimTicks(12)
            .cost(UnitCost.all(line(STONE, 75), line(WOOD, 50), line(IRON, 2))).buildTicks(20 * 50)
            .build();

    /** Kit-bought at a base for {@code BaseBlockEntity.BUILDING_COST}, not trained directly — hence NONE. */
    public static final UnitStat PHOTON_CANNON = UnitStat.builder("photon_cannon")
            .health(50.0).shield(50).rooted()
            .attackDamage(10.0).ranged(14.0f, 20)
            .followRange(14.0) // == range: never acquire what it can't shoot
            // Detection reaches two blocks past the gun on purpose: a cannon lights a cloaked
            // attacker up for the whole base a moment before it can shoot at it itself.
            .detector(16.0, 20, 60)
            .cost(UnitCost.NONE) // never trained, so no buildTicks either
            .build();

    // --- Zerg ---

    /**
     * Transcribed from {@code UnitStats.PROBE} verbatim, including {@code shield(10)} — a Drone is never
     * Protoss today, so {@code ShieldAttachments}' faction gate zeroes it in play regardless, but
     * this pass is behaviour-preserving. Follow-up: drop to 0 once a Drone truly can't be Protoss.
     */
    public static final UnitStat DRONE = UnitStat.builder("drone")
            .health(10.0).shield(10).speed(0.35).followRange(48.0)
            .cost(UnitCost.of(ANY, 50)).buildTicks(20)
            .build();

    public static final UnitStat ZERGLING = UnitStat.builder("zergling")
            .health(17.5).armor(0.0).speed(0.30)
            .attackDamage(2.5)
            .cost(UnitCost.of(ANY, 25)).buildTicks(20)
            .build();

    public static final UnitStat HYDRALISK = UnitStat.builder("hydralisk")
            .health(40.0).armor(0.0).speed(0.25)
            .attackDamage(5.0).ranged(6.0f, 20).attackAnimTicks(10)
            .cost(UnitCost.of(ANY, 100)).buildTicks(20)
            .build();

    public static final UnitStat MUTALISK = UnitStat.builder("mutalisk")
            .health(60.0).armor(0.0).speed(0.25)
            .attackDamage(4.5).ranged(9.0f, 30)
            .bounce(3, 0.5f, 5.0f)
            .flight(0.6, 6, 64.0f)
            .cost(UnitCost.of(ANY, 100)).buildTicks(20)
            .build();

    /**
     * The swarm's eye in the sky, and the mod's first <em>mobile</em> detector: everything that
     * detects before it — the Photon Cannon, the Spore Colony — is rooted, so detection could only
     * ever cover ground an army had already built on. An Overlord carries the same 16-block bubble
     * wherever it drifts, which is what lets the swarm push into a Dark Templar rather than wait for
     * one to wander into a colony.
     *
     * <p>It has no attack at all — the only unit besides the two workers that doesn't, which is the
     * rule {@code UnitStatsTest.onlyNonCombatUnitsLackAttackDamage} states. That is what it pays for
     * its detection with: it is slow, huge and entirely dependent on an escort.
     */
    public static final UnitStat OVERLORD = UnitStat.builder("overlord")
            .health(100.0).armor(0.0).speed(0.15)
            // Slower and higher than the Mutalisk's 0.6/6: a drifting sac, not a raider. The extra
            // block of altitude is what keeps it above the fight it is spotting for.
            .flight(0.35, 7, 64.0f)
            // The same envelope as the mod's other two detectors, so a race's mobile detector and
            // its rooted one see equally far and a player learns one radius, not two.
            .detector(16.0, 20, 60)
            .cost(UnitCost.of(ANY, 100)).buildTicks(20 * 40)
            .build();

    /**
     * The Zerg heavy: the only unit on either side that a Zealot ball can't simply out-trade, and the
     * reason to keep Dragoons or a Cannon around. Its minute-long build time is what keeps it rare —
     * a wave carrying one takes a minute longer to assemble than the same wave without.
     *
     * <p>It reuses the Zergling's geometry, scaled up — broad and long far more than tall, and more
     * than its hitbox is: see its registration in {@code AsteriskCraft} for why the height stays
     * under 2.0, and {@code client.zerg.UltraliskRenderer} for the silhouette that sits over it.
     */
    public static final UnitStat ULTRALISK = UnitStat.builder("ultralisk")
            .health(200.0).armor(1.0).speed(0.28)
            // Steps over a full block instead of jumping it. Two pathfinding nodes wide, it stops
            // 0.6 short of a ledge, and MoveControl only fires a jump inside sqrt(width) of the
            // waypoint — so on broken ground it would stall against every rise. See UnitStat.
            .stepHeight(1.1)
            .attackDamage(10.0).attackAnimTicks(12)
            .cost(UnitCost.of(ANY, 200)).buildTicks(20 * 60)
            .build();

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
    public static final UnitStat LURKER = UnitStat.builder("lurker")
            .health(63.0).armor(1.0).speed(0.25)
            .attackDamage(10.0).ranged(6.0f, 40).attackAnimTicks(12)
            .cost(UnitCost.of(ANY, 100)).buildTicks(20 * 40)
            .build();

    /** Pre-placed by {@code GameBootstrap}, not trained — hence NONE. */
    public static final UnitStat SUNKEN_COLONY = UnitStat.builder("sunken_colony")
            .health(150.0).armor(0.0).rooted()
            .attackDamage(20.0).ranged(11.0f, 32).attackAnimTicks(12)
            .followRange(11.0) // == range: a rooted attacker can't close the gap
            .cost(UnitCost.NONE) // never trained, so no buildTicks either
            .build();

    /**
     * The Zerg answer to the air, and the only thing on either side that fires <em>exclusively</em> at
     * it: acquisition is narrowed by {@code entity.Altitude#isAirborne} rather than by unit type, so
     * what it may shoot is decided by where a target is, not what it is. Pre-placed beside every Hive
     * like the Sunken, hence NONE.
     *
     * <p>Tuned as a wall rather than a gun: the toughest unit in the mod at 200 HP (a rooted structure
     * that cannot answer a ground army at all has to survive being ignored), but only 7.5 a shot on a
     * one-second cadence — 7.5 DPS, less than half the Sunken's, because a flyer it out-ranges cannot
     * shoot back at all and the exchange should still take a moment.
     */
    public static final UnitStat SPORE_COLONY = UnitStat.builder("spore_colony")
            .health(200.0).armor(0.0).rooted()
            .attackDamage(7.5).ranged(14.0f, 20).attackAnimTicks(10)
            .followRange(14.0) // == range: a rooted attacker can't close the gap
            // The Zerg detector, on the shared envelope — a Hive perimeter now lights up
            // cloaked attackers from both of its rooted defences.
            .detector(16.0, 20, 60)
            .cost(UnitCost.NONE) // never trained, so no buildTicks either
            .build();

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
    public static final UnitStat INFESTED_VILLAGER = UnitStat.builder("infested_villager")
            .health(30.0).armor(0.0).speed(0.32)
            .attackDamage(250.0).blast(3.0f, 30)
            .cost(UnitCost.NONE) // never trained, so no buildTicks either
            .build();

    // --- Terran ---

    /**
     * The SCV. Numerically the Probe with its shields taken away — the Terran carry none — which
     * leaves it the cheapest thing in the mod that can still be killed by a single Zergling.
     * Deliberately priced identically to the other two workers: what a race differs in is what it
     * builds with its economy, not what the economy itself costs to staff.
     */
    /**
     * The one worker in the mod that is armed. It never goes looking for a fight — see
     * {@code entity.terran.ScvEntity} for the retaliate-only goal set — but a Terran economy that
     * can answer a stray Zergling is a real difference between the races, not a rounding error, and
     * it is paid for in the build time: an SCV takes twice as long to come out as a Probe.
     */
    public static final UnitStat SCV = UnitStat.builder("scv")
            .health(30.0).shield(0).armor(0.0).speed(0.35).followRange(48.0)
            .attackDamage(3.0)
            .cost(UnitCost.either(List.of(line(WOOD, 50)), List.of(line(STONE, 50)))).buildTicks(400)
            .build();

    /** The Protoss roster, for balance grouping and the "Protoss stays picky" cost invariant. */
    public static final List<UnitStat> PROTOSS_ROSTER =
            List.of(PROBE, ZEALOT, DRAGOON, SCOUT, DARK_TEMPLAR, PHOTON_CANNON);

    /** The Zerg roster, for balance grouping and the "Zerg pays in any item" cost invariant. */
    public static final List<UnitStat> ZERG_ROSTER =
            List.of(DRONE, ZERGLING, HYDRALISK, MUTALISK, OVERLORD, ULTRALISK, LURKER, SUNKEN_COLONY,
                    SPORE_COLONY, INFESTED_VILLAGER);

    /**
     * The Terran roster. One entry for now — the race ships with an economy and no army — and it
     * holds the same "names a real resource, never ANY" invariant the Protoss one does.
     */
    public static final List<UnitStat> TERRAN_ROSTER = List.of(SCV);

    private UnitStats() {
    }

    /** The whole roster, for invariant tests and a future datapack override layer. */
    public static List<UnitStat> all() {
        return Stream.of(PROTOSS_ROSTER, ZERG_ROSTER, TERRAN_ROSTER).flatMap(List::stream).toList();
    }
}
