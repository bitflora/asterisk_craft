package net.bitflora.asteriskcraft.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.bitflora.asteriskcraft.AsteriskCraft;
import net.bitflora.asteriskcraft.entity.HydraliskEntity;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.resources.Identifier;

public class HydraliskRenderer extends MobRenderer<HydraliskEntity, LivingEntityRenderState, HydraliskModel> {
    private static final Identifier TEXTURE = AsteriskCraft.id("textures/entity/hydralisk.png");
    private static final Identifier OVERLAY = AsteriskCraft.id("textures/entity/hydralisk_overlay.png");
    private static final Identifier GLOW = AsteriskCraft.id("textures/entity/hydralisk_glow.png");

    public HydraliskRenderer(EntityRendererProvider.Context context) {
        super(context, new HydraliskModel(context.bakeLayer(AsteriskCraftClient.HYDRALISK_LAYER)), 0.7f);
        this.addLayer(new UnitOverlayLayer<>(this, OVERLAY));
        this.addLayer(new UnitGlowLayer<>(this, GLOW));
    }

    @Override
    public LivingEntityRenderState createRenderState() {
        return new LivingEntityRenderState();
    }

    @Override
    protected void scale(LivingEntityRenderState state, PoseStack poseStack) {
        poseStack.scale(1.3f, 1.3f, 1.3f);
    }

    @Override
    public Identifier getTextureLocation(LivingEntityRenderState state) {
        return TEXTURE;
    }
}
