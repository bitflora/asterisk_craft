package net.bitflora.asteriskcraft.command.client;

import net.bitflora.asteriskcraft.AsteriskCraft;
import net.bitflora.asteriskcraft.command.ControlGroupPacket;
import net.bitflora.asteriskcraft.command.CursorItem;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import org.lwjgl.glfw.GLFW;

/**
 * Captures Ctrl+numpad (save) / Shift+numpad (recall) control-group hotkeys while the Cursor is
 * held and forwards them to the server as a {@link ControlGroupPacket}. Uses the numpad row
 * (not the top-row 1-9) specifically to avoid colliding with vanilla hotbar-slot switching, which
 * is bound to the top row by default and polled independently of this (non-cancellable) event —
 * the numpad carries no vanilla binding, so there's nothing to suppress. Client-dist only — never
 * loaded on a dedicated server.
 */
@EventBusSubscriber(modid = AsteriskCraft.MODID, value = Dist.CLIENT)
public final class ControlGroupInputHandler {
    /** GLFW modifier bits (see {@code net.minecraft.client.gui.screens.Screen}). */
    private static final int MOD_SHIFT = 0x0001;
    private static final int MOD_CONTROL = 0x0002;

    private ControlGroupInputHandler() {
    }

    @SubscribeEvent
    static void onKey(InputEvent.Key event) {
        if (event.getAction() != GLFW.GLFW_PRESS) {
            return;
        }
        int slot = slotForKey(event.getKey());
        if (slot < 0) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || mc.screen != null) {
            return;
        }
        if (!(mc.player.getMainHandItem().getItem() instanceof CursorItem)) {
            return;
        }
        int modifiers = event.getModifiers();
        boolean ctrl = (modifiers & MOD_CONTROL) != 0;
        boolean shift = (modifiers & MOD_SHIFT) != 0;
        if (ctrl == shift) {
            return; // neither or both held: no defined behavior, ignore
        }
        ClientPacketDistributor.sendToServer(new ControlGroupPacket(slot, ctrl));
    }

    /** GLFW_KEY_KP_1..GLFW_KEY_KP_9 -> slot 1-9; -1 for anything else. Pure, unit-testable. */
    static int slotForKey(int key) {
        if (key < GLFW.GLFW_KEY_KP_1 || key > GLFW.GLFW_KEY_KP_9) {
            return -1;
        }
        return key - GLFW.GLFW_KEY_KP_1 + 1;
    }
}
