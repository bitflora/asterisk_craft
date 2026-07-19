package net.bitflora.asteriskcraft.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.bitflora.asteriskcraft.AsteriskCraft;
import net.bitflora.asteriskcraft.entity.DragoonEntity;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.resources.Identifier;

/**
 * Renders the Dragoon as the custom four-legged {@link DragoonModel} instead of a vanilla skeleton.
 * Being a plain {@link MobRenderer} (not a humanoid renderer), it deliberately does not draw the bow
 * the underlying Skeleton spawns holding — the walker has no hands, and its ranged attack is unchanged.
 * The emissive {@link UnitGlowLayer} lights the cockpit eye at full brightness (see the glow texture).
 */
public class DragoonRenderer extends MobRenderer<DragoonEntity, LivingEntityRenderState, DragoonModel> {
    private static final Identifier TEXTURE = AsteriskCraft.id("textures/entity/dragoon.png");
    private static final Identifier GLOW = AsteriskCraft.id("textures/entity/dragoon_glow.png");

    public DragoonRenderer(EntityRendererProvider.Context context) {
        super(context, new DragoonModel(context.bakeLayer(AsteriskCraftClient.DRAGOON_LAYER)), 0.7f);
        this.addLayer(new UnitGlowLayer<>(this, GLOW));
    }

    @Override
    public LivingEntityRenderState createRenderState() {
        return new LivingEntityRenderState();
    }

    @Override
    protected void scale(LivingEntityRenderState state, PoseStack poseStack) {
        // Bulky walker: reads a touch larger than the humanoid units. Tuned via runClient.
        poseStack.scale(1.15f, 1.15f, 1.15f);
    }

    @Override
    public Identifier getTextureLocation(LivingEntityRenderState state) {
        return TEXTURE;
    }
}
