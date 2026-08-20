package net.bitflora.asteriskcraft.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.EyesLayer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.resources.Identifier;

/**
 * Emissive glow layer for ported StarCraft units. Draws the parent model again with a "_glow"
 * texture at full brightness (ignores world lighting) — the same mechanism vanilla uses for mob
 * eyes. Re-authors the old mod's static-glow render pass (its RenderUtilities helper lived in an
 * external net.rom library and isn't portable). The old dynamic pulsing glow is not reproduced.
 * The inherited {@link EyesLayer#submit} re-submits the parent model with {@link #renderType()} —
 * except when the unit is invisible, see below.
 */
public class UnitGlowLayer<S extends LivingEntityRenderState, M extends EntityModel<S>> extends EyesLayer<S, M> {
    private final RenderType renderType;

    public UnitGlowLayer(RenderLayerParent<S, M> parent, Identifier glowTexture) {
        super(parent);
        this.renderType = RenderTypes.eyes(glowTexture);
    }

    /**
     * Suppressed entirely while the unit is invisible.
     *
     * <p>{@link EyesLayer} carries no {@code isInvisible} check of its own — unlike
     * {@code RenderLayer.coloredCutoutModelCopyLayerRender}, which has one — so without this a
     * cloaked unit would keep drawing a full-bright glow in the exact shape of the body that isn't
     * being drawn, which is worse than not hiding it at all. It matters for anything invisible, not
     * just for cloak: this is the layer, and this is where the check belongs, rather than in eleven
     * renderers that all happen to add it.
     */
    @Override
    public void submit(PoseStack poseStack, SubmitNodeCollector collector, int lightCoords, S state,
                       float yRot, float xRot) {
        if (state.isInvisible) {
            return;
        }
        super.submit(poseStack, collector, lightCoords, state, yRot, xRot);
    }

    @Override
    public RenderType renderType() {
        return renderType;
    }
}
