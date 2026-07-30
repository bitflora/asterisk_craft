package net.bitflora.asteriskcraft.director.script;

import net.bitflora.asteriskcraft.AsteriskCraft;
import net.bitflora.asteriskcraft.stats.UnitCost;
import net.bitflora.asteriskcraft.stats.UnitStats;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;

import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * The script-facing view of the Zerg roster: which units a build script can name, and how they are
 * produced ({@code EntityType}, worker/dye-armor flags). The numbers themselves — cost included —
 * come from {@link net.bitflora.asteriskcraft.stats.UnitStats}, the single source of truth for unit
 * stats; this class only wires that table up to a script-resolvable name.
 *
 * <p>Names resolve case-insensitively, and a unit's script name is exactly its
 * {@link net.bitflora.asteriskcraft.stats.UnitStat#id()}.
 */
public final class ZergUnitCatalog {

    /**
     * A producible unit: its entity type, its cost (from {@code UnitStats}), whether it is a worker
     * (Drone), and whether it gets team-colour armour on spawn (workers don't).
     */
    public record UnitDef(EntityType<? extends Mob> type, UnitCost cost, boolean isWorker, boolean dyeArmor) {
    }

    /** Lazily built after entity-type registration completes; keys are lower-cased. */
    private static volatile Map<String, UnitDef> byName;

    private ZergUnitCatalog() {
    }

    /** Resolves a script unit name (any case) to its definition, or empty if unknown. */
    public static Optional<UnitDef> resolve(String scriptName) {
        return Optional.ofNullable(catalog().get(scriptName.trim().toLowerCase(Locale.ROOT)));
    }

    /** Convenience for the worker-maintenance path: the Drone definition. */
    public static UnitDef drone() {
        return catalog().get(UnitStats.DRONE.id());
    }

    private static Map<String, UnitDef> catalog() {
        Map<String, UnitDef> local = byName;
        if (local == null) {
            local = build();
            byName = local;
        }
        return local;
    }

    private static Map<String, UnitDef> build() {
        UnitDef drone = new UnitDef(AsteriskCraft.DRONE.get(), UnitStats.DRONE.cost(), true, false);
        UnitDef zergling = new UnitDef(AsteriskCraft.ZERGLING.get(), UnitStats.ZERGLING.cost(), false, false);
        UnitDef hydralisk = new UnitDef(AsteriskCraft.HYDRALISK.get(), UnitStats.HYDRALISK.cost(), false, false);
        UnitDef mutalisk = new UnitDef(AsteriskCraft.MUTALISK.get(), UnitStats.MUTALISK.cost(), false, false);
        return Map.of(
                UnitStats.DRONE.id(), drone,
                UnitStats.ZERGLING.id(), zergling,
                UnitStats.HYDRALISK.id(), hydralisk,
                UnitStats.MUTALISK.id(), mutalisk);
    }
}
