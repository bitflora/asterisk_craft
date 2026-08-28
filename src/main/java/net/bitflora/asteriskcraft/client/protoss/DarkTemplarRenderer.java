package net.bitflora.asteriskcraft.client.protoss;

import com.mojang.blaze3d.vertex.PoseStack;
import net.bitflora.asteriskcraft.AsteriskCraft;
import net.bitflora.asteriskcraft.client.AsteriskCraftClient;
import net.bitflora.asteriskcraft.client.UnitGlowLayer;
import net.bitflora.asteriskcraft.entity.protoss.DarkTemplarEntity;
import net.bitflora.asteriskcraft.stats.UnitStats;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;

/**
 * Nothing here knows the unit is cloaked. The ghosting and the red detection outline are applied
 * afterwards by {@code client.DetectionRenderStateModifier}, which rewrites the finished render state —
 * so this renderer is exactly the Zealot's, and the emissive blade survives the transparency only
 * because {@link UnitGlowLayer} suppresses itself on a body that is not drawn at all rather than on
 * any invisible body.
 */
public class DarkTemplarRenderer extends MobRenderer<DarkTemplarEntity, DarkTemplarRenderState, DarkTemplarModel> {
    private static final Identifier TEXTURE = AsteriskCraft.id("textures/entity/dark_templar.png");
    private static final Identifier GLOW = AsteriskCraft.id("textures/entity/dark_templar_glow.png");

    public DarkTemplarRenderer(EntityRendererProvider.Context context) {
        super(context, new DarkTemplarModel(context.bakeLayer(AsteriskCraftClient.DARK_TEMPLAR_LAYER)), 0.5f);
        this.addLayer(new UnitGlowLayer<>(this, GLOW));
    }

    @Override
    public DarkTemplarRenderState createRenderState() {
        return new DarkTemplarRenderState();
    }

    @Override
    public void extractRenderState(DarkTemplarEntity templar, DarkTemplarRenderState state, float partialTicks) {
        super.extractRenderState(templar, state, partialTicks);
        // The entity counts the strike down to 0; the model wants it counting 0 -> 1 up, interpolated
        // so the chop stays smooth between ticks rather than stepping once per tick.
        int remaining = templar.getAttackTicks();
        state.attackProgress = remaining <= 0
                ? 0.0f
                : 1.0f - Mth.clamp((remaining - partialTicks) / UnitStats.DARK_TEMPLAR.attackAnimTicks(), 0.0f, 1.0f);
    }

    @Override
    protected void scale(DarkTemplarRenderState state, PoseStack poseStack) {
        // Marginally slighter than the Zealot's 0.95: same frame, leaner build.
        poseStack.scale(0.92f, 0.92f, 0.92f);
    }

    @Override
    public Identifier getTextureLocation(DarkTemplarRenderState state) {
        return TEXTURE;
    }
}
