package net.bitflora.asteriskcraft.client.zerg;

import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;

/**
 * Render state for the Lurker, adding the two things it needs and the base state doesn't carry: how
 * far through a spine volley we are, and how far into the ground it has dug.
 *
 * <p>The volley field is {@code HydraliskRenderState.attackProgress} verbatim, for the same reason —
 * a unit that never swings can't hang an attack animation off {@code getAttackAnim}. The burrow
 * field is what makes this unit look like what it is: the renderer sinks the whole model by it, so
 * terrain clips everything but the back spines while it is dug in.
 */
public class LurkerRenderState extends LivingEntityRenderState {
    /** 0 when idle, otherwise 0→1 across one volley: 0 is the wind-up, 1 the end of the recovery. */
    public float attackProgress;

    /**
     * 0 standing on the surface, 1 fully buried. Not interpolated across partial ticks, and doesn't
     * need to be: the dig takes 60 ticks to cover about a block, so a step is a quarter of a texel.
     */
    public float burrowFraction;
}
