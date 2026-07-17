package net.bitflora.asteriskcraft.building;

import net.bitflora.asteriskcraft.faction.Faction;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Guards the Photon Cannon's pure targeting logic — faction hostility routing and
 * nearest-candidate selection — with no live Level, so it runs as plain JUnit.
 */
class PhotonCannonTargetingTest {

    @Test
    void targetsOnlyLivingEnemyFactions() {
        assertTrue(PhotonCannonTargeting.isTargetable(Faction.PROTOSS, Faction.ZERG, true),
                "a Protoss cannon must fire on a living Zerg unit");
        assertFalse(PhotonCannonTargeting.isTargetable(Faction.PROTOSS, Faction.PROTOSS, true),
                "the cannon must never fire on its own faction");
        assertFalse(PhotonCannonTargeting.isTargetable(Faction.PROTOSS, Faction.NEUTRAL, true),
                "NEUTRAL entities (players, wild mobs) are never targeted");
        assertFalse(PhotonCannonTargeting.isTargetable(Faction.PROTOSS, Faction.ZERG, false),
                "a dead enemy is not a valid target");
    }

    @Test
    void nearestIsEmptyWhenNoCandidates() {
        assertTrue(PhotonCannonTargeting.nearest(List.<Double>of(), d -> d).isEmpty());
    }

    @Test
    void nearestPicksMinimumDistance() {
        // Candidates carry their own squared distance; the closest must win.
        Optional<Double> pick = PhotonCannonTargeting.nearest(List.of(9.0, 1.0, 4.0), d -> d);
        assertEquals(1.0, pick.orElseThrow());
    }
}
