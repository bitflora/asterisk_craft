package net.bitflora.asteriskcraft.entity.ai;

import net.bitflora.asteriskcraft.command.CommandAttachments;
import net.bitflora.asteriskcraft.command.CommandOrder;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;

import java.util.EnumSet;
import java.util.Objects;
import org.jetbrains.annotations.Nullable;

/**
 * Drives a unit toward the {@link CommandOrder.Kind#MOVE} destination on its command attachment
 * and clears the order on arrival. High priority in the goal selector so a move order overrides
 * autonomous behaviour, like an RTS move command — including a fight already in progress:
 * {@link CommandAttachments#setOrder} clears the unit's current target the instant a move order
 * lands, so this goal doesn't have to wait out a stale engagement. From there it yields for as
 * long as the unit has a live attack target, so a unit marching under a move order still stops to
 * fight hostiles it acquires or is struck by (via {@code FactionTargetGoal} or
 * {@code RetaliateGoal}) along the way instead of walking past them; it resumes the march once
 * that target is gone.
 * <p>
 * It does not dig, and it does not fight {@link SiegeBlockGoal} for the {@link Flag#MOVE} flag:
 * SiegeBlockGoal is installed at a higher priority, so when the path stalls at a breakable block it
 * simply preempts this goal, breaks through, and hands movement back — at which point {@link #start()}
 * resets the no-progress counter. That means a stall against a <em>breakable</em> obstruction never
 * accumulates toward giving up (the digger keeps interrupting it), while a stall against something
 * unbreakable (bedrock, a barrier, the void) has nothing to interrupt it and, after
 * {@link #GIVE_UP_TICKS} of continuous no-progress, clears the order so the unit isn't pinned forever.
 * Faction-generic — installed on any commandable {@link Mob}.
 */
public class CommandedMoveGoal extends Goal {
    private static final double ARRIVE_DIST_SQR = 2.25; // ~1.5 blocks
    private static final int GIVE_UP_TICKS = 200;       // ~10s of uninterrupted no-progress → abandon the order

    private final Mob mob;
    private final double speed;
    private double lastDistSqr = Double.MAX_VALUE;
    private int noProgressTicks;
    @Nullable
    private BlockPos lastTarget;

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
        return target != null && !arrived(target) && !hasLiveTarget();
    }

    @Override
    public boolean canContinueToUse() {
        BlockPos target = target();
        return target != null && !arrived(target) && !hasLiveTarget();
    }

    /** Whether the unit is currently engaged with a live attack target it should fight instead of marching past. */
    private boolean hasLiveTarget() {
        LivingEntity target = this.mob.getTarget();
        return target != null && target.isAlive();
    }

    @Override
    public void start() {
        BlockPos target = target();
        if (!Objects.equals(target, this.lastTarget)) {
            // A genuinely new order (not just resuming after SiegeBlockGoal dug an obstruction) —
            // reset the progress baseline so stale stall history doesn't carry over.
            this.lastTarget = target;
            this.lastDistSqr = Double.MAX_VALUE;
        }
        // Resuming after a dig counts as a fresh attempt: the world just changed in our favour, so the
        // no-progress clock restarts. This is what keeps a breakable wall from ever reaching GIVE_UP_TICKS.
        this.noProgressTicks = 0;
        moveTo(target);
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
            this.noProgressTicks = 0; // getting closer than ever — reset the give-up clock
        } else if (++this.noProgressTicks > GIVE_UP_TICKS) {
            // Continuous no-progress with nothing preempting us to dig (see class doc) — the
            // obstruction is unbreakable or the target is simply unreachable. Abandon the order.
            finish();
            return;
        }
        if (this.mob.getNavigation().isDone()) {
            // Stalled but still viable: leave navigation resolved-done between re-paths so a
            // higher-priority SiegeBlockGoal can take over and dig if a breakable block is in the way.
            moveTo(target);
        }
    }

    private void finish() {
        this.mob.getNavigation().stop();
        this.lastTarget = null;
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
