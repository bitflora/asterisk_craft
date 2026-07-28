package net.bitflora.asteriskcraft.client.protoss;

import net.bitflora.asteriskcraft.AsteriskCraft;
import net.bitflora.asteriskcraft.client.AsteriskCraftClient;
import net.bitflora.asteriskcraft.client.UnitGlowLayer;
import net.bitflora.asteriskcraft.entity.protoss.ProbeEntity;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.Identifier;

public class ProbeRenderer extends MobRenderer<ProbeEntity, ProbeRenderState, ProbeModel> {
    private static final Identifier TEXTURE = AsteriskCraft.id("textures/entity/probe.png");
    private static final Identifier GLOW = AsteriskCraft.id("textures/entity/probe_glow.png");

    public ProbeRenderer(EntityRendererProvider.Context context) {
        // No scale() override, deliberately: the model is built at true pixel scale and spans
        // y 9.6..21.6, i.e. 12px of unit hanging inside a 0.9-block (14.4px) hitbox with the hover
        // bob using the slack. The old 1.1 here quietly pushed the pod out of its own box.
        super(context, new ProbeModel(context.bakeLayer(AsteriskCraftClient.PROBE_LAYER)), 0.4f);
        this.addLayer(new UnitGlowLayer<>(this, GLOW));
    }

    @Override
    public ProbeRenderState createRenderState() {
        return new ProbeRenderState();
    }

    @Override
    public void extractRenderState(ProbeEntity probe, ProbeRenderState state, float partialTicks) {
        super.extractRenderState(probe, state, partialTicks);
        // getAttackAnim already interpolates the swing 0 -> 1 across partial ticks, so the mining
        // stroke stays smooth instead of stepping once per tick. HarvestGoal re-swings every tick it
        // is working a node, which is what keeps the emitters aimed for as long as the Probe mines.
        state.miningProgress = probe.getAttackAnim(partialTicks);
    }

    @Override
    public Identifier getTextureLocation(ProbeRenderState state) {
        return TEXTURE;
    }
}
