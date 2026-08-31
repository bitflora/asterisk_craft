package net.bitflora.asteriskcraft.client.terran;

import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;

/**
 * Render state for the Goliath, adding the one thing the base state doesn't carry: how far through a
 * burst it is.
 *
 * <p>Read off a {@code SynchedEntityData} int on the entity, so it needs no packet of the mod's own
 * — the same shape {@link MarineRenderState} and {@link MissileTurretRenderState} use.
 */
public class GoliathRenderState extends LivingEntityRenderState {
    /**
     * 0 at the start of a burst, 1 at the end, 0 when idle. Interpolated across partial ticks,
     * because the recoil is only eight ticks long and a stepped kick would read as a stutter.
     */
    public float attackProgress;
}
