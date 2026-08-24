package net.bitflora.asteriskcraft.building;

import net.bitflora.asteriskcraft.faction.Faction;
import net.bitflora.asteriskcraft.faction.Race;
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
        assertTrue(PhotonCannonTargeting.isTargetable(
                        Faction.BLUE, Race.PROTOSS, Faction.RED, true, WildKind.PASSIVE, false),
                "a cannon must fire on a living enemy unit");
        assertFalse(PhotonCannonTargeting.isTargetable(
                        Faction.BLUE, Race.PROTOSS, Faction.BLUE, true, WildKind.PASSIVE, false),
                "the cannon must never fire on its own side");
        assertFalse(PhotonCannonTargeting.isTargetable(
                        Faction.BLUE, Race.PROTOSS, Faction.NEUTRAL, true, WildKind.PASSIVE, false),
                "peaceful NEUTRAL entities (players, cows) are never targeted");
        assertFalse(PhotonCannonTargeting.isTargetable(
                        Faction.BLUE, Race.PROTOSS, Faction.RED, false, WildKind.PASSIVE, false),
                "a dead enemy is not a valid target");
    }

    @Test
    void targetsLivingNeutralMonsters() {
        assertTrue(PhotonCannonTargeting.isTargetable(
                        Faction.BLUE, Race.PROTOSS, Faction.NEUTRAL, true, WildKind.HOSTILE, false),
                "vanilla hostile monsters must be fired on even though they default to NEUTRAL");
        assertFalse(PhotonCannonTargeting.isTargetable(
                        Faction.BLUE, Race.PROTOSS, Faction.NEUTRAL, false, WildKind.HOSTILE, false),
                "a dead monster is not a valid target");
    }

    @Test
    void neverTargetsFactionTaggedMonstersOfAnAlliedOrOwnFaction() {
        // Zealot/Zergling/Dragoon/Hydralisk are all Java Monster subclasses, but they carry a real
        // Faction attachment and must never be caught by the "vanilla wild monster" fallback.
        assertFalse(PhotonCannonTargeting.isTargetable(
                        Faction.BLUE, Race.PROTOSS, Faction.BLUE, true, WildKind.HOSTILE, false),
                "an allied combat unit must never be fired on just because it's a Monster subclass");
        assertTrue(PhotonCannonTargeting.isTargetable(
                        Faction.BLUE, Race.PROTOSS, Faction.RED, true, WildKind.HOSTILE, false),
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
