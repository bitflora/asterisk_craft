package net.bitflora.asteriskcraft.entity.ai;

import net.bitflora.asteriskcraft.combat.BounceChain;
import net.bitflora.asteriskcraft.faction.FactionAttachments;
import net.bitflora.asteriskcraft.stats.UnitStat;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
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
 * back. Because the source now has an entity and its type is outside {@code #minecraft:no_knockback},
 * hits also knock their target away from the shooter.
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
                e -> e.isAlive() && e != target && FactionAttachments.areEnemies(attacker, e));

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

    private static void beam(ServerLevel level, Vec3 origin, Vec3 target, ParticleOptions particle) {
        Vec3 delta = target.subtract(origin);
        for (int i = 1; i <= BEAM_STEPS; i++) {
            Vec3 point = origin.add(delta.scale((double) i / BEAM_STEPS));
            level.sendParticles(particle, point.x, point.y, point.z, 1, 0.0, 0.0, 0.0, 0.0);
        }
    }
}
