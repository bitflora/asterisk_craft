package net.bitflora.asteriskcraft.building;

import net.bitflora.asteriskcraft.faction.Faction;
import net.bitflora.asteriskcraft.faction.FactionAttachments;

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
     * A candidate is fair game if it's alive and hostile to the cannon. Hostility itself is the
     * shared rule in {@link FactionAttachments#isHostile(Faction, Faction, boolean)} — including the
     * wild-hostile carve-out, which started here but is what <em>every</em> combat unit needs, so it
     * lives with the faction code now rather than being the cannon's private policy. This method
     * remains as the cannon's "alive and targetable" wrapper.
     *
     * @param wildHostile whether the candidate declares itself hostile via vanilla's {@code Enemy}
     *                    marker — the caller's {@code instanceof}, kept out of this pure rule.
     */
    public static boolean isTargetable(Faction cannon, Faction target, boolean alive, boolean wildHostile) {
        return alive && FactionAttachments.isHostile(cannon, target, wildHostile);
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
