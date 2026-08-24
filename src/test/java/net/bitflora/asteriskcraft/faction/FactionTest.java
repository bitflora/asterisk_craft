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
import org.jetbrains.annotations.Nullable;
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
        assertTrue(Faction.BLUE.isEnemy(Faction.RED));
        assertTrue(Faction.RED.isEnemy(Faction.BLUE));
    }

    @Test
    void sameFactionIsNeverEnemy() {
        assertFalse(Faction.BLUE.isEnemy(Faction.BLUE));
        assertFalse(Faction.RED.isEnemy(Faction.RED));
    }

    @Test
    void neutralIsEnemyOfNoOne() {
        assertFalse(Faction.NEUTRAL.isEnemy(Faction.BLUE));
        assertFalse(Faction.BLUE.isEnemy(Faction.NEUTRAL));
        assertFalse(Faction.NEUTRAL.isEnemy(Faction.NEUTRAL));
    }

    @Test
    void twoArmiesOfOneRaceAreStillEnemies() {
        // A side is not a race, which is what makes a mirror match a match: hostility is decided
        // between BLUE and RED and never consults what either of them is playing.
        for (Race race : Race.values()) {
            assertTrue(FactionAttachments.isHostile(Faction.BLUE, race, Faction.RED, WildKind.PASSIVE, false),
                    "two armies both playing " + race + " must still fight each other");
        }
    }

    @Test
    void enemyRuleIsEntityTypeAgnostic() {
        // Faction.isEnemy takes only factions, never an entity/role: combat targeting
        // (FactionTargetGoal) relies on this to treat an enemy-faction Probe as a valid
        // target just like an enemy soldier, with no entity-type whitelist anywhere.
        assertTrue(Faction.BLUE.isEnemy(Faction.RED));
    }

    // --- Hostility (FactionAttachments.isHostile): what combat code actually asks ---
    // isEnemy alone is not enough. Everything the mod doesn't own carries no faction attachment, so
    // it defaults to NEUTRAL, and "NEUTRAL fights no one" meant units watched a zombie chew on them
    // without ever fighting back. These pin the carve-out, its per-race split, and its two guards.

    /** A wild zombie or slime: NEUTRAL, and declares itself hostile via vanilla's Enemy marker. */
    private static boolean vsWildHostile(@Nullable Race selfRace) {
        return FactionAttachments.isHostile(Faction.BLUE, selfRace, Faction.NEUTRAL, WildKind.HOSTILE, false);
    }

    /** A villager, wandering trader or iron golem: NEUTRAL and part of the settled world. */
    private static boolean vsCivilian(@Nullable Race selfRace) {
        return FactionAttachments.isHostile(Faction.BLUE, selfRace, Faction.NEUTRAL, WildKind.CIVILIAN, false);
    }

    /** The player, a cow, a dropped boat: NEUTRAL and neither hostile nor civilian. */
    private static boolean vsPassive(@Nullable Race selfRace) {
        return FactionAttachments.isHostile(Faction.BLUE, selfRace, Faction.NEUTRAL, WildKind.PASSIVE, false);
    }

    /** The same question asked of the side a human is actually commanding. */
    private static boolean commandedVs(@Nullable Race selfRace, WildKind kind) {
        return FactionAttachments.isHostile(Faction.BLUE, selfRace, Faction.NEUTRAL, kind, true);
    }

    @Test
    void protossFightWildHostilesAndLeaveTheSettledWorldAlone() {
        assertTrue(vsWildHostile(Race.PROTOSS), "a Zealot must defend itself against a zombie");
        assertFalse(vsCivilian(Race.PROTOSS), "the Protoss have no quarrel with a villager");
    }

    @Test
    void zergHuntTheSettledWorldAndIgnoreMonsters() {
        // The mirror image of the Protoss rule, and the reason the carve-out can't be one boolean.
        assertTrue(vsCivilian(Race.ZERG), "a Zergling must overrun villagers and golems");
        assertFalse(vsWildHostile(Race.ZERG), "the swarm is not hunting zombies");
    }

    @Test
    void aCommandedSwarmAlsoHuntsWildHostiles() {
        // The swarm's "ignore monsters" doctrine is right for a scripted opponent parked across the
        // map and wrong for the army a person is standing in — it would let a creeper walk into
        // your Hive. Commanding it adds the wild hostiles without taking the villages away.
        assertTrue(commandedVs(Race.ZERG, WildKind.HOSTILE),
                "the swarm under human command must defend itself against a creeper");
        assertTrue(commandedVs(Race.ZERG, WildKind.CIVILIAN),
                "and must still overrun the settled world");
    }

    @Test
    void commandIsNotWhatMakesTheProtossFightMonsters() {
        // The Protoss answer is the same either way, which is what keeps this a per-race table
        // rather than a rule about humans bolted onto the choke point.
        assertEquals(vsWildHostile(Race.PROTOSS), commandedVs(Race.PROTOSS, WildKind.HOSTILE));
        assertEquals(vsCivilian(Race.PROTOSS), commandedVs(Race.PROTOSS, WildKind.CIVILIAN));
    }

    @Test
    void commandNeverWidensPastTheWildCarveOut() {
        // Whoever is holding the reins, a peaceful neutral is still nobody's target and NEUTRAL
        // still starts no fights — command may only ever widen which wild kinds a race hunts.
        for (Race self : Race.values()) {
            assertFalse(commandedVs(self, WildKind.PASSIVE),
                    self + " under command must still never turn on the player or a cow");
        }
        for (WildKind kind : WildKind.values()) {
            assertFalse(commandedVs(null, kind),
                    "an army with no race has nothing for command to widen");
        }
    }

    @Test
    void unitsStillIgnorePeacefulNeutrals() {
        // The player, wild animals and dropped boats are NEUTRAL and neither hostile nor civilian.
        // This is the invariant neither race's carve-out may break.
        for (Race self : Race.values()) {
            assertFalse(vsPassive(self), self + " units must never turn on the player or a cow");
        }
    }

    @Test
    void factionTaggedHostilesAreJudgedByFactionNotByClass() {
        // Zealots, Zerglings, Dragoons and Hydralisks are all Monster subclasses, so they all
        // implement Enemy. If the carve-out were a bare instanceof check they would attack their
        // own side.
        assertFalse(FactionAttachments.isHostile(Faction.BLUE, Race.PROTOSS, Faction.BLUE, WildKind.HOSTILE, false),
                "a Zealot must never target another Zealot for implementing Enemy");
        assertTrue(FactionAttachments.isHostile(Faction.BLUE, Race.PROTOSS, Faction.RED, WildKind.HOSTILE, false),
                "cross-faction hostility still applies to faction-tagged hostiles");
    }

    @Test
    void crossFactionHostilityIgnoresWhatTheCandidateWouldBeIfNeutral() {
        // The Zerg ignore wild monsters, but a Zealot is an enemy first and a Monster subclass
        // second: the classification may only ever gate the NEUTRAL branch.
        assertTrue(FactionAttachments.isHostile(Faction.BLUE, Race.ZERG, Faction.RED, WildKind.HOSTILE, false),
                "a Zergling must still fight a Zealot, which classifies as HOSTILE by class");
    }

    @Test
    void neutralFightsNoOne() {
        // An unfactioned unit keeps the original invariant: it starts no fights at all.
        for (WildKind kind : WildKind.values()) {
            assertFalse(FactionAttachments.isHostile(Faction.NEUTRAL, null, Faction.NEUTRAL, kind, false),
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
        assertEquals("blue", Faction.BLUE.getSerializedName());
        assertEquals("red", Faction.RED.getSerializedName());
    }
}
