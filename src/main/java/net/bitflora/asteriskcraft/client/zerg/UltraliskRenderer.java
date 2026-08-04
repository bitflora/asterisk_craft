package net.bitflora.asteriskcraft.client.zerg;

import com.mojang.blaze3d.vertex.PoseStack;
import net.bitflora.asteriskcraft.AsteriskCraft;
import net.bitflora.asteriskcraft.client.AsteriskCraftClient;
import net.bitflora.asteriskcraft.client.UnitGlowLayer;
import net.bitflora.asteriskcraft.entity.zerg.UltraliskEntity;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.Identifier;

/**
 * The Ultralisk is a Zergling grown into a heavy, so it reuses {@link ZerglingModel}, its texture and
 * its render state wholesale — only the scale and shadow differ. It bakes its own layer over the same
 * geometry rather than sharing {@code ZERGLING_LAYER}, so giving it a model of its own later is a
 * one-line change here.
 *
 * <p>The scale is <b>deliberately not uniform</b>: see {@link #WIDEN} and {@link #HEIGHTEN}.
 */
public class UltraliskRenderer extends MobRenderer<UltraliskEntity, ZerglingRenderState, ZerglingModel> {
    private static final Identifier TEXTURE = AsteriskCraft.id("textures/entity/zergling.png");
    private static final Identifier GLOW = AsteriskCraft.id("textures/entity/zergling_glow.png");

    /**
     * Scale across and along the ground. The Zergling's silhouette is 10.6px wide against 20px of
     * height, so scaling all three axes together gave something taller than it was wide — a big
     * Zergling rather than a heavy. Growing only the ground plane is what makes it read as bulk:
     * 10.6px * 2.6 / 16 = <b>1.72 blocks across</b>, on a body proportionally as long.
     */
    private static final float WIDEN = 2.6f;
    /**
     * Scale in height, held well below {@link #WIDEN} on purpose. The model's apex is its scythe
     * tips at 20px, so this stands it 20 * 1.8 / 16 = <b>2.25 blocks</b> tall — already a little
     * over the 1.99 hitbox, which is itself capped there so the unit can path through a 2-high
     * doorway (see {@code AsteriskCraft}). Raising this further would only make the model clip
     * through the ceilings it walks under.
     */
    private static final float HEIGHTEN = 1.8f;

    public UltraliskRenderer(EntityRendererProvider.Context context) {
        // Sized against the ground footprint above, not the Zergling's 0.4f — it is a much longer
        // animal than it is wide, so the shadow splits the difference between the two.
        super(context, new ZerglingModel(context.bakeLayer(AsteriskCraftClient.ULTRALISK_LAYER)), 1.1f);
        this.addLayer(new UnitGlowLayer<>(this, GLOW));
    }

    @Override
    public ZerglingRenderState createRenderState() {
        return new ZerglingRenderState();
    }

    @Override
    public void extractRenderState(UltraliskEntity ultralisk, ZerglingRenderState state, float partialTicks) {
        super.extractRenderState(ultralisk, state, partialTicks);
        // As the Zergling: getAttackAnim already interpolates the swing across partial ticks, and
        // Mob.doHurtTarget swings on every melee hit, so no entity-side animation state is needed.
        state.attackProgress = ultralisk.getAttackAnim(partialTicks);
    }

    @Override
    protected void scale(ZerglingRenderState state, PoseStack poseStack) {
        // Broad and long rather than tall — the proportions of a heavy, not of a scaled-up Zergling.
        // The body overhangs the 1.2 hitbox by about a quarter of a block on each side, the same
        // margin by which the scythes already stand over it, and the same licence the Zealot's
        // pauldrons take. Hitbox stays where pathing wants it; only the silhouette grows.
        poseStack.scale(WIDEN, HEIGHTEN, WIDEN);
    }

    @Override
    public Identifier getTextureLocation(ZerglingRenderState state) {
        return TEXTURE;
    }
}
