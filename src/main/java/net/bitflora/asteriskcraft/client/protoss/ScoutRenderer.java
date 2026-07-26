package net.bitflora.asteriskcraft.client.protoss;

import com.mojang.blaze3d.vertex.PoseStack;
import net.bitflora.asteriskcraft.AsteriskCraft;
import net.bitflora.asteriskcraft.client.AsteriskCraftClient;
import net.bitflora.asteriskcraft.client.UnitGlowLayer;
import net.bitflora.asteriskcraft.entity.protoss.ScoutEntity;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.resources.Identifier;

public class ScoutRenderer extends MobRenderer<ScoutEntity, LivingEntityRenderState, ScoutModel> {
    private static final Identifier TEXTURE = AsteriskCraft.id("textures/entity/scout.png");
    private static final Identifier GLOW = AsteriskCraft.id("textures/entity/scout_glow.png");

    public ScoutRenderer(EntityRendererProvider.Context context) {
        super(context, new ScoutModel(context.bakeLayer(AsteriskCraftClient.SCOUT_LAYER)), 0.8f);
        this.addLayer(new UnitGlowLayer<>(this, GLOW));
    }

    @Override
    public LivingEntityRenderState createRenderState() {
        return new LivingEntityRenderState();
    }

    @Override
    protected void scale(LivingEntityRenderState state, PoseStack poseStack) {
        // Slightly under life size: the wing blades already reach past the hitbox either side.
        poseStack.scale(0.9f, 0.9f, 0.9f);
    }

    @Override
    public Identifier getTextureLocation(LivingEntityRenderState state) {
        return TEXTURE;
    }
}
