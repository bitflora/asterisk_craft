package net.bitflora.asteriskcraft.client;

import net.bitflora.asteriskcraft.AsteriskCraft;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.renderer.blockentity.BeaconRenderer;
import net.minecraft.client.renderer.entity.SkeletonRenderer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;

@Mod(value = AsteriskCraft.MODID, dist = Dist.CLIENT)
@EventBusSubscriber(modid = AsteriskCraft.MODID, value = Dist.CLIENT)
public class AsteriskCraftClient {
    public static final ModelLayerLocation PROBE_LAYER = new ModelLayerLocation(AsteriskCraft.id("probe"), "main");
    public static final ModelLayerLocation ZEALOT_LAYER = new ModelLayerLocation(AsteriskCraft.id("zealot"), "main");
    public static final ModelLayerLocation ZERGLING_LAYER = new ModelLayerLocation(AsteriskCraft.id("zergling"), "main");
    public static final ModelLayerLocation HYDRALISK_LAYER = new ModelLayerLocation(AsteriskCraft.id("hydralisk"), "main");
    public static final ModelLayerLocation DRONE_LAYER = new ModelLayerLocation(AsteriskCraft.id("drone"), "main");

    @SubscribeEvent
    static void onRegisterLayers(EntityRenderersEvent.RegisterLayerDefinitions event) {
        event.registerLayerDefinition(PROBE_LAYER, ProbeModel::createBodyLayer);
        event.registerLayerDefinition(ZEALOT_LAYER, ZealotModel::createBodyLayer);
        event.registerLayerDefinition(ZERGLING_LAYER, ZerglingModel::createBodyLayer);
        event.registerLayerDefinition(HYDRALISK_LAYER, HydraliskModel::createBodyLayer);
        event.registerLayerDefinition(DRONE_LAYER, DroneModel::createBodyLayer);
    }

    @SubscribeEvent
    static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(AsteriskCraft.PROBE.get(), ProbeRenderer::new);
        event.registerEntityRenderer(AsteriskCraft.ZEALOT.get(), ZealotRenderer::new);
        event.registerEntityRenderer(AsteriskCraft.DRAGOON.get(), SkeletonRenderer::new);
        event.registerEntityRenderer(AsteriskCraft.DRONE.get(), DroneRenderer::new);
        event.registerEntityRenderer(AsteriskCraft.ZERGLING.get(), ZerglingRenderer::new);
        event.registerEntityRenderer(AsteriskCraft.HYDRALISK.get(), HydraliskRenderer::new);
        // Nexus/Hive shoot a vanilla beacon beam upward as a locator; reuses BeaconRenderer since
        // both block entities implement BeaconBeamOwner. See docs/neoforge-api-notes.md.
        event.registerBlockEntityRenderer(AsteriskCraft.NEXUS_BLOCK_ENTITY.get(), context -> new BeaconRenderer<>());
        event.registerBlockEntityRenderer(AsteriskCraft.HIVE_BLOCK_ENTITY.get(), context -> new BeaconRenderer<>());
    }

    @SubscribeEvent
    static void onRegisterMenuScreens(RegisterMenuScreensEvent event) {
        event.register(AsteriskCraft.PRODUCTION_MENU.get(), ProductionScreen::new);
    }
}
