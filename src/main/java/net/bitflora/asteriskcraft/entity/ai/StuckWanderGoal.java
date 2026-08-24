package net.bitflora.asteriskcraft.entity.ai;

import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;

import java.util.EnumSet;
import java.util.function.BooleanSupplier;

/**
 * The last resort for a unit that has stopped getting anywhere: after {@link StuckClock#STUCK_TICKS}
 * of no movement it seizes control and wanders to random nearby points for a few seconds, breaking
 * whatever geometry, crowd, or unreachable target had it pinned.
 *
 * <p><b>Install at priority -1</b>, above {@code SiegeBlockGoal}'s 0. Preemption is the whole point:
 * the goals that get a unit stuck are the ones already holding {@link Flag#MOVE}, so a goal that
 * merely fills a yield window could never take over from them ({@code WrappedGoal.canBeReplacedBy}
 * hands a flag over only to a strictly lower priority number). The same rule is what keeps the clock
 * running: {@code GoalSelector} skips {@code canUse()} on any goal whose flags it cannot claim, so at
 * any priority above 0 this goal would stop being asked precisely while the unit was stuck.
 *
 * <p>It deliberately leaves the unit's command order and target alone. The burst is a shove, not a
 * desertion — when it ends, the unit's ordinary goals pick the march or the fight back up from a new
 * position, and if that is still hopeless the clock simply trips again 40s later.
 *
 * <p>The wander goes through {@code getNavigation().moveTo}, like {@link GuardGoal}'s stroll, so the
 * Mutalisk needs nothing of its own here: {@link HoverFlyingNavigation} lifts the destination to
 * cruising altitude on the way past. Don't reimplement hovering in this class.
 */
public class StuckWanderGoal extends Goal {
    /** How long a wander burst lasts before ordinary goals get the unit back (10s). */
    static final int WANDER_TICKS = 200;
    /** How far the unit must get from where it broke loose for the burst to end early (6 blocks). */
    private static final double ESCAPED_DIST_SQR = 36.0;
    private static final int REPICK_TICKS = 40;
    private static final double MIN_RADIUS = 3.0;
    private static final double MAX_RADIUS = 10.0;
    private static final double WANDER_SPEED = 1.0;
    /** Default distance at which a melee unit counts as engaged rather than stuck. */
    private static final double MELEE_BUSY_RANGE = 3.0;

    private final Mob mob;
    private final double busyRangeSqr;
    private final BooleanSupplier alsoBusy;
    private final StuckClock clock = new StuckClock();
    private int wanderTicks;
    private int repickTicks;
    private double startX;
    private double startZ;

    /** For a melee unit, which counts as engaged inside its own reach. */
    public StuckWanderGoal(Mob mob) {
        this(mob, MELEE_BUSY_RANGE);
    }

    /**
     * @param busyRange the unit's attack range — inside it, with line of sight, standing still is
     *                  fighting rather than being stuck
     */
    public StuckWanderGoal(Mob mob, double busyRange) {
        this(mob, busyRange, () -> false);
    }

    /**
     * For a unit whose ordinary behaviour includes standing perfectly still for reasons neither
     * signal below can see — the Lurker, which is motionless the entire time it is burrowed and
     * would otherwise be judged stuck every 40 seconds and dig itself out to wander.
     *
     * @param busyRange  as above
     * @param alsoBusy   an extra reason to be standing still, OR-ed into the two built-in ones. Like
     *                   the narrowing predicates on {@code FactionTargetGoal} and
     *                   {@code RetaliateGoal}, it may only ever <em>add</em> patience, never remove
     *                   it: a unit this returns false for is judged exactly as it was before.
     */
    public StuckWanderGoal(Mob mob, double busyRange, BooleanSupplier alsoBusy) {
        this.mob = mob;
        this.busyRangeSqr = busyRange * busyRange;
        this.alsoBusy = alsoBusy;
        this.setFlags(EnumSet.of(Flag.MOVE));
    }

    @Override
    public boolean canUse() {
        // Advancing the clock from canUse matches GuardGoal and SiegeBlockGoal, which run their own
        // cooldowns here: GoalSelector calls it every tick on a goal that isn't running.
        return this.clock.tick(this.mob.getX(), this.mob.getY(), this.mob.getZ(), busy());
    }

    /**
     * Whether the unit is standing still for a good reason. Three signals, because no one of them
     * covers every unit: {@code swinging} catches a melee unit landing hits and a unit battering a
     * building (both go through {@code Mob.swing}), while a ranged unit does neither — vanilla's
     * {@code RangedAttackGoal} stops its navigation and fires without any swing, so a Hydralisk
     * shooting something down would otherwise read as pinned. A unit may name a fourth of its own
     * through the three-argument constructor.
     *
     * <p>The third is {@code isPassenger()}, and unlike the Lurker's it is universally true rather
     * than per-unit, which is why it is built in rather than passed: a unit that isn't moving because
     * it is riding something isn't stuck — its position is not its own, and no amount of wandering
     * would change it. Left out, a Marine garrisoned in a Bunker would be judged stuck every 40
     * seconds and this goal (priority -1, holding {@code MOVE}) would preempt the
     * {@code RangedAttackGoal} it is in there to run, so the whole garrison would fall silent in
     * bursts.
     */
    private boolean busy() {
        if (this.mob.swinging || this.mob.isPassenger() || this.alsoBusy.getAsBoolean()) {
            return true;
        }
        LivingEntity target = this.mob.getTarget();
        return target != null && target.isAlive()
                && this.mob.distanceToSqr(target) <= this.busyRangeSqr
                && this.mob.getSensing().hasLineOfSight(target);
    }

    @Override
    public boolean canContinueToUse() {
        return this.wanderTicks > 0 && !escaped();
    }

    /** True once the unit has put real ground between itself and where it was stuck. */
    private boolean escaped() {
        double dx = this.mob.getX() - this.startX;
        double dz = this.mob.getZ() - this.startZ;
        return dx * dx + dz * dz >= ESCAPED_DIST_SQR;
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public void start() {
        this.startX = this.mob.getX();
        this.startZ = this.mob.getZ();
        this.wanderTicks = WANDER_TICKS;
        pickPoint();
    }

    @Override
    public void stop() {
        this.mob.getNavigation().stop();
        // Full patience again: the unit gets another 40s to make something of its orders before it
        // is judged stuck a second time.
        this.clock.reset(this.mob.getX(), this.mob.getY(), this.mob.getZ());
    }

    @Override
    public void tick() {
        this.wanderTicks--;
        if (--this.repickTicks <= 0 || this.mob.getNavigation().isDone()) {
            pickPoint();
        }
    }

    private void pickPoint() {
        this.repickTicks = REPICK_TICKS;
        RandomSource random = this.mob.getRandom();
        double angle = random.nextDouble() * Math.PI * 2.0;
        double dist = MIN_RADIUS + random.nextDouble() * (MAX_RADIUS - MIN_RADIUS);
        this.mob.getNavigation().moveTo(
                this.mob.getX() + Math.cos(angle) * dist,
                this.mob.getY(),
                this.mob.getZ() + Math.sin(angle) * dist,
                WANDER_SPEED);
    }
}
