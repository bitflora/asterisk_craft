package net.bitflora.asteriskcraft.client.protoss;

import com.mojang.blaze3d.vertex.PoseStack;
import net.bitflora.asteriskcraft.AsteriskCraft;
import net.bitflora.asteriskcraft.client.AsteriskCraftClient;
import net.bitflora.asteriskcraft.client.UnitGlowLayer;
import net.bitflora.asteriskcraft.entity.protoss.ZealotEntity;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;

public class ZealotRenderer extends MobRenderer<ZealotEntity, ZealotRenderState, ZealotModel> {
    private static final Identifier TEXTURE = AsteriskCraft.id("textures/entity/zealot.png");
    private static final Identifier GLOW = AsteriskCraft.id("textures/entity/zealot_glow.png");

    public ZealotRenderer(EntityRendererProvider.Context context) {
        super(context, new ZealotModel(context.bakeLayer(AsteriskCraftClient.ZEALOT_LAYER)), 0.5f);
        this.addLayer(new UnitGlowLayer<>(this, GLOW));
    }

    @Override
    public ZealotRenderState createRenderState() {
        return new ZealotRenderState();
    }

    @Override
    public void extractRenderState(ZealotEntity zealot, ZealotRenderState state, float partialTicks) {
        super.extractRenderState(zealot, state, partialTicks);
        // The entity counts the strike down to 0; the model wants it counting 0 -> 1 up, interpolated
        // so the slash stays smooth between ticks rather than stepping once per tick.
        int remaining = zealot.getAttackTicks();
        state.attackProgress = remaining <= 0
                ? 0.0f
                : 1.0f - Mth.clamp((remaining - partialTicks) / ZealotEntity.ATTACK_ANIM_TICKS, 0.0f, 1.0f);
    }

    @Override
    protected void scale(ZealotRenderState state, PoseStack poseStack) {
        // Blocky biped is built at true humanoid pixel scale (~2 blocks tall); tuned via runClient.
        poseStack.scale(0.95f, 0.95f, 0.95f);
    }

    @Override
    public Identifier getTextureLocation(ZealotRenderState state) {
        return TEXTURE;
    }
}
