package net.bitflora.asteriskcraft.entity.ai;

import net.bitflora.asteriskcraft.command.CommandAttachments;
import net.bitflora.asteriskcraft.command.CommandOrder;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;
import java.util.Objects;
import org.jetbrains.annotations.Nullable;

/**
 * Drives a unit toward the {@link CommandOrder.Kind#MOVE} destination on its command attachment
 * and clears the order on arrival. High priority in the goal selector so a move order overrides
 * autonomous behaviour, like an RTS move command — including a fight already in progress:
 * {@link CommandAttachments#setOrder} clears the unit's current target the instant a move order
 * lands, so this goal doesn't have to wait out a stale engagement.
 * <p>
 * For the first {@link CommandAttachments#MOVE_FOCUS_TICKS} of an order the march simply wins:
 * {@code FactionTargetGoal} and {@code RetaliateGoal} both stand down (they check
 * {@link CommandAttachments#isMoveFocused}), and this goal drops any target something else manages
 * to set. That window is what makes a move order out of a fight mean anything — clearing the target
 * alone doesn't, since the unit re-acquires the enemy beside it on the next tick and never takes a
 * step, which is what made ordering a retreat look like it did nothing. Once the window lapses the
 * unit fights again: it yields for as long as it has a live attack target, stopping for hostiles it
 * acquires or is struck by along the way instead of walking past them, and resumes the march once
 * that target is gone.
 * <p>
 * It does not dig, and it does not fight {@link SiegeBlockGoal} for the {@link Flag#MOVE} flag:
 * SiegeBlockGoal is installed at a higher priority, so when the path stalls at a breakable block it
 * simply preempts this goal, breaks through, and hands movement back — at which point {@link #start()}
 * resets the no-progress clock. That means a stall against a <em>breakable</em> obstruction never
 * accumulates toward giving up (the digger keeps interrupting it), while a stall against something
 * unbreakable (bedrock, a barrier, the void) is left to the give-up rule below. To keep that handoff
 * working, {@link #tick()} deliberately leaves navigation resolved-done between re-paths rather than
 * holding a path open, so SiegeBlockGoal's {@code obstructionAhead} check can see the stall.
 * Faction-generic — installed on any commandable {@link Mob}.
 *
 * <h2>Not getting stuck</h2>
 * Two rules keep a unit from freezing, both learned from Dragoons stalling on group move orders:
 * <ul>
 *   <li><b>The destination is re-read every tick, not just on {@link #start()}.</b>
 *       {@code GoalSelector} only calls {@code start()} when it freshly acquires a goal, and this
 *       goal's {@link #canContinueToUse()} stays true across an order change — so a second move
 *       order issued to a unit already marching never restarts it. Without the per-tick check the
 *       unit would keep measuring progress against its <em>old</em> destination and abandon the new
 *       order within a tick or two, which is exactly why re-clicking a stuck unit appeared to do
 *       nothing.</li>
 *   <li><b>A stalled unit sidesteps before it gives up.</b> Re-pathing to an unchanged destination
 *       just recomputes the same failing path, so each stalled window sends the unit on a short
 *       detour perpendicular to its heading first. This matters most for units wider than one
 *       pathfinder node (the Dragoon, at 1.1 blocks: see the note on its registration in
 *       {@code AsteriskCraft}), which aim at block seams and are nudged off their waypoint by the
 *       units around them. Only after {@link #GIVE_UP_WINDOWS} stalled windows is the order dropped.
 * </ul>
 * Crowding is attacked at the source too — {@code command/MoveFormation} gives every unit in a
 * squad its own destination instead of sending them all to one block.
 */
public class CommandedMoveGoal extends Goal {
    /** Horizontal arrival radius, squared (~1.5 blocks). */
    private static final double ARRIVE_DIST_SQR = 2.25;
    /**
     * Vertical arrival tolerance, kept separate from the horizontal radius. Measuring a single 3D
     * radius against the destination block would spend most of a 2.25 budget on the one-block
     * difference between a clicked block and the surface a unit stands on, leaving a disc barely
     * wider than a Dragoon — so all but the first arrivals could never satisfy it.
     */
    private static final double ARRIVE_DIST_Y = 1.5;
    /**
     * How close counts as "near enough" once the unit has stopped making ground. Sidestepping to
     * squeeze the last few blocks out of a crowded destination is not worth ten seconds of shuffling,
     * so a stalled unit already this close simply calls the order done.
     */
    private static final double STALLED_ARRIVE_DIST_SQR = 36.0; // 6 blocks

    /** Stalled sampling windows before the order is abandoned — 5 x 40 ticks, i.e. ~10s as before. */
    private static final int GIVE_UP_WINDOWS = 5;

    /** How long a sidestep runs before the unit resumes heading straight at its destination. */
    private static final int DETOUR_TICKS = 20;
    /** How far to the side (and forward) a sidestep aims. */
    private static final double DETOUR_DISTANCE = 2.5;

    private final Mob mob;
    private final double speed;
    private final MoveProgress progress = new MoveProgress();
    @Nullable
    private BlockPos lastTarget;
    @Nullable
    private BlockPos detour;
    private int detourTicks;
    /** Cache backing {@link #destinationY}, so a flyer's heightmap lookup happens once per destination. */
    @Nullable
    private BlockPos settleYTarget;
    private double settleY;

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
        return target != null && !arrived(target) && !shouldYieldToFight();
    }

    @Override
    public boolean canContinueToUse() {
        BlockPos target = target();
        return target != null && !arrived(target) && !shouldYieldToFight();
    }

    /**
     * Whether the unit should break off the march to fight a live attack target instead of walking
     * past it — true whenever it has one, except during the focus window a fresh order opens (see
     * the class doc).
     */
    private boolean shouldYieldToFight() {
        LivingEntity target = this.mob.getTarget();
        return target != null && target.isAlive() && !CommandAttachments.isMoveFocused(this.mob);
    }

    @Override
    public void start() {
        BlockPos target = target();
        if (target == null) {
            return;
        }
        if (Objects.equals(target, this.lastTarget)) {
            // Resuming the same march after SiegeBlockGoal dug an obstruction: the world just changed
            // in our favour, so the stall clock restarts. This is what keeps a breakable wall from
            // ever reaching GIVE_UP_WINDOWS.
            this.progress.reset(distSqr(target));
        } else {
            adoptTarget(target);
        }
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
        if (!Objects.equals(target, this.lastTarget)) {
            // A new order landed mid-march — see the class doc on why start() cannot be relied on.
            adoptTarget(target);
        }
        if (CommandAttachments.isMoveFocused(this.mob) && this.mob.getTarget() != null) {
            // Belt and braces over the targeting goals standing down: whatever set a target during the
            // focus window — a goal this class doesn't know about, vanilla hurt handling — the order
            // still wins, and the attack goals all go quiet the moment there is no target to swing at.
            this.mob.setTarget(null);
        }
        this.mob.getLookControl().setLookAt(target.getX() + 0.5, target.getY() + 0.5, target.getZ() + 0.5);
        if (arrived(target)) {
            finish();
            return;
        }
        if (this.detourTicks > 0 && --this.detourTicks == 0) {
            this.detour = null;
            this.mob.getNavigation().stop(); // resume heading straight at the destination
        }
        if (this.progress.tick(distSqr(target))) {
            if (this.progress.stalledWindows() >= GIVE_UP_WINDOWS
                    || horizontalDistSqr(target) <= STALLED_ARRIVE_DIST_SQR) {
                // Either sustained no-progress that sidestepping never broke — the obstruction is
                // unbreakable or the target unreachable, and nothing preempted us to dig (see class
                // doc) — or we are near enough that jostling for the exact block isn't worth it.
                finish();
                return;
            }
            beginDetour(target);
        }
        if (this.mob.getNavigation().isDone()) {
            moveTo(target);
        }
    }

    /** Switches to a destination this goal has not been tracking, discarding all state from the old one. */
    private void adoptTarget(BlockPos target) {
        this.lastTarget = target;
        this.detour = null;
        this.detourTicks = 0;
        this.progress.reset(distSqr(target));
        this.mob.getNavigation().stop(); // drop the path to wherever we were previously headed
    }

    /**
     * Aims the unit sideways-and-onward for a moment so its next path is a genuinely different one.
     * The side is picked from the entity id so a stalled squad fans out both ways instead of every
     * unit shuffling into the same gap.
     */
    private void beginDetour(BlockPos target) {
        Vec3 toTarget = Vec3.atCenterOf(target).subtract(this.mob.position());
        double len = toTarget.horizontalDistance();
        if (len < 1.0E-4) {
            return;
        }
        double forwardX = toTarget.x / len;
        double forwardZ = toTarget.z / len;
        double side = (this.mob.getId() & 1) == 0 ? 1.0 : -1.0;
        Vec3 dest = this.mob.position()
                .add(-forwardZ * side * DETOUR_DISTANCE, 0.0, forwardX * side * DETOUR_DISTANCE)
                .add(forwardX * DETOUR_DISTANCE, 0.0, forwardZ * DETOUR_DISTANCE);
        // below(), so the detour carries the same meaning as a real move destination — the solid block
        // a unit stands on, not the space it occupies. destinationY() adds the standing height back.
        this.detour = BlockPos.containing(dest).below();
        this.detourTicks = DETOUR_TICKS;
        this.mob.getNavigation().stop(); // force the re-path below to pick the detour up
    }

    private void finish() {
        this.mob.getNavigation().stop();
        this.lastTarget = null;
        this.detour = null;
        this.detourTicks = 0;
        this.progress.reset(Double.MAX_VALUE);
        CommandAttachments.clearOrder(this.mob);
    }

    private void moveTo(BlockPos target) {
        BlockPos dest = this.detourTicks > 0 && this.detour != null ? this.detour : target;
        this.mob.getNavigation().moveTo(dest.getX() + 0.5, destinationY(dest), dest.getZ() + 0.5, this.speed);
    }

    /**
     * The Y a unit under this order actually comes to rest at — feet on top of the destination block,
     * or, for an air unit, the cruising altitude {@link HoverFlyingNavigation} lifts its path to.
     *
     * <p>Without the flight case a hovering unit could <em>never</em> arrive: a Scout parks six blocks
     * up, so measuring against the destination block leaves it permanently outside any sane radius.
     * Air units used to reach their destination and then sit there until the no-progress clock ran
     * out, which quietly made the give-up branch the only way an air move order ever ended.
     */
    private double destinationY(BlockPos target) {
        if (!(this.mob.getNavigation() instanceof HoverFlyingNavigation hover)) {
            return target.getY() + 1;
        }
        if (!target.equals(this.settleYTarget)) {
            this.settleYTarget = target;
            this.settleY = hover.hoverYAt(target);
        }
        return this.settleY;
    }

    private boolean arrived(BlockPos target) {
        return horizontalDistSqr(target) <= ARRIVE_DIST_SQR
                && Math.abs(this.mob.getY() - destinationY(target)) <= ARRIVE_DIST_Y;
    }

    private double horizontalDistSqr(BlockPos target) {
        double dx = this.mob.getX() - (target.getX() + 0.5);
        double dz = this.mob.getZ() - (target.getZ() + 0.5);
        return dx * dx + dz * dz;
    }

    private double distSqr(BlockPos target) {
        return this.mob.position().distanceToSqr(target.getX() + 0.5, destinationY(target), target.getZ() + 0.5);
    }
}
