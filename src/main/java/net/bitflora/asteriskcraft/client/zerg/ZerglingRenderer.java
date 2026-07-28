package net.bitflora.asteriskcraft.client.zerg;

import com.mojang.blaze3d.vertex.PoseStack;
import net.bitflora.asteriskcraft.AsteriskCraft;
import net.bitflora.asteriskcraft.client.AsteriskCraftClient;
import net.bitflora.asteriskcraft.client.UnitGlowLayer;
import net.bitflora.asteriskcraft.entity.zerg.ZerglingEntity;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.Identifier;

public class ZerglingRenderer extends MobRenderer<ZerglingEntity, ZerglingRenderState, ZerglingModel> {
    private static final Identifier TEXTURE = AsteriskCraft.id("textures/entity/zergling.png");
    private static final Identifier GLOW = AsteriskCraft.id("textures/entity/zergling_glow.png");

    public ZerglingRenderer(EntityRendererProvider.Context context) {
        super(context, new ZerglingModel(context.bakeLayer(AsteriskCraftClient.ZERGLING_LAYER)), 0.4f);
        this.addLayer(new UnitGlowLayer<>(this, GLOW));
    }

    @Override
    public ZerglingRenderState createRenderState() {
        return new ZerglingRenderState();
    }

    @Override
    public void extractRenderState(ZerglingEntity zergling, ZerglingRenderState state, float partialTicks) {
        super.extractRenderState(zergling, state, partialTicks);
        // getAttackAnim already interpolates the swing 0 -> 1 across partial ticks, so the bite stays
        // smooth instead of stepping once per tick. Mob.doHurtTarget swings on every melee hit, so no
        // entity-side animation state is needed for this — unlike the Zealot, whose strike animation
        // has to outlast a single swing.
        state.attackProgress = zergling.getAttackAnim(partialTicks);
    }

    @Override
    protected void scale(ZerglingRenderState state, PoseStack poseStack) {
        // Low four-legged beast built at true pixel scale; the scythe tips are the apex at ~1.07
        // blocks. Tuned via runClient.
        poseStack.scale(0.9f, 0.9f, 0.9f);
    }

    @Override
    public Identifier getTextureLocation(ZerglingRenderState state) {
        return TEXTURE;
    }
}
