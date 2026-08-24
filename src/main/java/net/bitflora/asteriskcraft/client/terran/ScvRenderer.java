package net.bitflora.asteriskcraft.client.terran;

import net.bitflora.asteriskcraft.AsteriskCraft;
import net.bitflora.asteriskcraft.client.AsteriskCraftClient;
import net.bitflora.asteriskcraft.client.UnitGlowLayer;
import net.bitflora.asteriskcraft.entity.terran.ScvEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.Identifier;

/**
 * The SCV: its own model, its own layer, its own render state, like every other unit. (It used to
 * bake the Probe's layer and render as one — see docs/shaping.md's V6a slice for why that was the
 * cheap answer at the time.)
 *
 * <p>Two layers, and the second is unusual for this mod: {@link ScvPilotLayer} draws vanilla's baby
 * villager head in the cab, off vanilla's own texture, which is a thing no other unit here needs.
 */
public class ScvRenderer extends MobRenderer<ScvEntity, ScvRenderState, ScvModel> {
    private static final Identifier TEXTURE = AsteriskCraft.id("textures/entity/scv.png");
    private static final Identifier GLOW = AsteriskCraft.id("textures/entity/scv_glow.png");

    public ScvRenderer(EntityRendererProvider.Context context) {
        super(context, new ScvModel(context.bakeLayer(AsteriskCraftClient.SCV_LAYER)), 0.5f);
        this.addLayer(new UnitGlowLayer<>(this, GLOW));
        // The pilot is a second draw with vanilla's own villager texture — see ScvPilotLayer.
        this.addLayer(new ScvPilotLayer(this, context));
    }

    @Override
    public ScvRenderState createRenderState() {
        return new ScvRenderState();
    }

    @Override
    public void extractRenderState(ScvEntity scv, ScvRenderState state, float partialTicks) {
        super.extractRenderState(scv, state, partialTicks);
        // getAttackAnim already interpolates the swing 0 -> 1 across partial ticks, so the stroke
        // stays smooth instead of stepping once per tick. It covers both jobs the cutter has:
        // HarvestGoal re-swings every tick it works a node, and melee swings the same hand.
        state.cutterProgress = scv.getAttackAnim(partialTicks);
    }

    @Override
    protected void scale(ScvRenderState state, PoseStack poseStack) {
        // The model spans y -5.5..24, i.e. 1.84 blocks, a shade over its own 1.8-block hitbox.
        poseStack.scale(0.95f, 0.95f, 0.95f);
    }

    @Override
    public Identifier getTextureLocation(ScvRenderState state) {
        return TEXTURE;
    }
}
