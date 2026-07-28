package net.bitflora.asteriskcraft.client.zerg;

import com.mojang.blaze3d.vertex.PoseStack;
import net.bitflora.asteriskcraft.AsteriskCraft;
import net.bitflora.asteriskcraft.client.AsteriskCraftClient;
import net.bitflora.asteriskcraft.client.UnitGlowLayer;
import net.bitflora.asteriskcraft.entity.zerg.HydraliskEntity;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;

public class HydraliskRenderer extends MobRenderer<HydraliskEntity, HydraliskRenderState, HydraliskModel> {
    private static final Identifier TEXTURE = AsteriskCraft.id("textures/entity/hydralisk.png");
    private static final Identifier GLOW = AsteriskCraft.id("textures/entity/hydralisk_glow.png");

    public HydraliskRenderer(EntityRendererProvider.Context context) {
        super(context, new HydraliskModel(context.bakeLayer(AsteriskCraftClient.HYDRALISK_LAYER)), 0.6f);
        this.addLayer(new UnitGlowLayer<>(this, GLOW));
    }

    @Override
    public HydraliskRenderState createRenderState() {
        return new HydraliskRenderState();
    }

    @Override
    public void extractRenderState(HydraliskEntity hydralisk, HydraliskRenderState state, float partialTicks) {
        super.extractRenderState(hydralisk, state, partialTicks);
        // The entity counts the volley down to 0; the model wants it counting 0 -> 1 up, interpolated
        // so the whip stays smooth between ticks rather than stepping once per tick. Same conversion
        // as ZealotRenderer's.
        int remaining = hydralisk.getFireTicks();
        state.attackProgress = remaining <= 0
                ? 0.0f
                : 1.0f - Mth.clamp((remaining - partialTicks) / HydraliskEntity.FIRE_ANIM_TICKS, 0.0f, 1.0f);
    }

    @Override
    protected void scale(HydraliskRenderState state, PoseStack poseStack) {
        // Built at true pixel scale: the coil rests on y=24 and the crest spines top out near y=-7.4,
        // i.e. 1.96 blocks, just inside the 1.99 hitbox — so this is 1:1 and the texels stay square.
        poseStack.scale(1.0f, 1.0f, 1.0f);
    }

    @Override
    public Identifier getTextureLocation(HydraliskRenderState state) {
        return TEXTURE;
    }
}
