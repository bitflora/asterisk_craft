package net.bitflora.asteriskcraft.entity.ai.zerg;

import net.bitflora.asteriskcraft.command.CommandAttachments;
import net.bitflora.asteriskcraft.entity.Altitude;
import net.bitflora.asteriskcraft.entity.zerg.LurkerEntity;
import net.bitflora.asteriskcraft.stats.UnitStat;
import net.bitflora.asteriskcraft.stats.UnitStats;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;

import java.util.EnumSet;
import org.jetbrains.annotations.Nullable;

/**
 * Closes on a target the Lurker cannot reach from where it is — the "dig out, walk, dig in" half of
 * the unit, and the reason a Lurker is a unit rather than a building.
 *
 * <p>It does none of the digging itself. It sets the intent (not burrowed) and paths; the entity
 * refuses to move at all until it is fully surfaced, so the observable behaviour — three seconds of
 * climbing out, then a walk — falls out with no state machine here. It sits above
 * {@link LurkerBurrowGoal} in priority, which is what lets it take the {@code MOVE} flag off a
 * dug-in Lurker in the first place; the burrow goal's {@code stop()} is what starts the dig-out.
 *
 * <p><b>The two distances are deliberately different, and that is the whole of the goal.</b> It
 * <em>starts</em> only when the target is outside the Lurker's actual range — nothing closer is worth
 * climbing out of the ground for — and then <em>keeps going</em> until the target is
 * {@code APPROACH_MARGIN} inside it. Collapsing those into one threshold breaks the unit in one
 * direction or the other: at the range edge alone, a Lurker digs in for three seconds only for its
 * target to take one step and be out of reach again, forever; at the inner distance alone, a dug-in
 * Lurker that could already shoot a target drifting between the two would climb out to close a
 * pointless half-block and stop shooting to do it.
 */
public class LurkerApproachGoal extends Goal {
    /** How far inside its range the Lurker walks before it stops and digs in. */
    private static final double APPROACH_MARGIN = 1.5;
    private static final double APPROACH_SPEED = 1.0;

    private static final UnitStat.Ranged RANGED = UnitStats.LURKER.rangedOrThrow();

    /** How close the chase gets before the Lurker will dig back in — the inner of the two distances. */
    private static final double HOLD_RANGE = Math.max(RANGED.range() - APPROACH_MARGIN, 1.0);

    private final LurkerEntity lurker;

    public LurkerApproachGoal(LurkerEntity lurker) {
        this.lurker = lurker;
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        // Beyond the actual range: the Lurker genuinely cannot hit this from here.
        return chaseTarget(RANGED.range()) != null;
    }

    @Override
    public boolean canContinueToUse() {
        // ...but once walking, close all the way inside the range before digging in.
        return chaseTarget(HOLD_RANGE) != null;
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public void start() {
        this.lurker.setWantsBurrowed(false);
    }

    @Override
    public void tick() {
        LivingEntity target = this.lurker.getTarget();
        if (target == null) {
            return;
        }
        this.lurker.getLookControl().setLookAt(target, 30.0f, 30.0f);
        // Nothing to gain from re-pathing while still climbing out: the entity stops navigation on
        // the same tick for as long as any part of it is still underground.
        if (this.lurker.isSurfaced() && this.lurker.getNavigation().isDone()) {
            this.lurker.getNavigation().moveTo(target, APPROACH_SPEED);
        }
    }

    @Override
    public void stop() {
        this.lurker.getNavigation().stop();
    }

    /**
     * A live ground target further away than {@code within}, or null. Airborne targets are excluded
     * for the same reason the Lurker's target selector excludes them — chasing something the spines
     * could never reach would walk the unit off across the map after a Mutalisk.
     *
     * <p>The move-focus check belongs here rather than only in {@link #canUse()} so that a move order
     * arriving mid-chase ends the chase too: a player pulling a Lurker out of a fight must not have
     * it turn round and walk back in.
     *
     * <p><b>Nothing is worth chasing while something is already in reach</b>
     * ({@link LurkerReach#holdingGround}), and that is asked here rather than left to the target
     * selector. {@link LurkerHoldGroundGoal} claiming the near enemy is what makes the unit
     * <em>shoot</em> it; this is what stops the unit digging, and it holds however the target got set —
     * the digging goal should not be reasoning about who called {@code setTarget} last.
     */
    @Nullable
    private LivingEntity chaseTarget(double within) {
        LivingEntity target = this.lurker.getTarget();
        if (target == null || !target.isAlive() || Altitude.isAirborne(target)) {
            return null;
        }
        if (this.lurker.distanceToSqr(target) <= within * within) {
            return null;
        }
        // Last, because it is the only expensive question here: nothing else has to be asked unless
        // there is genuinely a far target this goal would otherwise start walking at.
        return CommandAttachments.isMoveFocused(this.lurker) || LurkerReach.holdingGround(this.lurker)
                ? null : target;
    }
}
