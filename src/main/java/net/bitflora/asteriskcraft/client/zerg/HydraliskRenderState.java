package net.bitflora.asteriskcraft.client.zerg;

import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;

/**
 * Render state for the Hydralisk, adding the one thing its spine-volley animation needs and the base
 * state doesn't carry: how far through the shot we are. Unlike the Zergling, this can't ride on
 * {@code getAttackAnim} — that's the melee swing, and a ranged unit never takes one — so
 * {@link net.bitflora.asteriskcraft.entity.zerg.HydraliskEntity} counts the volley down itself, the
 * same shape as {@code ZealotRenderState}.
 */
public class HydraliskRenderState extends LivingEntityRenderState {
    /** 0 when idle, otherwise 0→1 across one volley: 0 is the wind-up, 1 the end of the recovery. */
    public float attackProgress;
}
