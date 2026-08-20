package net.bitflora.asteriskcraft.entity;

import net.bitflora.asteriskcraft.faction.Cloaked;
import net.bitflora.asteriskcraft.stats.UnitStat;
import net.minecraft.world.entity.Entity;

/**
 * Marks a unit that reveals {@link Cloaked} enemies around it, so its own faction can engage them.
 *
 * <p>The counterpart to {@code Cloaked}, and the same kind of contract as {@link Shielded}: a marker
 * that also names the one number the mechanic needs, pulled from the balance table rather than
 * declared here. Faction-generic — detection is a property of the unit, and
 * {@code combat.DetectionHandler} sweeps for whichever faction the detector happens to belong to.
 */
public interface Detector {
    /** This detector's envelope, straight off its {@code UnitStats} entry. */
    UnitStat.Detection detection();

    static boolean isDetector(Entity entity) {
        return entity instanceof Detector;
    }
}
