package net.bitflora.asteriskcraft.client.terran;

import com.mojang.blaze3d.vertex.PoseStack;
import net.bitflora.asteriskcraft.AsteriskCraft;
import net.bitflora.asteriskcraft.client.AsteriskCraftClient;
import net.bitflora.asteriskcraft.client.UnitGlowLayer;
import net.bitflora.asteriskcraft.entity.terran.GhostEntity;
import net.bitflora.asteriskcraft.stats.UnitStats;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;

public class GhostRenderer extends MobRenderer<GhostEntity, GhostRenderState, GhostModel> {
    private static final Identifier TEXTURE = AsteriskCraft.id("textures/entity/ghost.png");
    private static final Identifier GLOW = AsteriskCraft.id("textures/entity/ghost_glow.png");

    public GhostRenderer(EntityRendererProvider.Context context) {
        super(context, new GhostModel(context.bakeLayer(AsteriskCraftClient.GHOST_LAYER)), 0.5f);
        // The face, the mask and the eyepiece, before the glow: GhostModel owns no head geometry at
        // all, so without this the Ghost is a suit with nothing on top of it. See GhostHeadLayer.
        this.addLayer(new GhostHeadLayer(this, context));
        this.addLayer(new UnitGlowLayer<>(this, GLOW));
    }

    @Override
    public GhostRenderState createRenderState() {
        return new GhostRenderState();
    }

    @Override
    public void extractRenderState(GhostEntity ghost, GhostRenderState state, float partialTicks) {
        super.extractRenderState(ghost, state, partialTicks);
        // The entity counts the recoil down to 0; the model wants it counting 0 -> 1 up, interpolated
        // so the kick stays smooth between ticks rather than stepping once per tick. Same conversion
        // as MarineRenderer's.
        int remaining = ghost.getFireTicks();
        state.attackProgress = remaining <= 0
                ? 0.0f
                : 1.0f - Mth.clamp((remaining - partialTicks) / UnitStats.GHOST.attackAnimTicks(), 0.0f, 1.0f);
    }

    @Override
    protected void scale(GhostRenderState state, PoseStack poseStack) {
        // Built at true pixel scale, on vanilla's villager frame: soles on y=24 and the borrowed head
        // topping out at y=-10, i.e. 2.125 blocks over a 1.95 hitbox — the Firebat's overhang, since
        // like it the Ghost stacks nothing on the crown. 1:1 is also what lets GhostHeadLayer drop
        // vanilla's head straight in with no scale of its own.
        poseStack.scale(1.0f, 1.0f, 1.0f);
    }

    @Override
    public Identifier getTextureLocation(GhostRenderState state) {
        return TEXTURE;
    }
}
