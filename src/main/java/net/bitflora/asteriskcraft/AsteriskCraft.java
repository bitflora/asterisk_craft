package net.bitflora.asteriskcraft;

import com.mojang.logging.LogUtils;
import net.bitflora.asteriskcraft.building.ArmyBank;
import net.bitflora.asteriskcraft.building.BuildingKitItem;
import net.bitflora.asteriskcraft.building.BaseBlock;
import net.bitflora.asteriskcraft.building.BaseBlockEntity;
import net.bitflora.asteriskcraft.building.BuildingTemplates;
import net.bitflora.asteriskcraft.building.CoreCensus;
import net.bitflora.asteriskcraft.building.TechCensus;
import net.bitflora.asteriskcraft.building.DepletedNodeBlock;
import net.bitflora.asteriskcraft.building.DepletedNodeBlockEntity;
import net.bitflora.asteriskcraft.building.FactoryBlock;
import net.bitflora.asteriskcraft.building.FactoryBlockEntity;
import net.bitflora.asteriskcraft.building.GatewayBlock;
import net.bitflora.asteriskcraft.building.GatewayBlockEntity;
import net.bitflora.asteriskcraft.building.ProductionKind;
import net.bitflora.asteriskcraft.building.ProductionMenu;
import net.bitflora.asteriskcraft.building.PylonBlock;
import net.bitflora.asteriskcraft.building.PylonBlockEntity;
import net.bitflora.asteriskcraft.building.StructureBlock;
import net.bitflora.asteriskcraft.building.StructureBlockEntity;
import net.bitflora.asteriskcraft.command.CommandAttachments;
import net.bitflora.asteriskcraft.command.CursorItem;
import net.bitflora.asteriskcraft.command.CommandInputPacket;
import net.bitflora.asteriskcraft.command.CommandInputResolver;
import net.bitflora.asteriskcraft.command.UnitGroupPacket;
import net.bitflora.asteriskcraft.command.UnitGroupResolver;
import net.bitflora.asteriskcraft.command.UnitGroupSyncPacket;
import net.bitflora.asteriskcraft.combat.ShieldAttachments;
import net.bitflora.asteriskcraft.faction.DetectionAttachments;
import net.bitflora.asteriskcraft.combat.RegenAttachments;
import net.bitflora.asteriskcraft.entity.protoss.DarkTemplarEntity;
import net.bitflora.asteriskcraft.entity.protoss.DragoonEntity;
import net.bitflora.asteriskcraft.entity.zerg.DroneEntity;
import net.bitflora.asteriskcraft.entity.FactionSpawnEggItem;
import net.bitflora.asteriskcraft.entity.zerg.HydraliskEntity;
import net.bitflora.asteriskcraft.entity.protoss.PhotonCannonEntity;
import net.bitflora.asteriskcraft.entity.protoss.ProbeEntity;
import net.bitflora.asteriskcraft.entity.terran.BunkerEntity;
import net.bitflora.asteriskcraft.entity.terran.FirebatEntity;
import net.bitflora.asteriskcraft.entity.terran.GhostEntity;
import net.bitflora.asteriskcraft.entity.terran.MarineEntity;
import net.bitflora.asteriskcraft.entity.terran.MissileTurretEntity;
import net.bitflora.asteriskcraft.entity.terran.ScvEntity;
import net.bitflora.asteriskcraft.entity.terran.WraithEntity;
import net.bitflora.asteriskcraft.entity.protoss.ScoutEntity;
import net.bitflora.asteriskcraft.entity.protoss.ZealotEntity;
import net.bitflora.asteriskcraft.entity.zerg.InfestedVillagerEntity;
import net.bitflora.asteriskcraft.entity.zerg.LurkerEntity;
import net.bitflora.asteriskcraft.entity.zerg.LurkerSpineEntity;
import net.bitflora.asteriskcraft.entity.zerg.MutaliskEntity;
import net.bitflora.asteriskcraft.entity.zerg.OverlordEntity;
import net.bitflora.asteriskcraft.entity.zerg.SunkenColonyEntity;
import net.bitflora.asteriskcraft.entity.zerg.SporeColonyEntity;
import net.bitflora.asteriskcraft.entity.zerg.SunkenSpikeEntity;
import net.bitflora.asteriskcraft.entity.zerg.UltraliskEntity;
import net.bitflora.asteriskcraft.entity.zerg.ZerglingEntity;
import net.bitflora.asteriskcraft.faction.Race;
import net.bitflora.asteriskcraft.faction.FactionAttachments;
import net.bitflora.asteriskcraft.game.AsteriskCraftGameRules;
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
import net.minecraft.world.item.Item;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.phys.Vec3;
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

    // Both base cores are the same BaseBlock, registered once per race: what differs between a
    // Nexus and a Hive is the race hung off it (and the model and template that follow from that),
    // not any behaviour. See BaseBlockEntity.
    public static final DeferredBlock<BaseBlock> NEXUS_CORE = BLOCKS.registerBlock("nexus_core",
            props -> new BaseBlock(Race.PROTOSS, props),
            p -> p.mapColor(MapColor.GOLD).strength(15.0f, 1200.0f).lightLevel(s -> 12));

    public static final DeferredBlock<DepletedNodeBlock> DEPLETED_NODE = BLOCKS.registerBlock("depleted_node",
            DepletedNodeBlock::new,
            p -> p.mapColor(MapColor.COLOR_GRAY).strength(-1.0f, 3600000.0f).noLootTable());

    public static final DeferredBlock<GatewayBlock> GATEWAY_CORE = BLOCKS.registerBlock("gateway_core",
            GatewayBlock::new,
            p -> p.mapColor(MapColor.COLOR_PURPLE).strength(15.0f, 1200.0f).lightLevel(s -> 8));

    // Lit only once it has finished warping in: PylonBlock.ONLINE is both the light switch and the
    // synced fact PsiField reads, so a client's placement outline agrees with the server's refusal.
    public static final DeferredBlock<PylonBlock> PYLON_CORE = BLOCKS.registerBlock("pylon_core",
            PylonBlock::new,
            p -> p.mapColor(MapColor.COLOR_LIGHT_BLUE).strength(15.0f, 1200.0f)
                    .lightLevel(s -> s.getValue(PylonBlock.ONLINE) ? 15 : 0));

    public static final DeferredBlock<BaseBlock> HIVE_CORE = BLOCKS.registerBlock("hive_core",
            props -> new BaseBlock(Race.ZERG, props),
            p -> p.mapColor(MapColor.CRIMSON_HYPHAE).strength(15.0f, 1200.0f).lightLevel(s -> 7));

    public static final DeferredBlock<BaseBlock> COMMAND_CENTER_CORE = BLOCKS.registerBlock("command_center_core",
            props -> new BaseBlock(Race.TERRAN, props),
            p -> p.mapColor(MapColor.METAL).strength(15.0f, 1200.0f).lightLevel(s -> 10));

    // The cores of the four buildings a kit stamps. Two of them produce nothing and share one
    // StructureBlock/StructureBlockEntity pair, so each is a line of numbers rather than a class:
    // whose building it is (for resolving an owner that was never set), what it takes to raze, and
    // its build time — the warp-in countdown the kit starts, which is what a "build time" is once
    // the kit itself is instant. Appearance is borrowed via the block model's "parent", not copied
    // pixels. The other two carry a command card, which is the same block plus that card
    // (FactoryBlock) rather than a case added here.
    /** 70s, the quickest of the five: the Stargate is the priciest thing on any card already. */
    private static final int STARGATE_BUILD_TICKS = 20 * 70;
    private static final int BARRACKS_BUILD_TICKS = 20 * 80;
    /** The Barracks' time: nothing warps a Factory in yet, but a structure owes a build time. */
    private static final int TERRAN_FACTORY_BUILD_TICKS = 20 * 80;
    private static final int SPAWNING_POOL_BUILD_TICKS = 20 * 80;
    /** Two minutes, the Spire and an expansion base — the two things you commit an army's time to. */
    private static final int SPIRE_BUILD_TICKS = 20 * 120;
    /** 70s, matching the Stargate: each race's air building is the quickest of its tech buildings. */
    private static final int STARPORT_BUILD_TICKS = 20 * 70;
    // Staying power is per building rather than one shared number: what a structure is worth in a
    // fight is the same kind of fact as its build time, and the five differ. The Terran ones are the
    // toughest and carry no shields; the Stargate is the only one of the five that has any.
    private static final int STARGATE_HEALTH = 300;
    private static final int STARGATE_SHIELD = 300;
    private static final int SPAWNING_POOL_HEALTH = 375;
    private static final int SPIRE_HEALTH = 300;
    private static final int BARRACKS_HEALTH = 500;
    private static final int TERRAN_FACTORY_HEALTH = 625;
    /** The sturdiest structure in the mod — a Starport is the last thing a Terran army commits to. */
    private static final int STARPORT_HEALTH = 650;

    // A FactoryBlock, like the Barracks: the Protoss air unit is built here rather than at the
    // Gateway, so the Stargate is a structure plus a command card. The card is a supplier because
    // ProductionKind's constants name blocks.
    public static final DeferredBlock<FactoryBlock> STARGATE_CORE = BLOCKS.registerBlock("stargate_core",
            props -> new FactoryBlock(new StructureBlock.Defence(Race.PROTOSS, STARGATE_HEALTH,
                    STARGATE_SHIELD, STARGATE_BUILD_TICKS), () -> ProductionKind.PROTOSS_STARGATE, props),
            p -> p.mapColor(MapColor.COLOR_CYAN).strength(15.0f, 1200.0f).lightLevel(s -> 8));

    public static final DeferredBlock<StructureBlock> SPAWNING_POOL_CORE = BLOCKS.registerBlock("spawning_pool_core",
            props -> new StructureBlock(new StructureBlock.Defence(Race.ZERG, SPAWNING_POOL_HEALTH, 0,
                    SPAWNING_POOL_BUILD_TICKS), props),
            p -> p.mapColor(MapColor.COLOR_PURPLE).strength(15.0f, 1200.0f).lightLevel(s -> 15));

    // The Spire tapers to a point instead of filling its cube (see the block model), so it must not
    // occlude: an opaque full-cube block wearing a partial model culls its neighbours' facing sides
    // and lights itself as solid, leaving holes in the ground around the spike.
    public static final DeferredBlock<StructureBlock> SPIRE_CORE = BLOCKS.registerBlock("spire_core",
            props -> new StructureBlock(new StructureBlock.Defence(Race.ZERG, SPIRE_HEALTH, 0,
                    SPIRE_BUILD_TICKS), props),
            p -> p.mapColor(MapColor.CRIMSON_HYPHAE).strength(15.0f, 1200.0f).lightLevel(s -> 7)
                    .noOcclusion());

    // A FactoryBlock for the same reason the Stargate is: the same structure plus a command card.
    public static final DeferredBlock<FactoryBlock> BARRACKS_CORE = BLOCKS.registerBlock("barracks_core",
            props -> new FactoryBlock(new StructureBlock.Defence(Race.TERRAN, BARRACKS_HEALTH, 0,
                    BARRACKS_BUILD_TICKS), () -> ProductionKind.TERRAN_BARRACKS, props),
            p -> p.mapColor(MapColor.METAL).strength(15.0f, 1200.0f).lightLevel(s -> 10));

    // No kit sells a Factory yet, so its build time is a number nothing runs down — but a building
    // that can be placed at all can be shot at, and a block with no block entity behind it has no HP
    // to shoot off. It is a plain StructureBlock and not a FactoryBlock because it produces nothing
    // yet; that is a command card away, and it graduates the way the Barracks did.
    // The Terran air building, and a FactoryBlock for the same reason the Stargate is: the same
    // structure plus a command card. It graduated off StructureBlock the way the Barracks did, the
    // moment the race had something that flies to train on it.
    public static final DeferredBlock<FactoryBlock> STARPORT_CORE = BLOCKS.registerBlock("starport_core",
            props -> new FactoryBlock(new StructureBlock.Defence(Race.TERRAN, STARPORT_HEALTH, 0,
                    STARPORT_BUILD_TICKS), () -> ProductionKind.TERRAN_STARPORT, props),
            p -> p.mapColor(MapColor.METAL).strength(15.0f, 1200.0f).lightLevel(s -> 10));

    public static final DeferredBlock<StructureBlock> FACTORY_CORE = BLOCKS.registerBlock("factory_core",
            props -> new StructureBlock(new StructureBlock.Defence(Race.TERRAN, TERRAN_FACTORY_HEALTH, 0,
                    TERRAN_FACTORY_BUILD_TICKS), props),
            p -> p.mapColor(MapColor.METAL).strength(15.0f, 1200.0f).lightLevel(s -> 10));

    public static final DeferredItem<BlockItem> NEXUS_CORE_ITEM = ITEMS.registerSimpleBlockItem("nexus_core", NEXUS_CORE);
    public static final DeferredItem<BlockItem> GATEWAY_CORE_ITEM = ITEMS.registerSimpleBlockItem("gateway_core", GATEWAY_CORE);
    public static final DeferredItem<BlockItem> HIVE_CORE_ITEM = ITEMS.registerSimpleBlockItem("hive_core", HIVE_CORE);
    public static final DeferredItem<BlockItem> COMMAND_CENTER_CORE_ITEM = ITEMS.registerSimpleBlockItem("command_center_core", COMMAND_CENTER_CORE);
    public static final DeferredItem<BlockItem> PYLON_CORE_ITEM = ITEMS.registerSimpleBlockItem("pylon_core", PYLON_CORE);
    public static final DeferredItem<BlockItem> STARGATE_CORE_ITEM = ITEMS.registerSimpleBlockItem("stargate_core", STARGATE_CORE);
    public static final DeferredItem<BlockItem> SPAWNING_POOL_CORE_ITEM = ITEMS.registerSimpleBlockItem("spawning_pool_core", SPAWNING_POOL_CORE);
    public static final DeferredItem<BlockItem> SPIRE_CORE_ITEM = ITEMS.registerSimpleBlockItem("spire_core", SPIRE_CORE);
    public static final DeferredItem<BlockItem> BARRACKS_CORE_ITEM = ITEMS.registerSimpleBlockItem("barracks_core", BARRACKS_CORE);
    public static final DeferredItem<BlockItem> FACTORY_CORE_ITEM = ITEMS.registerSimpleBlockItem("factory_core", FACTORY_CORE);
    public static final DeferredItem<BlockItem> STARPORT_CORE_ITEM = ITEMS.registerSimpleBlockItem("starport_core", STARPORT_CORE);

    public static final DeferredItem<BuildingKitItem> GATEWAY_KIT = ITEMS.registerItem("gateway_kit",
            props -> new BuildingKitItem(props, BuildingTemplates.GATEWAY, GATEWAY_CORE,
                    BuildingTemplates.GATEWAY_FOOTPRINT, true));

    // Bought at the Nexus like the expansion kit, and exempt from its own rule: a Pylon is what
    // powers the ground, so it can't need powered ground itself.
    public static final DeferredItem<BuildingKitItem> PYLON_KIT = ITEMS.registerItem("pylon_kit",
            props -> new BuildingKitItem(props, BuildingTemplates.PYLON, PYLON_CORE,
                    BuildingTemplates.PYLON_FOOTPRINT, false));

    // Bought from the Nexus's own production menu (paid from the shared army bank) rather than
    // crafted, unlike the other kits — see BaseBlockEntity#trainOption. An expansion Nexus, so
    // it's deliberately not offered as a cheap personal-inventory crafting-table recipe.
    public static final DeferredItem<BuildingKitItem> NEXUS_KIT = ITEMS.registerItem("nexus_kit",
            props -> new BuildingKitItem(props, BuildingTemplates.NEXUS, NEXUS_CORE,
                    BuildingTemplates.NEXUS_FOOTPRINT, false));

    // The swarm's expansion kit, bought from the Hive's own command card exactly as the Nexus kit
    // is bought from the Nexus's. Exempt from the Pylon rule, which is a Protoss mechanic the Zerg
    // have no equivalent of — see PsiField, which names no building. Not exempt from the builder
    // rule: a Drone is called to the site and dies there to start the growth, which is the swarm's
    // whole construction doctrine (Race.consumesBuilders).
    public static final DeferredItem<BuildingKitItem> HIVE_KIT = ITEMS.registerItem("hive_kit",
            props -> new BuildingKitItem(props, BuildingTemplates.HIVE, HIVE_CORE,
                    BuildingTemplates.HIVE_FOOTPRINT, false, true).requiringBuilder());

    // The Terran expansion kit, the sibling of NEXUS_KIT and HIVE_KIT: same building as the starting
    // base, bought from the base's own command card. No ground prerequisite — psi is Protoss and
    // creep is Zerg — but an SCV has to come and build it, which is the Terran doctrine every one of
    // their structures follows (ConstructionSite; Race.consumesBuilders leaves the SCV alive).
    public static final DeferredItem<BuildingKitItem> COMMAND_CENTER_KIT = ITEMS.registerItem("command_center_kit",
            props -> new BuildingKitItem(props, BuildingTemplates.COMMAND_CENTER, COMMAND_CENTER_CORE,
                    BuildingTemplates.COMMAND_CENTER_FOOTPRINT, false).requiringBuilder());

    // The four unit-factory kits, sold at their own race's base (building/ProductionKind). Each is
    // gated by its race's placement rule — the Stargate needs psi, the Spawning Pool and Spire need
    // creep, the Barracks needs an SCV — and each stamps a StructureBlock core, which is where its
    // build time, its scaffold, its owner and its siege HP live. Whether the building that goes up
    // then produces anything is a question about its core block, not about the kit: the Barracks and
    // the Stargate carry a command card and the swarm's two do not, and nothing here says so.
    public static final DeferredItem<BuildingKitItem> BARRACKS_KIT = ITEMS.registerItem("barracks_kit",
            props -> new BuildingKitItem(props, BuildingTemplates.BARRACKS, BARRACKS_CORE,
                    BuildingTemplates.BARRACKS_FOOTPRINT, false).requiringBuilder());

    // The Terran air building, sold at the Command Center beside the Barracks — and, since the
    // Wraith, the thing that has to be standing before the race can put anything in the air.
    public static final DeferredItem<BuildingKitItem> STARPORT_KIT = ITEMS.registerItem("starport_kit",
            props -> new BuildingKitItem(props, BuildingTemplates.STARPORT, STARPORT_CORE,
                    BuildingTemplates.STARPORT_FOOTPRINT, false).requiringBuilder());

    public static final DeferredItem<BuildingKitItem> STARGATE_KIT = ITEMS.registerItem("stargate_kit",
            props -> new BuildingKitItem(props, BuildingTemplates.STARGATE, STARGATE_CORE,
                    BuildingTemplates.STARGATE_FOOTPRINT, true));

    // The swarm's two, both on creep and both built by a Drone that dies doing it — the Hive kit is
    // exempt from the creep rule because it is what creates creep, and these are not.
    public static final DeferredItem<BuildingKitItem> SPAWNING_POOL_KIT = ITEMS.registerItem("spawning_pool_kit",
            props -> new BuildingKitItem(props, BuildingTemplates.SPAWNING_POOL, SPAWNING_POOL_CORE,
                    BuildingTemplates.SPAWNING_POOL_FOOTPRINT, false, true, false).requiringBuilder());

    public static final DeferredItem<BuildingKitItem> SPIRE_KIT = ITEMS.registerItem("spire_kit",
            props -> new BuildingKitItem(props, BuildingTemplates.SPIRE, SPIRE_CORE,
                    BuildingTemplates.SPIRE_FOOTPRINT, false, true, false).requiringBuilder());

    // The Photon Cannon is an entity now, so its kit is a faction-stamping spawn item (it warps the
    // entity in on right-click) rather than a layout-stamping BuildingKitItem. Same crafted item + recipe.
    public static final DeferredItem<FactionSpawnEggItem> PHOTON_CANNON_KIT = ITEMS.registerItem("photon_cannon_kit",
            props -> new FactionSpawnEggItem(props, AsteriskCraft.PHOTON_CANNON, FactionSpawnEggItem.Side.ALLY, true));

    // The Bunker is an entity too, so its kit is a spawn item for the same reason the Photon Cannon's
    // is. No Pylon and no creep to ask about — the Terran prerequisite is a worker instead: an SCV is
    // called to the site and the Bunker only goes up while it is there (building/ConstructionSite).
    public static final DeferredItem<FactionSpawnEggItem> BUNKER_KIT = ITEMS.registerItem("bunker_kit",
            props -> new FactionSpawnEggItem(props, AsteriskCraft.BUNKER, FactionSpawnEggItem.Side.ALLY)
                    .requiringBuilder());

    // The Missile Turret's kit, gated exactly as the Bunker's is: no ground prerequisite, but an SCV
    // has to come and weld it together.
    public static final DeferredItem<FactionSpawnEggItem> MISSILE_TURRET_KIT = ITEMS.registerItem("missile_turret_kit",
            props -> new FactionSpawnEggItem(props, AsteriskCraft.MISSILE_TURRET, FactionSpawnEggItem.Side.ALLY)
                    .requiringBuilder());

    public static final DeferredItem<CursorItem> CURSOR = ITEMS.registerItem("cursor",
            CursorItem::new);

    // Icon-only item for the creative tab button; not added to displayItems.
    public static final DeferredItem<Item> TAB_ICON = ITEMS.registerSimpleItem("tab_icon");

    // --- Block entities ---

    // One type for every race's base — every core block is valid for it, which is what a shared
    // BaseBlockEntity means. A race added without its core block listed here gets a base block that
    // silently has no block entity behind it.
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<BaseBlockEntity>> BASE_BLOCK_ENTITY =
            BLOCK_ENTITY_TYPES.register("base",
                    () -> new BlockEntityType<>(BaseBlockEntity::new,
                            NEXUS_CORE.get(), HIVE_CORE.get(), COMMAND_CENTER_CORE.get()));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<DepletedNodeBlockEntity>> DEPLETED_NODE_BLOCK_ENTITY =
            BLOCK_ENTITY_TYPES.register("depleted_node", () -> new BlockEntityType<>(DepletedNodeBlockEntity::new, DEPLETED_NODE.get()));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<GatewayBlockEntity>> GATEWAY_BLOCK_ENTITY =
            BLOCK_ENTITY_TYPES.register("gateway", () -> new BlockEntityType<>(GatewayBlockEntity::new, GATEWAY_CORE.get()));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<PylonBlockEntity>> PYLON_BLOCK_ENTITY =
            BLOCK_ENTITY_TYPES.register("pylon", () -> new BlockEntityType<>(PylonBlockEntity::new, PYLON_CORE.get()));

    // One type for every plain structure, the way BASE_BLOCK_ENTITY is one for every race's base.
    // A StructureBlock registered without its core listed here gets no block entity behind it, and
    // so no build time and no siege HP — StructureBlockTest is what says otherwise.
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<StructureBlockEntity>> STRUCTURE_BLOCK_ENTITY =
            BLOCK_ENTITY_TYPES.register("structure",
                    () -> new BlockEntityType<>(StructureBlockEntity::new,
                            SPAWNING_POOL_CORE.get(), SPIRE_CORE.get(), FACTORY_CORE.get()));

    // One type for every unit factory, as BASE_BLOCK_ENTITY is one for every base. The Gateway keeps
    // its own — it predates the roster and dispatches its card positionally through an enum of
    // Protoss units, which is a merge worth doing on its own rather than as a side effect of this.
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<FactoryBlockEntity>> FACTORY_BLOCK_ENTITY =
            BLOCK_ENTITY_TYPES.register("factory",
                    () -> new BlockEntityType<>(FactoryBlockEntity::new,
                            BARRACKS_CORE.get(), STARGATE_CORE.get(), STARPORT_CORE.get()));

    // --- Menus ---

    public static final DeferredHolder<MenuType<?>, MenuType<ProductionMenu>> PRODUCTION_MENU =
            MENUS.register("production", () -> IMenuTypeExtension.create(ProductionMenu::new));

    // --- Entities ---

    public static final DeferredHolder<EntityType<?>, EntityType<ProbeEntity>> PROBE =
            ENTITY_TYPES.register("probe", () -> EntityType.Builder.of(ProbeEntity::new, MobCategory.CREATURE)
                    .sized(0.7f, 0.9f)
                    .clientTrackingRange(10)
                    .build(ResourceKey.create(Registries.ENTITY_TYPE, id("probe"))));

    // A walker's hitbox, not the Probe's hovering orb. Deliberately smaller than the rendered mech:
    // the shoulder pods reach ~1.2 blocks across and the arm booms ~0.9 blocks out front, but the
    // pathfinder sizes a node's footprint by floor(width + 1), so 0.8 still occupies a single node
    // where 1.0 would need two. Letting the pods and booms overhang is the better trade — the same
    // call ZEALOT makes below for its pauldrons.
    public static final DeferredHolder<EntityType<?>, EntityType<ScvEntity>> SCV =
            ENTITY_TYPES.register("scv", () -> EntityType.Builder.of(ScvEntity::new, MobCategory.CREATURE)
                    .sized(0.8f, 1.8f)
                    .clientTrackingRange(10)
                    .build(ResourceKey.create(Registries.ENTITY_TYPE, id("scv"))));

    // Vanilla's villager footprint, because the model is vanilla's villager: 1.95 keeps the
    // pathfinder's floor(height + 1) at two nodes tall, and 0.6 at one node wide, so a Marine walks
    // anywhere a villager does. The rifle reaches ~1.1 blocks out front and is left overhanging, the
    // same call SCV makes above for its booms.
    public static final DeferredHolder<EntityType<?>, EntityType<MarineEntity>> MARINE =
            ENTITY_TYPES.register("marine", () -> EntityType.Builder.of(MarineEntity::new, MobCategory.MONSTER)
                    .sized(0.6f, 1.95f)
                    .clientTrackingRange(8)
                    .build(ResourceKey.create(Registries.ENTITY_TYPE, id("marine"))));

    // The Marine's footprint exactly, because it is the Marine's frame: vanilla's villager, so 1.95
    // keeps floor(height + 1) at two nodes tall and 0.6 at one node wide. The flamethrower's barrels
    // overhang the front by about the same margin the Marine's rifle does, and are left to.
    public static final DeferredHolder<EntityType<?>, EntityType<FirebatEntity>> FIREBAT =
            ENTITY_TYPES.register("firebat", () -> EntityType.Builder.of(FirebatEntity::new, MobCategory.MONSTER)
                    .sized(0.6f, 1.95f)
                    .clientTrackingRange(8)
                    .build(ResourceKey.create(Registries.ENTITY_TYPE, id("firebat"))));

    // The Marine's footprint again, and for the third time the same reason: it is the Marine's
    // frame. The canister rifle overhangs the front no further than the Marine's does.
    public static final DeferredHolder<EntityType<?>, EntityType<GhostEntity>> GHOST =
            ENTITY_TYPES.register("ghost", () -> EntityType.Builder.of(GhostEntity::new, MobCategory.MONSTER)
                    .sized(0.6f, 1.95f)
                    .clientTrackingRange(8)
                    .build(ResourceKey.create(Registries.ENTITY_TYPE, id("ghost"))));

    // The Scout's footprint, and for the same reasons: wide and shallow because it is a craft rather
    // than a person, and tracked further out than the ground units because it cruises 4 blocks up and
    // so enters view sooner. A touch narrower than the Scout — the Wraith's wings sweep back along the
    // hull rather than out to either side.
    public static final DeferredHolder<EntityType<?>, EntityType<WraithEntity>> WRAITH =
            ENTITY_TYPES.register("wraith", () -> EntityType.Builder.of(WraithEntity::new, MobCategory.MONSTER)
                    .sized(1.4f, 0.9f)
                    .clientTrackingRange(10)
                    .build(ResourceKey.create(Registries.ENTITY_TYPE, id("wraith"))));

    // Squat where the other static defences are tall: the shared 2.6 bulk so a Bunker reads as the
    // Terran counterpart of a Photon Cannon, but 2.0 high, because a bunker is a thing you crouch
    // behind rather than a tower. Rooted, so none of the pathfinder footprint reasoning above applies.
    //
    // The four seats are the whole multi-passenger implementation (plus the cap in
    // BunkerEntity.canAddPassenger): Entity.positionRider indexes this list by a rider's position in
    // getPassengers(), so declaring four points is what stops all four stacking in one spot. They sit
    // inside the hull rather than on the roof — vanilla's fallback for PASSENGER is (0, height, 0),
    // which for a building means the garrison stands on top of it — and at 0.9 high so an eye-level
    // line-of-sight check leaves the model cleanly.
    public static final DeferredHolder<EntityType<?>, EntityType<BunkerEntity>> BUNKER =
            ENTITY_TYPES.register("bunker", () -> EntityType.Builder.of(BunkerEntity::new, MobCategory.MISC)
                    .sized(2.6f, 2.0f)
                    .passengerAttachments(
                            new Vec3(0.6, 0.9, 0.6), new Vec3(-0.6, 0.9, 0.6),
                            new Vec3(0.6, 0.9, -0.6), new Vec3(-0.6, 0.9, -0.6))
                    .clientTrackingRange(10)
                    .build(ResourceKey.create(Registries.ENTITY_TYPE, id("bunker"))));

    // The Missile Turret is an iron golem with a missile rack on each shoulder, and it is the racks
    // rather than the golem that size the box: held at 30 degrees they reach 1.50 blocks forward of
    // the centre line and their muzzles top out at 2.91, both a shade past the golem's own
    // 1.4 x 2.7. MISC rather than MONSTER for the reason the Photon Cannon and the Bunker are: a
    // building is not a mob that wandered in.
    public static final DeferredHolder<EntityType<?>, EntityType<MissileTurretEntity>> MISSILE_TURRET =
            ENTITY_TYPES.register("missile_turret", () -> EntityType.Builder.of(MissileTurretEntity::new, MobCategory.MISC)
                    .sized(3.2f, 3.0f)
                    .clientTrackingRange(10)
                    .build(ResourceKey.create(Registries.ENTITY_TYPE, id("missile_turret"))));

    public static final DeferredHolder<EntityType<?>, EntityType<ZealotEntity>> ZEALOT =
            ENTITY_TYPES.register("zealot", () -> EntityType.Builder.of(ZealotEntity::new, MobCategory.MONSTER)
                    // Deliberately narrower than the rendered model: the pauldrons and their horns
                    // reach ~1.25 blocks across, but the pathfinder sizes a node's footprint by
                    // floor(width + 1), so 0.8 still occupies one node and the Zealot keeps fitting
                    // through one-block gaps. Matching the true width (like the Dragoon's 1.1) would
                    // make the mod's main melee unit two nodes wide and quietly change how it
                    // navigates. The shoulders overhanging the hitbox is the better trade.
                    //
                    // Height does reach the top of the visible model: horn tips sit at model y=-9.4
                    // against feet at y=24, i.e. 1.98 blocks at the renderer's 0.95 scale. It must
                    // stay *under* 2.0 though: the same floor(height + 1) rule means 1.99 needs two
                    // blocks of vertical clearance to path but a flat 2.0 needs three, which silently
                    // shuts the unit out of doorways and 2-high tunnels the Zerg units walk through.
                    .sized(0.8f, 1.99f)
                    .clientTrackingRange(8)
                    .build(ResourceKey.create(Registries.ENTITY_TYPE, id("zealot"))));

    public static final DeferredHolder<EntityType<?>, EntityType<DragoonEntity>> DRAGOON =
            ENTITY_TYPES.register("dragoon", () -> EntityType.Builder.of(DragoonEntity::new, MobCategory.MONSTER)
                    // Wide footprint matching the four-legged spider walker (was the 0.6-wide Skeleton
                    // box). Height reaches the top of the visible model — the body pod rides high on
                    // tall vertical legs, so the rendered walker stands ~1.98 blocks (see
                    // client/DragoonModel: feet at model y=24, cockpit dome at ~-3.5, x1.15 render scale).
                    //
                    // 1.99 rather than a flat 2.0 for the clearance reason spelled out on the Zealot:
                    // the pathfinder's floor(height + 1) would demand three blocks of head-room at 2.0.
                    //
                    // The width is the one deliberate exception in the mod. floor(1.1 + 1) = 2 makes
                    // the Dragoon the only mobile ground unit two nodes wide, so it needs a clear
                    // 2x2 footprint per node and cannot squeeze through a one-block gap. That is
                    // accepted for the walker's silhouette; the crowding it used to cause on group
                    // move orders is handled in the command layer instead (see command/MoveFormation
                    // and entity/ai/CommandedMoveGoal), not by shrinking the box.
                    .sized(1.1f, 1.99f)
                    .clientTrackingRange(8)
                    .build(ResourceKey.create(Registries.ENTITY_TYPE, id("dragoon"))));

    public static final DeferredHolder<EntityType<?>, EntityType<ScoutEntity>> SCOUT =
            ENTITY_TYPES.register("scout", () -> EntityType.Builder.of(ScoutEntity::new, MobCategory.MONSTER)
                    // Wide and shallow like the Mutalisk's, a touch broader for the wingspan.
                    .sized(1.6f, 0.9f)
                    // Larger than the ground units': it cruises 6 blocks up, so it enters view sooner.
                    .clientTrackingRange(10)
                    .build(ResourceKey.create(Registries.ENTITY_TYPE, id("scout"))));

    public static final DeferredHolder<EntityType<?>, EntityType<DarkTemplarEntity>> DARK_TEMPLAR =
            ENTITY_TYPES.register("dark_templar", () -> EntityType.Builder.of(DarkTemplarEntity::new, MobCategory.MONSTER)
                    // Identical to the Zealot's, deliberately: it is the same frame, and the same
                    // pathfinding footprint means a squad of both moves through the same gaps.
                    .sized(0.8f, 1.99f)
                    .clientTrackingRange(8)
                    .build(ResourceKey.create(Registries.ENTITY_TYPE, id("dark_templar"))));

    public static final DeferredHolder<EntityType<?>, EntityType<DroneEntity>> DRONE =
            ENTITY_TYPES.register("drone", () -> EntityType.Builder.of(DroneEntity::new, MobCategory.CREATURE)
                    // Squat and wide, matching its carapace: broader than it is tall. Still under 1.0
                    // wide, so it can squeeze through a 1-block gap exactly as before.
                    .sized(0.9f, 0.8f)
                    .clientTrackingRange(10)
                    .build(ResourceKey.create(Registries.ENTITY_TYPE, id("drone"))));

    public static final DeferredHolder<EntityType<?>, EntityType<ZerglingEntity>> ZERGLING =
            ENTITY_TYPES.register("zergling", () -> EntityType.Builder.of(ZerglingEntity::new, MobCategory.MONSTER)
                    .sized(0.6f, 1.95f)
                    .clientTrackingRange(8)
                    .build(ResourceKey.create(Registries.ENTITY_TYPE, id("zergling"))));

    public static final DeferredHolder<EntityType<?>, EntityType<UltraliskEntity>> ULTRALISK =
            ENTITY_TYPES.register("ultralisk", () -> EntityType.Builder.of(UltraliskEntity::new, MobCategory.MONSTER)
                    // A scaled-up Zergling, but NOT scaled up here — the renderer grows the silhouette
                    // much further than this box (see UltraliskRenderer), because a node's footprint
                    // is floor(dim + 1), so a height matching the model would need four blocks of
                    // clearance and lock the unit out of every doorway and tunnel; 1.99 is the most
                    // that still fits a 2-high opening. The model overhangs it, as the Zealot's does.
                    // The width does double, which makes this the second unit — after the Dragoon —
                    // that is two pathfinding nodes across and so needs a clear 2x2 per node. That
                    // costs it 1-block gaps; the crowding it causes is handled in the command layer.
                    .sized(1.2f, 1.99f)
                    .clientTrackingRange(8)
                    .build(ResourceKey.create(Registries.ENTITY_TYPE, id("ultralisk"))));

    public static final DeferredHolder<EntityType<?>, EntityType<HydraliskEntity>> HYDRALISK =
            ENTITY_TYPES.register("hydralisk", () -> EntityType.Builder.of(HydraliskEntity::new, MobCategory.MONSTER)
                    // The model is built to fit this, not the other way round: at the renderer's 1.0
                    // scale the coil rests on y=24 and the crest spines top out at y≈-7.4, i.e. 1.96
                    // blocks, and nothing but the tail reaches past 4.8px of half-width.
                    .sized(0.6f, 1.99f)
                    .clientTrackingRange(8)
                    .build(ResourceKey.create(Registries.ENTITY_TYPE, id("hydralisk"))));

    public static final DeferredHolder<EntityType<?>, EntityType<MutaliskEntity>> MUTALISK =
            ENTITY_TYPES.register("mutalisk", () -> EntityType.Builder.of(MutaliskEntity::new, MobCategory.MONSTER)
                    // Wide and shallow: the winged silhouette is much broader than it is tall.
                    .sized(1.4f, 0.9f)
                    // Larger than the ground units': it cruises 6 blocks up, so it enters view sooner.
                    .clientTrackingRange(10)
                    .build(ResourceKey.create(Registries.ENTITY_TYPE, id("mutalisk"))));

    public static final DeferredHolder<EntityType<?>, EntityType<OverlordEntity>> OVERLORD =
            ENTITY_TYPES.register("overlord", () -> EntityType.Builder.of(OverlordEntity::new, MobCategory.MONSTER)
                    // By far the largest hitbox in the mod, and Ghast-sized on purpose: the silhouette
                    // is a bloated floating sac, and unlike the Ultralisk's this one is not an
                    // overhang — the body really is this big. Watch SpawnSpots when it is produced
                    // beside an already-crowded Hive; it clears a unit's whole spawn AABB.
                    .sized(4.0f, 4.0f)
                    // Larger than the ground units': it cruises 7 blocks up, so it enters view sooner.
                    .clientTrackingRange(10)
                    .build(ResourceKey.create(Registries.ENTITY_TYPE, id("overlord"))));

    public static final DeferredHolder<EntityType<?>, EntityType<LurkerEntity>> LURKER =
            ENTITY_TYPES.register("lurker", () -> EntityType.Builder.of(LurkerEntity::new, MobCategory.MONSTER)
                    // Spider-broad in the art but deliberately under 1.0 here, so it stays a single
                    // pathfinding node and still fits a one-block gap. The silhouette overhangs the
                    // hitbox, exactly as the Ultralisk's does. Low and long: the head rears, the body
                    // doesn't, and the back spines are what has to clear the ground when it burrows.
                    .sized(0.9f, 1.2f)
                    .clientTrackingRange(8)
                    .build(ResourceKey.create(Registries.ENTITY_TYPE, id("lurker"))));

    public static final DeferredHolder<EntityType<?>, EntityType<InfestedVillagerEntity>> INFESTED_VILLAGER =
            ENTITY_TYPES.register("infested_villager", () -> EntityType.Builder.of(InfestedVillagerEntity::new, MobCategory.MONSTER)
                    // A villager's own footprint, hunched: one pathfinding node wide, and under two
                    // blocks tall so it still walks through the doorways of the village it came from.
                    .sized(0.6f, 1.95f)
                    .clientTrackingRange(8)
                    .build(ResourceKey.create(Registries.ENTITY_TYPE, id("infested_villager"))));

    public static final DeferredHolder<EntityType<?>, EntityType<PhotonCannonEntity>> PHOTON_CANNON =
            ENTITY_TYPES.register("photon_cannon", () -> EntityType.Builder.of(PhotonCannonEntity::new, MobCategory.MISC)
                    .sized(2.6f, 2.5f) // 3x3-block star base + lens drum + domed head
                    .clientTrackingRange(10)
                    .build(ResourceKey.create(Registries.ENTITY_TYPE, id("photon_cannon"))));

    public static final DeferredHolder<EntityType<?>, EntityType<SunkenColonyEntity>> SUNKEN_COLONY =
            ENTITY_TYPES.register("sunken_colony", () -> EntityType.Builder.of(SunkenColonyEntity::new, MobCategory.MONSTER)
                    // Sized to the Photon Cannon's box: the two races' static defences are
                    // counterparts and should stand at the same bulk. See SunkenColonyRenderer for
                    // the scale that grows the model to fill it.
                    .sized(2.6f, 2.5f)
                    .clientTrackingRange(10)
                    .build(ResourceKey.create(Registries.ENTITY_TYPE, id("sunken_colony"))));

    public static final DeferredHolder<EntityType<?>, EntityType<SporeColonyEntity>> SPORE_COLONY =
            ENTITY_TYPES.register("spore_colony", () -> EntityType.Builder.of(SporeColonyEntity::new, MobCategory.MONSTER)
                    // The Sunken's box, and so the Photon Cannon's — the three static defences all
                    // stand at the same bulk. See SporeColonyRenderer for the scale that grows the
                    // model to fill it.
                    .sized(2.6f, 2.5f)
                    .clientTrackingRange(10)
                    .build(ResourceKey.create(Registries.ENTITY_TYPE, id("spore_colony"))));

    // The spike a Sunken Colony drives out of the ground. Sized like the vanilla Evoker Fangs it
    // extends, since it reuses their renderer wholesale (see AsteriskCraftClient).
    public static final DeferredHolder<EntityType<?>, EntityType<SunkenSpikeEntity>> SUNKEN_SPIKE =
            ENTITY_TYPES.register("sunken_spike", () -> EntityType.Builder.<SunkenSpikeEntity>of(SunkenSpikeEntity::new, MobCategory.MISC)
                    .sized(0.5f, 0.8f)
                    .clientTrackingRange(6)
                    .build(ResourceKey.create(Registries.ENTITY_TYPE, id("sunken_spike"))));

    // One spine of a Lurker's row. Same shape as the Sunken's spike and, like it, rendered by
    // vanilla's EvokerFangsRenderer (see AsteriskCraftClient).
    public static final DeferredHolder<EntityType<?>, EntityType<LurkerSpineEntity>> LURKER_SPINE =
            ENTITY_TYPES.register("lurker_spine", () -> EntityType.Builder.<LurkerSpineEntity>of(LurkerSpineEntity::new, MobCategory.MISC)
                    .sized(0.5f, 0.8f)
                    .clientTrackingRange(6)
                    .build(ResourceKey.create(Registries.ENTITY_TYPE, id("lurker_spine"))));

    // --- Spawn eggs ---
    // Two per unit: one stamps the spawned mob as the player's own side, the other as the
    // computer's — resolved against the match at use time (see FactionSpawnEggItem.Side), so a
    // match with the sides or races swapped hands out the same eggs. Independent of the unit's own
    // race, since Faction only controls targeting, not the mob's model/renderer.

    public static final DeferredItem<FactionSpawnEggItem> PROBE_SPAWN_EGG_ALLY = ITEMS.registerItem("probe_spawn_egg_ally",
            props -> new FactionSpawnEggItem(props, PROBE, FactionSpawnEggItem.Side.ALLY));
    public static final DeferredItem<FactionSpawnEggItem> PROBE_SPAWN_EGG_ENEMY = ITEMS.registerItem("probe_spawn_egg_enemy",
            props -> new FactionSpawnEggItem(props, PROBE, FactionSpawnEggItem.Side.ENEMY));

    public static final DeferredItem<FactionSpawnEggItem> SCV_SPAWN_EGG_ALLY = ITEMS.registerItem("scv_spawn_egg_ally",
            props -> new FactionSpawnEggItem(props, SCV, FactionSpawnEggItem.Side.ALLY));
    public static final DeferredItem<FactionSpawnEggItem> SCV_SPAWN_EGG_ENEMY = ITEMS.registerItem("scv_spawn_egg_enemy",
            props -> new FactionSpawnEggItem(props, SCV, FactionSpawnEggItem.Side.ENEMY));

    public static final DeferredItem<FactionSpawnEggItem> MARINE_SPAWN_EGG_ALLY = ITEMS.registerItem("marine_spawn_egg_ally",
            props -> new FactionSpawnEggItem(props, MARINE, FactionSpawnEggItem.Side.ALLY));
    public static final DeferredItem<FactionSpawnEggItem> MARINE_SPAWN_EGG_ENEMY = ITEMS.registerItem("marine_spawn_egg_enemy",
            props -> new FactionSpawnEggItem(props, MARINE, FactionSpawnEggItem.Side.ENEMY));
    public static final DeferredItem<FactionSpawnEggItem> FIREBAT_SPAWN_EGG_ALLY = ITEMS.registerItem("firebat_spawn_egg_ally",
            props -> new FactionSpawnEggItem(props, FIREBAT, FactionSpawnEggItem.Side.ALLY));
    public static final DeferredItem<FactionSpawnEggItem> FIREBAT_SPAWN_EGG_ENEMY = ITEMS.registerItem("firebat_spawn_egg_enemy",
            props -> new FactionSpawnEggItem(props, FIREBAT, FactionSpawnEggItem.Side.ENEMY));
    public static final DeferredItem<FactionSpawnEggItem> GHOST_SPAWN_EGG_ALLY = ITEMS.registerItem("ghost_spawn_egg_ally",
            props -> new FactionSpawnEggItem(props, GHOST, FactionSpawnEggItem.Side.ALLY));
    public static final DeferredItem<FactionSpawnEggItem> GHOST_SPAWN_EGG_ENEMY = ITEMS.registerItem("ghost_spawn_egg_enemy",
            props -> new FactionSpawnEggItem(props, GHOST, FactionSpawnEggItem.Side.ENEMY));
    public static final DeferredItem<FactionSpawnEggItem> WRAITH_SPAWN_EGG_ALLY = ITEMS.registerItem("wraith_spawn_egg_ally",
            props -> new FactionSpawnEggItem(props, WRAITH, FactionSpawnEggItem.Side.ALLY));
    public static final DeferredItem<FactionSpawnEggItem> WRAITH_SPAWN_EGG_ENEMY = ITEMS.registerItem("wraith_spawn_egg_enemy",
            props -> new FactionSpawnEggItem(props, WRAITH, FactionSpawnEggItem.Side.ENEMY));

    public static final DeferredItem<FactionSpawnEggItem> BUNKER_SPAWN_EGG_ALLY = ITEMS.registerItem("bunker_spawn_egg_ally",
            props -> new FactionSpawnEggItem(props, BUNKER, FactionSpawnEggItem.Side.ALLY));
    public static final DeferredItem<FactionSpawnEggItem> BUNKER_SPAWN_EGG_ENEMY = ITEMS.registerItem("bunker_spawn_egg_enemy",
            props -> new FactionSpawnEggItem(props, BUNKER, FactionSpawnEggItem.Side.ENEMY));

    public static final DeferredItem<FactionSpawnEggItem> MISSILE_TURRET_SPAWN_EGG_ALLY = ITEMS.registerItem("missile_turret_spawn_egg_ally",
            props -> new FactionSpawnEggItem(props, MISSILE_TURRET, FactionSpawnEggItem.Side.ALLY));
    public static final DeferredItem<FactionSpawnEggItem> MISSILE_TURRET_SPAWN_EGG_ENEMY = ITEMS.registerItem("missile_turret_spawn_egg_enemy",
            props -> new FactionSpawnEggItem(props, MISSILE_TURRET, FactionSpawnEggItem.Side.ENEMY));

    public static final DeferredItem<FactionSpawnEggItem> ZEALOT_SPAWN_EGG_ALLY = ITEMS.registerItem("zealot_spawn_egg_ally",
            props -> new FactionSpawnEggItem(props, ZEALOT, FactionSpawnEggItem.Side.ALLY));
    public static final DeferredItem<FactionSpawnEggItem> ZEALOT_SPAWN_EGG_ENEMY = ITEMS.registerItem("zealot_spawn_egg_enemy",
            props -> new FactionSpawnEggItem(props, ZEALOT, FactionSpawnEggItem.Side.ENEMY));

    public static final DeferredItem<FactionSpawnEggItem> DRAGOON_SPAWN_EGG_ALLY = ITEMS.registerItem("dragoon_spawn_egg_ally",
            props -> new FactionSpawnEggItem(props, DRAGOON, FactionSpawnEggItem.Side.ALLY));
    public static final DeferredItem<FactionSpawnEggItem> DRAGOON_SPAWN_EGG_ENEMY = ITEMS.registerItem("dragoon_spawn_egg_enemy",
            props -> new FactionSpawnEggItem(props, DRAGOON, FactionSpawnEggItem.Side.ENEMY));

    public static final DeferredItem<FactionSpawnEggItem> SCOUT_SPAWN_EGG_ALLY = ITEMS.registerItem("scout_spawn_egg_ally",
            props -> new FactionSpawnEggItem(props, SCOUT, FactionSpawnEggItem.Side.ALLY));
    public static final DeferredItem<FactionSpawnEggItem> SCOUT_SPAWN_EGG_ENEMY = ITEMS.registerItem("scout_spawn_egg_enemy",
            props -> new FactionSpawnEggItem(props, SCOUT, FactionSpawnEggItem.Side.ENEMY));

    public static final DeferredItem<FactionSpawnEggItem> DARK_TEMPLAR_SPAWN_EGG_ALLY = ITEMS.registerItem("dark_templar_spawn_egg_ally",
            props -> new FactionSpawnEggItem(props, DARK_TEMPLAR, FactionSpawnEggItem.Side.ALLY));
    public static final DeferredItem<FactionSpawnEggItem> DARK_TEMPLAR_SPAWN_EGG_ENEMY = ITEMS.registerItem("dark_templar_spawn_egg_enemy",
            props -> new FactionSpawnEggItem(props, DARK_TEMPLAR, FactionSpawnEggItem.Side.ENEMY));

    public static final DeferredItem<FactionSpawnEggItem> DRONE_SPAWN_EGG_ALLY = ITEMS.registerItem("drone_spawn_egg_ally",
            props -> new FactionSpawnEggItem(props, DRONE, FactionSpawnEggItem.Side.ALLY));
    public static final DeferredItem<FactionSpawnEggItem> DRONE_SPAWN_EGG_ENEMY = ITEMS.registerItem("drone_spawn_egg_enemy",
            props -> new FactionSpawnEggItem(props, DRONE, FactionSpawnEggItem.Side.ENEMY));

    public static final DeferredItem<FactionSpawnEggItem> ZERGLING_SPAWN_EGG_ALLY = ITEMS.registerItem("zergling_spawn_egg_ally",
            props -> new FactionSpawnEggItem(props, ZERGLING, FactionSpawnEggItem.Side.ALLY));
    public static final DeferredItem<FactionSpawnEggItem> ZERGLING_SPAWN_EGG_ENEMY = ITEMS.registerItem("zergling_spawn_egg_enemy",
            props -> new FactionSpawnEggItem(props, ZERGLING, FactionSpawnEggItem.Side.ENEMY));

    public static final DeferredItem<FactionSpawnEggItem> ULTRALISK_SPAWN_EGG_ALLY = ITEMS.registerItem("ultralisk_spawn_egg_ally",
            props -> new FactionSpawnEggItem(props, ULTRALISK, FactionSpawnEggItem.Side.ALLY));
    public static final DeferredItem<FactionSpawnEggItem> ULTRALISK_SPAWN_EGG_ENEMY = ITEMS.registerItem("ultralisk_spawn_egg_enemy",
            props -> new FactionSpawnEggItem(props, ULTRALISK, FactionSpawnEggItem.Side.ENEMY));

    public static final DeferredItem<FactionSpawnEggItem> HYDRALISK_SPAWN_EGG_ALLY = ITEMS.registerItem("hydralisk_spawn_egg_ally",
            props -> new FactionSpawnEggItem(props, HYDRALISK, FactionSpawnEggItem.Side.ALLY));
    public static final DeferredItem<FactionSpawnEggItem> HYDRALISK_SPAWN_EGG_ENEMY = ITEMS.registerItem("hydralisk_spawn_egg_enemy",
            props -> new FactionSpawnEggItem(props, HYDRALISK, FactionSpawnEggItem.Side.ENEMY));

    public static final DeferredItem<FactionSpawnEggItem> MUTALISK_SPAWN_EGG_ALLY = ITEMS.registerItem("mutalisk_spawn_egg_ally",
            props -> new FactionSpawnEggItem(props, MUTALISK, FactionSpawnEggItem.Side.ALLY));
    public static final DeferredItem<FactionSpawnEggItem> MUTALISK_SPAWN_EGG_ENEMY = ITEMS.registerItem("mutalisk_spawn_egg_enemy",
            props -> new FactionSpawnEggItem(props, MUTALISK, FactionSpawnEggItem.Side.ENEMY));
    public static final DeferredItem<FactionSpawnEggItem> OVERLORD_SPAWN_EGG_ALLY = ITEMS.registerItem("overlord_spawn_egg_ally",
            props -> new FactionSpawnEggItem(props, OVERLORD, FactionSpawnEggItem.Side.ALLY));
    public static final DeferredItem<FactionSpawnEggItem> OVERLORD_SPAWN_EGG_ENEMY = ITEMS.registerItem("overlord_spawn_egg_enemy",
            props -> new FactionSpawnEggItem(props, OVERLORD, FactionSpawnEggItem.Side.ENEMY));

    public static final DeferredItem<FactionSpawnEggItem> LURKER_SPAWN_EGG_ALLY = ITEMS.registerItem("lurker_spawn_egg_ally",
            props -> new FactionSpawnEggItem(props, LURKER, FactionSpawnEggItem.Side.ALLY));
    public static final DeferredItem<FactionSpawnEggItem> LURKER_SPAWN_EGG_ENEMY = ITEMS.registerItem("lurker_spawn_egg_enemy",
            props -> new FactionSpawnEggItem(props, LURKER, FactionSpawnEggItem.Side.ENEMY));

    public static final DeferredItem<FactionSpawnEggItem> INFESTED_VILLAGER_SPAWN_EGG_ALLY = ITEMS.registerItem("infested_villager_spawn_egg_ally",
            props -> new FactionSpawnEggItem(props, INFESTED_VILLAGER, FactionSpawnEggItem.Side.ALLY));
    public static final DeferredItem<FactionSpawnEggItem> INFESTED_VILLAGER_SPAWN_EGG_ENEMY = ITEMS.registerItem("infested_villager_spawn_egg_enemy",
            props -> new FactionSpawnEggItem(props, INFESTED_VILLAGER, FactionSpawnEggItem.Side.ENEMY));

    // The ally-side colony eggs are the real kits, bought off the Hive's card, so they carry the
    // Drone requirement. Their enemy-side twins stay ungated: they are testing tools, and there is
    // never an enemy Drone standing where a tester wants to plant one.
    public static final DeferredItem<FactionSpawnEggItem> SUNKEN_COLONY_SPAWN_EGG_ALLY = ITEMS.registerItem("sunken_colony_spawn_egg_ally",
            props -> new FactionSpawnEggItem(props, SUNKEN_COLONY, FactionSpawnEggItem.Side.ALLY, false, true, true)
                    .requiringBuilder());
    public static final DeferredItem<FactionSpawnEggItem> SUNKEN_COLONY_SPAWN_EGG_ENEMY = ITEMS.registerItem("sunken_colony_spawn_egg_enemy",
            props -> new FactionSpawnEggItem(props, SUNKEN_COLONY, FactionSpawnEggItem.Side.ENEMY, false, true, true));

    public static final DeferredItem<FactionSpawnEggItem> SPORE_COLONY_SPAWN_EGG_ALLY = ITEMS.registerItem("spore_colony_spawn_egg_ally",
            props -> new FactionSpawnEggItem(props, SPORE_COLONY, FactionSpawnEggItem.Side.ALLY, false, true, true)
                    .requiringBuilder());
    public static final DeferredItem<FactionSpawnEggItem> SPORE_COLONY_SPAWN_EGG_ENEMY = ITEMS.registerItem("spore_colony_spawn_egg_enemy",
            props -> new FactionSpawnEggItem(props, SPORE_COLONY, FactionSpawnEggItem.Side.ENEMY, false, true, true));

    // --- Sounds ---
    // Ambient events each name several ogg files in sounds.json; vanilla's sound system already
    // picks one at random per play, so a single registered SoundEvent covers all "live" variants.

    public static final DeferredHolder<SoundEvent, SoundEvent> ZEALOT_AMBIENT =
            SOUND_EVENTS.register("entity.zealot.ambient", () -> SoundEvent.createVariableRangeEvent(id("entity.zealot.ambient")));
    public static final DeferredHolder<SoundEvent, SoundEvent> ZEALOT_HURT =
            SOUND_EVENTS.register("entity.zealot.hurt", () -> SoundEvent.createVariableRangeEvent(id("entity.zealot.hurt")));
    public static final DeferredHolder<SoundEvent, SoundEvent> ZEALOT_DEATH =
            SOUND_EVENTS.register("entity.zealot.death", () -> SoundEvent.createVariableRangeEvent(id("entity.zealot.death")));
    public static final DeferredHolder<SoundEvent, SoundEvent> ZEALOT_ATTACK =
            SOUND_EVENTS.register("entity.zealot.attack", () -> SoundEvent.createVariableRangeEvent(id("entity.zealot.attack")));

    public static final DeferredHolder<SoundEvent, SoundEvent> ZERGLING_AMBIENT =
            SOUND_EVENTS.register("entity.zergling.ambient", () -> SoundEvent.createVariableRangeEvent(id("entity.zergling.ambient")));
    public static final DeferredHolder<SoundEvent, SoundEvent> ZERGLING_HURT =
            SOUND_EVENTS.register("entity.zergling.hurt", () -> SoundEvent.createVariableRangeEvent(id("entity.zergling.hurt")));
    public static final DeferredHolder<SoundEvent, SoundEvent> ZERGLING_DEATH =
            SOUND_EVENTS.register("entity.zergling.death", () -> SoundEvent.createVariableRangeEvent(id("entity.zergling.death")));

    // No hurt event: the source clips include no hurt bark, so the Ultralisk keeps the vanilla one
    // rather than borrowing another unit's voice — the same situation as the Dragoon.
    public static final DeferredHolder<SoundEvent, SoundEvent> ULTRALISK_AMBIENT =
            SOUND_EVENTS.register("entity.ultralisk.ambient", () -> SoundEvent.createVariableRangeEvent(id("entity.ultralisk.ambient")));
    public static final DeferredHolder<SoundEvent, SoundEvent> ULTRALISK_DEATH =
            SOUND_EVENTS.register("entity.ultralisk.death", () -> SoundEvent.createVariableRangeEvent(id("entity.ultralisk.death")));
    public static final DeferredHolder<SoundEvent, SoundEvent> ULTRALISK_ATTACK =
            SOUND_EVENTS.register("entity.ultralisk.attack", () -> SoundEvent.createVariableRangeEvent(id("entity.ultralisk.attack")));

    // No hurt or attack event: the Overlord has no attack at all, and the source clips include no
    // hurt bark, so it keeps the vanilla hurt sound rather than borrowing another unit's voice.
    public static final DeferredHolder<SoundEvent, SoundEvent> OVERLORD_AMBIENT =
            SOUND_EVENTS.register("entity.overlord.ambient", () -> SoundEvent.createVariableRangeEvent(id("entity.overlord.ambient")));
    public static final DeferredHolder<SoundEvent, SoundEvent> OVERLORD_DEATH =
            SOUND_EVENTS.register("entity.overlord.death", () -> SoundEvent.createVariableRangeEvent(id("entity.overlord.death")));

    public static final DeferredHolder<SoundEvent, SoundEvent> HYDRALISK_AMBIENT =
            SOUND_EVENTS.register("entity.hydralisk.ambient", () -> SoundEvent.createVariableRangeEvent(id("entity.hydralisk.ambient")));
    public static final DeferredHolder<SoundEvent, SoundEvent> HYDRALISK_HURT =
            SOUND_EVENTS.register("entity.hydralisk.hurt", () -> SoundEvent.createVariableRangeEvent(id("entity.hydralisk.hurt")));
    public static final DeferredHolder<SoundEvent, SoundEvent> HYDRALISK_DEATH =
            SOUND_EVENTS.register("entity.hydralisk.death", () -> SoundEvent.createVariableRangeEvent(id("entity.hydralisk.death")));

    public static final DeferredHolder<SoundEvent, SoundEvent> LURKER_AMBIENT =
            SOUND_EVENTS.register("entity.lurker.ambient", () -> SoundEvent.createVariableRangeEvent(id("entity.lurker.ambient")));
    public static final DeferredHolder<SoundEvent, SoundEvent> LURKER_ATTACK =
            SOUND_EVENTS.register("entity.lurker.attack", () -> SoundEvent.createVariableRangeEvent(id("entity.lurker.attack")));
    // One clip for both directions of the dig — it is the same animal doing the same thing.
    public static final DeferredHolder<SoundEvent, SoundEvent> LURKER_BURROW =
            SOUND_EVENTS.register("entity.lurker.burrow", () -> SoundEvent.createVariableRangeEvent(id("entity.lurker.burrow")));

    // Only the two "what" clips exist for this one, so it has an ambient bark and nothing else; hurt
    // and death fall back to vanilla, as the Ultralisk's and Dragoon's do. Its fuse and its blast are
    // vanilla sounds by design — a creeper's priming hiss is already the universal "get away from
    // that" cue, and re-voicing it would only make the warning less legible.
    public static final DeferredHolder<SoundEvent, SoundEvent> INFESTED_VILLAGER_AMBIENT =
            SOUND_EVENTS.register("entity.infested_villager.ambient", () -> SoundEvent.createVariableRangeEvent(id("entity.infested_villager.ambient")));

    public static final DeferredHolder<SoundEvent, SoundEvent> PROBE_AMBIENT =
            SOUND_EVENTS.register("entity.probe.ambient", () -> SoundEvent.createVariableRangeEvent(id("entity.probe.ambient")));
    public static final DeferredHolder<SoundEvent, SoundEvent> PROBE_HURT =
            SOUND_EVENTS.register("entity.probe.hurt", () -> SoundEvent.createVariableRangeEvent(id("entity.probe.hurt")));
    public static final DeferredHolder<SoundEvent, SoundEvent> PROBE_DEATH =
            SOUND_EVENTS.register("entity.probe.death", () -> SoundEvent.createVariableRangeEvent(id("entity.probe.death")));
    // No SCV_HURT: the archive has two acknowledgement lines and a death line for the SCV and no
    // pain line, so ScvEntity leaves getHurtSound at Mob's default rather than reusing a bark.
    public static final DeferredHolder<SoundEvent, SoundEvent> SCV_AMBIENT =
            SOUND_EVENTS.register("entity.scv.ambient", () -> SoundEvent.createVariableRangeEvent(id("entity.scv.ambient")));
    public static final DeferredHolder<SoundEvent, SoundEvent> SCV_DEATH =
            SOUND_EVENTS.register("entity.scv.death", () -> SoundEvent.createVariableRangeEvent(id("entity.scv.death")));
    // No MARINE_HURT either, and for the same reason as the SCV above: the archive has an
    // acknowledgement line and two death lines for the Marine and no pain line.
    public static final DeferredHolder<SoundEvent, SoundEvent> MARINE_AMBIENT =
            SOUND_EVENTS.register("entity.marine.ambient", () -> SoundEvent.createVariableRangeEvent(id("entity.marine.ambient")));
    public static final DeferredHolder<SoundEvent, SoundEvent> MARINE_DEATH =
            SOUND_EVENTS.register("entity.marine.death", () -> SoundEvent.createVariableRangeEvent(id("entity.marine.death")));
    public static final DeferredHolder<SoundEvent, SoundEvent> MARINE_ATTACK =
            SOUND_EVENTS.register("entity.marine.attack", () -> SoundEvent.createVariableRangeEvent(id("entity.marine.attack")));

    // No FIREBAT_HURT, for the third time and the same reason: the archive has two acknowledgement
    // lines, three death lines and two attack lines for the Firebat, and no pain line.
    public static final DeferredHolder<SoundEvent, SoundEvent> FIREBAT_AMBIENT =
            SOUND_EVENTS.register("entity.firebat.ambient", () -> SoundEvent.createVariableRangeEvent(id("entity.firebat.ambient")));
    public static final DeferredHolder<SoundEvent, SoundEvent> FIREBAT_DEATH =
            SOUND_EVENTS.register("entity.firebat.death", () -> SoundEvent.createVariableRangeEvent(id("entity.firebat.death")));
    public static final DeferredHolder<SoundEvent, SoundEvent> FIREBAT_ATTACK =
            SOUND_EVENTS.register("entity.firebat.attack", () -> SoundEvent.createVariableRangeEvent(id("entity.firebat.attack")));

    // No GHOST_HURT, for the fourth time and the same reason: the archive has two acknowledgement
    // lines, one death line and one attack line for the Ghost, and no pain line.
    public static final DeferredHolder<SoundEvent, SoundEvent> GHOST_AMBIENT =
            SOUND_EVENTS.register("entity.ghost.ambient", () -> SoundEvent.createVariableRangeEvent(id("entity.ghost.ambient")));
    public static final DeferredHolder<SoundEvent, SoundEvent> GHOST_DEATH =
            SOUND_EVENTS.register("entity.ghost.death", () -> SoundEvent.createVariableRangeEvent(id("entity.ghost.death")));
    public static final DeferredHolder<SoundEvent, SoundEvent> GHOST_ATTACK =
            SOUND_EVENTS.register("entity.ghost.attack", () -> SoundEvent.createVariableRangeEvent(id("entity.ghost.attack")));

    // No WRAITH_HURT, for the fifth time and the same reason: the archive has two acknowledgement
    // lines and one death line for the Wraith, and no pain line. Its attack is the exception to the
    // one-shot-sound-per-unit rule every other unit follows — the archive ships a separate ground
    // and air firing clip, which is the same split the unit's damage already makes, so the branch
    // that picks the damage picks the sound with it (see WraithEntity.performRangedAttack).
    public static final DeferredHolder<SoundEvent, SoundEvent> WRAITH_AMBIENT =
            SOUND_EVENTS.register("entity.wraith.ambient", () -> SoundEvent.createVariableRangeEvent(id("entity.wraith.ambient")));
    public static final DeferredHolder<SoundEvent, SoundEvent> WRAITH_DEATH =
            SOUND_EVENTS.register("entity.wraith.death", () -> SoundEvent.createVariableRangeEvent(id("entity.wraith.death")));
    public static final DeferredHolder<SoundEvent, SoundEvent> WRAITH_ATTACK_GROUND =
            SOUND_EVENTS.register("entity.wraith.attack_ground",
                    () -> SoundEvent.createVariableRangeEvent(id("entity.wraith.attack_ground")));
    public static final DeferredHolder<SoundEvent, SoundEvent> WRAITH_ATTACK_AIR =
            SOUND_EVENTS.register("entity.wraith.attack_air",
                    () -> SoundEvent.createVariableRangeEvent(id("entity.wraith.attack_air")));

    // The Missile Turret's only sound. A structure has no ambient line, no pain line and no death
    // cry — the Photon Cannon and the two colonies are the same, and only the shot makes noise.
    public static final DeferredHolder<SoundEvent, SoundEvent> MISSILE_TURRET_ATTACK =
            SOUND_EVENTS.register("entity.missile_turret.attack",
                    () -> SoundEvent.createVariableRangeEvent(id("entity.missile_turret.attack")));
    public static final DeferredHolder<SoundEvent, SoundEvent> DRONE_AMBIENT =
            SOUND_EVENTS.register("entity.drone.ambient", () -> SoundEvent.createVariableRangeEvent(id("entity.drone.ambient")));
    public static final DeferredHolder<SoundEvent, SoundEvent> DRONE_HURT =
            SOUND_EVENTS.register("entity.drone.hurt", () -> SoundEvent.createVariableRangeEvent(id("entity.drone.hurt")));
    public static final DeferredHolder<SoundEvent, SoundEvent> DRONE_DEATH =
            SOUND_EVENTS.register("entity.drone.death", () -> SoundEvent.createVariableRangeEvent(id("entity.drone.death")));

    public static final DeferredHolder<SoundEvent, SoundEvent> DRAGOON_AMBIENT =
            SOUND_EVENTS.register("entity.dragoon.ambient", () -> SoundEvent.createVariableRangeEvent(id("entity.dragoon.ambient")));
    public static final DeferredHolder<SoundEvent, SoundEvent> DRAGOON_DEATH =
            SOUND_EVENTS.register("entity.dragoon.death", () -> SoundEvent.createVariableRangeEvent(id("entity.dragoon.death")));
    public static final DeferredHolder<SoundEvent, SoundEvent> DRAGOON_ATTACK =
            SOUND_EVENTS.register("entity.dragoon.attack", () -> SoundEvent.createVariableRangeEvent(id("entity.dragoon.attack")));
    public static final DeferredHolder<SoundEvent, SoundEvent> SCOUT_AMBIENT =
            SOUND_EVENTS.register("entity.scout.ambient", () -> SoundEvent.createVariableRangeEvent(id("entity.scout.ambient")));
    public static final DeferredHolder<SoundEvent, SoundEvent> SCOUT_ATTACK =
            SOUND_EVENTS.register("entity.scout.attack", () -> SoundEvent.createVariableRangeEvent(id("entity.scout.attack")));
    public static final DeferredHolder<SoundEvent, SoundEvent> MUTALISK_ATTACK =
            SOUND_EVENTS.register("entity.mutalisk.attack", () -> SoundEvent.createVariableRangeEvent(id("entity.mutalisk.attack")));
    public static final DeferredHolder<SoundEvent, SoundEvent> PHOTON_CANNON_ATTACK =
            SOUND_EVENTS.register("entity.photon_cannon.attack",
                    () -> SoundEvent.createVariableRangeEvent(id("entity.photon_cannon.attack")));

    public static final DeferredHolder<SoundEvent, SoundEvent> DARK_TEMPLAR_AMBIENT =
            SOUND_EVENTS.register("entity.dark_templar.ambient",
                    () -> SoundEvent.createVariableRangeEvent(id("entity.dark_templar.ambient")));
    // No hurt event: the ported clips carry no hurt bark, so it keeps vanilla's (as the Ultralisk does).
    public static final DeferredHolder<SoundEvent, SoundEvent> DARK_TEMPLAR_DEATH =
            SOUND_EVENTS.register("entity.dark_templar.death",
                    () -> SoundEvent.createVariableRangeEvent(id("entity.dark_templar.death")));
    public static final DeferredHolder<SoundEvent, SoundEvent> DARK_TEMPLAR_ATTACK =
            SOUND_EVENTS.register("entity.dark_templar.attack",
                    () -> SoundEvent.createVariableRangeEvent(id("entity.dark_templar.attack")));

    // --- Creative tab ---

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> ASTERISKCRAFT_TAB = CREATIVE_MODE_TABS.register("asteriskcraft_tab", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.asteriskcraft"))
            .withTabsBefore(CreativeModeTabs.COMBAT)
            .icon(() -> TAB_ICON.get().getDefaultInstance())
            .displayItems((parameters, output) -> {
                output.accept(NEXUS_CORE_ITEM.get());
                output.accept(GATEWAY_CORE_ITEM.get());
                output.accept(PYLON_CORE_ITEM.get());
                output.accept(PYLON_KIT.get());
                output.accept(GATEWAY_KIT.get());
                output.accept(PHOTON_CANNON_KIT.get());
                output.accept(NEXUS_KIT.get());
                output.accept(HIVE_KIT.get());
                output.accept(SPAWNING_POOL_KIT.get());
                output.accept(SPIRE_KIT.get());
                output.accept(STARGATE_KIT.get());
                output.accept(COMMAND_CENTER_KIT.get());
                output.accept(BARRACKS_KIT.get());
                output.accept(STARPORT_KIT.get());
                output.accept(HIVE_CORE_ITEM.get());
                output.accept(COMMAND_CENTER_CORE_ITEM.get());
                output.accept(STARGATE_CORE_ITEM.get());
                output.accept(SPAWNING_POOL_CORE_ITEM.get());
                output.accept(SPIRE_CORE_ITEM.get());
                output.accept(BARRACKS_CORE_ITEM.get());
                output.accept(FACTORY_CORE_ITEM.get());
                output.accept(STARPORT_CORE_ITEM.get());
                output.accept(BUNKER_KIT.get());
                output.accept(MISSILE_TURRET_KIT.get());
                output.accept(CURSOR.get());
                output.accept(PROBE_SPAWN_EGG_ALLY.get());
                output.accept(PROBE_SPAWN_EGG_ENEMY.get());
                output.accept(SCV_SPAWN_EGG_ALLY.get());
                output.accept(SCV_SPAWN_EGG_ENEMY.get());
                output.accept(MARINE_SPAWN_EGG_ALLY.get());
                output.accept(MARINE_SPAWN_EGG_ENEMY.get());
                output.accept(FIREBAT_SPAWN_EGG_ALLY.get());
                output.accept(FIREBAT_SPAWN_EGG_ENEMY.get());
                output.accept(GHOST_SPAWN_EGG_ALLY.get());
                output.accept(GHOST_SPAWN_EGG_ENEMY.get());
                output.accept(WRAITH_SPAWN_EGG_ALLY.get());
                output.accept(WRAITH_SPAWN_EGG_ENEMY.get());
                output.accept(BUNKER_SPAWN_EGG_ALLY.get());
                output.accept(BUNKER_SPAWN_EGG_ENEMY.get());
                output.accept(MISSILE_TURRET_SPAWN_EGG_ALLY.get());
                output.accept(MISSILE_TURRET_SPAWN_EGG_ENEMY.get());
                output.accept(ZEALOT_SPAWN_EGG_ALLY.get());
                output.accept(ZEALOT_SPAWN_EGG_ENEMY.get());
                output.accept(DRAGOON_SPAWN_EGG_ALLY.get());
                output.accept(DRAGOON_SPAWN_EGG_ENEMY.get());
                output.accept(SCOUT_SPAWN_EGG_ALLY.get());
                output.accept(SCOUT_SPAWN_EGG_ENEMY.get());
            output.accept(DARK_TEMPLAR_SPAWN_EGG_ALLY.get());
            output.accept(DARK_TEMPLAR_SPAWN_EGG_ENEMY.get());
                output.accept(DRONE_SPAWN_EGG_ALLY.get());
                output.accept(DRONE_SPAWN_EGG_ENEMY.get());
                output.accept(ZERGLING_SPAWN_EGG_ALLY.get());
                output.accept(ZERGLING_SPAWN_EGG_ENEMY.get());
                output.accept(ULTRALISK_SPAWN_EGG_ALLY.get());
                output.accept(ULTRALISK_SPAWN_EGG_ENEMY.get());
                output.accept(HYDRALISK_SPAWN_EGG_ALLY.get());
                output.accept(HYDRALISK_SPAWN_EGG_ENEMY.get());
                output.accept(MUTALISK_SPAWN_EGG_ALLY.get());
                output.accept(MUTALISK_SPAWN_EGG_ENEMY.get());
                output.accept(OVERLORD_SPAWN_EGG_ALLY.get());
                output.accept(OVERLORD_SPAWN_EGG_ENEMY.get());
                output.accept(LURKER_SPAWN_EGG_ALLY.get());
                output.accept(LURKER_SPAWN_EGG_ENEMY.get());
                output.accept(INFESTED_VILLAGER_SPAWN_EGG_ALLY.get());
                output.accept(INFESTED_VILLAGER_SPAWN_EGG_ENEMY.get());
                output.accept(SUNKEN_COLONY_SPAWN_EGG_ALLY.get());
                output.accept(SUNKEN_COLONY_SPAWN_EGG_ENEMY.get());
                output.accept(SPORE_COLONY_SPAWN_EGG_ALLY.get());
                output.accept(SPORE_COLONY_SPAWN_EGG_ENEMY.get());
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
        DetectionAttachments.ATTACHMENT_TYPES.register(modEventBus);
        RegenAttachments.ATTACHMENT_TYPES.register(modEventBus);
        ArmyBank.ATTACHMENT_TYPES.register(modEventBus);
        CoreCensus.ATTACHMENT_TYPES.register(modEventBus);
        TechCensus.ATTACHMENT_TYPES.register(modEventBus);
        AsteriskCraftGameRules.GAME_RULES.register(modEventBus);

        modEventBus.addListener(this::registerEntityAttributes);
        modEventBus.addListener(this::registerPayloads);
        modEventBus.addListener(this::registerCapabilities);
    }

    private void registerCapabilities(RegisterCapabilitiesEvent event) {
        // Expose every base's inventory as an item handler so workers can deposit their harvest
        // yield straight into their home core building.
        event.registerBlockEntity(Capabilities.Item.BLOCK, BASE_BLOCK_ENTITY.get(),
                (base, side) -> VanillaContainerWrapper.of(base));
    }

    private void registerPayloads(RegisterPayloadHandlersEvent event) {
        event.registrar("1").playToServer(CommandInputPacket.TYPE, CommandInputPacket.STREAM_CODEC,
                (packet, context) -> context.enqueueWork(
                        () -> CommandInputResolver.handle(packet, (net.minecraft.server.level.ServerPlayer) context.player())));
        event.registrar("1").playToServer(UnitGroupPacket.TYPE, UnitGroupPacket.STREAM_CODEC,
                (packet, context) -> context.enqueueWork(
                        () -> UnitGroupResolver.handle(packet, (net.minecraft.server.level.ServerPlayer) context.player())));
        // The mod's only server->client payload. ClientUnitGroups is client-dist only, so it is
        // named inside the lambda body: a dedicated server registers this handler but never runs
        // it, and the class is therefore never resolved there.
        event.registrar("1").playToClient(UnitGroupSyncPacket.TYPE, UnitGroupSyncPacket.STREAM_CODEC,
                (packet, context) -> context.enqueueWork(
                        () -> net.bitflora.asteriskcraft.command.client.ClientUnitGroups.accept(packet)));
    }

    // One line per net.bitflora.asteriskcraft.stats.UnitStats entry — stats.UnitStatsTest pins the
    // roster size, so a new unit can't be added without landing here too. Each race's worker declares
    // its own createAttributes(), so its numbers are its own UnitStats entry rather than the shared
    // WorkerEntity's (which has none).
    private void registerEntityAttributes(EntityAttributeCreationEvent event) {
        event.put(PROBE.get(), ProbeEntity.createAttributes().build());
        event.put(SCV.get(), ScvEntity.createAttributes().build());
        event.put(MARINE.get(), MarineEntity.createAttributes().build());
        event.put(FIREBAT.get(), FirebatEntity.createAttributes().build());
        event.put(GHOST.get(), GhostEntity.createAttributes().build());
        event.put(WRAITH.get(), WraithEntity.createAttributes().build());
        event.put(BUNKER.get(), BunkerEntity.createAttributes().build());
        event.put(MISSILE_TURRET.get(), MissileTurretEntity.createAttributes().build());
        event.put(ZEALOT.get(), ZealotEntity.createAttributes().build());
        event.put(DRAGOON.get(), DragoonEntity.createAttributes().build());
        event.put(SCOUT.get(), ScoutEntity.createAttributes().build());
        event.put(DARK_TEMPLAR.get(), DarkTemplarEntity.createAttributes().build());
        event.put(DRONE.get(), DroneEntity.createAttributes().build());
        event.put(ZERGLING.get(), ZerglingEntity.createAttributes().build());
        event.put(ULTRALISK.get(), UltraliskEntity.createAttributes().build());
        event.put(HYDRALISK.get(), HydraliskEntity.createAttributes().build());
        event.put(MUTALISK.get(), MutaliskEntity.createAttributes().build());
        event.put(OVERLORD.get(), OverlordEntity.createAttributes().build());
        event.put(PHOTON_CANNON.get(), PhotonCannonEntity.createAttributes().build());
        event.put(LURKER.get(), LurkerEntity.createAttributes().build());
        event.put(INFESTED_VILLAGER.get(), InfestedVillagerEntity.createAttributes().build());
        event.put(SUNKEN_COLONY.get(), SunkenColonyEntity.createAttributes().build());
        event.put(SPORE_COLONY.get(), SporeColonyEntity.createAttributes().build());
        // No attributes for SUNKEN_SPIKE — it's a plain Entity, not a LivingEntity.
    }
}
