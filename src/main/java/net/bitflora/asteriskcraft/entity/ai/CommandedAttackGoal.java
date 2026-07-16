package net.bitflora.asteriskcraft.entity.ai;

import net.bitflora.asteriskcraft.command.CommandAttachments;
import net.bitflora.asteriskcraft.command.CommandOrder;
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
 */
public class CommandedAttackGoal extends Goal {
    private final Mob mob;

    public CommandedAttackGoal(Mob mob) {
        this.mob = mob;
        this.setFlags(EnumSet.of(Flag.TARGET));
    }

    @Nullable
    private LivingEntity target() {
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

    @Override
    public boolean canUse() {
        return target() != null;
    }

    @Override
    public boolean canContinueToUse() {
        return target() != null;
    }

    @Override
    public void start() {
        this.mob.setTarget(target());
    }

    @Override
    public void tick() {
        this.mob.setTarget(target());
    }

    @Override
    public void stop() {
        // Target is dead/gone: drop the order so autonomous FactionTargetGoal can take over again.
        if (target() == null) {
            CommandAttachments.clearOrder(this.mob);
        }
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }
}
