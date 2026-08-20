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
     * Suppressed when the body is not being drawn at all.
     *
     * <p>{@link EyesLayer} carries no invisibility check of its own — unlike
     * {@code RenderLayer.coloredCutoutModelCopyLayerRender}, which has one — so without this a
     * hidden unit would keep drawing a full-bright glow in the exact shape of the body that isn't
     * being drawn, which is worse than not hiding it at all. This is the layer, so this is where
     * the check belongs, rather than in every renderer that happens to add it.
     *
     * <p>The condition is deliberately <em>both</em> flags rather than {@code isInvisible} alone,
     * and the difference is the whole look of a cloaked unit. An invisible body that is still drawn
     * for this viewer — a {@code faction.Cloaked} unit seen by the side that commands it, which
     * vanilla renders at 15% alpha — keeps its glow, so a Dark Templar reads as a dim shimmer
     * carrying a bright blade instead of a flat grey smear. A body that is genuinely not submitted
     * has both flags set, and stays dark. See {@code client.CloakRenderStateModifier}, which is
     * what sets them.
     */
    @Override
    public void submit(PoseStack poseStack, SubmitNodeCollector collector, int lightCoords, S state,
                       float yRot, float xRot) {
        if (state.isInvisible && state.isInvisibleToPlayer) {
            return;
        }
        super.submit(poseStack, collector, lightCoords, state, yRot, xRot);
    }

    @Override
    public RenderType renderType() {
        return renderType;
    }
}
