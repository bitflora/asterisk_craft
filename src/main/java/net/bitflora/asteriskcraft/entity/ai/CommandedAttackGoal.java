package net.bitflora.asteriskcraft.entity.ai;

import net.bitflora.asteriskcraft.command.CommandAttachments;
import net.bitflora.asteriskcraft.command.CommandOrder;
import net.bitflora.asteriskcraft.faction.FactionAttachments;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;

import java.util.EnumSet;
import org.jetbrains.annotations.Nullable;

/**
 * Forces a unit's attack target to the {@link CommandOrder.Kind#ATTACK} target on its command
 * attachment, overriding autonomous {@code FactionTargetGoal} acquisition. Runs at target-selector
 * priority 0 (above the auto-target goal). When the commanded target dies or vanishes it clears the
 * order, so autonomous targeting resumes.
 *
 * <p><b>An order does not override the cloak gate.</b> {@link #engageable()} is {@link #ordered()}
 * plus {@code FactionAttachments.isHostile}, and it is the one the goal actually sets a target from —
 * without it this goal would re-assert an invisible target every tick and undo
 * {@code combat.TargetRetentionHandler}, making a standing order the one way to keep hitting something
 * that has cloaked.
 *
 * <p>The two are kept separate rather than merged because they answer different questions: an
 * unengageable target means <em>stand down for now</em>, while a dead or despawned one means
 * <em>the order is finished</em>. Only {@link #ordered()} decides the latter, so an order on a unit
 * that cloaks goes dormant and resumes the moment a detector picks it up again, instead of being
 * silently thrown away.
 */
public class CommandedAttackGoal extends Goal {
    private final Mob mob;

    public CommandedAttackGoal(Mob mob) {
        this.mob = mob;
        this.setFlags(EnumSet.of(Flag.TARGET));
    }

    /** The entity the order names, if it still exists and is alive. Drives the order's lifecycle. */
    @Nullable
    private LivingEntity ordered() {
        CommandOrder order = CommandAttachments.getOrder(this.mob);
        if (order.kind() != CommandOrder.Kind.ATTACK || order.target().isEmpty()) {
            return null;
        }
        if (this.mob.level() instanceof ServerLevel level
                && level.getEntity(order.target().get()) instanceof LivingEntity target
                && target.isAlive()) {
            return target;
        }
        return null;
    }

    /** The ordered entity, but only while this unit is actually allowed to engage it. */
    @Nullable
    private LivingEntity engageable() {
        LivingEntity target = ordered();
        return target != null && FactionAttachments.isHostile(this.mob, target) ? target : null;
    }

    @Override
    public boolean canUse() {
        return engageable() != null;
    }

    @Override
    public boolean canContinueToUse() {
        return engageable() != null;
    }

    @Override
    public void start() {
        this.mob.setTarget(engageable());
    }

    @Override
    public void tick() {
        this.mob.setTarget(engageable());
    }

    @Override
    public void stop() {
        // Dead or gone: drop the order so autonomous FactionTargetGoal can take over again. A target
        // that is merely out of sight behind a cloak keeps its order, so the unit resumes on reveal.
        if (ordered() == null) {
            CommandAttachments.clearOrder(this.mob);
        }
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }
}
