package net.bitflora.asteriskcraft.client.zerg;

import com.mojang.blaze3d.vertex.PoseStack;
import net.bitflora.asteriskcraft.AsteriskCraft;
import net.bitflora.asteriskcraft.client.AsteriskCraftClient;
import net.bitflora.asteriskcraft.client.UnitGlowLayer;
import net.bitflora.asteriskcraft.entity.zerg.DroneEntity;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.Identifier;

public class DroneRenderer extends MobRenderer<DroneEntity, DroneRenderState, DroneModel> {
    private static final Identifier TEXTURE = AsteriskCraft.id("textures/entity/drone.png");
    private static final Identifier GLOW = AsteriskCraft.id("textures/entity/drone_glow.png");

    public DroneRenderer(EntityRendererProvider.Context context) {
        // Wide shadow: this one is a squat crab, broader than it is tall.
        super(context, new DroneModel(context.bakeLayer(AsteriskCraftClient.DRONE_LAYER)), 0.4f);
        this.addLayer(new UnitGlowLayer<>(this, GLOW));
    }

    @Override
    public DroneRenderState createRenderState() {
        return new DroneRenderState();
    }

    @Override
    public void extractRenderState(DroneEntity drone, DroneRenderState state, float partialTicks) {
        super.extractRenderState(drone, state, partialTicks);
        // getAttackAnim already interpolates the swing 0 -> 1 across partial ticks, so the chop stays
        // smooth instead of stepping once per tick. HarvestGoal re-swings every tick it is in range of
        // a node, which is what keeps the Drone chopping for as long as it mines.
        state.swingProgress = drone.getAttackAnim(partialTicks);
    }

    @Override
    protected void scale(DroneRenderState state, PoseStack poseStack) {
        // Small squat worker; built at true pixel scale. Tuned via runClient.
        poseStack.scale(0.9f, 0.9f, 0.9f);
    }

    @Override
    public Identifier getTextureLocation(DroneRenderState state) {
        return TEXTURE;
    }
}
