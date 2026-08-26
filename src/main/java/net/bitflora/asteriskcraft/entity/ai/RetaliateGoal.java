package net.bitflora.asteriskcraft.entity.ai;

import net.bitflora.asteriskcraft.command.CommandAttachments;
import net.bitflora.asteriskcraft.faction.FactionAttachments;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.Goal;

import java.util.EnumSet;
import java.util.function.Predicate;

/**
 * Retaliation: a unit struck by a hostile attacker switches its target to that attacker,
 * taking priority over every other target-selector goal (autonomous {@link FactionTargetGoal}
 * acquisition and a player's {@link CommandedAttackGoal} order alike) — a unit under fire fights
 * back first. Install at the lowest (most urgent) target-selector priority number on any combat
 * unit. Stays active for as long as the attacker remains alive, hostile, and in follow range, so
 * it naturally covers a running fight rather than just the first hit.
 *
 * <p>A unit that can only engage part of the battlefield passes the same narrowing filter it gives
 * {@link FactionTargetGoal} to the second constructor — the Sunken Colony's ground-only rule. Without
 * it, being shot by something it cannot answer would pin its target on that attacker and leave it
 * inert against everything it <em>can</em> hit. Like there, the filter may only ever <em>narrow</em>
 * what {@code isHostile} already allowed.
 *
 * <p>Retaliation is for a unit that is <em>not already fighting</em>. A unit that already holds a
 * live target it can still answer — one that is alive, hostile, passes the same narrowing filter
 * and is still inside follow range — keeps swinging at it rather than turning to face whoever last
 * hit it; being shot in the back mid-fight must not walk a unit off the enemy in front of it, and a
 * crossfire would otherwise flip its target every time a second attacker landed a shot. Only once
 * that fight is over (or has run out of range) does the last attacker become the new target, which
 * is the first thing {@code canUse} then finds. Note that engagement here means a targeted
 * <em>unit</em>: a unit battering a building through {@link SiegeBlockGoal} holds no target and so
 * still turns on an attacker.
 *
 * <p>The one thing that outranks it is a fresh move order ({@link CommandAttachments#isMoveFocused}):
 * a retreat is ordered precisely when a unit is being shot at, so a unit that turned to fight back
 * every time it was hit could never be pulled out of a losing fight. Once that window lapses,
 * retaliation resumes mid-march as normal.
 */
public class RetaliateGoal extends Goal {
    private final Mob mob;
    private final Predicate<LivingEntity> filter;

    public RetaliateGoal(Mob mob) {
        this(mob, attacker -> true);
    }

    public RetaliateGoal(Mob mob, Predicate<LivingEntity> filter) {
        this.mob = mob;
        this.filter = filter;
        this.setFlags(EnumSet.of(Flag.TARGET));
    }

    @Override
    public boolean canUse() {
        LivingEntity attacker = this.mob.getLastHurtByMob();
        return attacker != null && attacker.isAlive()
                && !CommandAttachments.isMoveFocused(this.mob)
                && !isEngaged()
                && FactionAttachments.isHostile(this.mob, attacker)
                && this.filter.test(attacker)
                && withinFollowRange(attacker);
    }

    /**
     * Whether the unit is already in a fight it can still prosecute — a target that is alive, still
     * hostile, still one this unit may engage, and still in reach. A stale or unanswerable target
     * (dead, out of follow range, or one the filter excludes) is not an engagement and does not hold
     * retaliation off.
     */
    private boolean isEngaged() {
        LivingEntity target = this.mob.getTarget();
        return target != null && target.isAlive()
                && FactionAttachments.isHostile(this.mob, target)
                && this.filter.test(target)
                && withinFollowRange(target);
    }

    @Override
    public boolean canContinueToUse() {
        LivingEntity target = this.mob.getTarget();
        return target != null && target.isAlive() && target == this.mob.getLastHurtByMob()
                && this.filter.test(target)
                && withinFollowRange(target);
    }

    @Override
    public void start() {
        this.mob.setTarget(this.mob.getLastHurtByMob());
    }

    private boolean withinFollowRange(LivingEntity attacker) {
        double followRange = this.mob.getAttributeValue(Attributes.FOLLOW_RANGE);
        return this.mob.distanceToSqr(attacker) <= followRange * followRange;
    }
}
