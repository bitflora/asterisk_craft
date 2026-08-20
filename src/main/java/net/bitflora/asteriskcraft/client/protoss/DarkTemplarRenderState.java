package net.bitflora.asteriskcraft.client.protoss;

import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;

/**
 * Render state for the Dark Templar, adding the one thing its blade-strike animation needs and the
 * base state does not carry: how far through the chop we are. {@link LivingEntityRenderState} has no
 * attack/swing field in this version (that lives on the humanoid states) — the same reason
 * {@code ZealotRenderState} and {@code SunkenColonyRenderState} exist.
 */
public class DarkTemplarRenderState extends LivingEntityRenderState {
    /** 0 when idle, otherwise 0 to 1 across one strike: 0 is the wind-up, 1 the end of the recovery. */
    public float attackProgress;
}
