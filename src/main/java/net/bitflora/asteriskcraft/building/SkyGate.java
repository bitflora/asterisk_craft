package net.bitflora.asteriskcraft.building;

import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;

/**
 * Shared "can this building still see the sky?" gate used by the Nexus and the Hive: both go
 * dormant when buried. Each tick it recomputes whether the column above the core is open to the
 * sky, plays a beacon activate/deactivate cue on a transition, runs the owner's cleanup when it
 * goes dark, and returns the current lit state. Holds the last-known lit state so the cue only
 * fires on change (and never on the very first evaluation).
 */
public final class SkyGate {
    /** Null until the first {@link #update} evaluates it, so the first tick plays no spurious cue. */
    private Boolean skyLit = null;

    /**
     * Recomputes sky visibility for the block above {@code pos}, plays the activate/deactivate cue
     * on any change, runs {@code onWentDark} when it transitions from lit to dark, and returns the
     * current lit state.
     */
    public boolean update(Level level, BlockPos pos, Runnable onWentDark) {
        boolean lit = level.canSeeSky(pos.above());
        if (this.skyLit != null && this.skyLit != lit) {
            level.playSound(null, pos, lit ? SoundEvents.BEACON_ACTIVATE : SoundEvents.BEACON_DEACTIVATE,
                    SoundSource.BLOCKS, 1.0f, 1.0f);
            if (!lit) {
                onWentDark.run();
            }
        }
        this.skyLit = lit;
        return lit;
    }

    /** The last computed lit state, or {@code null} before the first {@link #update}. */
    public Boolean lit() {
        return this.skyLit;
    }
}
