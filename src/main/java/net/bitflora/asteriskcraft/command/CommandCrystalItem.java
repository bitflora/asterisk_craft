package net.bitflora.asteriskcraft.command;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;

import java.util.function.Consumer;

/**
 * A held marker item that enables command mode. It carries no server-side use logic itself —
 * while it is in the main hand, the client input handler reinterprets left/right clicks as
 * select/order and forwards them as a {@link CommandInputPacket}. See
 * {@code command/client/CommandInputHandler} and {@link CommandInputResolver}.
 */
public class CommandCrystalItem extends Item {
    public CommandCrystalItem(Properties properties) {
        super(properties);
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, TooltipDisplay display,
                                Consumer<Component> adder, TooltipFlag flag) {
        adder.accept(Component.translatable("item.asteriskcraft.command_crystal.tip.select").withStyle(ChatFormatting.GRAY));
        adder.accept(Component.translatable("item.asteriskcraft.command_crystal.tip.group").withStyle(ChatFormatting.GRAY));
        adder.accept(Component.translatable("item.asteriskcraft.command_crystal.tip.order").withStyle(ChatFormatting.GRAY));
    }
}
