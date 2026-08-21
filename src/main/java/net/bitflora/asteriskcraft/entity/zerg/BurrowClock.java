package net.bitflora.asteriskcraft.entity.zerg;

/**
 * The Lurker's burrow state, as one number: how far down it currently is, from {@code 0} (standing
 * on the surface) to {@link #TRANSITION_TICKS} (fully dug in). Digging either way costs the same
 * three seconds, one tick of depth at a time.
 *
 * <p>Everything the rest of the unit asks falls out of that single value, which is why it is worth
 * having rather than a state enum plus a timer:
 *
 * <table border="1">
 *   <caption>What each depth means</caption>
 *   <tr><th>depth</th><th>move</th><th>attack</th><th>cloaked</th></tr>
 *   <tr><td>{@code 0}</td><td>yes</td><td>no</td><td>no</td></tr>
 *   <tr><td>between</td><td>no</td><td>no</td><td>no</td></tr>
 *   <tr><td>{@code TRANSITION_TICKS}</td><td>no</td><td>yes</td><td>yes</td></tr>
 * </table>
 *
 * <p>Being caught halfway is therefore the worst place to be — neither mobile nor armed nor hidden —
 * which is the whole cost of the mechanic, and it lands automatically without anything checking a
 * direction. {@link #wantsBurrowed} is the only other state, and it is intent, not position: goals
 * set it and the clock walks toward it, so an order that arrives mid-dig reverses from wherever the
 * unit had got to instead of teleporting it or restarting the count.
 *
 * <p>Deliberately free of any Minecraft type. The entity owns one of these, syncs {@link #depth()}
 * for the client's sake and persists both fields; the rule itself is plain arithmetic and is tested
 * as such.
 */
public final class BurrowClock {
    /** How long a dig takes, in either direction — three seconds. */
    public static final int TRANSITION_TICKS = 60;

    private int depth;
    private boolean wantsBurrowed;

    /** A freshly produced Lurker stands on the surface. */
    public BurrowClock() {
    }

    /** Restores a saved state — used by the entity's load path, not by gameplay. */
    public void restore(int depth, boolean wantsBurrowed) {
        this.depth = Math.clamp(depth, 0, TRANSITION_TICKS);
        this.wantsBurrowed = wantsBurrowed;
    }

    /** Where the unit is trying to get to. Goals set this; the clock does the travelling. */
    public void setWantsBurrowed(boolean wantsBurrowed) {
        this.wantsBurrowed = wantsBurrowed;
    }

    public boolean wantsBurrowed() {
        return this.wantsBurrowed;
    }

    /**
     * Advances one tick toward {@link #wantsBurrowed()}.
     *
     * @return whether {@link #depth()} changed, so the caller only writes synced data on the ticks
     *         that moved — 57 of every 60 ticks a settled Lurker does nothing at all
     */
    public boolean tick() {
        int target = this.wantsBurrowed ? TRANSITION_TICKS : 0;
        if (this.depth == target) {
            return false;
        }
        this.depth += this.wantsBurrowed ? 1 : -1;
        return true;
    }

    /** How far down, in ticks of digging. */
    public int depth() {
        return this.depth;
    }

    /** How far down as 0→1, which is what the renderer sinks the model by. */
    public float fraction() {
        return (float) this.depth / TRANSITION_TICKS;
    }

    /** Fully dug in: the only state in which the Lurker is armed and hidden. */
    public boolean isBurrowed() {
        return this.depth == TRANSITION_TICKS;
    }

    /** Fully out: the only state in which the Lurker may move. */
    public boolean isSurfaced() {
        return this.depth == 0;
    }

    /**
     * Whether the unit is mid-dig. The gap between {@link #isSurfaced()} and {@link #isBurrowed()},
     * named because "can't do anything" is a state goals genuinely have to wait out.
     */
    public boolean isDigging() {
        return !isSurfaced() && !isBurrowed();
    }

    /**
     * Whether this tick is the first of a new dig, in either direction — the moment to play the
     * burrow bark. True exactly once per direction change: it reads the depth the clock is
     * <em>leaving</em>, so call it before {@link #tick()}.
     */
    public boolean isAboutToStartDigging() {
        return this.wantsBurrowed ? isSurfaced() : isBurrowed();
    }
}
