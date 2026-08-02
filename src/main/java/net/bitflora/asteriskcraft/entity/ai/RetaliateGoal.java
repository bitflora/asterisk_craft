package net.bitflora.asteriskcraft.entity.ai;

import net.bitflora.asteriskcraft.faction.FactionAttachments;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.Goal;

import java.util.EnumSet;

/**
 * Retaliation: a unit struck by a hostile attacker switches its target to that attacker,
 * taking priority over every other target-selector goal (autonomous {@link FactionTargetGoal}
 * acquisition and a player's {@link CommandedAttackGoal} order alike) — a unit under fire fights
 * back first. Install at the lowest (most urgent) target-selector priority number on any combat
 * unit. Stays active for as long as the attacker remains alive, hostile, and in follow range, so
 * it naturally covers a running fight rather than just the first hit.
 */
public class RetaliateGoal extends Goal {
    private final Mob mob;

    public RetaliateGoal(Mob mob) {
        this.mob = mob;
        this.setFlags(EnumSet.of(Flag.TARGET));
    }

    @Override
    public boolean canUse() {
        LivingEntity attacker = this.mob.getLastHurtByMob();
        return attacker != null && attacker.isAlive()
                && FactionAttachments.isHostile(this.mob, attacker)
                && withinFollowRange(attacker);
    }

    @Override
    public boolean canContinueToUse() {
        LivingEntity target = this.mob.getTarget();
        return target != null && target.isAlive() && target == this.mob.getLastHurtByMob()
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
