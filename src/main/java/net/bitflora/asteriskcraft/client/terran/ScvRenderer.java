package net.bitflora.asteriskcraft.client.terran;

import net.bitflora.asteriskcraft.AsteriskCraft;
import net.bitflora.asteriskcraft.client.AsteriskCraftClient;
import net.bitflora.asteriskcraft.client.UnitGlowLayer;
import net.bitflora.asteriskcraft.client.protoss.ProbeModel;
import net.bitflora.asteriskcraft.client.protoss.ProbeRenderState;
import net.bitflora.asteriskcraft.entity.terran.ScvEntity;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.Identifier;

/**
 * The SCV, which borrows the Probe's geometry outright: it bakes {@code PROBE_LAYER} rather than
 * registering a layer of its own, so there is one model class, one UV layout and one entry in the
 * model tests for the two of them.
 *
 * <p>That is why a Terran renderer names a Protoss render state. {@link ProbeModel} is an
 * {@code EntityModel<ProbeRenderState>}, so anything driving it must feed it one; the alternative
 * (generifying the model over a shared worker render state) buys nothing until the SCV grows
 * geometry of its own, at which point it gets its own model, state and layer like every other unit.
 *
 * <p>The textures are its own files from the start, so repainting the SCV in Blockbench (see
 * docs/texturing.md) never touches the Probe.
 */
public class ScvRenderer extends MobRenderer<ScvEntity, ProbeRenderState, ProbeModel> {
    private static final Identifier TEXTURE = AsteriskCraft.id("textures/entity/scv.png");
    private static final Identifier GLOW = AsteriskCraft.id("textures/entity/scv_glow.png");

    public ScvRenderer(EntityRendererProvider.Context context) {
        super(context, new ProbeModel(context.bakeLayer(AsteriskCraftClient.PROBE_LAYER)), 0.4f);
        this.addLayer(new UnitGlowLayer<>(this, GLOW));
    }

    @Override
    public ProbeRenderState createRenderState() {
        return new ProbeRenderState();
    }

    @Override
    public void extractRenderState(ScvEntity scv, ProbeRenderState state, float partialTicks) {
        super.extractRenderState(scv, state, partialTicks);
        state.miningProgress = scv.getAttackAnim(partialTicks);
    }

    @Override
    public Identifier getTextureLocation(ProbeRenderState state) {
        return TEXTURE;
    }
}
