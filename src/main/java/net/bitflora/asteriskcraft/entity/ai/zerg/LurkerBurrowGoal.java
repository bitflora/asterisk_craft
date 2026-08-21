package net.bitflora.asteriskcraft.entity.ai.zerg;

import net.bitflora.asteriskcraft.command.CommandAttachments;
import net.bitflora.asteriskcraft.entity.Altitude;
import net.bitflora.asteriskcraft.entity.zerg.LurkerEntity;
import net.bitflora.asteriskcraft.stats.UnitStat;
import net.bitflora.asteriskcraft.stats.UnitStats;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;

import java.util.EnumSet;

/**
 * The Lurker's resting state: dig in, and stay in.
 *
 * <p><b>It arbitrates by priority, not by asking anything.</b> Installed <em>below</em> every goal
 * that needs the unit walking — {@code SiegeBlockGoal}, {@code CommandedMoveGoal},
 * {@link LurkerApproachGoal} — and holding {@link Flag#MOVE} and {@link Flag#JUMP} for as long as it
 * runs. {@code GoalSelector} only calls {@code canUse()} on a goal whose flags it can claim and hands
 * a held flag over only to a strictly lower priority number (see docs/neoforge-api-notes.md), so this
 * goal simply never starts while something more important wants the unit moving, and is preempted —
 * surfacing it, via {@link #stop()} — the moment one of them does. No goal here knows another exists.
 *
 * <p>Holding {@code MOVE} is also what physically pins the unit: the lower-priority
 * {@code GuardGoal} can't stroll a dug-in Lurker away, which is deliberate. A Lurker guards by being
 * buried.
 *
 * <p>Two reasons to be dug in, and they are not the same question:
 * <ul>
 *   <li>there is a ground target inside attack range — dig in and shoot it;</li>
 *   <li>there is nothing to do at all, and there has been for {@code IDLE_BURROW_DELAY} — dig in and
 *       wait. This is the ambush half, and what makes an unattended Lurker a minefield rather than a
 *       statue.</li>
 * </ul>
 * The delay exists so a Lurker that has just finished a march doesn't dig in for the half-second
 * before its next order lands, spending three seconds down and three back up for nothing.
 *
 * <p>{@link #canContinueToUse()} deliberately asks a <em>weaker</em> question than {@link #canUse()}:
 * a target in range, <b>or no target at all</b>. So a dug-in Lurker stays dug in through the quiet,
 * and surfaces exactly when it acquires something it would have to walk to.
 */
public class LurkerBurrowGoal extends Goal {
    /** How long a Lurker must have nothing to do before it digs in to wait (3s). */
    private static final int IDLE_BURROW_DELAY = 60;

    private static final UnitStat.Ranged RANGED = UnitStats.LURKER.rangedOrThrow();

    private final LurkerEntity lurker;
    private int idleTicks;

    public LurkerBurrowGoal(LurkerEntity lurker) {
        this.lurker = lurker;
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.JUMP));
    }

    @Override
    public boolean canUse() {
        // Advancing the idle clock from canUse matches GuardGoal and SiegeBlockGoal, which run their
        // own cooldowns here: GoalSelector calls it every tick on a goal that isn't running. It is
        // also correct that the clock freezes while a higher-priority goal holds MOVE — a unit under
        // orders is not idle.
        this.idleTicks = hasNothingToDo() ? this.idleTicks + 1 : 0;
        if (CommandAttachments.isMoveFocused(this.lurker)) {
            return false;
        }
        return shouldStayDown() || this.idleTicks >= IDLE_BURROW_DELAY;
    }

    @Override
    public boolean canContinueToUse() {
        if (CommandAttachments.isMoveFocused(this.lurker)) {
            return false;
        }
        return shouldStayDown() || this.lurker.getTarget() == null;
    }

    @Override
    public void start() {
        this.lurker.setWantsBurrowed(true);
    }

    @Override
    public void stop() {
        this.lurker.setWantsBurrowed(false);
        this.idleTicks = 0;
    }

    /** Nothing acquired and nowhere to be — the condition the ambush timer counts. */
    private boolean hasNothingToDo() {
        return this.lurker.getTarget() == null && this.lurker.getNavigation().isDone();
    }

    /**
     * A reason to be in the ground: either the acquired target is in range, or — whatever the target
     * selector currently says — something else strikeable is. The second half is what stops a distant
     * attacker levering the unit out on its own: {@code canContinueToUse} releasing on an out-of-range
     * target surfaces it through this goal's own {@code stop()}, with no other goal involved, and
     * {@code canUse} would then refuse to re-bury it because it still has a target it cannot reach.
     * That is a dig-out that never touches {@link LurkerApproachGoal}, so gating the chase alone was
     * never going to be enough.
     */
    private boolean shouldStayDown() {
        return targetInRange() || LurkerReach.anythingInReach(this.lurker);
    }

    /**
     * Whether something the Lurker could actually shoot is close enough to shoot. The airborne
     * exclusion is the same one its target selector applies: a flyer overhead is not a reason to dig
     * in, because the spines would never reach it.
     */
    private boolean targetInRange() {
        LivingEntity target = this.lurker.getTarget();
        if (target == null || !target.isAlive() || Altitude.isAirborne(target)) {
            return false;
        }
        double reachSq = (double) RANGED.range() * RANGED.range();
        return this.lurker.distanceToSqr(target) <= reachSq;
    }
}
