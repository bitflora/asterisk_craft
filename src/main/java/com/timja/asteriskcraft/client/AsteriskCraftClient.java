package com.timja.asteriskcraft.client;

import com.timja.asteriskcraft.AsteriskCraft;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

@Mod(value = AsteriskCraft.MODID, dist = Dist.CLIENT)
@EventBusSubscriber(modid = AsteriskCraft.MODID, value = Dist.CLIENT)
public class AsteriskCraftClient {
    public static final ModelLayerLocation PROBE_LAYER = new ModelLayerLocation(AsteriskCraft.id("probe"), "main");

    @SubscribeEvent
    static void onRegisterLayers(EntityRenderersEvent.RegisterLayerDefinitions event) {
        event.registerLayerDefinition(PROBE_LAYER, ProbeModel::createBodyLayer);
    }

    @SubscribeEvent
    static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(AsteriskCraft.PROBE.get(), ProbeRenderer::new);
    }
}
