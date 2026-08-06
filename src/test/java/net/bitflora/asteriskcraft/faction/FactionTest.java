package net.bitflora.asteriskcraft.faction;

import net.bitflora.asteriskcraft.entity.protoss.ZealotEntity;
import net.bitflora.asteriskcraft.entity.zerg.ZerglingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.monster.Ghast;
import net.minecraft.world.entity.monster.MagmaCube;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.monster.Phantom;
import net.minecraft.world.entity.monster.Shulker;
import net.minecraft.world.entity.monster.Slime;
import net.minecraft.world.entity.monster.hoglin.Hoglin;
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
    // isEnemy alone is not enough. Vanilla hostiles carry no faction attachment, so they default to
    // NEUTRAL, and "NEUTRAL fights no one" meant units watched a zombie chew on them without ever
    // fighting back. These pin the carve-out and, just as importantly, its two guards.

    /** A wild zombie or slime: NEUTRAL, and declares itself hostile via vanilla's Enemy marker. */
    private static boolean vsWildHostile(Faction self) {
        return FactionAttachments.isHostile(self, Faction.NEUTRAL, true);
    }

    @Test
    void unitsFightWildHostiles() {
        assertTrue(vsWildHostile(Faction.PROTOSS), "a Zealot must defend itself against a zombie");
        assertTrue(vsWildHostile(Faction.ZERG), "the rule is faction-generic, not Protoss-only");
    }

    @Test
    void unitsStillIgnorePeacefulNeutrals() {
        // The player and wild animals are NEUTRAL but not Enemy. This is the invariant the
        // wild-hostile carve-out must not break.
        assertFalse(FactionAttachments.isHostile(Faction.PROTOSS, Faction.NEUTRAL, false),
                "units must never turn on the player or a cow");
    }

    @Test
    void factionTaggedHostilesAreJudgedByFactionNotByClass() {
        // Zealots, Zerglings, Dragoons and Hydralisks are all Monster subclasses, so they all
        // implement Enemy. If the carve-out were a bare instanceof check they would attack their
        // own side.
        assertFalse(FactionAttachments.isHostile(Faction.PROTOSS, Faction.PROTOSS, true),
                "a Zealot must never target another Zealot for implementing Enemy");
        assertTrue(FactionAttachments.isHostile(Faction.PROTOSS, Faction.ZERG, true),
                "cross-faction hostility still applies to faction-tagged hostiles");
    }

    @Test
    void neutralFightsNoOneEvenAgainstWildHostiles() {
        // An unfactioned unit keeps the original invariant: it starts no fights at all.
        assertFalse(vsWildHostile(Faction.NEUTRAL), "NEUTRAL fights no one, wild hostiles included");
    }

    // --- Which vanilla class actually marks "wild hostile" ---
    // The pure rule above takes the instanceof result as a boolean, so only a class-level assertion
    // can catch the caller testing the wrong type. It used to test Monster, which is just the
    // PathfinderMob branch of the hostiles — so slimes, ghasts, phantoms, shulkers and hoglins were
    // all invisible to targeting and a unit would stand still while a slime ate it.

    @Test
    void slimesAreWildHostilesDespiteNotBeingMonsters() {
        assertTrue(Enemy.class.isAssignableFrom(Slime.class),
                "Slime implements Enemy — this is what makes it targetable");
        assertFalse(Monster.class.isAssignableFrom(Slime.class),
                "Slime extends Mob, not Monster: keying targeting off Monster skips it entirely");
        assertTrue(Enemy.class.isAssignableFrom(MagmaCube.class),
                "MagmaCube extends Slime, so it comes along for free");
    }

    @Test
    void otherNonMonsterHostilesAreAlsoCovered() {
        // Same bug, same fix — worth pinning so nobody narrows the check back to Monster.
        for (Class<?> hostile : new Class<?>[] {Ghast.class, Phantom.class, Shulker.class, Hoglin.class}) {
            assertTrue(Enemy.class.isAssignableFrom(hostile), hostile.getSimpleName() + " must be Enemy");
            assertFalse(Monster.class.isAssignableFrom(hostile),
                    hostile.getSimpleName() + " is not a Monster, which is exactly why Enemy is the right test");
        }
    }

    @Test
    void modUnitsImplementEnemyAndSoStayFactionGated() {
        // The carve-out's NEUTRAL guard is load-bearing precisely because this is true.
        assertTrue(Enemy.class.isAssignableFrom(ZealotEntity.class));
        assertTrue(Enemy.class.isAssignableFrom(ZerglingEntity.class));
    }

    @Test
    void serializedNamesAreStable() {
        // These names are persisted in save data; changing them would break worlds.
        assertEquals("neutral", Faction.NEUTRAL.getSerializedName());
        assertEquals("protoss", Faction.PROTOSS.getSerializedName());
        assertEquals("zerg", Faction.ZERG.getSerializedName());
    }
}
