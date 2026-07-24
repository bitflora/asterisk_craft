package net.bitflora.asteriskcraft.client.zerg;

import com.mojang.blaze3d.vertex.PoseStack;
import net.bitflora.asteriskcraft.AsteriskCraft;
import net.bitflora.asteriskcraft.client.AsteriskCraftClient;
import net.bitflora.asteriskcraft.client.UnitGlowLayer;
import net.bitflora.asteriskcraft.entity.zerg.MutaliskEntity;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.resources.Identifier;

public class MutaliskRenderer extends MobRenderer<MutaliskEntity, LivingEntityRenderState, MutaliskModel> {
    private static final Identifier TEXTURE = AsteriskCraft.id("textures/entity/mutalisk.png");
    private static final Identifier GLOW = AsteriskCraft.id("textures/entity/mutalisk_glow.png");

    public MutaliskRenderer(EntityRendererProvider.Context context) {
        super(context, new MutaliskModel(context.bakeLayer(AsteriskCraftClient.MUTALISK_LAYER)), 0.8f);
        this.addLayer(new UnitGlowLayer<>(this, GLOW));
    }

    @Override
    public LivingEntityRenderState createRenderState() {
        return new LivingEntityRenderState();
    }

    @Override
    protected void scale(LivingEntityRenderState state, PoseStack poseStack) {
        // Slightly under life size: the wingspan is already the widest silhouette in the mod.
        poseStack.scale(0.9f, 0.9f, 0.9f);
    }

    @Override
    public Identifier getTextureLocation(LivingEntityRenderState state) {
        return TEXTURE;
    }
}
