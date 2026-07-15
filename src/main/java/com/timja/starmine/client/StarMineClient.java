package com.timja.starmine.client;

import com.timja.starmine.StarMine;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

@Mod(value = StarMine.MODID, dist = Dist.CLIENT)
@EventBusSubscriber(modid = StarMine.MODID, value = Dist.CLIENT)
public class StarMineClient {
    public static final ModelLayerLocation PROBE_LAYER = new ModelLayerLocation(StarMine.id("probe"), "main");

    @SubscribeEvent
    static void onRegisterLayers(EntityRenderersEvent.RegisterLayerDefinitions event) {
        event.registerLayerDefinition(PROBE_LAYER, ProbeModel::createBodyLayer);
    }

    @SubscribeEvent
    static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(StarMine.PROBE.get(), ProbeRenderer::new);
    }
}
