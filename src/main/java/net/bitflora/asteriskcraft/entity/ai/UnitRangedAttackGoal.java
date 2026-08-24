package net.bitflora.asteriskcraft.entity.ai;

import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.monster.RangedAttackMob;
import org.jetbrains.annotations.Nullable;

import java.util.EnumSet;
import java.util.function.DoubleSupplier;

/**
 * Vanilla's {@code RangedAttackGoal} with a <b>live</b> attack radius: the range is asked for on
 * every tick instead of being frozen at construction.
 *
 * <p>It exists for one reason. A unit shooting from inside a
 * {@link net.bitflora.asteriskcraft.faction.Garrison} reaches one block further than the same unit
 * standing in the open, and vanilla's goal takes {@code attackRadius} into a private final field
 * that both {@code canUse} and {@code tick} read — so there is nothing to override and the bonus
 * cannot be applied to it at all. Swapping the goal in and out of the selector as a unit boards
 * would be the alternative, and mutating a running {@code GoalSelector} to express a stat change is
 * exactly the kind of thing the goal ladder exists to avoid.
 *
 * <p>Everything else is transcribed from vanilla so the feel is identical, including the cadence
 * scaling with distance and the {@code power} argument handed to {@code performRangedAttack}. The one
 * structural difference is that the radius is read <em>once per tick</em> into a local rather than
 * per use, so a bonus that appears or disappears mid-tick can't make the distance test and the
 * cadence maths disagree with each other.
 *
 * <p>Only the Marine is on this today, because only the Marine is both {@code entity.Organic} (so it
 * can be inside anything) and ranged. The Dragoon, Hydralisk and Scout stay on vanilla's goal until
 * one of them has a reason for a live radius; migrating them is a behaviour-neutral pass, not part of
 * this one.
 */
public class UnitRangedAttackGoal extends Goal {
    /** How many ticks of unbroken line of sight before the unit stops closing and holds position. */
    private static final int SETTLE_TICKS = 5;

    private final Mob mob;
    private final RangedAttackMob rangedAttackMob;
    private final double speedModifier;
    private final int attackInterval;
    private final DoubleSupplier attackRadius;

    @Nullable
    private LivingEntity target;
    private int attackTime = -1;
    private int seeTime;

    /**
     * @param attackRadius the unit's current reach in blocks, re-read every tick. For a unit that can
     *                     be garrisoned this is its own range plus
     *                     {@code Garrison.rangeBonusFor(unit)}.
     */
    public UnitRangedAttackGoal(RangedAttackMob mob, double speedModifier, int attackInterval,
                                DoubleSupplier attackRadius) {
        if (!(mob instanceof Mob)) {
            throw new IllegalArgumentException("UnitRangedAttackGoal requires Mob implements RangedAttackMob");
        }
        this.rangedAttackMob = mob;
        this.mob = (Mob) mob;
        this.speedModifier = speedModifier;
        this.attackInterval = attackInterval;
        this.attackRadius = attackRadius;
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        LivingEntity bestTarget = this.mob.getTarget();
        if (bestTarget != null && bestTarget.isAlive()) {
            this.target = bestTarget;
            return true;
        }
        return false;
    }

    @Override
    public boolean canContinueToUse() {
        return this.canUse() || (this.target != null && this.target.isAlive() && !this.mob.getNavigation().isDone());
    }

    @Override
    public void stop() {
        this.target = null;
        this.seeTime = 0;
        this.attackTime = -1;
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public void tick() {
        LivingEntity current = this.target;
        if (current == null) {
            return;
        }
        // Read once per tick: the distance test below and the cadence maths further down have to be
        // talking about the same radius, and a unit can board or be thrown out between two reads.
        float radius = (float) this.attackRadius.getAsDouble();
        double targetDistSqr = this.mob.distanceToSqr(current.getX(), current.getY(), current.getZ());
        boolean hasLineOfSight = this.mob.getSensing().hasLineOfSight(current);
        this.seeTime = hasLineOfSight ? this.seeTime + 1 : 0;

        if (targetDistSqr <= radius * radius && this.seeTime >= SETTLE_TICKS) {
            this.mob.getNavigation().stop();
        } else {
            this.mob.getNavigation().moveTo(current, this.speedModifier);
        }

        this.mob.getLookControl().setLookAt(current, 30.0F, 30.0F);
        if (--this.attackTime == 0) {
            if (!hasLineOfSight) {
                return;
            }
            float reach = (float) Math.sqrt(targetDistSqr) / radius;
            this.rangedAttackMob.performRangedAttack(current, Mth.clamp(reach, 0.1F, 1.0F));
            this.attackTime = this.attackInterval;
        } else if (this.attackTime < 0) {
            this.attackTime = this.attackInterval;
        }
    }
}
