package net.bitflora.asteriskcraft.client.zerg;

import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;

/**
 * Render state for the Drone, adding the one thing its mining chop needs and the base state doesn't
 * carry: how far through the swing we are. {@link LivingEntityRenderState} has no attack/swing field in
 * this version (that lives on the humanoid states) — the same reason {@code ZealotRenderState} and
 * {@code SunkenColonyRenderState} exist.
 */
public class DroneRenderState extends LivingEntityRenderState {
    /** 0 when idle, otherwise 0→1 across one swing of the mining sickle. */
    public float swingProgress;
}
