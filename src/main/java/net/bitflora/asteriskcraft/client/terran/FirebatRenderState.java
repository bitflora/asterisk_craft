package net.bitflora.asteriskcraft.client.terran;

import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;

/** Render state for the Firebat: vanilla's, plus how far into a sweep it is. */
public class FirebatRenderState extends LivingEntityRenderState {
    /** 0 when idle, otherwise 0-&gt;1 across one sweep's thrust. */
    public float attackProgress;
}
