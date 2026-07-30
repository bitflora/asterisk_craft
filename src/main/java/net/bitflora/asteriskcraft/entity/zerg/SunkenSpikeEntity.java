package net.bitflora.asteriskcraft.entity.zerg;

import net.bitflora.asteriskcraft.AsteriskCraft;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.EvokerFangs;
import net.minecraft.world.level.Level;

/**
 * The ground spike a {@link SunkenColonyEntity} drives up beneath its target — vanilla Evoker Fangs
 * behaviour (warm-up, 20-tick bite, crit particles, {@code EVOKER_FANGS_ATTACK} sound) with none of
 * it reimplemented: this subclass exists purely so the spike carries its own {@code EntityType},
 * which is what
 * {@link net.bitflora.asteriskcraft.combat.SunkenSpikeDamageHandler SunkenSpikeDamageHandler}
 * matches on to replace vanilla's hardcoded 6 damage with the Sunken Colony's 20 and to spare
 * same-faction bystanders.
 *
 * <p>Two vanilla details make the "correct it from outside" approach necessary rather than merely
 * convenient (verified against the decompiled 26.1.2 source, see docs/neoforge-api-notes.md):
 * {@code EvokerFangs.dealDamageTo} is private and hardcodes {@code 6.0F}, and it hits <em>every</em>
 * {@code LivingEntity} in its box except the owner and the owner's vanilla-team allies — which knows
 * nothing about {@link net.bitflora.asteriskcraft.faction.Faction}.
 *
 * <p>Rendering is free too: {@code EvokerFangsRenderer} is generic over {@code EvokerFangs}, so
 * {@code AsteriskCraftClient} registers it for this type unchanged — no model or texture of our own.
 */
public class SunkenSpikeEntity extends EvokerFangs {

    public SunkenSpikeEntity(EntityType<? extends SunkenSpikeEntity> type, Level level) {
        super(type, level);
    }

    /**
     * Mirrors vanilla's convenience constructor, minus its warm-up delay: that field is private with
     * no setter, and vanilla's own convenience constructor can't be delegated to because it hardcodes
     * {@code EntityType.EVOKER_FANGS}. No loss — the colony spawns exactly one spike per attack
     * (staggered delays only exist to animate the Evoker's outward-marching line), and one spike per
     * attack is also what keeps an attack worth exactly one
     * {@link net.bitflora.asteriskcraft.stats.UnitStats#SUNKEN_COLONY}'s attack damage rather than a
     * multiple of it. With a zero delay the spike bites 8 ticks after it erupts, the
     * same beat as a vanilla fang.
     *
     * @param rotationRadians facing of the spike, in radians — the angle from the colony to its target
     */
    public SunkenSpikeEntity(Level level, double x, double y, double z, float rotationRadians, LivingEntity owner) {
        this(AsteriskCraft.SUNKEN_SPIKE.get(), level);
        this.setOwner(owner);
        this.setYRot(rotationRadians * (180.0f / (float) Math.PI));
        this.setPos(x, y, z);
    }

    /**
     * An orphaned spike is withdrawn rather than allowed to bite. Vanilla's ownerless branch deals a
     * flat 6 magic damage from a source with <em>no</em> direct entity, which the damage handler
     * can't recognise as ours and therefore can't correct or faction-filter — so a colony killed
     * during its spike's warm-up would otherwise land an untyped hit on friend and foe alike.
     */
    @Override
    public void tick() {
        if (!this.level().isClientSide() && this.getOwner() == null) {
            this.discard();
            return;
        }
        super.tick();
    }
}
