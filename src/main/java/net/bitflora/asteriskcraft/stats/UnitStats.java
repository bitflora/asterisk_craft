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
 * entry, and {@code building/ProductionKind} / {@code director/script/ZergUnitCatalog} for how the
 * costs reach the GUI and the enemy build script.
 */
public final class UnitStats {

    // --- Protoss ---

    public static final UnitStat PROBE = UnitStat.builder("probe")
            .health(10.0).shield(10).speed(0.35).followRange(48.0)
            .cost(UnitCost.either(List.of(line(WOOD, 50)), List.of(line(STONE, 50))))
            .build();

    public static final UnitStat ZEALOT = UnitStat.builder("zealot")
            .health(50.0).shield(30).armor(0.5).speed(0.25)
            .attackDamage(4.0).attackAnimTicks(10)
            .cost(UnitCost.all(line(WOOD, 50), line(STONE, 50)))
            .build();

    public static final UnitStat DRAGOON = UnitStat.builder("dragoon")
            .health(50.0).shield(40).armor(0.5).speed(0.25)
            .attackDamage(10.0).ranged(8.0f, 40)
            .cost(UnitCost.all(line(WOOD, 100), line(STONE, 50)))
            .build();

    public static final UnitStat SCOUT = UnitStat.builder("scout")
            .health(75.0).shield(50).armor(0.5).speed(0.25)
            .attackDamage(11.0).ranged(9.0f, 30)
            .flight(0.6, 6, 64.0f)
            .cost(UnitCost.all(line(STONE, 150), line(IRON, 20)))
            .build();

    /** Kit-bought at the Nexus for {@code NexusBlockEntity.BUILDING_COST}, not trained directly — hence NONE. */
    public static final UnitStat PHOTON_CANNON = UnitStat.builder("photon_cannon")
            .health(50.0).shield(50).rooted()
            .attackDamage(10.0).ranged(14.0f, 20)
            .followRange(14.0) // == range: never acquire what it can't shoot
            .cost(UnitCost.NONE)
            .build();

    // --- Zerg ---

    /**
     * Transcribed from {@code ProbeEntity} verbatim, including {@code shield(10)} — a Drone is never
     * Protoss today, so {@code ShieldAttachments}' faction gate zeroes it in play regardless, but
     * this pass is behaviour-preserving. Follow-up: drop to 0 once a Drone truly can't be Protoss.
     */
    public static final UnitStat DRONE = UnitStat.builder("drone")
            .health(10.0).shield(10).speed(0.35).followRange(48.0)
            .cost(UnitCost.of(ANY, 50))
            .build();

    public static final UnitStat ZERGLING = UnitStat.builder("zergling")
            .health(17.5).armor(0.0).speed(0.30)
            .attackDamage(2.5)
            .cost(UnitCost.of(ANY, 25))
            .build();

    public static final UnitStat HYDRALISK = UnitStat.builder("hydralisk")
            .health(40.0).armor(0.0).speed(0.25)
            .attackDamage(5.0).ranged(6.0f, 20).attackAnimTicks(10)
            .cost(UnitCost.of(ANY, 100))
            .build();

    public static final UnitStat MUTALISK = UnitStat.builder("mutalisk")
            .health(60.0).armor(0.0).speed(0.25)
            .attackDamage(4.5).ranged(9.0f, 30)
            .bounce(3, 0.5f, 5.0f)
            .flight(0.6, 6, 64.0f)
            .cost(UnitCost.of(ANY, 100))
            .build();

    /** Pre-placed by {@code GameBootstrap}, not trained — hence NONE. */
    public static final UnitStat SUNKEN_COLONY = UnitStat.builder("sunken_colony")
            .health(150.0).armor(0.0).rooted()
            .attackDamage(20.0).ranged(11.0f, 32).attackAnimTicks(12)
            .followRange(11.0) // == range: a rooted attacker can't close the gap
            .cost(UnitCost.NONE)
            .build();

    /** The Protoss roster, for balance grouping and the "Protoss stays picky" cost invariant. */
    public static final List<UnitStat> PROTOSS_ROSTER =
            List.of(PROBE, ZEALOT, DRAGOON, SCOUT, PHOTON_CANNON);

    /** The Zerg roster, for balance grouping and the "Zerg pays in any item" cost invariant. */
    public static final List<UnitStat> ZERG_ROSTER =
            List.of(DRONE, ZERGLING, HYDRALISK, MUTALISK, SUNKEN_COLONY);

    private UnitStats() {
    }

    /** The whole roster, for invariant tests and a future datapack override layer. */
    public static List<UnitStat> all() {
        return Stream.concat(PROTOSS_ROSTER.stream(), ZERG_ROSTER.stream()).toList();
    }
}
