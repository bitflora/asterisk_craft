package net.bitflora.asteriskcraft.client;

import net.bitflora.asteriskcraft.AsteriskCraft;
import net.bitflora.asteriskcraft.entity.PhotonCannonEntity;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.resources.Identifier;

public class PhotonCannonRenderer extends MobRenderer<PhotonCannonEntity, LivingEntityRenderState, PhotonCannonModel> {
    private static final Identifier TEXTURE = AsteriskCraft.id("textures/entity/photon_cannon.png");

    public PhotonCannonRenderer(EntityRendererProvider.Context context) {
        super(context, new PhotonCannonModel(context.bakeLayer(AsteriskCraftClient.PHOTON_CANNON_LAYER)), 1.2f);
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
