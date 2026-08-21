package net.bitflora.asteriskcraft.combat;

import net.bitflora.asteriskcraft.faction.Faction;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.npc.villager.AbstractVillager;

/**
 * When a kill raises the victim back up as one of the killer's own.
 *
 * <p>The rule is kept apart from {@link InfestationHandler}, which owns the world, because it is
 * purely a fact about a faction, an entity <em>class</em> and a die roll — so the whole table is
 * testable without a live level, which the JUnit bootstrap cannot build. That is the same split
 * {@code faction.WildKind} makes for exactly the same reason, and why {@link #infests} takes a
 * {@code Class} rather than an {@code Entity}.
 *
 * <p>Which side raises the dead is a fact about its <em>race</em> ({@code faction.Race.infests}),
 * not about which side it is on, so this asks the faction's race and never names one.
 *
 * <p>The victim test is {@link AbstractVillager}, so it covers wandering traders as well as villagers
 * — anyone who lives in the settled world and can be dragged into the swarm. It is deliberately
 * narrower than the {@code WildKind.CIVILIAN} the Zerg actually hunt, which also sweeps in iron
 * golems: a golem is a machine, and there is nothing in it to infest.
 */
public final class Infestation {

    /** How often a qualifying kill raises an Infested Villager. */
    public static final float CHANCE = 0.75f;

    private Infestation() {
    }

    /**
     * Whether this kill raises an Infested Villager.
     *
     * @param killer the faction of whatever landed the killing blow; NEUTRAL for an unowned killer
     * @param victim what died
     * @param roll   a uniform sample in [0, 1)
     */
    public static boolean infests(Faction killer, Class<? extends Entity> victim, float roll) {
        return killer.infests()
                && AbstractVillager.class.isAssignableFrom(victim)
                && roll < CHANCE;
    }
}
