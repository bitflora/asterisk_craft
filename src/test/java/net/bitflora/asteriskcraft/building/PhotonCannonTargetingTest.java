package net.bitflora.asteriskcraft.building;

import net.bitflora.asteriskcraft.faction.Faction;
import net.bitflora.asteriskcraft.faction.WildKind;
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
        assertTrue(PhotonCannonTargeting.isTargetable(Faction.PROTOSS, Faction.ZERG, true, WildKind.PASSIVE),
                "a Protoss cannon must fire on a living Zerg unit");
        assertFalse(PhotonCannonTargeting.isTargetable(Faction.PROTOSS, Faction.PROTOSS, true, WildKind.PASSIVE),
                "the cannon must never fire on its own faction");
        assertFalse(PhotonCannonTargeting.isTargetable(Faction.PROTOSS, Faction.NEUTRAL, true, WildKind.PASSIVE),
                "peaceful NEUTRAL entities (players, cows) are never targeted");
        assertFalse(PhotonCannonTargeting.isTargetable(Faction.PROTOSS, Faction.ZERG, false, WildKind.PASSIVE),
                "a dead enemy is not a valid target");
    }

    @Test
    void targetsLivingNeutralMonsters() {
        assertTrue(PhotonCannonTargeting.isTargetable(Faction.PROTOSS, Faction.NEUTRAL, true, WildKind.HOSTILE),
                "vanilla hostile monsters must be fired on even though they default to NEUTRAL");
        assertFalse(PhotonCannonTargeting.isTargetable(Faction.PROTOSS, Faction.NEUTRAL, false, WildKind.HOSTILE),
                "a dead monster is not a valid target");
    }

    @Test
    void neverTargetsFactionTaggedMonstersOfAnAlliedOrOwnFaction() {
        // Zealot/Zergling/Dragoon/Hydralisk are all Java Monster subclasses, but they carry a real
        // Faction attachment and must never be caught by the "vanilla wild monster" fallback.
        assertFalse(PhotonCannonTargeting.isTargetable(Faction.PROTOSS, Faction.PROTOSS, true, WildKind.HOSTILE),
                "an allied combat unit must never be fired on just because it's a Monster subclass");
        assertTrue(PhotonCannonTargeting.isTargetable(Faction.PROTOSS, Faction.ZERG, true, WildKind.HOSTILE),
                "an enemy-faction combat unit is still targetable through the enemy-faction path");
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
