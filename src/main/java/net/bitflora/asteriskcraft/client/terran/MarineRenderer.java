package net.bitflora.asteriskcraft.client.terran;

import com.mojang.blaze3d.vertex.PoseStack;
import net.bitflora.asteriskcraft.AsteriskCraft;
import net.bitflora.asteriskcraft.client.AsteriskCraftClient;
import net.bitflora.asteriskcraft.client.UnitGlowLayer;
import net.bitflora.asteriskcraft.entity.terran.MarineEntity;
import net.bitflora.asteriskcraft.stats.UnitStats;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;

public class MarineRenderer extends MobRenderer<MarineEntity, MarineRenderState, MarineModel> {
    private static final Identifier TEXTURE = AsteriskCraft.id("textures/entity/marine.png");
    private static final Identifier GLOW = AsteriskCraft.id("textures/entity/marine_glow.png");

    public MarineRenderer(EntityRendererProvider.Context context) {
        super(context, new MarineModel(context.bakeLayer(AsteriskCraftClient.MARINE_LAYER)), 0.5f);
        // The face, before the glow: MarineModel owns no head geometry at all, so without this the
        // Marine is a helmet with a hole in it. See MarineHeadLayer.
        this.addLayer(new MarineHeadLayer(this, context));
        this.addLayer(new UnitGlowLayer<>(this, GLOW));
    }

    @Override
    public MarineRenderState createRenderState() {
        return new MarineRenderState();
    }

    @Override
    public void extractRenderState(MarineEntity marine, MarineRenderState state, float partialTicks) {
        super.extractRenderState(marine, state, partialTicks);
        // The entity counts the recoil down to 0; the model wants it counting 0 -> 1 up, interpolated
        // so the kick stays smooth between ticks rather than stepping once per tick. Same conversion
        // as HydraliskRenderer's.
        int remaining = marine.getFireTicks();
        state.attackProgress = remaining <= 0
                ? 0.0f
                : 1.0f - Mth.clamp((remaining - partialTicks) / UnitStats.MARINE.attackAnimTicks(), 0.0f, 1.0f);
    }

    @Override
    protected void scale(MarineRenderState state, PoseStack poseStack) {
        // Built at true pixel scale, on vanilla's villager frame: soles on y=24 and the helmet crown
        // topping out at y=-12, i.e. 2.25 blocks over a 1.95 hitbox — the same overhang vanilla's own
        // villager carries. 1:1 is also what lets MarineHeadLayer drop vanilla's head straight in
        // with no scale of its own, unlike the SCV's pilot.
        poseStack.scale(1.0f, 1.0f, 1.0f);
    }

    @Override
    public Identifier getTextureLocation(MarineRenderState state) {
        return TEXTURE;
    }
}
