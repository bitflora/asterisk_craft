package net.bitflora.asteriskcraft.faction;

import net.minecraft.world.entity.Entity;

/**
 * Marks a unit that fights cloaked: an enemy faction cannot acquire, engage or even retaliate
 * against it unless one of that faction's detectors currently reveals it (see {@link Cloaking}).
 *
 * <p>A marker with no numbers on it, exactly like {@code entity.Flyer} and {@code entity.Shielded} —
 * "is this unit cloaked" is a property of the unit, not of a side, and combat code asks
 * {@link #isCloaked} rather than naming a concrete entity class. The detector half of the pair
 * <em>does</em> carry numbers and so lives in {@code entity.Detector} with its
 * {@code UnitStat.Detection} envelope.
 *
 * <p><b>Why this one marker sits in {@code faction} rather than beside its siblings in
 * {@code entity}:</b> the cloak gate has to be inside
 * {@link FactionAttachments#isHostile(Entity, Entity)} — that is the single choke point every
 * targeting path already goes through, and a gate anywhere else would be one someone could forget
 * to call. But {@code faction} is deliberately a leaf package (nothing in it imports another of the
 * mod's packages), which is what keeps that choke point free of layering cycles. Putting the marker
 * here keeps it that way. Nothing about cloaking needs the {@code entity} package.
 */
public interface Cloaked {
    /** Whether {@code entity} fights cloaked. */
    static boolean isCloaked(Entity entity) {
        return entity instanceof Cloaked;
    }
}
