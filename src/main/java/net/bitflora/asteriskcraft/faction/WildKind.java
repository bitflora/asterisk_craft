package net.bitflora.asteriskcraft.faction;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.animal.golem.AbstractGolem;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.npc.villager.AbstractVillager;

/**
 * What an unfactioned ({@link Faction#NEUTRAL}) entity <em>is</em>, as far as targeting cares.
 *
 * <p>Everything the mod does not own defaults to NEUTRAL, and "NEUTRAL fights no one" is the
 * invariant that keeps units off the player and the wildlife. But the races don't agree on which
 * parts of that neutral world they pick a fight with, so the carve-out can't be a single boolean:
 * this classifies the candidate, and {@link Faction#attacksWild} decides — one table, per race,
 * instead of race checks scattered through targeting.
 *
 * <p>The classification is deliberately the <em>only</em> place entity classes are tested; it is
 * consumed exclusively by {@link FactionAttachments#isHostile}. It also stays in {@code faction/},
 * which imports nothing else of the mod's, so the hostility choke point keeps its leaf position.
 *
 * <p>{@link #HOSTILE} is checked first and so wins ties: a Shulker is both {@code Enemy} and an
 * {@code AbstractGolem}, and it is a monster, not a village guard.
 */
public enum WildKind {
    /** Nothing anyone starts a fight with: the player, a cow, an armour stand, a boat. */
    PASSIVE,
    /**
     * Vanilla's {@link Enemy} marker — zombies, creepers, slimes, ghasts, phantoms, hoglins.
     * The test is {@code Enemy} and not {@code Monster}: {@code Monster} is only the
     * {@code PathfinderMob} branch of the hostiles, so keying off it silently excluded
     * {@code Slime}/{@code MagmaCube}, {@code Ghast}, {@code Phantom}, {@code Shulker} and
     * {@code Hoglin}, and units stood still while a slime ate them.
     */
    HOSTILE,
    /**
     * The settled world: villagers, wandering traders and golems. Nothing attacks these by
     * default — the Zerg do, because overrunning a village is exactly what they are for.
     */
    CIVILIAN;

    /** Classifies a candidate. Only meaningful for a NEUTRAL one; mod units are judged by faction. */
    public static WildKind of(Entity entity) {
        return of(entity.getClass());
    }

    /**
     * The classification itself, which is purely a fact about the entity's class — so the whole
     * table is unit-testable without constructing a live entity, which the JUnit bootstrap can't
     * do. {@link #of(Entity)} is the form targeting calls.
     */
    public static WildKind of(Class<? extends Entity> type) {
        if (Enemy.class.isAssignableFrom(type)) {
            return HOSTILE;
        }
        if (AbstractVillager.class.isAssignableFrom(type) || AbstractGolem.class.isAssignableFrom(type)) {
            return CIVILIAN;
        }
        return PASSIVE;
    }
}
