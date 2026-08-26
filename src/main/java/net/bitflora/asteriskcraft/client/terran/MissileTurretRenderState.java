package net.bitflora.asteriskcraft.client.terran;

import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;

/**
 * Render state for the Missile Turret, adding the two things the base state doesn't carry: how far
 * through a salvo it is, and how far through construction.
 *
 * <p>Both are read off {@code SynchedEntityData} ints on the entity, so neither needs a packet of
 * the mod's own — see {@code client.zerg.SporeColonyRenderState} and
 * {@link BunkerRenderState}, which carry the same two ideas separately.
 */
public class MissileTurretRenderState extends LivingEntityRenderState {
    /**
     * 0 at the start of a salvo, 1 at the end, 0 when idle. Interpolated across partial ticks,
     * because the strike animation is only ten ticks long and a stepped recoil would read as a
     * stutter.
     */
    public float attackProgress;

    /**
     * 0 the moment construction starts, 1 once it is standing. Not interpolated and doesn't need to
     * be: the build takes 600 ticks to cover under three blocks, so one tick of it is under a texel.
     */
    public float buildProgress;
}
