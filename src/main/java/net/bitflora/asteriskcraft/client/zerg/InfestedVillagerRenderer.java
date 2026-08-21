package net.bitflora.asteriskcraft.client.zerg;

import com.mojang.blaze3d.vertex.PoseStack;
import net.bitflora.asteriskcraft.AsteriskCraft;
import net.bitflora.asteriskcraft.client.AsteriskCraftClient;
import net.bitflora.asteriskcraft.client.UnitGlowLayer;
import net.bitflora.asteriskcraft.entity.zerg.InfestedVillagerEntity;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;

/**
 * Draws the Infested Villager, and — the only reason this class is more than boilerplate — gives it
 * the two cues a player already knows mean "get away from that": the body swells, and it flashes
 * white faster and faster as the fuse burns.
 *
 * <p>Both are lifted from vanilla's {@code CreeperRenderer} deliberately rather than invented. This
 * unit deals 250 in a three-block radius to everything including the player, so the warning it gives
 * has to be one nobody has to learn; a bespoke tell would be prettier and would get people killed.
 */
public class InfestedVillagerRenderer
        extends MobRenderer<InfestedVillagerEntity, InfestedVillagerRenderState, InfestedVillagerModel> {

    private static final Identifier TEXTURE = AsteriskCraft.id("textures/entity/infested_villager.png");
    private static final Identifier GLOW = AsteriskCraft.id("textures/entity/infested_villager_glow.png");

    public InfestedVillagerRenderer(EntityRendererProvider.Context context) {
        super(context,
                new InfestedVillagerModel(context.bakeLayer(AsteriskCraftClient.INFESTED_VILLAGER_LAYER)),
                0.4f);
        this.addLayer(new UnitGlowLayer<>(this, GLOW));
    }

    @Override
    public InfestedVillagerRenderState createRenderState() {
        return new InfestedVillagerRenderState();
    }

    @Override
    public void extractRenderState(InfestedVillagerEntity bomber, InfestedVillagerRenderState state,
            float partialTicks) {
        super.extractRenderState(bomber, state, partialTicks);
        state.swelling = bomber.getSwelling(partialTicks);
    }

    /**
     * The swell: the body inflates and shudders as the fuse burns, quartic so nearly all of it
     * happens in the last few ticks. Also carries this unit's overall render scale — the model is
     * authored a touch larger than the 0.6 x 1.95 hitbox, as every humanoid model here is.
     */
    @Override
    protected void scale(InfestedVillagerRenderState state, PoseStack poseStack) {
        float fuse = state.swelling;
        float shudder = 1.0f + Mth.sin(fuse * 100.0f) * fuse * 0.01f;
        fuse = Mth.clamp(fuse, 0.0f, 1.0f);
        fuse *= fuse;
        fuse *= fuse;
        float wide = (1.0f + fuse * 0.4f) * shudder;
        float tall = (1.0f + fuse * 0.1f) / shudder;
        poseStack.scale(0.95f * wide, 0.95f * tall, 0.95f * wide);
    }

    /**
     * The flash. {@code getWhiteOverlayProgress} takes only the state in this version — there is no
     * {@code partialTicks} argument — and the alternating strobe comes from the fuse fraction itself,
     * so it speeds up on its own as the count accelerates toward 1.
     */
    @Override
    protected float getWhiteOverlayProgress(InfestedVillagerRenderState state) {
        float fuse = state.swelling;
        return (int) (fuse * 10.0f) % 2 == 0 ? 0.0f : Mth.clamp(fuse, 0.5f, 1.0f);
    }

    @Override
    public Identifier getTextureLocation(InfestedVillagerRenderState state) {
        return TEXTURE;
    }
}
