package net.bitflora.asteriskcraft.client.zerg;

import net.bitflora.asteriskcraft.AsteriskCraft;
import net.bitflora.asteriskcraft.client.AsteriskCraftClient;
import net.bitflora.asteriskcraft.client.UnitGlowLayer;
import net.bitflora.asteriskcraft.entity.zerg.SporeColonyEntity;
import net.bitflora.asteriskcraft.stats.UnitStats;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;

public class SporeColonyRenderer extends MobRenderer<SporeColonyEntity, SporeColonyRenderState, SporeColonyModel> {
    private static final Identifier TEXTURE = AsteriskCraft.id("textures/entity/spore_colony.png");
    private static final Identifier GLOW = AsteriskCraft.id("textures/entity/spore_colony_glow.png");

    public SporeColonyRenderer(EntityRendererProvider.Context context) {
        super(context, new SporeColonyModel(context.bakeLayer(AsteriskCraftClient.SPORE_COLONY_LAYER)), 0.8f);
        this.addLayer(new UnitGlowLayer<>(this, GLOW));
    }

    @Override
    public SporeColonyRenderState createRenderState() {
        return new SporeColonyRenderState();
    }

    @Override
    public void extractRenderState(SporeColonyEntity colony, SporeColonyRenderState state, float partialTicks) {
        super.extractRenderState(colony, state, partialTicks);
        // The entity counts the shot down to 0; the model wants it counting 0 -> 1 up, interpolated
        // so the clench stays smooth between ticks rather than stepping once per tick.
        int remaining = colony.getAttackTicks();
        state.attackProgress = remaining <= 0
                ? 0.0f
                : 1.0f - Mth.clamp((remaining - partialTicks) / UnitStats.SPORE_COLONY.attackAnimTicks(), 0.0f, 1.0f);
    }

    @Override
    public Identifier getTextureLocation(SporeColonyRenderState state) {
        return TEXTURE;
    }
}
