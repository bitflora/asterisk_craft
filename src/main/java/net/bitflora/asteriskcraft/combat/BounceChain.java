package net.bitflora.asteriskcraft.combat;

import java.util.ArrayList;
import java.util.List;
import java.util.function.ToDoubleBiFunction;

/**
 * Pure resolution logic for a chaining ("bouncing glave") attack, deliberately free of any live
 * {@code Level}/{@code Entity} dependency so it's unit-testable in the JUnit bootstrap — see
 * {@code building.PhotonCannonTargeting} for the same pattern. The Mutalisk's
 * {@code entity.ai.HitscanAttacks#fireChained} feeds it a faction-filtered candidate list; the
 * world scan itself stays there.
 */
public final class BounceChain {
    private BounceChain() {
    }

    /** Damage for hit {@code hitIndex} (0-based, 0 = the primary target): compounding falloff. */
    public static float damageAt(double baseDamage, float damageFalloff, int hitIndex) {
        return (float) (baseDamage * Math.pow(damageFalloff, hitIndex));
    }

    /**
     * The ordered hit list: {@code primary} first, then repeatedly the nearest not-yet-hit
     * candidate within {@code searchRadiusSq} of the target just hit (not the attacker) — so a
     * glave can walk down a line of enemies past its own attack range — up to {@code maxHits}
     * total. Stops early if no candidate is left in range. Never repeats a target.
     */
    public static <T> List<T> resolve(T primary, List<T> candidates, int maxHits,
            double searchRadiusSq, ToDoubleBiFunction<T, T> distanceSq) {
        List<T> hits = new ArrayList<>(Math.max(1, maxHits));
        hits.add(primary);
        List<T> remaining = new ArrayList<>(candidates);
        remaining.remove(primary);

        T last = primary;
        while (hits.size() < maxHits) {
            T nearest = null;
            double nearestDist = Double.MAX_VALUE;
            for (T candidate : remaining) {
                double dist = distanceSq.applyAsDouble(last, candidate);
                if (dist <= searchRadiusSq && dist < nearestDist) {
                    nearest = candidate;
                    nearestDist = dist;
                }
            }
            if (nearest == null) {
                break;
            }
            hits.add(nearest);
            remaining.remove(nearest);
            last = nearest;
        }
        return hits;
    }
}
