package net.bitflora.asteriskcraft.entity.ai;

/**
 * How long a unit has gone without getting anywhere, backing {@link StuckWanderGoal}'s "give this up
 * and wander instead" rule. Separated from the goal for the same reason as {@link MoveProgress}: the
 * timing is then testable without a live ServerLevel.
 *
 * <p>It measures displacement <em>through the world</em>, not progress toward a destination, which is
 * what {@link MoveProgress} does. That difference is the point: {@code MoveProgress} only runs while
 * {@code CommandedMoveGoal} does, and that goal stands down entirely whenever the unit has a live
 * target — so a unit grinding against a wall chasing something it can never reach is exactly the case
 * that clock cannot see. This one is driven from a goal that outranks everything, so it keeps running
 * whatever else has hold of the unit.
 *
 * <p>Samples on a window rather than per tick because a stuck unit is rarely perfectly still: shoved
 * by its neighbours, sliding off a slab, bobbing in water, it drifts a fraction of a block per tick
 * and would clear any per-tick epsilon while going nowhere. Over a whole second, that drift is still
 * under half a block.
 */
final class StuckClock {
    /** Ticks per displacement sample (1s). */
    static final int SAMPLE_TICKS = 20;
    /** Squared distance a sample must cover to count as having moved (0.5 blocks). */
    static final double EPSILON_SQR = 0.25;
    /** How long a unit must fail to move before it is judged stuck (40s). */
    static final int STUCK_TICKS = 800;

    private double anchorX;
    private double anchorY;
    private double anchorZ;
    private int sampleTicks;
    private int stalledTicks;
    private boolean anchored;

    /**
     * Advances one tick from the unit's current position.
     *
     * @param busy whether the unit is standing still for a good reason (fighting, sieging) — it
     *             re-anchors exactly as movement does, so a legitimate stand-off never trips
     * @return whether the unit has now been stuck for {@link #STUCK_TICKS}
     */
    boolean tick(double x, double y, double z, boolean busy) {
        if (busy) {
            reset(x, y, z);
            return false;
        }
        if (!this.anchored) {
            reset(x, y, z);
            return false;
        }
        if (++this.sampleTicks < SAMPLE_TICKS) {
            return this.stalledTicks >= STUCK_TICKS;
        }
        this.sampleTicks = 0;
        double dx = x - this.anchorX;
        double dy = y - this.anchorY;
        double dz = z - this.anchorZ;
        if (dx * dx + dy * dy + dz * dz >= EPSILON_SQR) {
            // Moved: re-anchor here and start counting again from zero.
            reset(x, y, z);
            return false;
        }
        // Not moved. Re-anchor anyway so the next sample is measured from where the unit actually is
        // — otherwise a unit creeping just under the epsilon each sample eventually clears it against
        // a stale anchor and is credited for going nowhere.
        this.anchorX = x;
        this.anchorY = y;
        this.anchorZ = z;
        this.stalledTicks += SAMPLE_TICKS;
        return this.stalledTicks >= STUCK_TICKS;
    }

    /** Re-anchors at a position and clears the count. */
    void reset(double x, double y, double z) {
        this.anchorX = x;
        this.anchorY = y;
        this.anchorZ = z;
        this.sampleTicks = 0;
        this.stalledTicks = 0;
        this.anchored = true;
    }

    int stalledTicks() {
        return this.stalledTicks;
    }
}
