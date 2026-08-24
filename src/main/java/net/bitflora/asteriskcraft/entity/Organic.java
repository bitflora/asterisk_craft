package net.bitflora.asteriskcraft.entity;

import net.minecraft.world.entity.Entity;

/**
 * Marks a unit made of flesh rather than machinery — the Terran infantry a Bunker can hold. Today
 * that is the Marine and the SCV; a Firebat or a Ghost joins them with a bare {@code implements}
 * and no change to the rule that consults it.
 *
 * <p>Deliberately faction-generic and deliberately a marker, exactly like {@link Rooted},
 * {@link Flyer} and {@link CreepSource}. It is what lets {@code entity.terran.BunkerEntity}'s
 * boarding rule ask a <em>capability</em> question — "is this something that can climb inside?" —
 * rather than naming a race or a unit class, the same way {@code Detector} and {@code CreepSource}
 * each carry a race fact without any race check existing anywhere.
 *
 * <p>It is not the same question as {@link Rooted}'s negation, and must not be conflated with it: a
 * Photon Cannon can't move and isn't organic, while a burrowed Lurker is rooted and (were it
 * Terran) would still be flesh. Nor is it "not a {@link Flyer}" — an unarmoured flyer would still
 * be organic, it simply has no business inside a bunker, which is why the boarding rule tests
 * both.
 */
public interface Organic {
    /** Whether {@code entity} is a flesh-and-blood unit. */
    static boolean isOrganic(Entity entity) {
        return entity instanceof Organic;
    }
}
