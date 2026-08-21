package net.bitflora.asteriskcraft.entity.zerg;

import net.bitflora.asteriskcraft.combat.FangStrikeDamageHandler;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.EvokerFangs;
import net.minecraft.world.level.Level;

/**
 * A spike driven up out of the ground under a target: vanilla Evoker Fangs behaviour (warm-up,
 * 20-tick bite, crit particles, {@code EVOKER_FANGS_ATTACK} sound) with none of it reimplemented.
 * The Sunken Colony's tentacle strike and the Lurker's row of spines are both this.
 *
 * <p>The subclass exists at all because vanilla's fangs are unusable as-is for a faction game, and
 * both problems have to be fixed from <em>outside</em> the entity (verified against the decompiled
 * 26.1.2 source, see docs/neoforge-api-notes.md): {@code EvokerFangs.dealDamageTo} is private and
 * hardcodes {@code 6.0F} of {@code minecraft:indirect_magic}, and it hits <em>every</em>
 * {@link LivingEntity} in its box except the owner and the owner's vanilla-team allies — which knows
 * nothing about {@link net.bitflora.asteriskcraft.faction.Faction}. So a subclass carries its own
 * {@code EntityType}, {@link FangStrikeDamageHandler} matches on this class, and the two abstract
 * methods below tell it what the hit should actually have been.
 *
 * <p>Rendering is free: {@code EvokerFangsRenderer} is generic over {@code EvokerFangs}, so
 * {@code AsteriskCraftClient} registers it for each subclass unchanged — no model or texture.
 */
public abstract class FangStrikeEntity extends EvokerFangs {

    protected FangStrikeEntity(EntityType<? extends FangStrikeEntity> type, Level level) {
        super(type, level);
    }

    /**
     * Mirrors vanilla's convenience constructor, minus its warm-up delay: that field is private with
     * no setter, and vanilla's own convenience constructor can't be delegated to because it hardcodes
     * {@code EntityType.EVOKER_FANGS}. No loss — staggered delays only exist to animate an Evoker's
     * outward-marching line, and a caller that wants a marching line (the Lurker) gets a truer one by
     * spawning the spikes on a cadence of its own. With a zero delay a spike bites 8 ticks after it
     * erupts, the same beat as a vanilla fang.
     *
     * @param rotationRadians facing of the spike, in radians — the angle from the attacker to its target
     */
    protected void placeAt(double x, double y, double z, float rotationRadians, LivingEntity owner) {
        this.setOwner(owner);
        this.setYRot(rotationRadians * (180.0f / (float) Math.PI));
        this.setPos(x, y, z);
    }

    /** The damage type this strike deals, per the mod's one-type-per-attacker rule. */
    public abstract ResourceKey<DamageType> damageType();

    /** What a hit is worth, from the balance table — never vanilla's hardcoded 6. */
    public abstract float strikeDamage();

    /**
     * An orphaned spike is withdrawn rather than allowed to bite. Vanilla's ownerless branch deals a
     * flat 6 magic damage from a source with <em>no</em> direct entity, which the damage handler
     * can't recognise as ours and therefore can't replace or faction-filter — so an attacker killed
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
