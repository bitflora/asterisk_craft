package net.bitflora.asteriskcraft.command;

import net.bitflora.asteriskcraft.AsteriskCraft;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

import java.util.function.Consumer;

/**
 * A held marker item that enables command mode. It carries no server-side use logic itself —
 * while it is in the main hand, the client input handler reinterprets left/right clicks as
 * select/order and forwards them as a {@link CommandInputPacket}. See
 * {@code command/client/CommandInputHandler} and {@link CommandInputResolver}.
 *
 * <p>It's the player's core mode-switching tool, so it never leaves them: {@link #onDrops}
 * strips it out of death drops and {@link #onClone} re-grants it on respawn (skipped when the
 * keepInventory game rule already carried it over, to avoid duplicating).
 */
@EventBusSubscriber(modid = AsteriskCraft.MODID)
public class CursorItem extends Item {
    public CursorItem(Properties properties) {
        super(properties);
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, TooltipDisplay display,
                                Consumer<Component> adder, TooltipFlag flag) {
        adder.accept(Component.translatable("item.asteriskcraft.cursor.tip.select").withStyle(ChatFormatting.GRAY));
        adder.accept(Component.translatable("item.asteriskcraft.cursor.tip.group").withStyle(ChatFormatting.GRAY));
        adder.accept(Component.translatable("item.asteriskcraft.cursor.tip.order").withStyle(ChatFormatting.GRAY));
    }

    @SubscribeEvent
    public static void onDrops(LivingDropsEvent event) {
        if (event.getEntity() instanceof ServerPlayer) {
            event.getDrops().removeIf(itemEntity -> itemEntity.getItem().is(AsteriskCraft.CURSOR.get()));
        }
    }

    @SubscribeEvent
    public static void onClone(PlayerEvent.Clone event) {
        if (!event.isWasDeath() || !(event.getEntity() instanceof ServerPlayer newPlayer)) {
            return;
        }
        boolean alreadyCarried = newPlayer.getInventory().contains(stack -> stack.is(AsteriskCraft.CURSOR.get()));
        if (!alreadyCarried) {
            newPlayer.getInventory().add(new ItemStack(AsteriskCraft.CURSOR.get()));
        }
    }
}
