package net.bitflora.asteriskcraft.client.terran;

import com.mojang.blaze3d.vertex.PoseStack;
import net.bitflora.asteriskcraft.AsteriskCraft;
import net.bitflora.asteriskcraft.client.AsteriskCraftClient;
import net.bitflora.asteriskcraft.client.UnitGlowLayer;
import net.bitflora.asteriskcraft.entity.terran.GoliathEntity;
import net.bitflora.asteriskcraft.stats.UnitStats;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;

/**
 * Draws the Goliath: the mod's own muzzles and cockpit tub from {@code goliath.png}, vanilla's iron
 * golem and the cannon pods that replaced its arms from {@link GoliathGolemLayer}, and the villager
 * riding it from {@link GoliathPilotLayer}.
 *
 * <p>Layer order matters: both borrows go on before the glow, so the emissive pass re-submits only
 * the model this renderer owns and neither borrowed draw is ever lit — the order
 * {@code MarineRenderer} and {@code MissileTurretRenderer} use for the same reason.
 */
public class GoliathRenderer extends MobRenderer<GoliathEntity, GoliathRenderState, GoliathModel> {
    private static final Identifier TEXTURE = AsteriskCraft.id("textures/entity/goliath.png");
    private static final Identifier GLOW = AsteriskCraft.id("textures/entity/goliath_glow.png");

    /**
     * How far the whole assembly is shrunk. Vanilla's iron golem stands 43 pixels — 2.69 blocks —
     * and {@code entity.UnitFootprintTest} lets no ground unit past 2.0, because
     * {@code NodeEvaluator} would then demand three blocks of clearance and the unit would refuse
     * every doorway. 0.72 brings the chassis to 1.94 and leaves it 0.81 wide, which is one
     * pathfinding node. The pilot's head and the muzzles still overhang the hitbox, which is the
     * same overhang the Marine's helmet and rifle carry.
     */
    private static final float SCALE = 0.72f;

    public GoliathRenderer(EntityRendererProvider.Context context) {
        super(context, new GoliathModel(context.bakeLayer(AsteriskCraftClient.GOLIATH_LAYER)), 0.6f);
        this.addLayer(new GoliathGolemLayer(this, context));
        this.addLayer(new GoliathPilotLayer(this, context));
        this.addLayer(new UnitGlowLayer<>(this, GLOW));
    }

    @Override
    public GoliathRenderState createRenderState() {
        return new GoliathRenderState();
    }

    @Override
    public void extractRenderState(GoliathEntity goliath, GoliathRenderState state, float partialTicks) {
        super.extractRenderState(goliath, state, partialTicks);
        // The entity counts the recoil down to 0; the model wants it counting 0 -> 1 up, interpolated
        // so an eight-tick kick stays smooth rather than stepping once per tick. Same conversion as
        // MarineRenderer's.
        int remaining = goliath.getFireTicks();
        state.attackProgress = remaining <= 0
                ? 0.0f
                : 1.0f - Mth.clamp((remaining - partialTicks) / UnitStats.GOLIATH.attackAnimTicks(),
                        0.0f, 1.0f);
    }

    @Override
    protected void scale(GoliathRenderState state, PoseStack poseStack) {
        poseStack.scale(SCALE, SCALE, SCALE);
    }

    @Override
    public Identifier getTextureLocation(GoliathRenderState state) {
        return TEXTURE;
    }
}
