package net.bitflora.asteriskcraft.client.zerg;

import com.mojang.blaze3d.vertex.PoseStack;
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

    /** Grows the model to the Photon Cannon's footprint; see {@link #scale}. */
    private static final float SCALE = 1.95f;

    public SporeColonyRenderer(EntityRendererProvider.Context context) {
        // Shadow sized to the scaled footprint below, which is the Photon Cannon's.
        super(context, new SporeColonyModel(context.bakeLayer(AsteriskCraftClient.SPORE_COLONY_LAYER)), 1.2f);
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
    protected void scale(SporeColonyRenderState state, PoseStack poseStack) {
        // The colony is the swarm's counterpart to a Photon Cannon and was standing at half its
        // linear size. Uniform, because the Spore is already the same squat-and-broad shape the
        // Cannon is, so growing it needs no per-axis correction and the texels stay square: the
        // shell wings go 21px -> 41px across (2.56 blocks, against the Cannon's 2.56) and the
        // chimney 18px -> 35px tall (2.19), keeping the same headroom inside the 2.5 hitbox that
        // 1.125-in-1.4 gave it before. The mesh is untouched — re-authoring it at this size would
        // invalidate the hand-packed UV islands (see docs/texturing.md).
        poseStack.scale(SCALE, SCALE, SCALE);
    }

    @Override
    public Identifier getTextureLocation(SporeColonyRenderState state) {
        return TEXTURE;
    }
}
