package net.bitflora.asteriskcraft.client;

import net.minecraft.client.model.EntityModel;
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
 * The inherited {@link EyesLayer#submit} re-submits the parent model with {@link #renderType()}.
 */
public class UnitGlowLayer<S extends LivingEntityRenderState, M extends EntityModel<S>> extends EyesLayer<S, M> {
    private final RenderType renderType;

    public UnitGlowLayer(RenderLayerParent<S, M> parent, Identifier glowTexture) {
        super(parent);
        this.renderType = RenderTypes.eyes(glowTexture);
    }

    @Override
    public RenderType renderType() {
        return renderType;
    }
}
