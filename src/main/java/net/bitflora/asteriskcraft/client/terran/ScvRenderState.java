package net.bitflora.asteriskcraft.client.terran;

import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;

/**
 * Render state for the SCV, adding the one thing its cutter pose needs and the base state doesn't
 * carry: how far through the working stroke we are. {@link LivingEntityRenderState} has no
 * attack/swing field in this version (that lives on the humanoid states) — the same reason
 * {@code ProbeRenderState} and {@code ZealotRenderState} exist.
 */
public class ScvRenderState extends LivingEntityRenderState {
    /**
     * 0 when idle, otherwise 0→1 across one stroke of the fusion cutter. One field for two jobs:
     * {@code WorkerEntity.HarvestGoal} swings the main hand while mining and vanilla melee swings
     * the same hand, so mining and fighting drive the identical piston stroke.
     */
    public float cutterProgress;
}
