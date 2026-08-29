package net.bitflora.asteriskcraft.client.terran;

import com.mojang.blaze3d.vertex.PoseStack;
import net.bitflora.asteriskcraft.AsteriskCraft;
import net.bitflora.asteriskcraft.client.AsteriskCraftClient;
import net.bitflora.asteriskcraft.client.UnitGlowLayer;
import net.bitflora.asteriskcraft.entity.terran.WraithEntity;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.resources.Identifier;

/**
 * The Scout's renderer arrangement, and for the same reasons: a plain {@link LivingEntityRenderState}
 * because the model animates off {@code ageInTicks} alone, plus the shared glow pass.
 *
 * <p>Nothing here knows about the cloak. Visibility is decided per viewer in
 * {@code client.DetectionRenderStateModifier}, which rewrites {@code isInvisible} before this
 * renderer ever sees the state — so a cloaked Wraith is a ghost to its own side, nothing at all to an
 * undetected enemy and a red-outlined ghost to a detecting one, with no branch in here.
 */
public class WraithRenderer extends MobRenderer<WraithEntity, LivingEntityRenderState, WraithModel> {
    private static final Identifier TEXTURE = AsteriskCraft.id("textures/entity/wraith.png");
    private static final Identifier GLOW = AsteriskCraft.id("textures/entity/wraith_glow.png");

    public WraithRenderer(EntityRendererProvider.Context context) {
        super(context, new WraithModel(context.bakeLayer(AsteriskCraftClient.WRAITH_LAYER)), 0.7f);
        this.addLayer(new UnitGlowLayer<>(this, GLOW));
    }

    @Override
    public LivingEntityRenderState createRenderState() {
        return new LivingEntityRenderState();
    }

    @Override
    protected void scale(LivingEntityRenderState state, PoseStack poseStack) {
        // Slightly under life size, as the Scout is: the wing panels already reach past the hitbox
        // either side, and the cannons overhang the front of it.
        poseStack.scale(0.9f, 0.9f, 0.9f);
    }

    @Override
    public Identifier getTextureLocation(LivingEntityRenderState state) {
        return TEXTURE;
    }
}
