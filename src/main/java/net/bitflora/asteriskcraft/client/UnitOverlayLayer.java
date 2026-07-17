package net.bitflora.asteriskcraft.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.resources.Identifier;

/**
 * Detail-overlay layer for ported StarCraft units. Draws the parent model again with an "_overlay"
 * texture as a lit cutout pass over the base skin (the old mod layered a separate overlay texture
 * this way). Re-authors the old RenderUtilities overlay pass, which isn't portable.
 */
public class UnitOverlayLayer<S extends LivingEntityRenderState, M extends EntityModel<? super S>> extends RenderLayer<S, M> {
    private final Identifier overlayTexture;

    public UnitOverlayLayer(RenderLayerParent<S, M> parent, Identifier overlayTexture) {
        super(parent);
        this.overlayTexture = overlayTexture;
    }

    @Override
    public void submit(PoseStack poseStack, SubmitNodeCollector submitNodeCollector, int lightCoords, S state,
                       float yRot, float xRot) {
        coloredCutoutModelCopyLayerRender(getParentModel(), overlayTexture, poseStack, submitNodeCollector,
                lightCoords, state, -1, 1);
    }
}
