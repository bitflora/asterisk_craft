package net.bitflora.asteriskcraft.entity.ai.terran;

import net.bitflora.asteriskcraft.AsteriskCraft;
import net.bitflora.asteriskcraft.combat.AsteriskCraftDamageTypes;
import net.bitflora.asteriskcraft.entity.Altitude;
import net.bitflora.asteriskcraft.entity.ai.HitscanAttacks;
import net.bitflora.asteriskcraft.entity.terran.MissileTurretEntity;
import net.bitflora.asteriskcraft.stats.UnitStat;
import net.bitflora.asteriskcraft.stats.UnitStats;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;

import java.util.EnumSet;
import org.jetbrains.annotations.Nullable;

/**
 * The Missile Turret's auto-fire: the same shape as {@code entity.ai.zerg.SporeFireGoal} — an
 * instant hitscan salvo once per cooldown at the target its owner's target selector picked — with
 * the same two extra conditions and one of its own.
 *
 * <p>The first is {@link Altitude#isAirborne}, and it is <em>not</em> redundant with the same check
 * inside the turret's {@code FactionTargetGoal}. The altitude rule is positional, so a target can
 * descend after it was acquired; the target selector only re-runs on its own cadence while this goal
 * ticks every tick, so this is what actually makes the turret stop firing the moment a Mutalisk
 * drops out of the sky.
 *
 * <p>The one of its own is {@code isUnderConstruction}: a turret still coming up out of the ground
 * has no missiles to fire, and it is on the halved health pool while it does, so letting it shoot
 * would let a half-built building trade as though it were finished.
 */
public class MissileTurretFireGoal extends Goal {
    private static final UnitStat STAT = UnitStats.MISSILE_TURRET;
    private static final UnitStat.Ranged RANGED = STAT.rangedOrThrow();

    private final MissileTurretEntity turret;
    private int cooldown;

    public MissileTurretFireGoal(MissileTurretEntity turret) {
        this.turret = turret;
        this.setFlags(EnumSet.of(Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        return inRangeTarget() != null;
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
        this.turret.getLookControl().setLookAt(target, 30.0f, 30.0f);
        if (--this.cooldown > 0) {
            return;
        }
        this.cooldown = RANGED.cooldown();
        fireAt(target);
    }

    @Nullable
    private LivingEntity inRangeTarget() {
        if (this.turret.isUnderConstruction()) {
            return null;
        }
        LivingEntity target = this.turret.getTarget();
        if (target == null || !target.isAlive() || !Altitude.isAirborne(target)) {
            return null;
        }
        double reachSq = (double) RANGED.range() * RANGED.range();
        if (this.turret.distanceToSqr(target) > reachSq) {
            return null;
        }
        return this.turret.getSensing().hasLineOfSight(target) ? target : null;
    }

    private void fireAt(LivingEntity target) {
        this.turret.triggerAttackAnimation();
        HitscanAttacks.fire(this.turret, target, STAT.attackDamageOrThrow(),
                AsteriskCraftDamageTypes.LONGBOLT_MISSILE, ParticleTypes.SMOKE,
                AsteriskCraft.MISSILE_TURRET_ATTACK.get());
    }
}
