package net.bitflora.asteriskcraft.client.zerg;

import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;

/**
 * Adds the one thing this unit's renderer and model both need and vanilla's state does not carry: how
 * far along its fuse it is.
 */
public class InfestedVillagerRenderState extends LivingEntityRenderState {
    /** 0 when idle, rising to 1 at the instant of detonation. */
    public float swelling;
}
