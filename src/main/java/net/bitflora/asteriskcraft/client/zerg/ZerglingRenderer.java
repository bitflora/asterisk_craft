package net.bitflora.asteriskcraft.client.zerg;

import com.mojang.blaze3d.vertex.PoseStack;
import net.bitflora.asteriskcraft.AsteriskCraft;
import net.bitflora.asteriskcraft.client.AsteriskCraftClient;
import net.bitflora.asteriskcraft.client.UnitGlowLayer;
import net.bitflora.asteriskcraft.entity.zerg.ZerglingEntity;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.resources.Identifier;

public class ZerglingRenderer extends MobRenderer<ZerglingEntity, LivingEntityRenderState, ZerglingModel> {
    private static final Identifier TEXTURE = AsteriskCraft.id("textures/entity/zergling.png");
    private static final Identifier GLOW = AsteriskCraft.id("textures/entity/zergling_glow.png");

    public ZerglingRenderer(EntityRendererProvider.Context context) {
        super(context, new ZerglingModel(context.bakeLayer(AsteriskCraftClient.ZERGLING_LAYER)), 0.4f);
        this.addLayer(new UnitGlowLayer<>(this, GLOW));
    }

    @Override
    public LivingEntityRenderState createRenderState() {
        return new LivingEntityRenderState();
    }

    @Override
    protected void scale(LivingEntityRenderState state, PoseStack poseStack) {
        // Small, crouched bug — reads a bit under one block tall. Tuned via runClient.
        poseStack.scale(0.9f, 0.9f, 0.9f);
    }

    @Override
    public Identifier getTextureLocation(LivingEntityRenderState state) {
        return TEXTURE;
    }
}
