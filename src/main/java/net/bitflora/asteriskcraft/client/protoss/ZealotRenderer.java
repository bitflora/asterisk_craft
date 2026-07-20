package net.bitflora.asteriskcraft.client.protoss;

import com.mojang.blaze3d.vertex.PoseStack;
import net.bitflora.asteriskcraft.AsteriskCraft;
import net.bitflora.asteriskcraft.client.AsteriskCraftClient;
import net.bitflora.asteriskcraft.client.UnitGlowLayer;
import net.bitflora.asteriskcraft.entity.protoss.ZealotEntity;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.resources.Identifier;

public class ZealotRenderer extends MobRenderer<ZealotEntity, LivingEntityRenderState, ZealotModel> {
    private static final Identifier TEXTURE = AsteriskCraft.id("textures/entity/zealot.png");
    private static final Identifier GLOW = AsteriskCraft.id("textures/entity/zealot_glow.png");

    public ZealotRenderer(EntityRendererProvider.Context context) {
        super(context, new ZealotModel(context.bakeLayer(AsteriskCraftClient.ZEALOT_LAYER)), 0.5f);
        this.addLayer(new UnitGlowLayer<>(this, GLOW));
    }

    @Override
    public LivingEntityRenderState createRenderState() {
        return new LivingEntityRenderState();
    }

    @Override
    protected void scale(LivingEntityRenderState state, PoseStack poseStack) {
        // Blocky biped is built at true humanoid pixel scale (~2 blocks tall); tuned via runClient.
        poseStack.scale(0.95f, 0.95f, 0.95f);
    }

    @Override
    public Identifier getTextureLocation(LivingEntityRenderState state) {
        return TEXTURE;
    }
}
