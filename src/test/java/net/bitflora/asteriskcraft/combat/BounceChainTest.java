package net.bitflora.asteriskcraft.combat;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.function.ToDoubleBiFunction;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Guards the pure chaining-attack math and target selection behind the Mutalisk's bouncing glave
 * ({@link net.bitflora.asteriskcraft.entity.zerg.MutaliskEntity}). Kept free of any live
 * {@code Level}/{@code Entity} so the world-scan side ({@code HitscanAttacks#fireChained}) stays
 * the only untestable part, following the pattern in {@code building.PhotonCannonTargetingTest}.
 */
class BounceChainTest {

    /** A trivial 1D point standing in for a LivingEntity, so distance is just the value gap. */
    private record Point(String name, double at) {
    }

    private static final ToDoubleBiFunction<Point, Point> DISTANCE_SQ =
            (a, b) -> (a.at() - b.at()) * (a.at() - b.at());

    @Test
    void damageHalvesEachHop() {
        assertEquals(4.5f, BounceChain.damageAt(4.5, 0.5f, 0));
        assertEquals(2.25f, BounceChain.damageAt(4.5, 0.5f, 1));
        assertEquals(1.125f, BounceChain.damageAt(4.5, 0.5f, 2));
    }

    @Test
    void chainStopsAtMaxHitsEvenWithMoreCandidatesInReach() {
        Point primary = new Point("primary", 0);
        List<Point> candidates = List.of(new Point("a", 1), new Point("b", 2), new Point("c", 3), new Point("d", 4));
        List<Point> hits = BounceChain.resolve(primary, candidates, 3, 25.0, DISTANCE_SQ);
        assertEquals(3, hits.size(), "must not exceed the configured maxHits");
    }

    @Test
    void aTargetIsNeverHitTwice() {
        Point primary = new Point("primary", 0);
        List<Point> candidates = List.of(new Point("a", 1));
        List<Point> hits = BounceChain.resolve(primary, candidates, 5, 25.0, DISTANCE_SQ);
        assertEquals(2, hits.size(), "only one real candidate exists, so the chain can't grow past it");
        assertEquals(1, hits.stream().filter(p -> p == candidates.get(0)).count());
    }

    @Test
    void candidateOutsideSearchRadiusOfTheLastHitIsSkipped() {
        Point primary = new Point("primary", 0);
        Point tooFar = new Point("far", 10);
        List<Point> hits = BounceChain.resolve(primary, List.of(tooFar), 3, 25.0, DISTANCE_SQ);
        assertEquals(1, hits.size(), "no candidate within radius, so the chain stops at the primary hit");
        assertFalse(hits.contains(tooFar));
    }

    @Test
    void nearestCandidateIsPickedFirst() {
        Point primary = new Point("primary", 0);
        Point near = new Point("near", 2);
        Point far = new Point("far", 4);
        List<Point> hits = BounceChain.resolve(primary, List.of(far, near), 2, 25.0, DISTANCE_SQ);
        assertEquals(near, hits.get(1), "the nearer of two in-range candidates bounces first");
    }

    @Test
    void chainMeasuresFromTheLastHitNotTheAttacker() {
        // primary at 0, hop 1 at 4 (within a 5-radius of primary), hop 2 at 8 (within a 5-radius of
        // hop 1 at 4, but 8 blocks from the primary — outside a radius measured from the origin).
        Point primary = new Point("primary", 0);
        Point hop1 = new Point("hop1", 4);
        Point hop2 = new Point("hop2", 8);
        List<Point> hits = BounceChain.resolve(primary, List.of(hop1, hop2), 3, 25.0, DISTANCE_SQ);
        assertEquals(List.of(primary, hop1, hop2), hits,
                "a glave chains from the enemy it just hit, so it can walk past its own reach");
    }
}
