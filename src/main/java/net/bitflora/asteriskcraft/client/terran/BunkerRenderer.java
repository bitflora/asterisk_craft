package net.bitflora.asteriskcraft.client.terran;

import com.mojang.blaze3d.vertex.PoseStack;
import net.bitflora.asteriskcraft.AsteriskCraft;
import net.bitflora.asteriskcraft.client.AsteriskCraftClient;
import net.bitflora.asteriskcraft.client.UnitGlowLayer;
import net.bitflora.asteriskcraft.entity.terran.BunkerEntity;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.Identifier;

/**
 * Draws the Bunker, and reads two things off the entity that the base render state doesn't carry: how
 * many units are inside (one barrel each) and how far through construction it is.
 */
public class BunkerRenderer extends MobRenderer<BunkerEntity, BunkerRenderState, BunkerModel> {
    private static final Identifier TEXTURE = AsteriskCraft.id("textures/entity/bunker.png");
    private static final Identifier GLOW = AsteriskCraft.id("textures/entity/bunker_glow.png");

    /**
     * How far the building sits underground at the start of its build. The model stands about 1.6
     * blocks tall, so two blocks of sink leaves nothing showing on the first tick and the roof
     * breaking the surface about a third of the way in.
     */
    private static final float SINK_BLOCKS = 2.0f;

    public BunkerRenderer(EntityRendererProvider.Context context) {
        super(context, new BunkerModel(context.bakeLayer(AsteriskCraftClient.BUNKER_LAYER)), 1.4f);
        this.addLayer(new UnitGlowLayer<>(this, GLOW));
    }

    @Override
    public BunkerRenderState createRenderState() {
        return new BunkerRenderState();
    }

    @Override
    public void extractRenderState(BunkerEntity bunker, BunkerRenderState state, float partialTicks) {
        super.extractRenderState(bunker, state, partialTicks);
        // No synced field of the mod's own behind either of these: the passenger link is vanilla-synced
        // and the build counter is a SynchedEntityData int, so both are already true on this side.
        state.garrison = bunker.garrisonSize();
        state.buildProgress = bunker.buildProgress();
    }

    /**
     * Rises out of the ground as it is built, so the terrain itself hides the unfinished part — the
     * Lurker's trick, used for the opposite motion, and for the same reason: no second model and no
     * scaffold geometry.
     *
     * <p><b>+Y is down here.</b> {@code LivingEntityRenderer.submit} applies
     * {@code poseStack.scale(-1, -1, 1)} immediately before calling this hook, so the model's own
     * Y-down authoring space is what this translate is in — a positive Y sinks the building.
     */
    @Override
    protected void scale(BunkerRenderState state, PoseStack poseStack) {
        poseStack.translate(0.0f, (1.0f - state.buildProgress) * SINK_BLOCKS, 0.0f);
    }

    @Override
    public Identifier getTextureLocation(BunkerRenderState state) {
        return TEXTURE;
    }
}
