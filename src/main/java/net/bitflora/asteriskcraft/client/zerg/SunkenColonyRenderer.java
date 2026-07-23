package net.bitflora.asteriskcraft.client.zerg;

import net.bitflora.asteriskcraft.AsteriskCraft;
import net.bitflora.asteriskcraft.client.AsteriskCraftClient;
import net.bitflora.asteriskcraft.client.UnitGlowLayer;
import net.bitflora.asteriskcraft.entity.zerg.SunkenColonyEntity;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;

public class SunkenColonyRenderer extends MobRenderer<SunkenColonyEntity, SunkenColonyRenderState, SunkenColonyModel> {
    private static final Identifier TEXTURE = AsteriskCraft.id("textures/entity/sunken_colony.png");
    private static final Identifier GLOW = AsteriskCraft.id("textures/entity/sunken_colony_glow.png");

    public SunkenColonyRenderer(EntityRendererProvider.Context context) {
        super(context, new SunkenColonyModel(context.bakeLayer(AsteriskCraftClient.SUNKEN_COLONY_LAYER)), 0.9f);
        this.addLayer(new UnitGlowLayer<>(this, GLOW));
    }

    @Override
    public SunkenColonyRenderState createRenderState() {
        return new SunkenColonyRenderState();
    }

    @Override
    public void extractRenderState(SunkenColonyEntity colony, SunkenColonyRenderState state, float partialTicks) {
        super.extractRenderState(colony, state, partialTicks);
        // The entity counts the strike down to 0; the model wants it counting 0 -> 1 up, interpolated
        // so the whip stays smooth between ticks rather than stepping once per tick.
        int remaining = colony.getAttackTicks();
        state.attackProgress = remaining <= 0
                ? 0.0f
                : 1.0f - Mth.clamp((remaining - partialTicks) / SunkenColonyEntity.ATTACK_ANIM_TICKS, 0.0f, 1.0f);
    }

    @Override
    public Identifier getTextureLocation(SunkenColonyRenderState state) {
        return TEXTURE;
    }
}
