package net.bitflora.asteriskcraft.building;

import net.minecraft.world.entity.Entity;

/**
 * A building-as-entity that counts down while it stands itself up, and so has to be told when
 * nobody actually built it.
 *
 * <p>Every such building starts on {@link WarpInVulnerability}'s halved pool and finishes on the
 * full one, which is right for something a player paid for and placed, and wrong for one world
 * generation stamped beside a base: that one was never built by anybody, and left alone it would
 * open the match half-built, at half health, and — for the two that care — refusing to do its job
 * for the first thirty seconds of the game. {@code BaseBlockEntity.skipWarpIn} is the same idea for
 * the buildings that are blocks; this is it for the ones that are entities.
 *
 * <p>It exists because there is now more than one of them and they share no supertype but
 * {@code Mob}: {@code entity.terran.BunkerEntity} refuses passengers until it is finished and
 * {@code entity.terran.MissileTurretEntity} refuses to fire. {@code game.GameBootstrap} stands up
 * whatever a race's {@code baseDefences} handed it through {@link #standUp}, so a race that later
 * declares a third such building needs no edit there.
 */
public interface PrePlaced {
    /** Finishes the countdown at once and scales the halved pool back up. Idempotent. */
    void skipConstruction();

    /** Stands {@code entity} up if it is one of these, and does nothing if it is not. */
    static void standUp(Entity entity) {
        if (entity instanceof PrePlaced building) {
            building.skipConstruction();
        }
    }
}
