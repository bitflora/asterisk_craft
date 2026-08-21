package net.bitflora.asteriskcraft;

import com.mojang.logging.LogUtils;
import net.bitflora.asteriskcraft.building.ArmyBank;
import net.bitflora.asteriskcraft.building.BuildingKitItem;
import net.bitflora.asteriskcraft.building.BaseBlock;
import net.bitflora.asteriskcraft.building.BaseBlockEntity;
import net.bitflora.asteriskcraft.building.BuildingTemplates;
import net.bitflora.asteriskcraft.building.CoreCensus;
import net.bitflora.asteriskcraft.building.DepletedNodeBlock;
import net.bitflora.asteriskcraft.building.DepletedNodeBlockEntity;
import net.bitflora.asteriskcraft.building.GatewayBlock;
import net.bitflora.asteriskcraft.building.GatewayBlockEntity;
import net.bitflora.asteriskcraft.building.ProductionMenu;
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
import net.bitflora.asteriskcraft.entity.protoss.ScoutEntity;
import net.bitflora.asteriskcraft.entity.protoss.ZealotEntity;
import net.bitflora.asteriskcraft.entity.zerg.InfestedVillagerEntity;
import net.bitflora.asteriskcraft.entity.zerg.LurkerEntity;
import net.bitflora.asteriskcraft.entity.zerg.LurkerSpineEntity;
import net.bitflora.asteriskcraft.entity.zerg.MutaliskEntity;
import net.bitflora.asteriskcraft.entity.zerg.SunkenColonyEntity;
import net.bitflora.asteriskcraft.entity.zerg.SporeColonyEntity;
import net.bitflora.asteriskcraft.entity.zerg.SunkenSpikeEntity;
import net.bitflora.asteriskcraft.entity.zerg.UltraliskEntity;
import net.bitflora.asteriskcraft.entity.zerg.ZerglingEntity;
import net.bitflora.asteriskcraft.faction.Race;
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
import net.minecraft.world.item.Item;
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

    public static final DeferredBlock<BaseBlock> HIVE_CORE = BLOCKS.registerBlock("hive_core",
            props -> new BaseBlock(Race.ZERG, props),
            p -> p.mapColor(MapColor.CRIMSON_HYPHAE).strength(15.0f, 1200.0f).lightLevel(s -> 7));

    public static final DeferredItem<BlockItem> NEXUS_CORE_ITEM = ITEMS.registerSimpleBlockItem("nexus_core", NEXUS_CORE);
    public static final DeferredItem<BlockItem> GATEWAY_CORE_ITEM = ITEMS.registerSimpleBlockItem("gateway_core", GATEWAY_CORE);
    public static final DeferredItem<BlockItem> HIVE_CORE_ITEM = ITEMS.registerSimpleBlockItem("hive_core", HIVE_CORE);

    public static final DeferredItem<BuildingKitItem> GATEWAY_KIT = ITEMS.registerItem("gateway_kit",
            props -> new BuildingKitItem(props, BuildingTemplates.GATEWAY, GATEWAY_CORE,
                    BuildingTemplates.GATEWAY_FOOTPRINT));

    // Bought from the Nexus's own production menu (paid from the shared army bank) rather than
    // crafted, unlike the other kits — see BaseBlockEntity#trainOption. An expansion Nexus, so
    // it's deliberately not offered as a cheap personal-inventory crafting-table recipe.
    public static final DeferredItem<BuildingKitItem> NEXUS_KIT = ITEMS.registerItem("nexus_kit",
            props -> new BuildingKitItem(props, BuildingTemplates.NEXUS, NEXUS_CORE,
                    BuildingTemplates.NEXUS_FOOTPRINT));

    // The Photon Cannon is an entity now, so its kit is a faction-stamping spawn item (it warps the
    // entity in on right-click) rather than a layout-stamping BuildingKitItem. Same crafted item + recipe.
    public static final DeferredItem<FactionSpawnEggItem> PHOTON_CANNON_KIT = ITEMS.registerItem("photon_cannon_kit",
            props -> new FactionSpawnEggItem(props, AsteriskCraft.PHOTON_CANNON, FactionSpawnEggItem.Side.ALLY));

    public static final DeferredItem<CursorItem> CURSOR = ITEMS.registerItem("cursor",
            CursorItem::new);

    // Icon-only item for the creative tab button; not added to displayItems.
    public static final DeferredItem<Item> TAB_ICON = ITEMS.registerSimpleItem("tab_icon");

    // --- Block entities ---

    // One type for every race's base — both core blocks are valid for it, which is what a shared
    // BaseBlockEntity means.
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<BaseBlockEntity>> BASE_BLOCK_ENTITY =
            BLOCK_ENTITY_TYPES.register("base",
                    () -> new BlockEntityType<>(BaseBlockEntity::new, NEXUS_CORE.get(), HIVE_CORE.get()));

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
                    .sized(1.4f, 1.6f) // squat root mound, wider than it is tall
                    .clientTrackingRange(10)
                    .build(ResourceKey.create(Registries.ENTITY_TYPE, id("sunken_colony"))));

    public static final DeferredHolder<EntityType<?>, EntityType<SporeColonyEntity>> SPORE_COLONY =
            ENTITY_TYPES.register("spore_colony", () -> EntityType.Builder.of(SporeColonyEntity::new, MobCategory.MONSTER)
                    // Squat and wide like the Sunken's mound, per the reference art: a low body
                    // with a chimney on top, broader than it is tall.
                    .sized(1.4f, 1.4f)
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

    public static final DeferredItem<FactionSpawnEggItem> LURKER_SPAWN_EGG_ALLY = ITEMS.registerItem("lurker_spawn_egg_ally",
            props -> new FactionSpawnEggItem(props, LURKER, FactionSpawnEggItem.Side.ALLY));
    public static final DeferredItem<FactionSpawnEggItem> LURKER_SPAWN_EGG_ENEMY = ITEMS.registerItem("lurker_spawn_egg_enemy",
            props -> new FactionSpawnEggItem(props, LURKER, FactionSpawnEggItem.Side.ENEMY));

    public static final DeferredItem<FactionSpawnEggItem> INFESTED_VILLAGER_SPAWN_EGG_ALLY = ITEMS.registerItem("infested_villager_spawn_egg_ally",
            props -> new FactionSpawnEggItem(props, INFESTED_VILLAGER, FactionSpawnEggItem.Side.ALLY));
    public static final DeferredItem<FactionSpawnEggItem> INFESTED_VILLAGER_SPAWN_EGG_ENEMY = ITEMS.registerItem("infested_villager_spawn_egg_enemy",
            props -> new FactionSpawnEggItem(props, INFESTED_VILLAGER, FactionSpawnEggItem.Side.ENEMY));

    public static final DeferredItem<FactionSpawnEggItem> SUNKEN_COLONY_SPAWN_EGG_ALLY = ITEMS.registerItem("sunken_colony_spawn_egg_ally",
            props -> new FactionSpawnEggItem(props, SUNKEN_COLONY, FactionSpawnEggItem.Side.ALLY));
    public static final DeferredItem<FactionSpawnEggItem> SUNKEN_COLONY_SPAWN_EGG_ENEMY = ITEMS.registerItem("sunken_colony_spawn_egg_enemy",
            props -> new FactionSpawnEggItem(props, SUNKEN_COLONY, FactionSpawnEggItem.Side.ENEMY));

    public static final DeferredItem<FactionSpawnEggItem> SPORE_COLONY_SPAWN_EGG_ALLY = ITEMS.registerItem("spore_colony_spawn_egg_ally",
            props -> new FactionSpawnEggItem(props, SPORE_COLONY, FactionSpawnEggItem.Side.ALLY));
    public static final DeferredItem<FactionSpawnEggItem> SPORE_COLONY_SPAWN_EGG_ENEMY = ITEMS.registerItem("spore_colony_spawn_egg_enemy",
            props -> new FactionSpawnEggItem(props, SPORE_COLONY, FactionSpawnEggItem.Side.ENEMY));

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
                output.accept(GATEWAY_KIT.get());
                output.accept(PHOTON_CANNON_KIT.get());
                output.accept(NEXUS_KIT.get());
                output.accept(HIVE_CORE_ITEM.get());
                output.accept(CURSOR.get());
                output.accept(PROBE_SPAWN_EGG_ALLY.get());
                output.accept(PROBE_SPAWN_EGG_ENEMY.get());
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
        event.put(ZEALOT.get(), ZealotEntity.createAttributes().build());
        event.put(DRAGOON.get(), DragoonEntity.createAttributes().build());
        event.put(SCOUT.get(), ScoutEntity.createAttributes().build());
        event.put(DARK_TEMPLAR.get(), DarkTemplarEntity.createAttributes().build());
        event.put(DRONE.get(), DroneEntity.createAttributes().build());
        event.put(ZERGLING.get(), ZerglingEntity.createAttributes().build());
        event.put(ULTRALISK.get(), UltraliskEntity.createAttributes().build());
        event.put(HYDRALISK.get(), HydraliskEntity.createAttributes().build());
        event.put(MUTALISK.get(), MutaliskEntity.createAttributes().build());
        event.put(PHOTON_CANNON.get(), PhotonCannonEntity.createAttributes().build());
        event.put(LURKER.get(), LurkerEntity.createAttributes().build());
        event.put(INFESTED_VILLAGER.get(), InfestedVillagerEntity.createAttributes().build());
        event.put(SUNKEN_COLONY.get(), SunkenColonyEntity.createAttributes().build());
        event.put(SPORE_COLONY.get(), SporeColonyEntity.createAttributes().build());
        // No attributes for SUNKEN_SPIKE — it's a plain Entity, not a LivingEntity.
    }
}
