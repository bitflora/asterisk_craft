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

    /** A candidate is fair game only if it's alive and belongs to an enemy faction of the cannon. */
    public static boolean isTargetable(Faction cannon, Faction target, boolean alive) {
        return alive && cannon.isEnemy(target);
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
