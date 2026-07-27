package net.bitflora.asteriskcraft.client.protoss;

import com.mojang.blaze3d.vertex.PoseStack;
import net.bitflora.asteriskcraft.AsteriskCraft;
import net.bitflora.asteriskcraft.client.AsteriskCraftClient;
import net.bitflora.asteriskcraft.client.UnitGlowLayer;
import net.bitflora.asteriskcraft.entity.protoss.DragoonEntity;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.resources.Identifier;

/**
 * Renders the Dragoon as the custom four-legged {@link DragoonModel}. A plain {@link MobRenderer}
 * rather than a humanoid one: the walker has no hands and carries nothing, so there is no held-item
 * or armour layer to draw. The emissive {@link UnitGlowLayer} lights the blue energy lens and the
 * barrel tip at full brightness — those are the only zones painted into the glow texture.
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
