package net.bitflora.asteriskcraft.entity.ai;

import net.bitflora.asteriskcraft.combat.BounceChain;
import net.bitflora.asteriskcraft.faction.FactionAttachments;
import net.bitflora.asteriskcraft.stats.UnitStat;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * Instant, no-projectile ranged damage shared by ranged units (Dragoon, Hydralisk). Ported from
 * {@link CannonFireGoal}'s {@code fireAt}: there is no travel time to dodge, so the attack never
 * misses. Draws a stepped particle beam from attacker to target as hit feedback.
 *
 * <p>The damage type is the caller's, exactly like the particle and the sound — every unit names its
 * own from {@link net.bitflora.asteriskcraft.combat.AsteriskCraftDamageTypes}, so a kill reads as the
 * weapon that made it. The source is built with the attacker as its cause, which is what sets
 * {@code getLastHurtByMob()} and so lets {@link RetaliateGoal} fire on ranged hits — the old shared
 * {@code damageSources().magic()} carried no entity at all, and nothing shot at range ever fought
 * back.
 *
 * <p>Every type fired through here is in {@code #minecraft:no_knockback}, so a shot hurts without
 * shoving. That is what separates ranged from melee, which keeps its stagger: nudging a target back
 * on each shot means a firing line kites whatever walks at it and an assault never closes. The tag is
 * the whole mechanism — {@code hurtServer} gates its own shove on it — so there is nothing to do here
 * beyond naming a type that is a member (see {@code AsteriskCraftDamageTypes}).
 */
public final class HitscanAttacks {
    private static final int BEAM_STEPS = 8;

    private HitscanAttacks() {
    }

    public static void fire(Mob attacker, LivingEntity target, double damage, ResourceKey<DamageType> damageType,
            ParticleOptions particle, SoundEvent sound) {
        fire(attacker, target, damage, damageType, particle, sound, 1.0f);
    }

    /**
     * As {@link #fire(Mob, LivingEntity, double, ResourceKey, ParticleOptions, SoundEvent)} but with
     * an explicit sound pitch.
     */
    public static void fire(Mob attacker, LivingEntity target, double damage, ResourceKey<DamageType> damageType,
            ParticleOptions particle, SoundEvent sound, float pitch) {
        if (!(attacker.level() instanceof ServerLevel level)) {
            return;
        }
        target.hurtServer(level, level.damageSources().source(damageType, attacker), (float) damage);
        beam(level, attacker.getEyePosition(), target.getEyePosition(), particle);
        level.playSound(null, attacker.blockPosition(), sound, SoundSource.HOSTILE, 1.0f, pitch);
    }

    /**
     * As {@link #fire(Mob, LivingEntity, double, ResourceKey, ParticleOptions, SoundEvent)}, but the
     * shot may chain past {@code target} onto other nearby enemies per {@code bounce} (the Mutalisk's
     * glave). Each hop deals a fraction of {@code damage} per {@link UnitStat.Bounce#damageFalloff()}
     * and is drawn as its own beam segment, so the chain reads as one shot hopping between bodies.
     *
     * <p>Candidates are scanned once, in a single AABB sized for the worst-case chain, rather than
     * per hop. Line of sight is deliberately not required for a bounce — the primary shot already
     * can't miss, and the radius is short enough that a wall-clip doesn't read wrong. Only living
     * entities are candidates: buildings are {@code SiegeTarget} block entities, not
     * {@code LivingEntity}, so a glave never chains into a core.
     */
    public static void fireChained(Mob attacker, LivingEntity target, double damage, ResourceKey<DamageType> damageType,
            ParticleOptions particle, SoundEvent sound, UnitStat.Bounce bounce) {
        if (!(attacker.level() instanceof ServerLevel level)) {
            return;
        }
        double reach = bounce.searchRadius() * bounce.maxHits();
        List<LivingEntity> candidates = level.getEntitiesOfClass(LivingEntity.class,
                target.getBoundingBox().inflate(reach),
                e -> e.isAlive() && e != target && FactionAttachments.isHostile(attacker, e));

        List<LivingEntity> hits = BounceChain.resolve(target, candidates, bounce.maxHits(),
                (double) bounce.searchRadius() * bounce.searchRadius(), LivingEntity::distanceToSqr);

        Vec3 from = attacker.getEyePosition();
        for (int i = 0; i < hits.size(); i++) {
            LivingEntity hit = hits.get(i);
            hit.hurtServer(level, level.damageSources().source(damageType, attacker),
                    BounceChain.damageAt(damage, bounce.damageFalloff(), i));
            Vec3 to = hit.getEyePosition();
            beam(level, from, to, particle);
            from = to;
        }
        level.playSound(null, attacker.blockPosition(), sound, SoundSource.HOSTILE, 1.0f, 1.0f);
    }

    /**
     * As {@link #fire(Mob, LivingEntity, double, ResourceKey, ParticleOptions, SoundEvent)}, but the
     * hit also washes onto every other enemy standing within {@code splash.radius()} of the target
     * (the Archon's psionic shockwave). Each of those takes
     * {@link UnitStat.Splash#damageFraction()} of what the target took; the target itself takes the
     * full amount.
     *
     * <p>Not a {@link #fireChained} with different numbers. A chain walks from body to body, so its
     * falloff compounds along a hop order and its reach is a multiple of one hop; a splash is a
     * single sphere around one point, so every bystander is equally far down the falloff and there
     * is no order for anything to compound along.
     *
     * <p>Candidates are filtered through {@code FactionAttachments.isHostile}, exactly as the bounce
     * chain and the Firebat's cone are, so cloak, garrison and the per-race wild carve-out all apply
     * to a shockwave with nothing written for them here. Only living entities are candidates:
     * buildings are {@code SiegeTarget} block entities holding their health on the block entity
     * rather than on a target, so a shockwave never washes onto a core.
     *
     * <p>{@code igniteTicks} sets <em>the target alone</em> alight, not the bystanders — unlike
     * {@link FlameAttacks#sweep}, where the flame is the thing that spreads. Here the shockwave is
     * what spreads and the psionic contact is what burns, so a unit merely caught in the blast is
     * hurt without catching fire. Pass 0 for a splash that does not ignite at all.
     */
    public static void fireSplash(Mob attacker, LivingEntity target, double damage,
            ResourceKey<DamageType> damageType, ParticleOptions particle, SoundEvent sound,
            UnitStat.Splash splash, int igniteTicks) {
        if (!(attacker.level() instanceof ServerLevel level)) {
            return;
        }
        DamageSource source = level.damageSources().source(damageType, attacker);
        if (target.hurtServer(level, source, (float) damage) && igniteTicks > 0
                && !target.fireImmune()) {
            target.igniteForTicks(igniteTicks);
        }

        double radius = splash.radius();
        // The broad phase is a box, so each candidate is then measured against the radius proper —
        // otherwise the corners of that box would reach nearly twice as far as the stated splash.
        double radiusSq = radius * radius;
        Vec3 centre = target.position();
        float wash = (float) (damage * splash.damageFraction());
        for (LivingEntity caught : level.getEntitiesOfClass(LivingEntity.class,
                target.getBoundingBox().inflate(radius),
                e -> e.isAlive() && e != target && FactionAttachments.isHostile(attacker, e))) {
            if (caught.distanceToSqr(centre) <= radiusSq) {
                caught.hurtServer(level, source, wash);
            }
        }

        beam(level, attacker.getEyePosition(), target.getEyePosition(), particle);
        level.playSound(null, attacker.blockPosition(), sound, SoundSource.HOSTILE, 1.0f, 1.0f);
    }

    private static void beam(ServerLevel level, Vec3 origin, Vec3 target, ParticleOptions particle) {
        Vec3 delta = target.subtract(origin);
        for (int i = 1; i <= BEAM_STEPS; i++) {
            Vec3 point = origin.add(delta.scale((double) i / BEAM_STEPS));
            level.sendParticles(particle, point.x, point.y, point.z, 1, 0.0, 0.0, 0.0, 0.0);
        }
    }
}
