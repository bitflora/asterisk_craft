package net.bitflora.asteriskcraft.client.terran;

import com.mojang.blaze3d.vertex.PoseStack;
import net.bitflora.asteriskcraft.AsteriskCraft;
import net.bitflora.asteriskcraft.client.AsteriskCraftClient;
import net.bitflora.asteriskcraft.client.UnitGlowLayer;
import net.bitflora.asteriskcraft.entity.terran.FirebatEntity;
import net.bitflora.asteriskcraft.stats.UnitStats;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;

public class FirebatRenderer extends MobRenderer<FirebatEntity, FirebatRenderState, FirebatModel> {
    private static final Identifier TEXTURE = AsteriskCraft.id("textures/entity/firebat.png");
    private static final Identifier GLOW = AsteriskCraft.id("textures/entity/firebat_glow.png");

    public FirebatRenderer(EntityRendererProvider.Context context) {
        super(context, new FirebatModel(context.bakeLayer(AsteriskCraftClient.FIREBAT_LAYER)), 0.5f);
        // The face, before the glow: FirebatModel owns no head geometry at all, so without this the
        // Firebat is a suit with nothing on top of it. See FirebatHeadLayer.
        this.addLayer(new FirebatHeadLayer(this, context));
        this.addLayer(new UnitGlowLayer<>(this, GLOW));
    }

    @Override
    public FirebatRenderState createRenderState() {
        return new FirebatRenderState();
    }

    @Override
    public void extractRenderState(FirebatEntity firebat, FirebatRenderState state, float partialTicks) {
        super.extractRenderState(firebat, state, partialTicks);
        // The entity counts the sweep down to 0; the model wants it counting 0 -> 1 up, interpolated
        // so the thrust stays smooth between ticks rather than stepping once per tick. Same
        // conversion as MarineRenderer's.
        int remaining = firebat.getFireTicks();
        state.attackProgress = remaining <= 0
                ? 0.0f
                : 1.0f - Mth.clamp((remaining - partialTicks) / UnitStats.FIREBAT.attackAnimTicks(), 0.0f, 1.0f);
    }

    @Override
    protected void scale(FirebatRenderState state, PoseStack poseStack) {
        // Built at true pixel scale, on vanilla's villager frame: soles on y=24 and the borrowed head
        // topping out at y=-10, i.e. 2.125 blocks over a 1.95 hitbox — a shade under the Marine's
        // overhang, since nothing is stacked on the crown. 1:1 is also what lets FirebatHeadLayer
        // drop vanilla's head straight in with no scale of its own.
        poseStack.scale(1.0f, 1.0f, 1.0f);
    }

    @Override
    public Identifier getTextureLocation(FirebatRenderState state) {
        return TEXTURE;
    }
}
