package net.bitflora.asteriskcraft.entity;

import net.minecraft.world.entity.Entity;

/**
 * Marks a unit that fights from the air (the Scout and the Mutalisk): it hovers at a fixed altitude,
 * melee ground units cannot reach it, and an anti-air attack gets its bonus against it.
 *
 * <p>Deliberately faction-generic and deliberately a marker, exactly like {@link Shielded}: air-vs-ground
 * is a property of the unit, not of a side, and combat code asks {@link #isAir} rather than naming a
 * concrete entity class. It is <em>not</em> "is currently off the ground" — a Zealot knocked into the
 * air is still a ground unit, and a Scout parked on the floor is still air.
 */
public interface Flyer {
    /** Whether {@code entity} counts as an air target for anti-air bonuses. */
    static boolean isAir(Entity entity) {
        return entity instanceof Flyer;
    }
}
