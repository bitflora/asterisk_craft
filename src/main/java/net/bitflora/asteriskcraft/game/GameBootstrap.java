package net.bitflora.asteriskcraft.game;

import net.bitflora.asteriskcraft.AsteriskCraft;
import net.bitflora.asteriskcraft.building.BuildingLayouts;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

/**
 * Places the player's Nexus and starting chest the first time someone joins a new
 * world. We do this on first join (rather than at server start) because the player's
 * chunk is guaranteed loaded with a settled surface height — placing at server start
 * can land the structure at the world floor before terrain is ready. From the player's
 * point of view the world still "starts" with a Nexus already standing (R1).
 */
@EventBusSubscriber(modid = AsteriskCraft.MODID)
public final class GameBootstrap {
    private static final int NEXUS_OFFSET = 6;
    private static final int STARTING_LOGS = 100;
    private static final int STARTING_COBBLESTONE = 100;

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
        placeStartingChest(level, origin.offset(3, 1, 0));

        BlockPos core = origin.offset(0, 2, 0);
        level.setData(GameAttachments.NEXUS_POS, core);
        level.setData(GameAttachments.BOOTSTRAPPED, true);

        // The Command Crystal enables unit select/order mode while held (R5).
        player.getInventory().add(new ItemStack(AsteriskCraft.COMMAND_CRYSTAL.get()));

        AsteriskCraft.LOGGER.info("AsteriskCraft: placed Nexus core at {}", core);
    }

    private static void placeStartingChest(ServerLevel level, BlockPos pos) {
        level.setBlock(pos.below(), Blocks.SMOOTH_QUARTZ.defaultBlockState(), Block.UPDATE_ALL);
        level.setBlock(pos, Blocks.CHEST.defaultBlockState(), Block.UPDATE_ALL);
        if (level.getBlockEntity(pos) instanceof ChestBlockEntity chest) {
            chest.setItem(0, new ItemStack(Items.OAK_LOG, 64));
            chest.setItem(1, new ItemStack(Items.OAK_LOG, STARTING_LOGS - 64));
            chest.setItem(2, new ItemStack(Items.COBBLESTONE, 64));
            chest.setItem(3, new ItemStack(Items.COBBLESTONE, STARTING_COBBLESTONE - 64));
            chest.setItem(4, new ItemStack(AsteriskCraft.GATEWAY_KIT.get()));
        }
    }
}
