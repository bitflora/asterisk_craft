package net.bitflora.asteriskcraft.entity;

import net.minecraft.world.entity.Entity;

/**
 * Marks a unit that cannot step out of the way: the static defences (Photon Cannon, Sunken Colony,
 * Spore Colony), which never move at all, and the Lurker, which cannot while it is burrowed.
 * The static defences carry no movement goals, zero movement speed and full knockback resistance —
 * all of which comes from {@code stats.UnitStat.Builder#rooted()}, not from here. The Lurker holds
 * itself still for the duration instead, since being immobile is a state it enters rather than a
 * property of the unit; nothing here cares which way a unit arrives at "can't move".
 *
 * <p>Deliberately faction-generic and deliberately a marker, exactly like {@link Flyer} and
 * {@link Shielded}. It exists because "can this thing step aside?" is a question other code genuinely
 * has to ask: {@code building.SpawnSpots} treats a spot occupied by a rooted unit as taken, since two
 * mobile units spawned on the same block shove each other apart within a tick while two rooted ones
 * stand inside each other forever.
 */
public interface Rooted {
    /**
     * Whether this unit is unable to step aside <em>right now</em>. Defaults to always, which is
     * what the three static defences want and why they opt in with a bare {@code implements}.
     *
     * <p>A method rather than a bare {@code instanceof} because a burrowed Lurker is rooted and a
     * surfaced one is not, and {@code building.SpawnSpots} has to get that right: Lurkers are
     * produced at a Hive and dig in where they stand, so a spot held by a buried one must count as
     * taken exactly the way a Sunken Colony's does.
     */
    default boolean isRootedNow() {
        return true;
    }

    /** Whether {@code entity} is a unit that cannot move out of the way at this moment. */
    static boolean isRooted(Entity entity) {
        return entity instanceof Rooted rooted && rooted.isRootedNow();
    }
}
