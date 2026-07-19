package net.bitflora.asteriskcraft;

import com.mojang.logging.LogUtils;
import net.bitflora.asteriskcraft.building.ArmyBank;
import net.bitflora.asteriskcraft.building.BuildingKitItem;
import net.bitflora.asteriskcraft.building.BuildingLayouts;
import net.bitflora.asteriskcraft.building.DepletedNodeBlock;
import net.bitflora.asteriskcraft.building.DepletedNodeBlockEntity;
import net.bitflora.asteriskcraft.building.GatewayBlock;
import net.bitflora.asteriskcraft.building.GatewayBlockEntity;
import net.bitflora.asteriskcraft.building.HiveBlock;
import net.bitflora.asteriskcraft.building.HiveBlockEntity;
import net.bitflora.asteriskcraft.building.NexusBlock;
import net.bitflora.asteriskcraft.building.NexusBlockEntity;
import net.bitflora.asteriskcraft.building.ProductionMenu;
import net.bitflora.asteriskcraft.command.CommandAttachments;
import net.bitflora.asteriskcraft.command.CursorItem;
import net.bitflora.asteriskcraft.command.CommandInputPacket;
import net.bitflora.asteriskcraft.command.CommandInputResolver;
import net.bitflora.asteriskcraft.combat.ShieldAttachments;
import net.bitflora.asteriskcraft.combat.ZergRegenAttachments;
import net.bitflora.asteriskcraft.entity.DragoonEntity;
import net.bitflora.asteriskcraft.entity.DroneEntity;
import net.bitflora.asteriskcraft.entity.FactionSpawnEggItem;
import net.bitflora.asteriskcraft.entity.HydraliskEntity;
import net.bitflora.asteriskcraft.entity.PhotonCannonEntity;
import net.bitflora.asteriskcraft.entity.ProbeEntity;
import net.bitflora.asteriskcraft.entity.ZealotEntity;
import net.bitflora.asteriskcraft.entity.ZerglingEntity;
import net.bitflora.asteriskcraft.faction.Faction;
import net.bitflora.asteriskcraft.faction.FactionAttachments;
import net.bitflora.asteriskcraft.game.GameAttachments;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.transfer.item.VanillaContainerWrapper;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.slf4j.Logger;

@Mod(AsteriskCraft.MODID)
public class AsteriskCraft {
    public static final String MODID = "asteriskcraft";
    public static final Logger LOGGER = LogUtils.getLogger();

    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(MODID);
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(MODID);
    public static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, MODID);
    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES = DeferredRegister.create(Registries.ENTITY_TYPE, MODID);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITY_TYPES = DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, MODID);
    public static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(Registries.MENU, MODID);
    public static final DeferredRegister<SoundEvent> SOUND_EVENTS = DeferredRegister.create(Registries.SOUND_EVENT, MODID);

    public static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(MODID, path);
    }

    // --- Blocks ---
    // NeoForge 26.1 registerBlock takes a UnaryOperator that decorates the base Properties.

    public static final DeferredBlock<NexusBlock> NEXUS_CORE = BLOCKS.registerBlock("nexus_core",
            NexusBlock::new,
            p -> p.mapColor(MapColor.GOLD).strength(15.0f, 1200.0f).lightLevel(s -> 12));

    public static final DeferredBlock<DepletedNodeBlock> DEPLETED_NODE = BLOCKS.registerBlock("depleted_node",
            DepletedNodeBlock::new,
            p -> p.mapColor(MapColor.COLOR_GRAY).strength(-1.0f, 3600000.0f).noLootTable());

    public static final DeferredBlock<GatewayBlock> GATEWAY_CORE = BLOCKS.registerBlock("gateway_core",
            GatewayBlock::new,
            p -> p.mapColor(MapColor.COLOR_PURPLE).strength(15.0f, 1200.0f).lightLevel(s -> 8));

    public static final DeferredBlock<HiveBlock> HIVE_CORE = BLOCKS.registerBlock("hive_core",
            HiveBlock::new,
            p -> p.mapColor(MapColor.CRIMSON_HYPHAE).strength(15.0f, 1200.0f).lightLevel(s -> 7));

    public static final DeferredItem<BlockItem> NEXUS_CORE_ITEM = ITEMS.registerSimpleBlockItem("nexus_core", NEXUS_CORE);
    public static final DeferredItem<BlockItem> GATEWAY_CORE_ITEM = ITEMS.registerSimpleBlockItem("gateway_core", GATEWAY_CORE);
    public static final DeferredItem<BlockItem> HIVE_CORE_ITEM = ITEMS.registerSimpleBlockItem("hive_core", HIVE_CORE);

    public static final DeferredItem<BuildingKitItem> GATEWAY_KIT = ITEMS.registerItem("gateway_kit",
            props -> new BuildingKitItem(props, BuildingLayouts::gateway, BuildingLayouts.GATEWAY_CORE_OFFSET));

    // The Photon Cannon is an entity now, so its kit is a faction-stamping spawn item (it warps the
    // entity in on right-click) rather than a layout-stamping BuildingKitItem. Same crafted item + recipe.
    public static final DeferredItem<FactionSpawnEggItem> PHOTON_CANNON_KIT = ITEMS.registerItem("photon_cannon_kit",
            props -> new FactionSpawnEggItem(props, AsteriskCraft.PHOTON_CANNON, Faction.PROTOSS));

    public static final DeferredItem<CursorItem> CURSOR = ITEMS.registerItem("cursor",
            CursorItem::new);

    // --- Block entities ---

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<NexusBlockEntity>> NEXUS_BLOCK_ENTITY =
            BLOCK_ENTITY_TYPES.register("nexus", () -> new BlockEntityType<>(NexusBlockEntity::new, NEXUS_CORE.get()));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<DepletedNodeBlockEntity>> DEPLETED_NODE_BLOCK_ENTITY =
            BLOCK_ENTITY_TYPES.register("depleted_node", () -> new BlockEntityType<>(DepletedNodeBlockEntity::new, DEPLETED_NODE.get()));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<GatewayBlockEntity>> GATEWAY_BLOCK_ENTITY =
            BLOCK_ENTITY_TYPES.register("gateway", () -> new BlockEntityType<>(GatewayBlockEntity::new, GATEWAY_CORE.get()));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<HiveBlockEntity>> HIVE_BLOCK_ENTITY =
            BLOCK_ENTITY_TYPES.register("hive", () -> new BlockEntityType<>(HiveBlockEntity::new, HIVE_CORE.get()));

    // --- Menus ---

    public static final DeferredHolder<MenuType<?>, MenuType<ProductionMenu>> PRODUCTION_MENU =
            MENUS.register("production", () -> IMenuTypeExtension.create(ProductionMenu::new));

    // --- Entities ---

    public static final DeferredHolder<EntityType<?>, EntityType<ProbeEntity>> PROBE =
            ENTITY_TYPES.register("probe", () -> EntityType.Builder.of(ProbeEntity::new, MobCategory.CREATURE)
                    .sized(0.7f, 0.9f)
                    .clientTrackingRange(10)
                    .build(ResourceKey.create(Registries.ENTITY_TYPE, id("probe"))));

    public static final DeferredHolder<EntityType<?>, EntityType<ZealotEntity>> ZEALOT =
            ENTITY_TYPES.register("zealot", () -> EntityType.Builder.of(ZealotEntity::new, MobCategory.MONSTER)
                    .sized(0.6f, 1.95f)
                    .clientTrackingRange(8)
                    .build(ResourceKey.create(Registries.ENTITY_TYPE, id("zealot"))));

    public static final DeferredHolder<EntityType<?>, EntityType<DragoonEntity>> DRAGOON =
            ENTITY_TYPES.register("dragoon", () -> EntityType.Builder.of(DragoonEntity::new, MobCategory.MONSTER)
                    .sized(0.6f, 1.99f)
                    .clientTrackingRange(8)
                    .build(ResourceKey.create(Registries.ENTITY_TYPE, id("dragoon"))));

    public static final DeferredHolder<EntityType<?>, EntityType<DroneEntity>> DRONE =
            ENTITY_TYPES.register("drone", () -> EntityType.Builder.of(DroneEntity::new, MobCategory.CREATURE)
                    .sized(0.7f, 0.9f)
                    .clientTrackingRange(10)
                    .build(ResourceKey.create(Registries.ENTITY_TYPE, id("drone"))));

    public static final DeferredHolder<EntityType<?>, EntityType<ZerglingEntity>> ZERGLING =
            ENTITY_TYPES.register("zergling", () -> EntityType.Builder.of(ZerglingEntity::new, MobCategory.MONSTER)
                    .sized(0.6f, 1.95f)
                    .clientTrackingRange(8)
                    .build(ResourceKey.create(Registries.ENTITY_TYPE, id("zergling"))));

    public static final DeferredHolder<EntityType<?>, EntityType<HydraliskEntity>> HYDRALISK =
            ENTITY_TYPES.register("hydralisk", () -> EntityType.Builder.of(HydraliskEntity::new, MobCategory.MONSTER)
                    .sized(0.6f, 1.99f)
                    .clientTrackingRange(8)
                    .build(ResourceKey.create(Registries.ENTITY_TYPE, id("hydralisk"))));

    public static final DeferredHolder<EntityType<?>, EntityType<PhotonCannonEntity>> PHOTON_CANNON =
            ENTITY_TYPES.register("photon_cannon", () -> EntityType.Builder.of(PhotonCannonEntity::new, MobCategory.MISC)
                    .sized(2.6f, 2.5f) // 3x3-block star base + lens drum + domed head
                    .clientTrackingRange(10)
                    .build(ResourceKey.create(Registries.ENTITY_TYPE, id("photon_cannon"))));

    // --- Spawn eggs ---
    // Two per unit: one stamps the spawned mob as the player's own (PROTOSS, matching
    // ControlledFaction), the other as the enemy (ZERG) — independent of the unit's own race,
    // since Faction only controls targeting, not the mob's model/renderer.

    public static final DeferredItem<FactionSpawnEggItem> PROBE_SPAWN_EGG_ALLY = ITEMS.registerItem("probe_spawn_egg_ally",
            props -> new FactionSpawnEggItem(props, PROBE, Faction.PROTOSS));
    public static final DeferredItem<FactionSpawnEggItem> PROBE_SPAWN_EGG_ENEMY = ITEMS.registerItem("probe_spawn_egg_enemy",
            props -> new FactionSpawnEggItem(props, PROBE, Faction.ZERG));

    public static final DeferredItem<FactionSpawnEggItem> ZEALOT_SPAWN_EGG_ALLY = ITEMS.registerItem("zealot_spawn_egg_ally",
            props -> new FactionSpawnEggItem(props, ZEALOT, Faction.PROTOSS));
    public static final DeferredItem<FactionSpawnEggItem> ZEALOT_SPAWN_EGG_ENEMY = ITEMS.registerItem("zealot_spawn_egg_enemy",
            props -> new FactionSpawnEggItem(props, ZEALOT, Faction.ZERG));

    public static final DeferredItem<FactionSpawnEggItem> DRAGOON_SPAWN_EGG_ALLY = ITEMS.registerItem("dragoon_spawn_egg_ally",
            props -> new FactionSpawnEggItem(props, DRAGOON, Faction.PROTOSS));
    public static final DeferredItem<FactionSpawnEggItem> DRAGOON_SPAWN_EGG_ENEMY = ITEMS.registerItem("dragoon_spawn_egg_enemy",
            props -> new FactionSpawnEggItem(props, DRAGOON, Faction.ZERG));

    public static final DeferredItem<FactionSpawnEggItem> DRONE_SPAWN_EGG_ALLY = ITEMS.registerItem("drone_spawn_egg_ally",
            props -> new FactionSpawnEggItem(props, DRONE, Faction.PROTOSS));
    public static final DeferredItem<FactionSpawnEggItem> DRONE_SPAWN_EGG_ENEMY = ITEMS.registerItem("drone_spawn_egg_enemy",
            props -> new FactionSpawnEggItem(props, DRONE, Faction.ZERG));

    public static final DeferredItem<FactionSpawnEggItem> ZERGLING_SPAWN_EGG_ALLY = ITEMS.registerItem("zergling_spawn_egg_ally",
            props -> new FactionSpawnEggItem(props, ZERGLING, Faction.PROTOSS));
    public static final DeferredItem<FactionSpawnEggItem> ZERGLING_SPAWN_EGG_ENEMY = ITEMS.registerItem("zergling_spawn_egg_enemy",
            props -> new FactionSpawnEggItem(props, ZERGLING, Faction.ZERG));

    public static final DeferredItem<FactionSpawnEggItem> HYDRALISK_SPAWN_EGG_ALLY = ITEMS.registerItem("hydralisk_spawn_egg_ally",
            props -> new FactionSpawnEggItem(props, HYDRALISK, Faction.PROTOSS));
    public static final DeferredItem<FactionSpawnEggItem> HYDRALISK_SPAWN_EGG_ENEMY = ITEMS.registerItem("hydralisk_spawn_egg_enemy",
            props -> new FactionSpawnEggItem(props, HYDRALISK, Faction.ZERG));

    // --- Sounds ---
    // Ambient events each name several ogg files in sounds.json; vanilla's sound system already
    // picks one at random per play, so a single registered SoundEvent covers all "live" variants.

    public static final DeferredHolder<SoundEvent, SoundEvent> ZEALOT_AMBIENT =
            SOUND_EVENTS.register("entity.zealot.ambient", () -> SoundEvent.createVariableRangeEvent(id("entity.zealot.ambient")));
    public static final DeferredHolder<SoundEvent, SoundEvent> ZEALOT_HURT =
            SOUND_EVENTS.register("entity.zealot.hurt", () -> SoundEvent.createVariableRangeEvent(id("entity.zealot.hurt")));
    public static final DeferredHolder<SoundEvent, SoundEvent> ZEALOT_DEATH =
            SOUND_EVENTS.register("entity.zealot.death", () -> SoundEvent.createVariableRangeEvent(id("entity.zealot.death")));

    public static final DeferredHolder<SoundEvent, SoundEvent> ZERGLING_AMBIENT =
            SOUND_EVENTS.register("entity.zergling.ambient", () -> SoundEvent.createVariableRangeEvent(id("entity.zergling.ambient")));
    public static final DeferredHolder<SoundEvent, SoundEvent> ZERGLING_HURT =
            SOUND_EVENTS.register("entity.zergling.hurt", () -> SoundEvent.createVariableRangeEvent(id("entity.zergling.hurt")));
    public static final DeferredHolder<SoundEvent, SoundEvent> ZERGLING_DEATH =
            SOUND_EVENTS.register("entity.zergling.death", () -> SoundEvent.createVariableRangeEvent(id("entity.zergling.death")));

    public static final DeferredHolder<SoundEvent, SoundEvent> HYDRALISK_AMBIENT =
            SOUND_EVENTS.register("entity.hydralisk.ambient", () -> SoundEvent.createVariableRangeEvent(id("entity.hydralisk.ambient")));
    public static final DeferredHolder<SoundEvent, SoundEvent> HYDRALISK_HURT =
            SOUND_EVENTS.register("entity.hydralisk.hurt", () -> SoundEvent.createVariableRangeEvent(id("entity.hydralisk.hurt")));
    public static final DeferredHolder<SoundEvent, SoundEvent> HYDRALISK_DEATH =
            SOUND_EVENTS.register("entity.hydralisk.death", () -> SoundEvent.createVariableRangeEvent(id("entity.hydralisk.death")));

    public static final DeferredHolder<SoundEvent, SoundEvent> PROBE_AMBIENT =
            SOUND_EVENTS.register("entity.probe.ambient", () -> SoundEvent.createVariableRangeEvent(id("entity.probe.ambient")));
    public static final DeferredHolder<SoundEvent, SoundEvent> PROBE_HURT =
            SOUND_EVENTS.register("entity.probe.hurt", () -> SoundEvent.createVariableRangeEvent(id("entity.probe.hurt")));
    public static final DeferredHolder<SoundEvent, SoundEvent> PROBE_DEATH =
            SOUND_EVENTS.register("entity.probe.death", () -> SoundEvent.createVariableRangeEvent(id("entity.probe.death")));
    public static final DeferredHolder<SoundEvent, SoundEvent> DRONE_AMBIENT =
            SOUND_EVENTS.register("entity.drone.ambient", () -> SoundEvent.createVariableRangeEvent(id("entity.drone.ambient")));
    public static final DeferredHolder<SoundEvent, SoundEvent> DRONE_HURT =
            SOUND_EVENTS.register("entity.drone.hurt", () -> SoundEvent.createVariableRangeEvent(id("entity.drone.hurt")));
    public static final DeferredHolder<SoundEvent, SoundEvent> DRONE_DEATH =
            SOUND_EVENTS.register("entity.drone.death", () -> SoundEvent.createVariableRangeEvent(id("entity.drone.death")));

    // --- Creative tab ---

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> ASTERISKCRAFT_TAB = CREATIVE_MODE_TABS.register("asteriskcraft_tab", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.asteriskcraft"))
            .withTabsBefore(CreativeModeTabs.COMBAT)
            .icon(() -> NEXUS_CORE_ITEM.get().getDefaultInstance())
            .displayItems((parameters, output) -> {
                output.accept(NEXUS_CORE_ITEM.get());
                output.accept(GATEWAY_CORE_ITEM.get());
                output.accept(GATEWAY_KIT.get());
                output.accept(PHOTON_CANNON_KIT.get());
                output.accept(HIVE_CORE_ITEM.get());
                output.accept(CURSOR.get());
                output.accept(PROBE_SPAWN_EGG_ALLY.get());
                output.accept(PROBE_SPAWN_EGG_ENEMY.get());
                output.accept(ZEALOT_SPAWN_EGG_ALLY.get());
                output.accept(ZEALOT_SPAWN_EGG_ENEMY.get());
                output.accept(DRAGOON_SPAWN_EGG_ALLY.get());
                output.accept(DRAGOON_SPAWN_EGG_ENEMY.get());
                output.accept(DRONE_SPAWN_EGG_ALLY.get());
                output.accept(DRONE_SPAWN_EGG_ENEMY.get());
                output.accept(ZERGLING_SPAWN_EGG_ALLY.get());
                output.accept(ZERGLING_SPAWN_EGG_ENEMY.get());
                output.accept(HYDRALISK_SPAWN_EGG_ALLY.get());
                output.accept(HYDRALISK_SPAWN_EGG_ENEMY.get());
            }).build());

    public AsteriskCraft(IEventBus modEventBus, ModContainer modContainer) {
        BLOCKS.register(modEventBus);
        ITEMS.register(modEventBus);
        CREATIVE_MODE_TABS.register(modEventBus);
        ENTITY_TYPES.register(modEventBus);
        BLOCK_ENTITY_TYPES.register(modEventBus);
        MENUS.register(modEventBus);
        SOUND_EVENTS.register(modEventBus);
        FactionAttachments.ATTACHMENT_TYPES.register(modEventBus);
        GameAttachments.ATTACHMENT_TYPES.register(modEventBus);
        CommandAttachments.ATTACHMENT_TYPES.register(modEventBus);
        ShieldAttachments.ATTACHMENT_TYPES.register(modEventBus);
        ZergRegenAttachments.ATTACHMENT_TYPES.register(modEventBus);
        ArmyBank.ATTACHMENT_TYPES.register(modEventBus);

        modEventBus.addListener(this::registerEntityAttributes);
        modEventBus.addListener(this::registerPayloads);
        modEventBus.addListener(this::registerCapabilities);
    }

    private void registerCapabilities(RegisterCapabilitiesEvent event) {
        // Expose the Nexus's and Hive's inventories as item handlers so Probes/Drones can
        // deposit their harvest yield straight into their home core building.
        event.registerBlockEntity(Capabilities.Item.BLOCK, NEXUS_BLOCK_ENTITY.get(),
                (nexus, side) -> VanillaContainerWrapper.of(nexus));
        event.registerBlockEntity(Capabilities.Item.BLOCK, HIVE_BLOCK_ENTITY.get(),
                (hive, side) -> VanillaContainerWrapper.of(hive));
    }

    private void registerPayloads(RegisterPayloadHandlersEvent event) {
        event.registrar("1").playToServer(CommandInputPacket.TYPE, CommandInputPacket.STREAM_CODEC,
                (packet, context) -> context.enqueueWork(
                        () -> CommandInputResolver.handle(packet, (net.minecraft.server.level.ServerPlayer) context.player())));
    }

    private void registerEntityAttributes(EntityAttributeCreationEvent event) {
        event.put(PROBE.get(), ProbeEntity.createAttributes().build());
        event.put(ZEALOT.get(), ZealotEntity.createAttributes().build());
        event.put(DRAGOON.get(), DragoonEntity.createAttributes().build());
        event.put(DRONE.get(), DroneEntity.createAttributes().build());
        event.put(ZERGLING.get(), ZerglingEntity.createAttributes().build());
        event.put(HYDRALISK.get(), HydraliskEntity.createAttributes().build());
        event.put(PHOTON_CANNON.get(), PhotonCannonEntity.createAttributes().build());
    }
}
