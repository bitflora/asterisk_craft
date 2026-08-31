package net.bitflora.asteriskcraft.client.protoss;

import com.mojang.blaze3d.vertex.PoseStack;
import net.bitflora.asteriskcraft.AsteriskCraft;
import net.bitflora.asteriskcraft.client.AsteriskCraftClient;
import net.bitflora.asteriskcraft.client.UnitGlowLayer;
import net.bitflora.asteriskcraft.entity.protoss.ArchonEntity;
import net.bitflora.asteriskcraft.stats.UnitStats;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;

/**
 * Renders the Archon. The {@link UnitGlowLayer} is doing more here than on any other unit: it is not
 * lighting a detail on the model, it is drawing the ball of light that the model's {@code orb*}
 * parts are — and which this renderer's own body pass discards as transparent. See
 * {@link ArchonModel}.
 */
public class ArchonRenderer extends MobRenderer<ArchonEntity, ArchonRenderState, ArchonModel> {
    private static final Identifier TEXTURE = AsteriskCraft.id("textures/entity/archon.png");
    private static final Identifier GLOW = AsteriskCraft.id("textures/entity/archon_glow.png");

    public ArchonRenderer(EntityRendererProvider.Context context) {
        super(context, new ArchonModel(context.bakeLayer(AsteriskCraftClient.ARCHON_LAYER)), 1.0f);
        this.addLayer(new UnitGlowLayer<>(this, GLOW));
    }

    @Override
    public ArchonRenderState createRenderState() {
        return new ArchonRenderState();
    }

    @Override
    public void extractRenderState(ArchonEntity archon, ArchonRenderState state, float partialTicks) {
        super.extractRenderState(archon, state, partialTicks);
        // The entity counts the strike down to 0; the model wants it counting 0 -> 1 up, interpolated
        // so the release stays smooth between ticks rather than stepping once per tick.
        int remaining = archon.getAttackTicks();
        state.attackProgress = remaining <= 0
                ? 0.0f
                : 1.0f - Mth.clamp((remaining - partialTicks) / UnitStats.ARCHON.attackAnimTicks(), 0.0f, 1.0f);
    }

    @Override
    protected void scale(ArchonRenderState state, PoseStack poseStack) {
        // The "large Protoss" the design asks for, and the largest scale on any ground unit. The
        // hitbox cannot follow it: 0.9 x 1.99 is what a unit that walks is allowed to be, since
        // UnitFootprintTest requires a ground unit to fit a two-high opening and anything from 2.0
        // up demands three blocks of clearance. So the model deliberately overruns its box — a
        // block or so of ball stands above it, on top of the horizontal overhang the Zealot's
        // pauldrons already established. That is the accepted price of the size; the alternative is
        // a unit that cannot walk through a doorway. Tuned via runClient.
        poseStack.scale(1.95f, 1.95f, 1.95f);
    }

    @Override
    public Identifier getTextureLocation(ArchonRenderState state) {
        return TEXTURE;
    }
}
