package net.bitflora.asteriskcraft.client;

import net.bitflora.asteriskcraft.AsteriskCraft;
import net.bitflora.asteriskcraft.client.protoss.DarkTemplarModel;
import net.bitflora.asteriskcraft.client.protoss.DarkTemplarRenderer;
import net.bitflora.asteriskcraft.client.protoss.DragoonModel;
import net.bitflora.asteriskcraft.client.protoss.DragoonRenderer;
import net.bitflora.asteriskcraft.client.protoss.PhotonCannonModel;
import net.bitflora.asteriskcraft.client.protoss.PhotonCannonRenderer;
import net.bitflora.asteriskcraft.client.protoss.ProbeModel;
import net.bitflora.asteriskcraft.client.protoss.ProbeRenderer;
import net.bitflora.asteriskcraft.client.protoss.ScoutModel;
import net.bitflora.asteriskcraft.client.protoss.ScoutRenderer;
import net.bitflora.asteriskcraft.client.protoss.ZealotModel;
import net.bitflora.asteriskcraft.client.protoss.ZealotRenderer;
import net.bitflora.asteriskcraft.client.zerg.DroneModel;
import net.bitflora.asteriskcraft.client.zerg.DroneRenderer;
import net.bitflora.asteriskcraft.client.zerg.HydraliskModel;
import net.bitflora.asteriskcraft.client.zerg.HydraliskRenderer;
import net.bitflora.asteriskcraft.client.zerg.LurkerModel;
import net.bitflora.asteriskcraft.client.zerg.LurkerRenderer;
import net.bitflora.asteriskcraft.client.zerg.MutaliskModel;
import net.bitflora.asteriskcraft.client.zerg.MutaliskRenderer;
import net.bitflora.asteriskcraft.client.zerg.SunkenColonyModel;
import net.bitflora.asteriskcraft.client.zerg.SporeColonyModel;
import net.bitflora.asteriskcraft.client.zerg.SunkenColonyRenderer;
import net.bitflora.asteriskcraft.client.zerg.SporeColonyRenderer;
import net.bitflora.asteriskcraft.client.zerg.UltraliskRenderer;
import net.bitflora.asteriskcraft.client.zerg.ZerglingModel;
import net.bitflora.asteriskcraft.client.zerg.ZerglingRenderer;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.renderer.blockentity.BeaconRenderer;
import net.minecraft.client.renderer.entity.EvokerFangsRenderer;
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
    public static final ModelLayerLocation DRAGOON_LAYER = new ModelLayerLocation(AsteriskCraft.id("dragoon"), "main");
    public static final ModelLayerLocation SCOUT_LAYER = new ModelLayerLocation(AsteriskCraft.id("scout"), "main");
    public static final ModelLayerLocation DARK_TEMPLAR_LAYER = new ModelLayerLocation(AsteriskCraft.id("dark_templar"), "main");
    public static final ModelLayerLocation ZERGLING_LAYER = new ModelLayerLocation(AsteriskCraft.id("zergling"), "main");
    /** Same geometry as the Zergling, baked separately so the Ultralisk can fork its model later. */
    public static final ModelLayerLocation ULTRALISK_LAYER = new ModelLayerLocation(AsteriskCraft.id("ultralisk"), "main");
    public static final ModelLayerLocation HYDRALISK_LAYER = new ModelLayerLocation(AsteriskCraft.id("hydralisk"), "main");
    public static final ModelLayerLocation LURKER_LAYER = new ModelLayerLocation(AsteriskCraft.id("lurker"), "main");
    public static final ModelLayerLocation MUTALISK_LAYER = new ModelLayerLocation(AsteriskCraft.id("mutalisk"), "main");
    public static final ModelLayerLocation DRONE_LAYER = new ModelLayerLocation(AsteriskCraft.id("drone"), "main");
    public static final ModelLayerLocation PHOTON_CANNON_LAYER = new ModelLayerLocation(AsteriskCraft.id("photon_cannon"), "main");
    public static final ModelLayerLocation SUNKEN_COLONY_LAYER = new ModelLayerLocation(AsteriskCraft.id("sunken_colony"), "main");
    public static final ModelLayerLocation SPORE_COLONY_LAYER = new ModelLayerLocation(AsteriskCraft.id("spore_colony"), "main");

    @SubscribeEvent
    static void onRegisterLayers(EntityRenderersEvent.RegisterLayerDefinitions event) {
        event.registerLayerDefinition(PROBE_LAYER, ProbeModel::createBodyLayer);
        event.registerLayerDefinition(ZEALOT_LAYER, ZealotModel::createBodyLayer);
        event.registerLayerDefinition(DRAGOON_LAYER, DragoonModel::createBodyLayer);
        event.registerLayerDefinition(SCOUT_LAYER, ScoutModel::createBodyLayer);
        event.registerLayerDefinition(DARK_TEMPLAR_LAYER, DarkTemplarModel::createBodyLayer);
        event.registerLayerDefinition(ZERGLING_LAYER, ZerglingModel::createBodyLayer);
        event.registerLayerDefinition(ULTRALISK_LAYER, ZerglingModel::createBodyLayer);
        event.registerLayerDefinition(HYDRALISK_LAYER, HydraliskModel::createBodyLayer);
        event.registerLayerDefinition(LURKER_LAYER, LurkerModel::createBodyLayer);
        event.registerLayerDefinition(MUTALISK_LAYER, MutaliskModel::createBodyLayer);
        event.registerLayerDefinition(DRONE_LAYER, DroneModel::createBodyLayer);
        event.registerLayerDefinition(PHOTON_CANNON_LAYER, PhotonCannonModel::createBodyLayer);
        event.registerLayerDefinition(SUNKEN_COLONY_LAYER, SunkenColonyModel::createBodyLayer);
        event.registerLayerDefinition(SPORE_COLONY_LAYER, SporeColonyModel::createBodyLayer);
    }

    @SubscribeEvent
    static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(AsteriskCraft.PROBE.get(), ProbeRenderer::new);
        event.registerEntityRenderer(AsteriskCraft.ZEALOT.get(), ZealotRenderer::new);
        event.registerEntityRenderer(AsteriskCraft.DRAGOON.get(), DragoonRenderer::new);
        event.registerEntityRenderer(AsteriskCraft.SCOUT.get(), ScoutRenderer::new);
        event.registerEntityRenderer(AsteriskCraft.DARK_TEMPLAR.get(), DarkTemplarRenderer::new);
        event.registerEntityRenderer(AsteriskCraft.DRONE.get(), DroneRenderer::new);
        event.registerEntityRenderer(AsteriskCraft.ZERGLING.get(), ZerglingRenderer::new);
        event.registerEntityRenderer(AsteriskCraft.ULTRALISK.get(), UltraliskRenderer::new);
        event.registerEntityRenderer(AsteriskCraft.HYDRALISK.get(), HydraliskRenderer::new);
        event.registerEntityRenderer(AsteriskCraft.LURKER.get(), LurkerRenderer::new);
        event.registerEntityRenderer(AsteriskCraft.MUTALISK.get(), MutaliskRenderer::new);
        event.registerEntityRenderer(AsteriskCraft.PHOTON_CANNON.get(), PhotonCannonRenderer::new);
        event.registerEntityRenderer(AsteriskCraft.SUNKEN_COLONY.get(), SunkenColonyRenderer::new);
        event.registerEntityRenderer(AsteriskCraft.SPORE_COLONY.get(), SporeColonyRenderer::new);
        // The Sunken Colony's spike extends EvokerFangs, and EvokerFangsRenderer is generic over that
        // class — so the vanilla renderer works verbatim, no model or texture of our own needed.
        event.registerEntityRenderer(AsteriskCraft.SUNKEN_SPIKE.get(), EvokerFangsRenderer::new);
        event.registerEntityRenderer(AsteriskCraft.LURKER_SPINE.get(), EvokerFangsRenderer::new);
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
