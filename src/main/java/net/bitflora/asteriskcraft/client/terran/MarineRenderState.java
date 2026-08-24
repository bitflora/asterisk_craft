package net.bitflora.asteriskcraft.client.terran;

import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;

/** Render state for the Marine: vanilla's, plus how far into a shot it is. */
public class MarineRenderState extends LivingEntityRenderState {
    /** 0 when idle, otherwise 0-&gt;1 across one shot's recoil. */
    public float attackProgress;
}
