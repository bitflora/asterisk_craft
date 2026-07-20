package net.bitflora.asteriskcraft.client.protoss;

import net.bitflora.asteriskcraft.AsteriskCraft;
import net.bitflora.asteriskcraft.client.AsteriskCraftClient;
import net.bitflora.asteriskcraft.client.UnitGlowLayer;
import net.bitflora.asteriskcraft.entity.protoss.PhotonCannonEntity;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.resources.Identifier;

public class PhotonCannonRenderer extends MobRenderer<PhotonCannonEntity, LivingEntityRenderState, PhotonCannonModel> {
    private static final Identifier TEXTURE = AsteriskCraft.id("textures/entity/photon_cannon.png");
    private static final Identifier GLOW = AsteriskCraft.id("textures/entity/photon_cannon_glow.png");

    public PhotonCannonRenderer(EntityRendererProvider.Context context) {
        super(context, new PhotonCannonModel(context.bakeLayer(AsteriskCraftClient.PHOTON_CANNON_LAYER)), 1.2f);
        // Emissive pass: the lens, eyes, and accent panels glow at full brightness (see UnitGlowLayer).
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
