package net.bitflora.asteriskcraft.client.zerg;

import com.mojang.blaze3d.vertex.PoseStack;
import net.bitflora.asteriskcraft.AsteriskCraft;
import net.bitflora.asteriskcraft.client.AsteriskCraftClient;
import net.bitflora.asteriskcraft.client.UnitGlowLayer;
import net.bitflora.asteriskcraft.entity.zerg.OverlordEntity;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.resources.Identifier;

/**
 * Draws the Overlord. The geometry in {@link OverlordModel} is built at ordinary unit scale and the
 * size comes from the one multiplier here — the arrangement {@code UltraliskRenderer} uses, and the
 * reason a 16-unit body cube lands at the four blocks the entity's hitbox declares.
 *
 * <p>The shadow radius is sized to the body rather than left at a unit's usual fraction of a block:
 * at this scale anything smaller reads as a mis-anchored sprite floating over its own shadow.
 */
public class OverlordRenderer extends MobRenderer<OverlordEntity, LivingEntityRenderState, OverlordModel> {
    private static final Identifier TEXTURE = AsteriskCraft.id("textures/entity/overlord.png");
    private static final Identifier GLOW = AsteriskCraft.id("textures/entity/overlord_glow.png");

    /** 16 model units x this = 4 blocks, matching the entity's declared hitbox. */
    private static final float SCALE = 4.0f;

    public OverlordRenderer(EntityRendererProvider.Context context) {
        super(context, new OverlordModel(context.bakeLayer(AsteriskCraftClient.OVERLORD_LAYER)), 2.5f);
        this.addLayer(new UnitGlowLayer<>(this, GLOW));
    }

    @Override
    public LivingEntityRenderState createRenderState() {
        return new LivingEntityRenderState();
    }

    @Override
    protected void scale(LivingEntityRenderState state, PoseStack poseStack) {
        poseStack.scale(SCALE, SCALE, SCALE);
    }

    @Override
    public Identifier getTextureLocation(LivingEntityRenderState state) {
        return TEXTURE;
    }
}
