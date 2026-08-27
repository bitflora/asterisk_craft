package net.bitflora.asteriskcraft.client.zerg;

import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;

/**
 * Render state for the Sunken Colony, adding the one thing its strike animation needs and the base
 * state doesn't carry: how far through the tentacle whip we are. {@link LivingEntityRenderState} has
 * no attack/swing field in this version (that lives on the humanoid states), and every other unit in
 * this mod animates purely from walk speed and head rotation, so this is the first custom state here.
 */
public class SunkenColonyRenderState extends LivingEntityRenderState {
    /** 0 when idle, otherwise 0→1 across one strike: 0 is the wind-up, 1 the fully extended lunge. */
    public float attackProgress;

    /**
     * 0 on the first tick of the colony growing, 1 once it is standing. Sinks the model into the
     * ground so the terrain hides what has not grown yet.
     */
    public float buildProgress;
}
