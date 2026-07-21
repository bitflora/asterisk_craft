package net.bitflora.asteriskcraft.director.script;

import net.bitflora.asteriskcraft.AsteriskCraft;
import net.bitflora.asteriskcraft.building.ResourceBank;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.Items;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * The single source of truth for which units a Zerg build script can name, what they cost, and how
 * they are produced. Centralises the unit costs that used to be scattered as constants across
 * {@code ZergDirector} and {@code HiveBlockEntity}.
 *
 * <p>A unit's {@code costAlternatives} is a list of interchangeable cost bundles: paying any one
 * of them produces the unit. Combat units have a single bundle (e.g. wood <b>and</b> cobble); the
 * Drone has two (wood <b>or</b> cobble). Names resolve case-insensitively.
 */
public final class ZergUnitCatalog {
    // Amounts mirror the current live values (which in turn mirror the Protoss economy for the
    // shared resources; the Hydralisk is the ranged unit and costs iron).
    public static final int DRONE_COST = 50;               // wood OR cobble
    public static final int ZERGLING_WOOD_COST = 50;
    public static final int ZERGLING_COBBLE_COST = 50;
    public static final int HYDRALISK_IRON_COST = 10;

    /**
     * A producible unit: its entity type, the interchangeable cost bundles (pay any one), whether
     * it is a worker (Drone), and whether it gets team-colour armour on spawn (workers don't).
     */
    public record UnitDef(EntityType<? extends Mob> type,
                          List<List<ResourceBank.Cost>> costAlternatives,
                          boolean isWorker,
                          boolean dyeArmor) {
    }

    private static final ResourceBank.Cost WOOD = new ResourceBank.Cost(stack -> stack.is(ItemTags.LOGS), DRONE_COST);
    private static final ResourceBank.Cost COBBLE = new ResourceBank.Cost(stack -> stack.is(Items.COBBLESTONE), DRONE_COST);

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
                List.of(List.of(WOOD), List.of(COBBLE)), true, false);
        UnitDef zergling = new UnitDef(AsteriskCraft.ZERGLING.get(),
                List.of(List.of(
                        new ResourceBank.Cost(stack -> stack.is(ItemTags.LOGS), ZERGLING_WOOD_COST),
                        new ResourceBank.Cost(stack -> stack.is(Items.COBBLESTONE), ZERGLING_COBBLE_COST))),
                false, false);
        UnitDef hydralisk = new UnitDef(AsteriskCraft.HYDRALISK.get(),
                List.of(List.of(new ResourceBank.Cost(stack -> stack.is(Items.IRON_INGOT), HYDRALISK_IRON_COST))),
                false, false);
        return Map.of("drone", drone, "zergling", zergling, "hydralisk", hydralisk);
    }
}
