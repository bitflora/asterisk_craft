package net.bitflora.asteriskcraft.building;

import net.bitflora.asteriskcraft.faction.Faction;

import java.util.List;
import java.util.Optional;
import java.util.function.ToDoubleFunction;

/**
 * Pure target-selection logic for the Photon Cannon, deliberately free of any live
 * {@code Level}/{@code Entity} dependency so it's unit-testable in the JUnit bootstrap
 * (which can't bind item components/tags or easily construct entities). The block entity
 * feeds it faction data + a distance function; the world scan itself stays in the BE.
 */
public final class PhotonCannonTargeting {
    private PhotonCannonTargeting() {
    }

    /**
     * A candidate is fair game if it's alive and either belongs to an enemy faction of the
     * cannon, or is a vanilla hostile monster (zombies, creepers, etc.) — those default to
     * {@link Faction#NEUTRAL} but the cannon should still defend the base against them. The
     * monster fallback is gated on the target actually being {@code NEUTRAL} (not just a Java
     * {@code Monster} subclass), since faction-tagged combat units (Zealot, Zergling, Dragoon,
     * Hydralisk) are themselves {@code Monster} subclasses and must never be caught by it.
     */
    public static boolean isTargetable(Faction cannon, Faction target, boolean alive, boolean isMonster) {
        return alive && (cannon.isEnemy(target) || (target == Faction.NEUTRAL && isMonster));
    }

    /** Returns the candidate with the smallest distance (empty if the list is empty). */
    public static <T> Optional<T> nearest(List<T> candidates, ToDoubleFunction<T> distanceSq) {
        T best = null;
        double bestDist = Double.MAX_VALUE;
        for (T candidate : candidates) {
            double dist = distanceSq.applyAsDouble(candidate);
            if (dist < bestDist) {
                bestDist = dist;
                best = candidate;
            }
        }
        return Optional.ofNullable(best);
    }
}
