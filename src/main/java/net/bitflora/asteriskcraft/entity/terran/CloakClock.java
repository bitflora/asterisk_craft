package net.bitflora.asteriskcraft.entity.terran;

/**
 * The Ghost's reactive cloak, as one signed number: how many ticks of cloak are left, or — once
 * that has run out — how many ticks of lockout are left before it may cloak again.
 *
 * <table border="1">
 *   <caption>What the one number means</caption>
 *   <tr><th>value</th><th>cloaked</th><th>a hit does</th></tr>
 *   <tr><td>{@code > 0}</td><td>yes</td><td>nothing — the minute does not extend</td></tr>
 *   <tr><td>{@code 0}</td><td>no</td><td>engages the cloak for {@link #CLOAK_TICKS}</td></tr>
 *   <tr><td>{@code < 0}</td><td>no</td><td>nothing — still locked out</td></tr>
 * </table>
 *
 * <p>One field rather than a state enum plus two timers, for the reason
 * {@code entity.zerg.BurrowClock} is one depth: everything the unit asks falls out of the sign and
 * the magnitude, so there is no pair of values that can disagree with each other, and the whole
 * state syncs to the client as a single int — which it must, since the cloak gate runs on the
 * server and the render decision on the client.
 *
 * <p><b>A hit while already cloaked deliberately does not refresh the minute.</b> A Ghost standing
 * inside a detector's envelope is being hit continuously; refreshing would hand it a cloak that
 * never ends in exactly the situation the cloak is supposed to have failed in.
 *
 * <p>The lockout is the cost, and it is charged on <em>coming out</em> rather than on going in:
 * a Ghost that has just been forced to hide is defenceless for the two minutes after it reappears,
 * which is what stops the cloak being a free second health bar on every engagement.
 *
 * <p>Deliberately free of any Minecraft type. The entity owns one of these, syncs {@link #value()}
 * for the client's sake and persists it; the rule itself is plain arithmetic and is tested as such.
 */
public final class CloakClock {
    /** How long one cloak lasts — one minute. */
    public static final int CLOAK_TICKS = 20 * 60;
    /** How long after a cloak drops before another may be triggered — two minutes. */
    public static final int COOLDOWN_TICKS = 20 * 120;

    private int value;

    /** A freshly produced Ghost is uncloaked and ready. */
    public CloakClock() {
    }

    /** Restores a saved state — used by the entity's load path, not by gameplay. */
    public void restore(int value) {
        this.value = Math.clamp(value, -COOLDOWN_TICKS, CLOAK_TICKS);
    }

    /**
     * Reacts to the Ghost having been hurt.
     *
     * @return whether the cloak came up on this hit, so the caller can bark or write synced data
     *         only when something actually happened
     */
    public boolean onDamaged() {
        if (this.value != 0) {
            return false;
        }
        this.value = CLOAK_TICKS;
        return true;
    }

    /**
     * Advances one tick. A cloak that runs out on this tick rolls straight into the lockout, so
     * there is never a tick on which the unit is both uncloaked and able to re-cloak.
     *
     * @return whether {@link #value()} changed, so the caller only writes synced data on the ticks
     *         that moved — which is none of them for a Ghost that has never been shot at
     */
    public boolean tick() {
        if (this.value == 0) {
            return false;
        }
        if (this.value > 0) {
            this.value--;
            if (this.value == 0) {
                this.value = -COOLDOWN_TICKS;
            }
        } else {
            this.value++;
        }
        return true;
    }

    /** The whole state, as the entity syncs and saves it. */
    public int value() {
        return this.value;
    }

    /** Whether the cloak is up right now — the entire content of {@code Cloaked.isCloakActive}. */
    public boolean isCloaked() {
        return this.value > 0;
    }

    /** Whether a hit right now would engage the cloak. */
    public boolean isReady() {
        return this.value == 0;
    }
}
