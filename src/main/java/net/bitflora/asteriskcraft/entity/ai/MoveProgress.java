package net.bitflora.asteriskcraft.entity.ai;

/**
 * The "are we still getting anywhere?" clock behind {@link CommandedMoveGoal}'s give-up rule, split
 * out from the goal so it is pure — no level, no entity, no navigation — and therefore unit-testable
 * without a live {@code ServerLevel} (the same split as {@code BuildingTemplates.planSitePrep}).
 *
 * <p>Progress is judged over fixed <em>sampling windows</em> rather than against an all-time-best
 * distance, and that distinction is the whole point of the class. An all-time-best record only ever
 * ratchets downward, so a unit shoved backwards by the crowd around a shared destination can never
 * register progress again no matter how well it recovers — it accumulates the give-up clock while
 * genuinely marching. Re-baselining at every window boundary, whatever the verdict was, means a unit
 * only gives up when it truly stops making ground over a sustained stretch.
 */
final class MoveProgress {
    /** How long a sampling window runs before progress is judged. */
    static final int WINDOW_TICKS = 40; // 2s

    /** How much closer (squared) the unit must be at the end of a window for it to count as ground made. */
    static final double EPSILON_SQR = 0.25;

    private double windowStartDistSqr = Double.MAX_VALUE;
    private int windowTicks;
    private int stalledWindows;

    /** Restarts the clock from {@code distSqr} — on a fresh order, a new destination, or a resumed march. */
    void reset(double distSqr) {
        this.windowStartDistSqr = distSqr;
        this.windowTicks = 0;
        this.stalledWindows = 0;
    }

    /**
     * Advances the clock one tick.
     *
     * @param distSqr the unit's current squared distance to its destination
     * @return true if this tick closed a sampling window in which the unit failed to make ground —
     *         the caller's cue to check {@link #stalledWindows()} and react
     */
    boolean tick(double distSqr) {
        if (++this.windowTicks < WINDOW_TICKS) {
            return false;
        }
        this.windowTicks = 0;
        boolean progressed = distSqr < this.windowStartDistSqr - EPSILON_SQR;
        // Re-baseline every window, whatever the verdict — see the class doc.
        this.windowStartDistSqr = distSqr;
        if (progressed) {
            this.stalledWindows = 0;
            return false;
        }
        this.stalledWindows++;
        return true;
    }

    /** Consecutive sampling windows closed without the unit making ground; 0 while it is still closing. */
    int stalledWindows() {
        return this.stalledWindows;
    }
}
