package net.bitflora.asteriskcraft.client.protoss;

import com.mojang.blaze3d.vertex.PoseStack;
import net.bitflora.asteriskcraft.AsteriskCraft;
import net.bitflora.asteriskcraft.client.AsteriskCraftClient;
import net.bitflora.asteriskcraft.client.UnitGlowLayer;
import net.bitflora.asteriskcraft.entity.protoss.ObserverEntity;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.resources.Identifier;

/**
 * Draws the Observer. Nothing here knows the unit is cloaked, exactly as in {@code DarkTemplarRenderer}:
 * the ghosting its own side sees and the red outline a detecting enemy sees are both applied
 * afterwards by {@code client.DetectionRenderStateModifier}, which rewrites the finished render
 * state per viewer. So this is the Scout's renderer with a different model and a smaller shadow.
 *
 * <p>A bare {@link LivingEntityRenderState} is enough — {@link ObserverModel} animates off
 * {@code ageInTicks} alone and needs nothing extracted off the entity.
 */
public class ObserverRenderer extends MobRenderer<ObserverEntity, LivingEntityRenderState, ObserverModel> {
    private static final Identifier TEXTURE = AsteriskCraft.id("textures/entity/observer.png");
    private static final Identifier GLOW = AsteriskCraft.id("textures/entity/observer_glow.png");

    public ObserverRenderer(EntityRendererProvider.Context context) {
        super(context, new ObserverModel(context.bakeLayer(AsteriskCraftClient.OBSERVER_LAYER)), 0.4f);
        this.addLayer(new UnitGlowLayer<>(this, GLOW));
    }

    @Override
    public LivingEntityRenderState createRenderState() {
        return new LivingEntityRenderState();
    }

    @Override
    protected void scale(LivingEntityRenderState state, PoseStack poseStack) {
        // The cage measures 13.5 model units across; at 0.9 that lands just inside the one-block
        // hitbox, the same relationship the Scout's scale keeps to its own.
        poseStack.scale(0.9f, 0.9f, 0.9f);
    }

    @Override
    public Identifier getTextureLocation(LivingEntityRenderState state) {
        return TEXTURE;
    }
}
