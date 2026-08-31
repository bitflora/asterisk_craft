package net.bitflora.asteriskcraft.combat;

import net.minecraft.util.RandomSource;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins {@code combat.SupportPulse}'s choice. The rule is deliberately generic over its candidate
 * type, so these run on plain strings with no level, no registries and no entities — the world scan
 * that produces the two lists lives in {@code entity.terran.ScienceVesselEntity} and is verified in
 * game, the way every other targeting rule in the mod is.
 */
class SupportPulseTest {

    @Test
    void anEnemyInRangeIsIrradiatedRatherThanAnAllyCovered() {
        Optional<SupportPulse.Choice<String>> choice =
                SupportPulse.choose(List.of("enemy"), List.of("friend"), RandomSource.create(1L));

        assertTrue(choice.isPresent());
        assertEquals(SupportPulse.Effect.IRRADIATE, choice.get().effect());
        assertEquals("enemy", choice.get().target());
    }

    @Test
    void theFirstHostileIsTaken() {
        // The caller passes hostiles nearest-first, so "which enemy" is a question about ordering
        // and this is what makes that contract real.
        Optional<SupportPulse.Choice<String>> choice =
                SupportPulse.choose(List.of("near", "far"), List.of(), RandomSource.create(1L));

        assertEquals("near", choice.orElseThrow().target());
    }

    @Test
    void withNoEnemyAnAllyIsCoveredInstead() {
        Optional<SupportPulse.Choice<String>> choice =
                SupportPulse.choose(List.of(), List.of("friend"), RandomSource.create(1L));

        assertTrue(choice.isPresent());
        assertEquals(SupportPulse.Effect.MATRIX, choice.get().effect());
        assertEquals("friend", choice.get().target());
    }

    @Test
    void aPulseWithNobodyToSpendItselfOnIsNotSpent() {
        assertEquals(Optional.empty(),
                SupportPulse.choose(List.of(), List.of(), RandomSource.create(1L)));
    }

    @Test
    void alliesAreDrawnAtRandomRatherThanAlwaysTheSameOne() {
        // A Vessel parked in a standing army must spread its cover around; picking positionally
        // would re-buff whichever ally happens to sort first for the whole match.
        List<String> friends = List.of("a", "b", "c", "d");
        RandomSource random = RandomSource.create(1234L);

        Set<String> seen = new HashSet<>();
        for (int i = 0; i < 200; i++) {
            seen.add(SupportPulse.choose(List.of(), friends, random).orElseThrow().target());
        }

        assertEquals(new HashSet<>(friends), seen, "every ally should be reachable by the draw");
    }

    @Test
    void theChoiceIsTheCallersOwnObject() {
        // Identity, not equality: the entity switches on the returned target to apply an effect to
        // it, so a copy would land the poison on the wrong thing.
        String enemy = new String("enemy");
        assertSame(enemy,
                SupportPulse.choose(List.of(enemy), List.of(), RandomSource.create(1L))
                        .orElseThrow().target());
    }
}
