package net.bitflora.asteriskcraft.combat;

import net.minecraft.util.RandomSource;

import java.util.List;
import java.util.Optional;

/**
 * Which way a support unit's periodic pulse goes: onto an enemy if it can see one, and onto a
 * friend otherwise. The whole of {@code entity.terran.ScienceVesselEntity}'s ability rule, pulled
 * out here for the reason {@code building.ConstructionSite#decide} and {@code game.GameOutcome}
 * are — it is a decision, and a decision is worth having somewhere a test can reach without a live
 * {@code ServerLevel}.
 *
 * <p><b>Deliberately generic over the candidate type</b>, so nothing in here touches an entity, a
 * registry or a {@code MobEffect}. Finding the candidates, resolving hostility and actually
 * applying the effect are the caller's — those need a live world and cannot be pure. What is left
 * is the part that could silently regress: enemies take priority over friends, and a pulse with
 * nobody to spend itself on is not spent.
 *
 * <p>The asymmetry between the two lists is the rule, not an accident. A hostile is picked by
 * <em>order</em> — the caller passes them nearest-first, because the enemy standing on top of you
 * is the one worth poisoning — while a friend is picked at <em>random</em>, so a Vessel parked in a
 * standing army spreads its buff around rather than re-covering whichever ally happens to sort
 * first for the whole match.
 */
public final class SupportPulse {

    /** Which of the two things a pulse can be. */
    public enum Effect {
        /** The offensive half: something hostile was in range. */
        IRRADIATE,
        /** The defensive half: nothing hostile was in range, so a friend gets covered instead. */
        MATRIX
    }

    /** A pulse that is going to happen: who it lands on, and which way it went. */
    public record Choice<T>(T target, Effect effect) {
    }

    private SupportPulse() {
    }

    /**
     * Picks this pulse's target, or empty if there is nobody at all to spend it on.
     *
     * @param hostiles candidates the pulse would irradiate, <b>nearest first</b> — the head is
     *                 taken, so the caller owns the ordering
     * @param friends  candidates the pulse would cover, in any order; one is drawn at random. The
     *                 caller must have excluded itself already: a Vessel does not buff itself, and
     *                 that is enforced by never handing it to this method rather than by a check in
     *                 here, which could not tell one {@code T} from another.
     */
    public static <T> Optional<Choice<T>> choose(List<T> hostiles, List<T> friends, RandomSource random) {
        if (!hostiles.isEmpty()) {
            return Optional.of(new Choice<>(hostiles.getFirst(), Effect.IRRADIATE));
        }
        if (!friends.isEmpty()) {
            return Optional.of(new Choice<>(friends.get(random.nextInt(friends.size())), Effect.MATRIX));
        }
        return Optional.empty();
    }
}
