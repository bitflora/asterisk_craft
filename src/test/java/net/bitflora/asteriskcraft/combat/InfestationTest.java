package net.bitflora.asteriskcraft.combat;

import net.bitflora.asteriskcraft.faction.Faction;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.animal.cow.Cow;
import net.minecraft.world.entity.animal.golem.IronGolem;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.npc.wanderingtrader.WanderingTrader;
import net.minecraft.world.entity.player.Player;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins which kills raise an Infested Villager. The rule spans three things that are easy to widen by
 * accident — the killer's faction, the victim's class, and the die roll — and getting any of them
 * wrong is close to invisible in play: a rule that is slightly too broad just looks like the swarm
 * getting lucky, and one slightly too narrow looks like it getting unlucky.
 *
 * <p>Class-level rather than entity-level on purpose. {@code Infestation.infests} takes a
 * {@code Class} for the same reason {@code faction.WildKind.of} has a {@code Class} overload: the
 * JUnit bootstrap cannot construct a live entity, and the rule is a fact about the type anyway.
 */
class InfestationTest {

    /** A roll certain to pass the chance gate, so a test about the victim is only about the victim. */
    private static final float ALWAYS = 0.0f;

    private static boolean zergKills(Class<? extends Entity> victim) {
        return Infestation.infests(Faction.ZERG, victim, ALWAYS);
    }

    @Test
    void onlyTheZergInfest() {
        assertTrue(zergKills(Villager.class), "the Zerg are what infestation is for");
        assertFalse(Infestation.infests(Faction.PROTOSS, Villager.class, ALWAYS),
                "a Protoss unit that killed a villager has not recruited it");
        assertFalse(Infestation.infests(Faction.NEUTRAL, Villager.class, ALWAYS),
                "an unfactioned killer — a zombie, a fall, a cactus — raises nothing");
    }

    /**
     * The victim test is {@code AbstractVillager}, which is deliberately narrower than the
     * {@code WildKind.CIVILIAN} the Zerg actually hunt: that also sweeps in iron golems, and there is
     * nothing in a machine to infest.
     */
    @Test
    void onlyThePeopleOfTheSettledWorldInfest() {
        assertTrue(zergKills(Villager.class), "a villager is the whole point");
        assertTrue(zergKills(WanderingTrader.class),
                "a wandering trader is a villager as far as this rule is concerned");
        assertFalse(zergKills(IronGolem.class),
                "a golem is a machine — the Zerg kill it, but nothing gets back up");
        assertFalse(zergKills(Cow.class), "wildlife is not infested");
        assertFalse(zergKills(Zombie.class), "the already-dead are not infested");
        assertFalse(zergKills(Player.class), "the player is never infested");
    }

    /**
     * The roll is a half-open comparison against {@link Infestation#CHANCE}, so a uniform sample in
     * [0, 1) infests exactly that fraction of the time. Both boundaries are checked because an
     * inclusive comparison would be an off-by-one that no amount of play would ever reveal.
     */
    @Test
    void theChanceGateIsHalfOpen() {
        assertTrue(zergKills(Villager.class), "a roll of 0 always infests");
        assertTrue(Infestation.infests(Faction.ZERG, Villager.class, Math.nextDown(Infestation.CHANCE)),
                "the last roll below the chance still infests");
        assertFalse(Infestation.infests(Faction.ZERG, Villager.class, Infestation.CHANCE),
                "the chance itself does not infest — the comparison is strictly less-than");
        assertFalse(Infestation.infests(Faction.ZERG, Villager.class, 1.0f),
                "the top of the range never infests");
    }
}
