package net.bitflora.asteriskcraft;

import com.mojang.logging.LogUtils;
import net.bitflora.asteriskcraft.building.BuildingKitItem;
import net.bitflora.asteriskcraft.building.BuildingLayouts;
import net.bitflora.asteriskcraft.building.DebugZergSpawnerItem;
import net.bitflora.asteriskcraft.building.DepletedNodeBlock;
import net.bitflora.asteriskcraft.building.DepletedNodeBlockEntity;
import net.bitflora.asteriskcraft.building.GatewayBlock;
import net.bitflora.asteriskcraft.building.GatewayBlockEntity;
import net.bitflora.asteriskcraft.building.NexusBlock;
import net.bitflora.asteriskcraft.building.NexusBlockEntity;
import net.bitflora.asteriskcraft.building.ProductionMenu;
import net.bitflora.asteriskcraft.command.CommandAttachments;
import net.bitflora.asteriskcraft.command.CommandCrystalItem;
import net.bitflora.asteriskcraft.command.CommandInputPacket;
import net.bitflora.asteriskcraft.command.CommandInputResolver;
import net.bitflora.asteriskcraft.entity.DragoonEntity;
import net.bitflora.asteriskcraft.entity.ProbeEntity;
import net.bitflora.asteriskcraft.entity.ZealotEntity;
import net.bitflora.asteriskcraft.faction.FactionAttachments;
import net.bitflora.asteriskcraft.game.GameAttachments;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
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
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
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

    public static final DeferredItem<BlockItem> NEXUS_CORE_ITEM = ITEMS.registerSimpleBlockItem("nexus_core", NEXUS_CORE);
    public static final DeferredItem<BlockItem> GATEWAY_CORE_ITEM = ITEMS.registerSimpleBlockItem("gateway_core", GATEWAY_CORE);

    public static final DeferredItem<BuildingKitItem> GATEWAY_KIT = ITEMS.registerItem("gateway_kit",
            props -> new BuildingKitItem(props, BuildingLayouts::gateway, BuildingLayouts.GATEWAY_CORE_OFFSET));

    public static final DeferredItem<DebugZergSpawnerItem> DEBUG_ZERG_SPAWNER = ITEMS.registerItem("debug_zerg_spawner",
            DebugZergSpawnerItem::new);

    public static final DeferredItem<CommandCrystalItem> COMMAND_CRYSTAL = ITEMS.registerItem("command_crystal",
            CommandCrystalItem::new);

    // --- Block entities ---

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<NexusBlockEntity>> NEXUS_BLOCK_ENTITY =
            BLOCK_ENTITY_TYPES.register("nexus", () -> new BlockEntityType<>(NexusBlockEntity::new, NEXUS_CORE.get()));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<DepletedNodeBlockEntity>> DEPLETED_NODE_BLOCK_ENTITY =
            BLOCK_ENTITY_TYPES.register("depleted_node", () -> new BlockEntityType<>(DepletedNodeBlockEntity::new, DEPLETED_NODE.get()));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<GatewayBlockEntity>> GATEWAY_BLOCK_ENTITY =
            BLOCK_ENTITY_TYPES.register("gateway", () -> new BlockEntityType<>(GatewayBlockEntity::new, GATEWAY_CORE.get()));

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

    // --- Creative tab ---

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> ASTERISKCRAFT_TAB = CREATIVE_MODE_TABS.register("asteriskcraft_tab", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.asteriskcraft"))
            .withTabsBefore(CreativeModeTabs.COMBAT)
            .icon(() -> NEXUS_CORE_ITEM.get().getDefaultInstance())
            .displayItems((parameters, output) -> {
                output.accept(NEXUS_CORE_ITEM.get());
                output.accept(GATEWAY_CORE_ITEM.get());
                output.accept(GATEWAY_KIT.get());
                output.accept(COMMAND_CRYSTAL.get());
                output.accept(DEBUG_ZERG_SPAWNER.get());
            }).build());

    public AsteriskCraft(IEventBus modEventBus, ModContainer modContainer) {
        BLOCKS.register(modEventBus);
        ITEMS.register(modEventBus);
        CREATIVE_MODE_TABS.register(modEventBus);
        ENTITY_TYPES.register(modEventBus);
        BLOCK_ENTITY_TYPES.register(modEventBus);
        MENUS.register(modEventBus);
        FactionAttachments.ATTACHMENT_TYPES.register(modEventBus);
        GameAttachments.ATTACHMENT_TYPES.register(modEventBus);
        CommandAttachments.ATTACHMENT_TYPES.register(modEventBus);

        modEventBus.addListener(this::registerEntityAttributes);
        modEventBus.addListener(this::registerPayloads);
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
    }
}
