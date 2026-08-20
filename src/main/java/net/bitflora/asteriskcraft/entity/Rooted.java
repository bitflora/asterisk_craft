package net.bitflora.asteriskcraft.entity;

import net.minecraft.world.entity.Entity;

/**
 * Marks a unit that never moves: the static defences (Photon Cannon, Sunken Colony, Spore Colony).
 * They carry no movement goals, zero movement speed and full knockback resistance — all of which
 * comes from {@code stats.UnitStat.Builder#rooted()}, not from here.
 *
 * <p>Deliberately faction-generic and deliberately a marker, exactly like {@link Flyer} and
 * {@link Shielded}. It exists because "can this thing step aside?" is a question other code genuinely
 * has to ask: {@code building.SpawnSpots} treats a spot occupied by a rooted unit as taken, since two
 * mobile units spawned on the same block shove each other apart within a tick while two rooted ones
 * stand inside each other forever.
 */
public interface Rooted {
    /** Whether {@code entity} is a unit that can never move out of the way. */
    static boolean isRooted(Entity entity) {
        return entity instanceof Rooted;
    }
}
