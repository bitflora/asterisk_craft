package net.bitflora.asteriskcraft.client.terran;

import com.mojang.blaze3d.vertex.PoseStack;
import net.bitflora.asteriskcraft.AsteriskCraft;
import net.bitflora.asteriskcraft.client.AsteriskCraftClient;
import net.bitflora.asteriskcraft.client.UnitGlowLayer;
import net.bitflora.asteriskcraft.entity.terran.ScienceVesselEntity;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.resources.Identifier;

/**
 * Draws the Science Vessel — the Wraith's renderer with a different model and the glow layer lighting
 * a sensor dome instead of a canopy and two exhausts.
 *
 * <p>A bare {@link LivingEntityRenderState} is enough: {@link ScienceVesselModel} animates off
 * {@code ageInTicks} alone and needs nothing extracted off the entity. Nothing here knows about the
 * support pulse — it has no animation, only a sound and a plume over its target, both emitted
 * server-side.
 */
public class ScienceVesselRenderer
        extends MobRenderer<ScienceVesselEntity, LivingEntityRenderState, ScienceVesselModel> {
    private static final Identifier TEXTURE = AsteriskCraft.id("textures/entity/science_vessel.png");
    private static final Identifier GLOW = AsteriskCraft.id("textures/entity/science_vessel_glow.png");

    public ScienceVesselRenderer(EntityRendererProvider.Context context) {
        // Shadow 1.5f: vanilla's own Ghast, which this matches in size.
        super(context, new ScienceVesselModel(context.bakeLayer(AsteriskCraftClient.SCIENCE_VESSEL_LAYER)), 1.5f);
        this.addLayer(new UnitGlowLayer<>(this, GLOW));
    }

    @Override
    public LivingEntityRenderState createRenderState() {
        return new LivingEntityRenderState();
    }

    @Override
    protected void scale(LivingEntityRenderState state, PoseStack poseStack) {
        // The one place this unit's size lives. Hull plus outriggers measure 28 model units across
        // and 16 from dome crown to keel tip, so 2.3 lands it at 4.03 x 2.30 blocks — flush inside
        // the 4.0 x 2.4 hitbox, and the same width as a Ghast.
        //
        // Scaling here rather than through MeshTransformer.scaling on the LayerDefinition, which is
        // how vanilla sizes its own Ghast: this mod's texture tooling reads createBodyLayer() and
        // takes model units to be texels (a cube's UV island is 2*(w+d) by d+h), so a scaled mesh
        // would report islands several times larger than the sheet and fail ModelUvLayoutTest. The
        // cost is texel density — one texel covers 0.14 blocks here against the usual 0.06 — which
        // is still twice as fine as the Ghast this stands beside.
        poseStack.scale(2.3f, 2.3f, 2.3f);
    }

    @Override
    public Identifier getTextureLocation(LivingEntityRenderState state) {
        return TEXTURE;
    }
}
