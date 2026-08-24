package net.bitflora.asteriskcraft.client.terran;

import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;

/**
 * Render state for the Bunker, adding the two things the base state doesn't carry: how many units
 * are inside it, and how far through construction it is.
 *
 * <p>The garrison count is what makes the building legible from across the field — a Bunker with a
 * barrel out of each of its four slits is a full one, and a bare one is an empty shell you can walk
 * past. It costs nothing to know: the vehicle-passenger link is synced by vanilla, so the client can
 * simply count {@code getPassengers()} and no field of the mod's own has to be pushed over the wire.
 *
 * <p>The build fraction sinks the model into the ground while it goes up, the way
 * {@code client.zerg.LurkerRenderState.burrowFraction} does — no second model, and the terrain does
 * the work of hiding what isn't finished.
 */
public class BunkerRenderState extends LivingEntityRenderState {
    /** How many units are inside, 0 to {@code BunkerEntity.CAPACITY}; one barrel is drawn per unit. */
    public int garrison;

    /**
     * 0 the moment construction starts, 1 once it is standing. Not interpolated across partial ticks
     * and doesn't need to be: the build takes 600 ticks to cover under two blocks, so one tick of it
     * is well under a texel.
     */
    public float buildProgress;
}
