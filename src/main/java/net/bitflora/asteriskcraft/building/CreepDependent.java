package net.bitflora.asteriskcraft.building;

import net.bitflora.asteriskcraft.faction.Race;
import org.jetbrains.annotations.Nullable;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

/**
 * An item that places a building the creep prerequisite applies to — the Zerg sibling of
 * {@link PsiDependent}, mirrored deliberately: which buildings are exempt is a flag on the placing
 * item rather than a case inside {@link CreepField}, and there are the same two unrelated
 * implementors, {@link BuildingKitItem} and {@code entity/FactionSpawnEggItem}.
 *
 * <p>Its one client-side user is {@code client/CreepFieldOverlay}.
 */
public interface CreepDependent {
    /** Whether this item's building may only be placed within {@link CreepField#RADIUS} of creep. */
    boolean requiresCreep();

    /**
     * The race whose building is going down — the placer's own race for a kit, or the race of the
     * side the egg's {@code Side} resolves to. {@link CreepField} doesn't filter creep sources by
     * ownership (creep is shared territory, not army-specific), so this only decides whose ground
     * block counts as creep for the on-creep clause; the two call sites already resolve it
     * differently, and the overlay has to ask the same question the server is about to, so this is
     * what lets it without naming either item class.
     *
     * <p>Null for a player who has no army yet, which {@link CreepField} reads as "no ground counts
     * as creep" and so leaves the range clause to answer on its own.
     */
    @Nullable Race placingRace(Level level, Player player);
}
