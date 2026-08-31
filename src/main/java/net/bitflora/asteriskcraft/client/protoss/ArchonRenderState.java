package net.bitflora.asteriskcraft.client.protoss;

import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;

/**
 * Render state for the Archon, adding the one thing its strike animation needs and the base state
 * doesn't carry: how far through the shot we are. {@link LivingEntityRenderState} has no
 * attack/swing field in this version (that lives on the humanoid states) — the same reason
 * {@code ZealotRenderState} exists.
 */
public class ArchonRenderState extends LivingEntityRenderState {
    /** 0 when idle, otherwise 0→1 across one shot: 0 is the wind-up, 1 the end of the recovery. */
    public float attackProgress;
}
