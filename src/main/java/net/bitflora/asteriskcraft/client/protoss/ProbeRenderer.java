package net.bitflora.asteriskcraft.client.protoss;

import com.mojang.blaze3d.vertex.PoseStack;
import net.bitflora.asteriskcraft.AsteriskCraft;
import net.bitflora.asteriskcraft.client.AsteriskCraftClient;
import net.bitflora.asteriskcraft.client.UnitGlowLayer;
import net.bitflora.asteriskcraft.client.UnitOverlayLayer;
import net.bitflora.asteriskcraft.entity.protoss.ProbeEntity;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.resources.Identifier;

public class ProbeRenderer extends MobRenderer<ProbeEntity, LivingEntityRenderState, ProbeModel> {
    private static final Identifier TEXTURE = AsteriskCraft.id("textures/entity/probe.png");
    private static final Identifier OVERLAY = AsteriskCraft.id("textures/entity/probe_overlay.png");
    private static final Identifier GLOW = AsteriskCraft.id("textures/entity/probe_glow.png");

    public ProbeRenderer(EntityRendererProvider.Context context) {
        super(context, new ProbeModel(context.bakeLayer(AsteriskCraftClient.PROBE_LAYER)), 0.4f);
        this.addLayer(new UnitOverlayLayer<>(this, OVERLAY));
        this.addLayer(new UnitGlowLayer<>(this, GLOW));
    }

    @Override
    public LivingEntityRenderState createRenderState() {
        return new LivingEntityRenderState();
    }

    @Override
    protected void scale(LivingEntityRenderState state, PoseStack poseStack) {
        poseStack.scale(1.5f, 1.5f, 1.5f);
    }

    @Override
    public Identifier getTextureLocation(LivingEntityRenderState state) {
        return TEXTURE;
    }
}
