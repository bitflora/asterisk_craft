package net.bitflora.asteriskcraft.client.zerg;

import com.mojang.blaze3d.vertex.PoseStack;
import net.bitflora.asteriskcraft.AsteriskCraft;
import net.bitflora.asteriskcraft.client.AsteriskCraftClient;
import net.bitflora.asteriskcraft.client.UnitGlowLayer;
import net.bitflora.asteriskcraft.entity.zerg.LurkerEntity;
import net.bitflora.asteriskcraft.stats.UnitStats;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;

public class LurkerRenderer extends MobRenderer<LurkerEntity, LurkerRenderState, LurkerModel> {
    private static final Identifier TEXTURE = AsteriskCraft.id("textures/entity/lurker.png");
    private static final Identifier GLOW = AsteriskCraft.id("textures/entity/lurker_glow.png");

    /**
     * How far the body drops when fully burrowed. The model stands about 1.2 blocks tall, so a block
     * of sink leaves the back spines — and nothing else — breaking the surface.
     */
    private static final float SINK_BLOCKS = 1.0f;

    public LurkerRenderer(EntityRendererProvider.Context context) {
        super(context, new LurkerModel(context.bakeLayer(AsteriskCraftClient.LURKER_LAYER)), 0.7f);
        this.addLayer(new UnitGlowLayer<>(this, GLOW));
    }

    @Override
    public LurkerRenderState createRenderState() {
        return new LurkerRenderState();
    }

    @Override
    public void extractRenderState(LurkerEntity lurker, LurkerRenderState state, float partialTicks) {
        super.extractRenderState(lurker, state, partialTicks);
        // The entity counts the volley down to 0; the model wants it counting 0 -> 1 up, interpolated
        // so the strike stays smooth between ticks. Same conversion as HydraliskRenderer's.
        int remaining = lurker.getAttackTicks();
        state.attackProgress = remaining <= 0
                ? 0.0f
                : 1.0f - Mth.clamp((remaining - partialTicks) / UnitStats.LURKER.attackAnimTicks(), 0.0f, 1.0f);
        state.burrowFraction = lurker.burrowFraction();
    }

    /**
     * Sinks the whole model as it digs in, so the terrain itself clips the buried part — no separate
     * "burrowed" model, and the back spines left above ground are exactly the ones the geometry puts
     * highest.
     *
     * <p><b>+Y is down here.</b> {@code LivingEntityRenderer.submit} applies
     * {@code poseStack.scale(-1, -1, 1)} immediately before calling this hook (verified against the
     * decompiled 26.1.2 source), so the model's own Y-down authoring space is what this translate is
     * in — a positive Y moves the unit into the ground, not out of it.
     */
    @Override
    protected void scale(LurkerRenderState state, PoseStack poseStack) {
        poseStack.translate(0.0f, state.burrowFraction * SINK_BLOCKS, 0.0f);
    }

    @Override
    public Identifier getTextureLocation(LurkerRenderState state) {
        return TEXTURE;
    }
}
