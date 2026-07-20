package net.bitflora.asteriskcraft.client.zerg;

import net.bitflora.asteriskcraft.AsteriskCraft;
import net.bitflora.asteriskcraft.client.AsteriskCraftClient;
import net.bitflora.asteriskcraft.client.UnitGlowLayer;
import net.bitflora.asteriskcraft.client.UnitOverlayLayer;
import net.bitflora.asteriskcraft.entity.zerg.DroneEntity;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.resources.Identifier;

public class DroneRenderer extends MobRenderer<DroneEntity, LivingEntityRenderState, DroneModel> {
    private static final Identifier TEXTURE = AsteriskCraft.id("textures/entity/drone.png");
    private static final Identifier OVERLAY = AsteriskCraft.id("textures/entity/drone_overlay.png");
    private static final Identifier GLOW = AsteriskCraft.id("textures/entity/drone_glow.png");

    public DroneRenderer(EntityRendererProvider.Context context) {
        super(context, new DroneModel(context.bakeLayer(AsteriskCraftClient.DRONE_LAYER)), 0.3f);
        this.addLayer(new UnitOverlayLayer<>(this, OVERLAY));
        this.addLayer(new UnitGlowLayer<>(this, GLOW));
    }

    @Override
    public LivingEntityRenderState createRenderState() {
        return new LivingEntityRenderState();
    }

    @Override
    public Identifier getTextureLocation(LivingEntityRenderState state) {
        return TEXTURE;
    }
}
