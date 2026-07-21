package net.bitflora.asteriskcraft.entity.ai.protoss;

import net.bitflora.asteriskcraft.entity.protoss.PhotonCannonEntity;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;
import org.jetbrains.annotations.Nullable;

/**
 * The Photon Cannon's auto-fire. Once per {@link PhotonCannonEntity#ATTACK_COOLDOWN}, while the
 * target chosen by the cannon's target selector is alive and within {@link PhotonCannonEntity#RANGE},
 * it deals the bolt's magic damage and draws an {@code END_ROD} energy-beam trail from the cannon to
 * the target, with a zap sound. Ported from the old block entity's {@code fireAt} — but retaliation
 * is now automatic (the target is hurt as an ordinary {@code LivingEntity}), so there's no
 * building-aggro bookkeeping here.
 */
public class CannonFireGoal extends Goal {
    private static final int BEAM_STEPS = 8;

    private final PhotonCannonEntity cannon;
    private int cooldown;

    public CannonFireGoal(PhotonCannonEntity cannon) {
        this.cannon = cannon;
        this.setFlags(EnumSet.of(Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        return !this.cannon.isWarping() && inRangeTarget() != null;
    }

    @Override
    public boolean canContinueToUse() {
        return canUse();
    }

    @Override
    public void start() {
        this.cooldown = 0; // fire as soon as a target is acquired, then on cadence
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public void tick() {
        LivingEntity target = inRangeTarget();
        if (target == null) {
            return;
        }
        this.cannon.getLookControl().setLookAt(target, 30.0f, 30.0f);
        if (--this.cooldown > 0) {
            return;
        }
        this.cooldown = PhotonCannonEntity.ATTACK_COOLDOWN;
        fireAt(target);
    }

    @Nullable
    private LivingEntity inRangeTarget() {
        LivingEntity target = this.cannon.getTarget();
        if (target == null || !target.isAlive()) {
            return null;
        }
        double reachSq = PhotonCannonEntity.RANGE * PhotonCannonEntity.RANGE;
        if (this.cannon.distanceToSqr(target) > reachSq) {
            return null;
        }
        return this.cannon.getSensing().hasLineOfSight(target) ? target : null;
    }

    private void fireAt(LivingEntity target) {
        if (!(this.cannon.level() instanceof ServerLevel level)) {
            return;
        }
        target.hurtServer(level, level.damageSources().magic(), PhotonCannonEntity.ATTACK_DAMAGE);
        Vec3 origin = this.cannon.getEyePosition();
        Vec3 delta = target.getEyePosition().subtract(origin);
        for (int i = 1; i <= BEAM_STEPS; i++) {
            Vec3 point = origin.add(delta.scale((double) i / BEAM_STEPS));
            level.sendParticles(ParticleTypes.END_ROD, point.x, point.y, point.z, 1, 0.0, 0.0, 0.0, 0.0);
        }
        level.playSound(null, this.cannon.blockPosition(), SoundEvents.BEACON_POWER_SELECT, SoundSource.HOSTILE, 1.0f, 1.6f);
    }
}
