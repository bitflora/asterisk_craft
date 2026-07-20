package net.bitflora.asteriskcraft.game;

import net.bitflora.asteriskcraft.AsteriskCraft;
import net.bitflora.asteriskcraft.building.ArmyBank;
import net.bitflora.asteriskcraft.building.BuildingLayouts;
import net.bitflora.asteriskcraft.building.HiveBlockEntity;
import net.bitflora.asteriskcraft.building.NexusBlockEntity;
import net.bitflora.asteriskcraft.director.ZergSpawns;
import net.bitflora.asteriskcraft.entity.zerg.DroneEntity;
import net.bitflora.asteriskcraft.faction.Faction;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

import java.util.ArrayList;
import java.util.List;

/**
 * Places the player's Nexus (seeded with starting resources) the first time someone joins a
 * new world. We do this on first join (rather than at server start) because the player's
 * chunk is guaranteed loaded with a settled surface height — placing at server start
 * can land the structure at the world floor before terrain is ready. From the player's
 * point of view the world still "starts" with a Nexus already standing (R1).
 */
@EventBusSubscriber(modid = AsteriskCraft.MODID)
public final class GameBootstrap {
    private static final int NEXUS_OFFSET = 6;
    private static final int STARTING_LOGS = 100;
    private static final int STARTING_COBBLESTONE = 100;

    // The Zerg base sits ~110 blocks east of the Nexus — a real march, but inside typical
    // simulation distance so wave units and Drones never freeze in unloaded chunks (V3 keeps
    // chunk tickets out of scope; see docs/shaping.md).
    private static final int ZERG_BASE_DISTANCE = 110;
    private static final int[][] HIVE_OFFSETS = {{0, 0}, {12, -7}, {12, 7}};
    // Total seeded into the shared Zerg army bank once, matching the old 128/128/48-per-Hive x3
    // total (each Hive used to hold its own independent stock before they shared one pool).
    private static final int STARTING_ZERG_LOGS = 128 * 3;
    private static final int STARTING_ZERG_COBBLE = 128 * 3;
    private static final int STARTING_ZERG_IRON = 48 * 3;
    private static final int INITIAL_DRONES_PER_HIVE = 2;

    private GameBootstrap() {
    }

    @SubscribeEvent
    public static void onPlayerJoin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        ServerLevel overworld = player.level().getServer().overworld();

        if (!overworld.getData(GameAttachments.BOOTSTRAPPED)) {
            placeStartingBase(overworld, player);
        }

        BlockPos nexus = overworld.getData(GameAttachments.NEXUS_POS);
        player.sendSystemMessage(Component.translatable(
                "message.asteriskcraft.nexus_location", nexus.getX(), nexus.getY(), nexus.getZ()));
    }

    private static void placeStartingBase(ServerLevel level, ServerPlayer player) {
        int x = player.blockPosition().getX() + NEXUS_OFFSET;
        int z = player.blockPosition().getZ() + NEXUS_OFFSET;
        int y = level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z) - 1;
        BlockPos origin = new BlockPos(x, y, z);

        BuildingLayouts.place(level, origin, BuildingLayouts.nexus());

        BlockPos core = origin.offset(0, 2, 0);
        level.setData(GameAttachments.NEXUS_POS, core);
        if (level.getBlockEntity(core) instanceof NexusBlockEntity nexus) {
            seedNexus(nexus);
        }

        placeZergBase(level, x, z);

        level.setData(GameAttachments.BOOTSTRAPPED, true);

        // The Command Crystal enables unit select/order mode while held (R5).
        player.getInventory().add(new ItemStack(AsteriskCraft.CURSOR.get()));
        player.getInventory().add(new ItemStack(AsteriskCraft.GATEWAY_KIT.get()));
        player.getInventory().add(new ItemStack(AsteriskCraft.PHOTON_CANNON_KIT.get()));
        player.sendSystemMessage(Component.translatable("message.asteriskcraft.zerg_location", "east"));

        AsteriskCraft.LOGGER.info("AsteriskCraft: placed Nexus core at {}", core);
    }

    /** Stamps the AI Zerg's three Hives (plus a small resource garden and starter Drones) east of the Nexus. */
    private static void placeZergBase(ServerLevel level, int nexusX, int nexusZ) {
        int baseX = nexusX + ZERG_BASE_DISTANCE;
        int baseZ = nexusZ;
        List<BlockPos> cores = new ArrayList<>();
        for (int[] offset : HIVE_OFFSETS) {
            int hx = baseX + offset[0];
            int hz = baseZ + offset[1];
            BlockPos core = placeHive(level, hx, hz);
            if (core != null) {
                cores.add(core);
            }
            seedResourceGarden(level, hx, hz);
        }
        level.setData(GameAttachments.HIVE_POSITIONS, List.copyOf(cores));
        seedZergArmyBank(level);
        AsteriskCraft.LOGGER.info("AsteriskCraft: placed {} Zerg Hives near {},{}", cores.size(), baseX, baseZ);
    }

    private static BlockPos placeHive(ServerLevel level, int x, int z) {
        int y = level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z) - 1;
        BlockPos origin = new BlockPos(x, y, z);
        BuildingLayouts.place(level, origin, BuildingLayouts.hive());
        BlockPos core = origin.offset(BuildingLayouts.HIVE_CORE_OFFSET.getX(),
                BuildingLayouts.HIVE_CORE_OFFSET.getY(), BuildingLayouts.HIVE_CORE_OFFSET.getZ());
        if (level.getBlockEntity(core) instanceof HiveBlockEntity hive) {
            hive.setFaction(Faction.ZERG);
            for (int i = 0; i < INITIAL_DRONES_PER_HIVE; i++) {
                DroneEntity drone = ZergSpawns.spawn(level, core, AsteriskCraft.DRONE.get(), Faction.ZERG, false);
                if (drone != null) {
                    drone.setHomePos(core);
                }
            }
        }
        return core;
    }

    /**
     * Seeds the shared Zerg army bank once (not per-Hive — all three Hives are linked chests
     * onto the same pool) with enough resources to bankroll the first Drones and the director's
     * early waves.
     */
    private static void seedZergArmyBank(ServerLevel level) {
        NonNullList<ItemStack> bank = ArmyBank.of(level, Faction.ZERG);
        int slot = 0;
        slot = seedStacks(bank, slot, Items.OAK_LOG, STARTING_ZERG_LOGS);
        slot = seedStacks(bank, slot, Items.COBBLESTONE, STARTING_ZERG_COBBLE);
        seedStacks(bank, slot, Items.IRON_INGOT, STARTING_ZERG_IRON);
    }

    /**
     * Fills consecutive bank slots starting at {@code slot} with {@code amount} of {@code item},
     * each slot capped at the item's max stack size (64 for these resources) — a naive single
     * split assuming the leftover fits in one slot silently produces oversized stacks once a
     * total exceeds twice the max stack size, which then fails to (de)serialize.
     */
    private static int seedStacks(NonNullList<ItemStack> bank, int slot, net.minecraft.world.item.Item item, int amount) {
        int maxStackSize = new ItemStack(item).getMaxStackSize();
        int remaining = amount;
        while (remaining > 0) {
            int count = Math.min(remaining, maxStackSize);
            bank.set(slot++, new ItemStack(item, count));
            remaining -= count;
        }
        return slot;
    }

    /** Exposes a handful of surface harvestable blocks near a Hive so its Drones can keep mining. */
    private static void seedResourceGarden(ServerLevel level, int cx, int cz) {
        BlockState[] nodes = {
                Blocks.STONE.defaultBlockState(), Blocks.STONE.defaultBlockState(), Blocks.STONE.defaultBlockState(),
                Blocks.OAK_LOG.defaultBlockState(), Blocks.OAK_LOG.defaultBlockState(), Blocks.OAK_LOG.defaultBlockState(),
                Blocks.IRON_ORE.defaultBlockState(), Blocks.IRON_ORE.defaultBlockState(), Blocks.IRON_ORE.defaultBlockState()};
        int[][] offsets = {{5, 0}, {6, 2}, {5, -2}, {-5, 0}, {-6, 2}, {-5, -2}, {0, 5}, {2, 6}, {-2, 5}};
        for (int i = 0; i < offsets.length; i++) {
            int x = cx + offsets[i][0];
            int z = cz + offsets[i][1];
            int y = level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z) - 1;
            level.setBlock(new BlockPos(x, y, z), nodes[i], Block.UPDATE_ALL);
        }
    }

    /** Seeds the Nexus's own input slots with enough resources to bankroll the first Probes. */
    private static void seedNexus(NexusBlockEntity nexus) {
        nexus.setItem(0, new ItemStack(Items.OAK_LOG, 64));
        nexus.setItem(1, new ItemStack(Items.OAK_LOG, STARTING_LOGS - 64));
        nexus.setItem(2, new ItemStack(Items.COBBLESTONE, 64));
        nexus.setItem(3, new ItemStack(Items.COBBLESTONE, STARTING_COBBLESTONE - 64));
    }
}
