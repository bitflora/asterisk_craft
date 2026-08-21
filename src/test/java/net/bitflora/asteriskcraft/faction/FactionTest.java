package net.bitflora.asteriskcraft.faction;

import net.bitflora.asteriskcraft.entity.protoss.ZealotEntity;
import net.bitflora.asteriskcraft.entity.zerg.ZerglingEntity;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.animal.cow.Cow;
import net.minecraft.world.entity.animal.golem.AbstractGolem;
import net.minecraft.world.entity.animal.golem.IronGolem;
import net.minecraft.world.entity.animal.golem.SnowGolem;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.monster.Ghast;
import net.minecraft.world.entity.monster.MagmaCube;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.monster.Phantom;
import net.minecraft.world.entity.monster.Shulker;
import net.minecraft.world.entity.monster.Slime;
import net.minecraft.world.entity.monster.hoglin.Hoglin;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.npc.wanderingtrader.WanderingTrader;
import net.minecraft.world.entity.player.Player;
import org.junit.jupiter.api.Test;

import java.util.List;

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
    // isEnemy alone is not enough. Everything the mod doesn't own carries no faction attachment, so
    // it defaults to NEUTRAL, and "NEUTRAL fights no one" meant units watched a zombie chew on them
    // without ever fighting back. These pin the carve-out, its per-race split, and its two guards.

    /** A wild zombie or slime: NEUTRAL, and declares itself hostile via vanilla's Enemy marker. */
    private static boolean vsWildHostile(Faction self) {
        return FactionAttachments.isHostile(self, Faction.NEUTRAL, WildKind.HOSTILE);
    }

    /** A villager, wandering trader or iron golem: NEUTRAL and part of the settled world. */
    private static boolean vsCivilian(Faction self) {
        return FactionAttachments.isHostile(self, Faction.NEUTRAL, WildKind.CIVILIAN);
    }

    @Test
    void protossFightWildHostilesAndLeaveTheSettledWorldAlone() {
        assertTrue(vsWildHostile(Faction.PROTOSS), "a Zealot must defend itself against a zombie");
        assertFalse(vsCivilian(Faction.PROTOSS), "the Protoss have no quarrel with a villager");
    }

    @Test
    void zergHuntTheSettledWorldAndIgnoreMonsters() {
        // The mirror image of the Protoss rule, and the reason the carve-out can't be one boolean.
        assertTrue(vsCivilian(Faction.ZERG), "a Zergling must overrun villagers and golems");
        assertFalse(vsWildHostile(Faction.ZERG), "the swarm is not hunting zombies");
    }

    @Test
    void unitsStillIgnorePeacefulNeutrals() {
        // The player, wild animals and dropped boats are NEUTRAL and neither hostile nor civilian.
        // This is the invariant neither race's carve-out may break.
        for (Faction self : new Faction[] {Faction.PROTOSS, Faction.ZERG}) {
            assertFalse(FactionAttachments.isHostile(self, Faction.NEUTRAL, WildKind.PASSIVE),
                    self + " units must never turn on the player or a cow");
        }
    }

    @Test
    void factionTaggedHostilesAreJudgedByFactionNotByClass() {
        // Zealots, Zerglings, Dragoons and Hydralisks are all Monster subclasses, so they all
        // implement Enemy. If the carve-out were a bare instanceof check they would attack their
        // own side.
        assertFalse(FactionAttachments.isHostile(Faction.PROTOSS, Faction.PROTOSS, WildKind.HOSTILE),
                "a Zealot must never target another Zealot for implementing Enemy");
        assertTrue(FactionAttachments.isHostile(Faction.PROTOSS, Faction.ZERG, WildKind.HOSTILE),
                "cross-faction hostility still applies to faction-tagged hostiles");
    }

    @Test
    void crossFactionHostilityIgnoresWhatTheCandidateWouldBeIfNeutral() {
        // The Zerg ignore wild monsters, but a Zealot is an enemy first and a Monster subclass
        // second: the classification may only ever gate the NEUTRAL branch.
        assertTrue(FactionAttachments.isHostile(Faction.ZERG, Faction.PROTOSS, WildKind.HOSTILE),
                "a Zergling must still fight a Zealot, which classifies as HOSTILE by class");
    }

    @Test
    void neutralFightsNoOne() {
        // An unfactioned unit keeps the original invariant: it starts no fights at all.
        for (WildKind kind : WildKind.values()) {
            assertFalse(FactionAttachments.isHostile(Faction.NEUTRAL, Faction.NEUTRAL, kind),
                    "NEUTRAL fights no one, " + kind + " included");
        }
    }

    // --- Which vanilla classes the neutral world is sorted into ---
    // The pure rule above takes the classification as a parameter, so only these can catch
    // WildKind.of sorting something into the wrong bucket. The hostile test used to be Monster,
    // which is just the PathfinderMob branch of the hostiles — so slimes, ghasts, phantoms,
    // shulkers and hoglins were all invisible to targeting and a unit would stand still while a
    // slime ate it.

    @Test
    void slimesAreWildHostilesDespiteNotBeingMonsters() {
        assertEquals(WildKind.HOSTILE, WildKind.of(Slime.class),
                "Slime implements Enemy — this is what makes it targetable");
        assertFalse(Monster.class.isAssignableFrom(Slime.class),
                "Slime extends Mob, not Monster: keying targeting off Monster skips it entirely");
        assertEquals(WildKind.HOSTILE, WildKind.of(MagmaCube.class),
                "MagmaCube extends Slime, so it comes along for free");
    }

    @Test
    void otherNonMonsterHostilesAreAlsoCovered() {
        // Same bug, same fix — worth pinning so nobody narrows the check back to Monster.
        for (Class<? extends Entity> hostile : List.of(Ghast.class, Phantom.class, Shulker.class, Hoglin.class)) {
            assertEquals(WildKind.HOSTILE, WildKind.of(hostile),
                    hostile.getSimpleName() + " must classify as a wild hostile");
            assertFalse(Monster.class.isAssignableFrom(hostile),
                    hostile.getSimpleName() + " is not a Monster, which is exactly why Enemy is the right test");
        }
    }

    @Test
    void shulkersAreMonstersRatherThanGolems() {
        // A Shulker is both Enemy and AbstractGolem. HOSTILE wins the tie, so the Zerg keep
        // ignoring it and the Protoss keep shooting it — it is a monster, not a village guard.
        assertTrue(AbstractGolem.class.isAssignableFrom(Shulker.class),
                "if this stops being true the HOSTILE-first tie-break stops being load-bearing");
        assertEquals(WildKind.HOSTILE, WildKind.of(Shulker.class));
    }

    @Test
    void villagersAndGolemsAreTheSettledWorld() {
        for (Class<? extends Entity> civilian
                : List.of(Villager.class, WanderingTrader.class, IronGolem.class, SnowGolem.class)) {
            assertEquals(WildKind.CIVILIAN, WildKind.of(civilian),
                    civilian.getSimpleName() + " is what the Zerg are sent to overrun");
        }
    }

    @Test
    void everythingElseIsPassive() {
        assertEquals(WildKind.PASSIVE, WildKind.of(Cow.class), "a cow is nobody's target");
        assertEquals(WildKind.PASSIVE, WildKind.of(Player.class), "and neither is the player");
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
