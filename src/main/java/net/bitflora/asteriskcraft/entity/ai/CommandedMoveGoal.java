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
 * autonomous behaviour, like an RTS move command — except it yields for as long as the unit has a
 * live attack target, so a unit marching under a move order still stops to fight hostiles it
 * acquires (via {@code FactionTargetGoal} or {@code RetaliateGoal}) along the way instead of
 * walking past them; it resumes the march once that target is gone.
 * <p>
 * It also yields — without clearing the order — while stuck making no progress, so that
 * {@link SiegeBlockGoal} (lower priority, same {@link Flag#MOVE} flag) can dig through whatever is
 * blocking the path. Only after several such stall/dig cycles produce zero net progress (e.g. the
 * obstruction is unbreakable bedrock) does it finally give up and clear the order, so a unit isn't
 * pinned forever. Faction-generic — installed on any commandable {@link Mob}.
 */
public class CommandedMoveGoal extends Goal {
    private static final double ARRIVE_DIST_SQR = 2.25; // ~1.5 blocks
    private static final int NO_PROGRESS_LIMIT = 100;   // ~5s without getting closer → yield
    private static final int STUCK_BACKOFF_TICKS = 40;  // ~2s yielded to let SiegeBlockGoal dig
    private static final int MAX_BACKOFF_CYCLES = 6;    // consecutive stalls with zero net progress → give up

    private final Mob mob;
    private final double speed;
    private double lastDistSqr = Double.MAX_VALUE;
    private int noProgressTicks;
    private int stuckBackoffTicks;
    private int backoffCycles;
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
        if (this.stuckBackoffTicks > 0) {
            this.stuckBackoffTicks--;
            return false; // yielded — let a lower-priority goal (e.g. SiegeBlockGoal) use Flag.MOVE
        }
        BlockPos target = target();
        return target != null && !arrived(target) && !hasLiveTarget();
    }

    @Override
    public boolean canContinueToUse() {
        if (this.stuckBackoffTicks > 0) {
            return false;
        }
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
            // A genuinely new order (not just resuming after a stuck-backoff yield) — reset the
            // progress baseline and give-up counter so stale stall history doesn't carry over.
            this.lastTarget = target;
            this.lastDistSqr = Double.MAX_VALUE;
            this.backoffCycles = 0;
        }
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
            this.noProgressTicks = 0;
            this.backoffCycles = 0; // real progress since the last stall — reset the give-up counter
        } else if (++this.noProgressTicks > NO_PROGRESS_LIMIT) {
            this.noProgressTicks = 0;
            this.mob.getNavigation().stop();
            if (++this.backoffCycles > MAX_BACKOFF_CYCLES) {
                finish(); // several stall/dig cycles with zero net progress — truly stuck, give up
                return;
            }
            this.stuckBackoffTicks = STUCK_BACKOFF_TICKS; // yield MOVE so SiegeBlockGoal can dig
            return;
        }
        if (this.mob.getNavigation().isDone()) {
            moveTo(target);
        }
    }

    private void finish() {
        this.mob.getNavigation().stop();
        this.stuckBackoffTicks = 0;
        this.backoffCycles = 0;
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
