package com.timja.asteriskcraft.faction;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Guards the faction hostility invariant that all targeting relies on, and that
 * keeps the mod race-generic for future PvP: enemies are strictly cross-faction,
 * and NEUTRAL fights no one.
 */
class FactionTest {

    @Test
    void differentPlayableFactionsAreEnemies() {
        assertTrue(Faction.PROTOSS.isEnemy(Faction.ZERG));
        assertTrue(Faction.ZERG.isEnemy(Faction.PROTOSS));
    }

    @Test
    void sameFactionIsNeverEnemy() {
        assertFalse(Faction.PROTOSS.isEnemy(Faction.PROTOSS));
        assertFalse(Faction.ZERG.isEnemy(Faction.ZERG));
    }

    @Test
    void neutralIsEnemyOfNoOne() {
        assertFalse(Faction.NEUTRAL.isEnemy(Faction.PROTOSS));
        assertFalse(Faction.PROTOSS.isEnemy(Faction.NEUTRAL));
        assertFalse(Faction.NEUTRAL.isEnemy(Faction.NEUTRAL));
    }

    @Test
    void serializedNamesAreStable() {
        // These names are persisted in save data; changing them would break worlds.
        assertEquals("neutral", Faction.NEUTRAL.getSerializedName());
        assertEquals("protoss", Faction.PROTOSS.getSerializedName());
        assertEquals("zerg", Faction.ZERG.getSerializedName());
    }
}
