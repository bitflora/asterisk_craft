package net.bitflora.asteriskcraft.entity.ai.protoss;

import net.bitflora.asteriskcraft.entity.ai.HitscanAttacks;
import net.bitflora.asteriskcraft.entity.protoss.PhotonCannonEntity;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;

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
        HitscanAttacks.fire(this.cannon, target, PhotonCannonEntity.ATTACK_DAMAGE,
                ParticleTypes.END_ROD, SoundEvents.BEACON_POWER_SELECT, 1.6f);
    }
}
