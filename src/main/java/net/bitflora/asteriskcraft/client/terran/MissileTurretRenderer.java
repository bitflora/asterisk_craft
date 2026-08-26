package net.bitflora.asteriskcraft.client.terran;

import com.mojang.blaze3d.vertex.PoseStack;
import net.bitflora.asteriskcraft.AsteriskCraft;
import net.bitflora.asteriskcraft.client.AsteriskCraftClient;
import net.bitflora.asteriskcraft.client.UnitGlowLayer;
import net.bitflora.asteriskcraft.entity.terran.MissileTurretEntity;
import net.bitflora.asteriskcraft.stats.UnitStats;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;

/**
 * Draws the Missile Turret: the mod's own missile racks from {@code missile_turret.png}, plus
 * vanilla's iron golem underneath them from {@link MissileTurretGolemLayer}.
 *
 * <p>Layer order matters: the golem goes on before the glow, so the emissive pass re-submits only
 * the model this renderer owns and the borrowed body is never lit — the same order
 * {@code MarineRenderer} uses for the same reason.
 */
public class MissileTurretRenderer
        extends MobRenderer<MissileTurretEntity, MissileTurretRenderState, MissileTurretModel> {
    private static final Identifier TEXTURE = AsteriskCraft.id("textures/entity/missile_turret.png");
    private static final Identifier GLOW = AsteriskCraft.id("textures/entity/missile_turret_glow.png");

    /**
     * How far the turret sits underground at the start of its build. It stands about 2.9 blocks, so
     * three blocks of sink leaves nothing showing on the first tick and the muzzles breaking the
     * surface around halfway.
     */
    private static final float SINK_BLOCKS = 3.0f;

    public MissileTurretRenderer(EntityRendererProvider.Context context) {
        super(context, new MissileTurretModel(context.bakeLayer(AsteriskCraftClient.MISSILE_TURRET_LAYER)), 1.0f);
        this.addLayer(new MissileTurretGolemLayer(this, context));
        this.addLayer(new UnitGlowLayer<>(this, GLOW));
    }

    @Override
    public MissileTurretRenderState createRenderState() {
        return new MissileTurretRenderState();
    }

    @Override
    public void extractRenderState(MissileTurretEntity turret, MissileTurretRenderState state, float partialTicks) {
        super.extractRenderState(turret, state, partialTicks);
        // The entity counts the strike animation down; the model wants it counting up, and
        // interpolated so a ten-tick recoil doesn't read as a stutter.
        int remaining = turret.getAttackTicks();
        state.attackProgress = remaining <= 0 ? 0.0f
                : 1.0f - Mth.clamp((remaining - partialTicks) / UnitStats.MISSILE_TURRET.attackAnimTicks(),
                        0.0f, 1.0f);
        state.buildProgress = turret.buildProgress();
    }

    /**
     * Rises out of the ground as it is built, so the terrain itself hides the unfinished part — the
     * Bunker's trick, and the Lurker's before it: no second model and no scaffold geometry.
     *
     * <p><b>+Y is down here.</b> {@code LivingEntityRenderer.submit} applies
     * {@code poseStack.scale(-1, -1, 1)} immediately before calling this hook, so the model's own
     * Y-down authoring space is what this translate is in — a positive Y sinks the building.
     */
    @Override
    protected void scale(MissileTurretRenderState state, PoseStack poseStack) {
        poseStack.translate(0.0f, (1.0f - state.buildProgress) * SINK_BLOCKS, 0.0f);
    }

    @Override
    public Identifier getTextureLocation(MissileTurretRenderState state) {
        return TEXTURE;
    }
}
