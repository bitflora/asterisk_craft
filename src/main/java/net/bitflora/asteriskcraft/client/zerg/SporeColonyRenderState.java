package net.bitflora.asteriskcraft.client.zerg;

import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;

/**
 * Render state for the Spore Colony — the same single addition {@link SunkenColonyRenderState} makes,
 * for the same reason: {@link LivingEntityRenderState} carries no attack/swing field in this version,
 * so the firing animation needs its own progress value extracted from the entity.
 */
public class SporeColonyRenderState extends LivingEntityRenderState {
    /** 0 when idle, otherwise 0→1 across one shot: 0 is the clench, 1 the spent recoil. */
    public float attackProgress;
}
