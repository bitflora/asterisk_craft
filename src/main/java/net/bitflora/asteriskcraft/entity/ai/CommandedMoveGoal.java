package net.bitflora.asteriskcraft.entity.ai;

import net.bitflora.asteriskcraft.command.CommandAttachments;
import net.bitflora.asteriskcraft.command.CommandOrder;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;

import java.util.EnumSet;
import org.jetbrains.annotations.Nullable;

/**
 * Drives a unit toward the {@link CommandOrder.Kind#MOVE} destination on its command attachment
 * and clears the order on arrival (or after it stops making progress). High priority in the
 * goal selector so a move order overrides autonomous behaviour, like an RTS move command.
 * Faction-generic — installed on any commandable {@link Mob}.
 */
public class CommandedMoveGoal extends Goal {
    private static final double ARRIVE_DIST_SQR = 2.25; // ~1.5 blocks
    private static final int NO_PROGRESS_LIMIT = 100;   // ~5s without getting closer → give up

    private final Mob mob;
    private final double speed;
    private double lastDistSqr;
    private int noProgressTicks;

    public CommandedMoveGoal(Mob mob, double speed) {
        this.mob = mob;
        this.speed = speed;
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Nullable
    private BlockPos target() {
        CommandOrder order = CommandAttachments.getOrder(this.mob);
        return order.kind() == CommandOrder.Kind.MOVE ? order.pos().orElse(null) : null;
    }

    @Override
    public boolean canUse() {
        BlockPos target = target();
        return target != null && !arrived(target);
    }

    @Override
    public boolean canContinueToUse() {
        BlockPos target = target();
        return target != null && !arrived(target);
    }

    @Override
    public void start() {
        this.lastDistSqr = Double.MAX_VALUE;
        this.noProgressTicks = 0;
        moveTo(target());
    }

    @Override
    public void stop() {
        this.mob.getNavigation().stop();
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public void tick() {
        BlockPos target = target();
        if (target == null) {
            return;
        }
        this.mob.getLookControl().setLookAt(target.getX() + 0.5, target.getY() + 0.5, target.getZ() + 0.5);
        if (arrived(target)) {
            finish();
            return;
        }
        double dist = distSqr(target);
        if (dist < this.lastDistSqr - 0.25) {
            this.lastDistSqr = dist;
            this.noProgressTicks = 0;
        } else if (++this.noProgressTicks > NO_PROGRESS_LIMIT) {
            finish(); // unreachable / stuck — drop the order rather than thrash forever
            return;
        }
        if (this.mob.getNavigation().isDone()) {
            moveTo(target);
        }
    }

    private void finish() {
        this.mob.getNavigation().stop();
        CommandAttachments.clearOrder(this.mob);
    }

    private void moveTo(@Nullable BlockPos target) {
        if (target != null) {
            this.mob.getNavigation().moveTo(target.getX() + 0.5, target.getY(), target.getZ() + 0.5, this.speed);
        }
    }

    private boolean arrived(BlockPos target) {
        return distSqr(target) <= ARRIVE_DIST_SQR;
    }

    private double distSqr(BlockPos target) {
        return this.mob.position().distanceToSqr(target.getX() + 0.5, target.getY(), target.getZ() + 0.5);
    }
}
