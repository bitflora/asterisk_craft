package net.bitflora.asteriskcraft.building;

import net.bitflora.asteriskcraft.faction.Faction;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;

import java.util.function.Supplier;

/**
 * A generic warp-in kit: right-click the ground to stamp a building's structure template
 * immediately, leaving the core block entity to run its own warp-in countdown.
 * Reusable by later kits (Photon Cannon, etc.) that share this place-then-warm-up shape.
 */
public class BuildingKitItem extends Item {
    private final Identifier template;
    // A supplier, not the Block itself: kits are registered alongside the blocks they place, so
    // the block isn't resolvable yet at construction time.
    private final Supplier<? extends Block> coreBlock;

    public BuildingKitItem(Properties properties, Identifier template, Supplier<? extends Block> coreBlock) {
        super(properties);
        this.template = template;
        this.coreBlock = coreBlock;
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
        if (!BuildingTemplates.isSiteClear(serverLevel, origin, this.template)) {
            overlay(player, Component.translatable("message.asteriskcraft.kit.blocked"));
            return InteractionResult.FAIL;
        }

        // No support fill: the template's own stonework is the base, so a gap under a sloping
        // edge is left as a gap rather than plugged with a foreign block.
        BlockPos corePos = BuildingTemplates.place(serverLevel, origin, this.template, this.coreBlock.get(), null);
        if (corePos == null) {
            // The template failed to load, so nothing was stamped — don't eat the kit for it.
            overlay(player, Component.translatable("message.asteriskcraft.kit.blocked"));
            return InteractionResult.FAIL;
        }
        // Every kit is placed by the player for now, so it's always PROTOSS; a real
        // player->faction registry arrives with race selection/PvP (see docs/shaping.md V5).
        if (serverLevel.getBlockEntity(corePos) instanceof WarpInBuilding building) {
            building.setFaction(Faction.PROTOSS);
        }
        context.getItemInHand().shrink(1);
        overlay(player, Component.translatable("message.asteriskcraft.kit.warping"));
        return InteractionResult.SUCCESS;
    }

    private static void overlay(Player player, Component message) {
        if (player instanceof ServerPlayer serverPlayer) {
            serverPlayer.sendSystemMessage(message, true);
        }
    }
}
