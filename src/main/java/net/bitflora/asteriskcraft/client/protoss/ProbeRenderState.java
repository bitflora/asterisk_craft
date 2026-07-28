package net.bitflora.asteriskcraft.client.protoss;

import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;

/**
 * Render state for the Probe, adding the one thing its mining pose needs and the base state doesn't
 * carry: how far through the harvest stroke we are. {@link LivingEntityRenderState} has no
 * attack/swing field in this version (that lives on the humanoid states) — the same reason
 * {@code DroneRenderState} and {@code ZealotRenderState} exist.
 */
public class ProbeRenderState extends LivingEntityRenderState {
    /** 0 when idle, otherwise 0→1 across one pulse of the mining beam. */
    public float miningProgress;
}
