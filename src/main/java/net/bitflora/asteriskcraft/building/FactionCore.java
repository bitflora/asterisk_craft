package net.bitflora.asteriskcraft.building;

import net.bitflora.asteriskcraft.faction.Faction;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.Block;

/**
 * A destructible faction "core" block entity — the Nexus and each Zerg Hive. Combat units
 * assault these through {@link net.bitflora.asteriskcraft.entity.ai.SiegeBlockGoal}, which
 * stays faction-generic by only ever asking a core for its {@link #coreFaction()} and calling
 * {@link #damageCore}. It never checks Nexus-vs-Hive directly, so PvP/race-swaps stay possible.
 */
public interface FactionCore {
    /** Siege health of a core. At the default assault rate a lone unit needs a real fight to raze one. */
    int CORE_MAX_HEALTH = 300;

    /** The faction this core belongs to. A unit only sieges cores whose faction is its enemy. */
    Faction coreFaction();

    /**
     * Applies {@code amount} points of siege damage. When the core's health reaches zero it
     * removes its own block (which fires the win/lose outcome via {@code preRemoveSideEffects}).
     */
    void damageCore(int amount, ServerLevel level, BlockPos pos);

    /**
     * Shared damage arithmetic + effects for both cores. Returns the clamped new health; when it
     * hits zero the core block is removed with {@code destroyBlock}, whose side effects resolve the
     * match. Kept here so the Nexus and Hive behave identically.
     */
    static int applyDamage(int health, int amount, ServerLevel level, BlockPos pos) {
        int newHealth = Math.max(0, health - amount);
        level.playSound(null, pos, SoundEvents.NETHERITE_BLOCK_HIT, SoundSource.BLOCKS, 0.7f, 0.8f);
        level.levelEvent(2001, pos, Block.getId(level.getBlockState(pos))); // crack particles at the core
        if (newHealth <= 0) {
            level.destroyBlock(pos, false); // fires preRemoveSideEffects -> GameOutcome
        }
        return newHealth;
    }
}
