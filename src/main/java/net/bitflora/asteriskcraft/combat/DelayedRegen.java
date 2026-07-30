package net.bitflora.asteriskcraft.combat;

import net.minecraft.world.entity.LivingEntity;
import net.neoforged.neoforge.attachment.AttachmentType;

import java.util.function.Supplier;

/**
 * The shared "recharge after a lull" tick shape behind both {@link ShieldEventHandler} (Protoss
 * shield buffer) and {@link ZergRegenEventHandler} (Zerg HP): each hit resets a per-entity delay
 * attachment, and once that many ticks pass without damage the value climbs back toward its max at
 * a fixed rate. Only the pool being refilled (shield buffer vs. actual health) differs, so each
 * handler supplies its own current/max/setter; the delay countdown and the per-tick top-up live here.
 */
public final class DelayedRegen {
    private DelayedRegen() {
    }

    /** Applies {@code value} to whatever pool the caller is refilling (shield buffer or health). */
    @FunctionalInterface
    public interface FloatSetter {
        void accept(float value);
    }

    /**
     * Advances one tick of delayed regen for {@code living}. If the delay attachment is still
     * counting down it is decremented and nothing regenerates this tick; otherwise, while the
     * current value is below {@code max}, it is raised by {@code perTick} (clamped to {@code max})
     * via {@code setter}.
     */
    public static void tick(LivingEntity living, Supplier<AttachmentType<Integer>> delayAttachment,
                            float perTick, float current, float max, FloatSetter setter) {
        int delay = living.getData(delayAttachment);
        if (delay > 0) {
            living.setData(delayAttachment, delay - 1);
            return;
        }
        if (current < max) {
            setter.accept(regen(current, max, perTick));
        }
    }

    /**
     * The per-tick top-up on its own, for a pool that isn't an entity attachment — a building's
     * shield buffer, which is saved block-entity state (see
     * {@link net.bitflora.asteriskcraft.building.BuildingDefense}). Such a caller owns its own delay
     * countdown; this keeps the refill rule itself in one place.
     */
    public static float regen(float current, float max, float perTick) {
        return current < max ? Math.min(max, current + perTick) : current;
    }
}
