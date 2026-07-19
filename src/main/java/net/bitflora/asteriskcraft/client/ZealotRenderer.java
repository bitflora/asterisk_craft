package net.bitflora.asteriskcraft.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.bitflora.asteriskcraft.AsteriskCraft;
import net.bitflora.asteriskcraft.entity.ZealotEntity;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.resources.Identifier;

public class ZealotRenderer extends MobRenderer<ZealotEntity, LivingEntityRenderState, ZealotModel> {
    private static final Identifier TEXTURE = AsteriskCraft.id("textures/entity/zealot.png");
    private static final Identifier OVERLAY = AsteriskCraft.id("textures/entity/zealot_overlay.png");
    private static final Identifier GLOW = AsteriskCraft.id("textures/entity/zealot_glow.png");

    public ZealotRenderer(EntityRendererProvider.Context context) {
        super(context, new ZealotModel(context.bakeLayer(AsteriskCraftClient.ZEALOT_LAYER)), 0.5f);
        this.addLayer(new UnitOverlayLayer<>(this, OVERLAY));
        this.addLayer(new UnitGlowLayer<>(this, GLOW));
    }

    @Override
    public LivingEntityRenderState createRenderState() {
        return new LivingEntityRenderState();
    }

    @Override
    protected void scale(LivingEntityRenderState state, PoseStack poseStack) {
        poseStack.scale(0.525f, 0.525f, 0.525f);
    }

    @Override
    public Identifier getTextureLocation(LivingEntityRenderState state) {
        return TEXTURE;
    }
}
