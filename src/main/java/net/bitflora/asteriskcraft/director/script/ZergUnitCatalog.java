package net.bitflora.asteriskcraft.director.script;

import net.bitflora.asteriskcraft.AsteriskCraft;
import net.bitflora.asteriskcraft.building.ResourceBank;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;

/**
 * The single source of truth for which units a Zerg build script can name, what they cost, and how
 * they are produced. Centralises the unit costs that used to be scattered as constants across
 * {@code ZergDirector} and {@code HiveBlockEntity}.
 *
 * <p>A unit's {@code costAlternatives} is a list of interchangeable cost bundles: paying any one
 * of them produces the unit. Zerg costs accept any item in the shared bank (unlike Protoss, which
 * is picky about wood/cobblestone) — a single bundle matching any stack. Names resolve
 * case-insensitively.
 */
public final class ZergUnitCatalog {
    public static final int DRONE_COST = 50;
    public static final int ZERGLING_COST = 25;
    public static final int HYDRALISK_COST = 100;
    public static final int MUTALISK_COST = 100;

    /**
     * A producible unit: its entity type, the interchangeable cost bundles (pay any one), whether
     * it is a worker (Drone), and whether it gets team-colour armour on spawn (workers don't).
     */
    public record UnitDef(EntityType<? extends Mob> type,
                          List<List<ResourceBank.Cost>> costAlternatives,
                          boolean isWorker,
                          boolean dyeArmor) {
    }

    private static final Predicate<ItemStack> ANY_ITEM = stack -> true;

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
        return catalog().get("drone");
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
        UnitDef drone = new UnitDef(AsteriskCraft.DRONE.get(),
                List.of(List.of(new ResourceBank.Cost(ANY_ITEM, DRONE_COST))), true, false);
        UnitDef zergling = new UnitDef(AsteriskCraft.ZERGLING.get(),
                List.of(List.of(new ResourceBank.Cost(ANY_ITEM, ZERGLING_COST))), false, false);
        UnitDef hydralisk = new UnitDef(AsteriskCraft.HYDRALISK.get(),
                List.of(List.of(new ResourceBank.Cost(ANY_ITEM, HYDRALISK_COST))), false, false);
        UnitDef mutalisk = new UnitDef(AsteriskCraft.MUTALISK.get(),
                List.of(List.of(new ResourceBank.Cost(ANY_ITEM, MUTALISK_COST))), false, false);
        return Map.of("drone", drone, "zergling", zergling, "hydralisk", hydralisk, "mutalisk", mutalisk);
    }
}
