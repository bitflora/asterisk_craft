package net.bitflora.asteriskcraft.client.zerg;

import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;

/**
 * Render state for the Zergling, adding the one thing its bite-and-slash needs and the base state
 * doesn't carry: how far through the strike we are. {@link LivingEntityRenderState} has no attack or
 * swing field in this version (that lives on the humanoid states) — the same reason
 * {@code DroneRenderState} and {@code ZealotRenderState} exist.
 */
public class ZerglingRenderState extends LivingEntityRenderState {
    /** 0 when idle, otherwise 0→1 across one strike: 0 is the gape, 1 the end of the recovery. */
    public float attackProgress;
}
