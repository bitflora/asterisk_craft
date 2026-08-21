package net.bitflora.asteriskcraft.client.zerg;

import com.mojang.blaze3d.vertex.PoseStack;
import net.bitflora.asteriskcraft.AsteriskCraft;
import net.bitflora.asteriskcraft.client.AsteriskCraftClient;
import net.bitflora.asteriskcraft.client.UnitGlowLayer;
import net.bitflora.asteriskcraft.entity.zerg.SunkenColonyEntity;
import net.bitflora.asteriskcraft.stats.UnitStats;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;

public class SunkenColonyRenderer extends MobRenderer<SunkenColonyEntity, SunkenColonyRenderState, SunkenColonyModel> {
    private static final Identifier TEXTURE = AsteriskCraft.id("textures/entity/sunken_colony.png");
    private static final Identifier GLOW = AsteriskCraft.id("textures/entity/sunken_colony_glow.png");

    /**
     * Scale across the ground. The colony is the swarm's counterpart to a Photon Cannon and was
     * standing at half its linear size; this takes the skirt and its splayed roots from ~20px to
     * ~41px across, i.e. <b>2.56 blocks</b>, the same footprint the Cannon's spike tips cover.
     */
    private static final float WIDEN = 2.05f;
    /**
     * Scale in height, held well below {@link #WIDEN} because the Sunken is a narrow mound under a
     * tall tentacle: scaling it uniformly until it was as wide as the Cannon would have stood it
     * over three blocks tall. The claw tip goes ~26px -> ~40px, i.e. <b>2.5 blocks</b>, exactly the
     * Cannon's height and the new hitbox's.
     *
     * <p>The cost of the split is paid by the tentacle: its segments come out roughly twice as
     * thick without getting proportionally longer, so it reads stubbier than it did, and its texels
     * stretch horizontally. That is deliberate — footprint parity with the Cannon was the point.
     */
    private static final float HEIGHTEN = 1.55f;

    public SunkenColonyRenderer(EntityRendererProvider.Context context) {
        // Shadow sized to the widened footprint above, which is the Photon Cannon's.
        super(context, new SunkenColonyModel(context.bakeLayer(AsteriskCraftClient.SUNKEN_COLONY_LAYER)), 1.2f);
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
                : 1.0f - Mth.clamp((remaining - partialTicks) / UnitStats.SUNKEN_COLONY.attackAnimTicks(), 0.0f, 1.0f);
    }

    @Override
    protected void scale(SunkenColonyRenderState state, PoseStack poseStack) {
        // Broad across the ground and only half as much again in height — the proportions of a
        // squat root mound grown to building scale, not of a stretched one. The mesh is untouched;
        // re-authoring it at this size would invalidate the hand-packed UV islands (see
        // docs/texturing.md).
        poseStack.scale(WIDEN, HEIGHTEN, WIDEN);
    }

    @Override
    public Identifier getTextureLocation(SunkenColonyRenderState state) {
        return TEXTURE;
    }
}
