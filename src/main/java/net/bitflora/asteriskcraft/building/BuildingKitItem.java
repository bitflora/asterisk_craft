package net.bitflora.asteriskcraft.building;

import net.bitflora.asteriskcraft.faction.Faction;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Map;
import java.util.function.Supplier;

/**
 * A generic warp-in kit: right-click the ground to place a building's full layout
 * immediately, leaving the core block entity to run its own warp-in countdown.
 * Reusable by later kits (Photon Cannon, etc.) that share this place-then-warm-up shape.
 */
public class BuildingKitItem extends Item {
    private final Supplier<Map<BlockPos, BlockState>> layout;
    private final BlockPos coreOffset;

    public BuildingKitItem(Properties properties, Supplier<Map<BlockPos, BlockState>> layout, BlockPos coreOffset) {
        super(properties);
        this.layout = layout;
        this.coreOffset = coreOffset;
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        if (!(level instanceof ServerLevel serverLevel)) {
            return InteractionResult.SUCCESS;
        }
        Player player = context.getPlayer();
        if (player == null) {
            return InteractionResult.FAIL;
        }

        BlockPos origin = context.getClickedPos().relative(context.getClickedFace());
        Map<BlockPos, BlockState> structure = this.layout.get();
        if (!isFootprintClear(serverLevel, origin, structure)) {
            overlay(player, Component.translatable("message.asteriskcraft.kit.blocked"));
            return InteractionResult.FAIL;
        }

        BuildingLayouts.place(serverLevel, origin, structure);
        BlockPos corePos = origin.offset(this.coreOffset.getX(), this.coreOffset.getY(), this.coreOffset.getZ());
        // Every kit is placed by the player for now, so it's always PROTOSS; a real
        // player->faction registry arrives with race selection/PvP (see docs/shaping.md V5).
        if (serverLevel.getBlockEntity(corePos) instanceof GatewayBlockEntity gateway) {
            gateway.setFaction(Faction.PROTOSS);
        }
        context.getItemInHand().shrink(1);
        overlay(player, Component.translatable("message.asteriskcraft.kit.warping"));
        return InteractionResult.SUCCESS;
    }

    private static boolean isFootprintClear(Level level, BlockPos origin, Map<BlockPos, BlockState> layout) {
        for (BlockPos offset : layout.keySet()) {
            BlockState current = level.getBlockState(origin.offset(offset.getX(), offset.getY(), offset.getZ()));
            if (!current.isAir() && !current.canBeReplaced()) {
                return false;
            }
        }
        return true;
    }

    private static void overlay(Player player, Component message) {
        if (player instanceof ServerPlayer serverPlayer) {
            serverPlayer.sendSystemMessage(message, true);
        }
    }
}
