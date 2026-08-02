package net.bitflora.asteriskcraft.faction;

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
    void enemyRuleIsEntityTypeAgnostic() {
        // Faction.isEnemy takes only factions, never an entity/role: combat targeting
        // (FactionTargetGoal) relies on this to treat an enemy-faction Probe as a valid
        // target just like an enemy soldier, with no entity-type whitelist anywhere.
        assertTrue(Faction.PROTOSS.isEnemy(Faction.ZERG));
    }

    // --- Hostility (FactionAttachments.isHostile): what combat code actually asks ---
    // isEnemy alone is not enough. Vanilla monsters carry no faction attachment, so they default to
    // NEUTRAL, and "NEUTRAL fights no one" meant units watched a zombie chew on them without ever
    // fighting back. These pin the carve-out and, just as importantly, its two guards.

    /** A wild zombie: NEUTRAL, and a Monster subclass. */
    private static boolean vsWildMonster(Faction self) {
        return FactionAttachments.isHostile(self, Faction.NEUTRAL, true);
    }

    @Test
    void unitsFightWildMonsters() {
        assertTrue(vsWildMonster(Faction.PROTOSS), "a Zealot must defend itself against a zombie");
        assertTrue(vsWildMonster(Faction.ZERG), "the rule is faction-generic, not Protoss-only");
    }

    @Test
    void unitsStillIgnorePeacefulNeutrals() {
        // The player and wild animals are NEUTRAL but not Monsters. This is the invariant the
        // wild-monster carve-out must not break.
        assertFalse(FactionAttachments.isHostile(Faction.PROTOSS, Faction.NEUTRAL, false),
                "units must never turn on the player or a cow");
    }

    @Test
    void factionTaggedMonstersAreJudgedByFactionNotByClass() {
        // Zealots, Zerglings, Dragoons and Hydralisks are all Monster subclasses. If the carve-out
        // were a bare instanceof check they would attack their own side.
        assertFalse(FactionAttachments.isHostile(Faction.PROTOSS, Faction.PROTOSS, true),
                "a Zealot must never target another Zealot for being a Monster");
        assertTrue(FactionAttachments.isHostile(Faction.PROTOSS, Faction.ZERG, true),
                "cross-faction hostility still applies to faction-tagged monsters");
    }

    @Test
    void neutralFightsNoOneEvenAgainstMonsters() {
        // An unfactioned unit keeps the original invariant: it starts no fights at all.
        assertFalse(vsWildMonster(Faction.NEUTRAL), "NEUTRAL fights no one, wild monsters included");
    }

    @Test
    void serializedNamesAreStable() {
        // These names are persisted in save data; changing them would break worlds.
        assertEquals("neutral", Faction.NEUTRAL.getSerializedName());
        assertEquals("protoss", Faction.PROTOSS.getSerializedName());
        assertEquals("zerg", Faction.ZERG.getSerializedName());
    }
}
