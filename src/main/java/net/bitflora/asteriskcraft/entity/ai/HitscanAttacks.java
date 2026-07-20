package net.bitflora.asteriskcraft.entity.ai;

import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;

/**
 * Instant, no-projectile ranged damage shared by ranged units (Dragoon, Hydralisk). Ported from
 * {@link CannonFireGoal}'s {@code fireAt}: magic damage source means no knockback and no travel
 * time to dodge, so the attack never misses. Draws a stepped particle beam from attacker to
 * target as hit feedback.
 */
public final class HitscanAttacks {
    private static final int BEAM_STEPS = 8;

    private HitscanAttacks() {
    }

    public static void fire(Mob attacker, LivingEntity target, double damage, ParticleOptions particle, SoundEvent sound) {
        if (!(attacker.level() instanceof ServerLevel level)) {
            return;
        }
        target.hurtServer(level, level.damageSources().magic(), (float) damage);
        Vec3 origin = attacker.getEyePosition();
        Vec3 delta = target.getEyePosition().subtract(origin);
        for (int i = 1; i <= BEAM_STEPS; i++) {
            Vec3 point = origin.add(delta.scale((double) i / BEAM_STEPS));
            level.sendParticles(particle, point.x, point.y, point.z, 1, 0.0, 0.0, 0.0, 0.0);
        }
        level.playSound(null, attacker.blockPosition(), sound, SoundSource.HOSTILE, 1.0f, 1.0f);
    }
}
