package net.bitflora.asteriskcraft.command.client;

import org.junit.jupiter.api.Test;
import org.lwjgl.glfw.GLFW;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Pure-logic guard for the numpad-to-slot mapping. The modifier gating and packet dispatch in
 * {@link ControlGroupInputHandler#onKey} touch {@code Minecraft.getInstance()} and the live event
 * bus, so they stay covered by the manual runClient verification script instead.
 */
class ControlGroupInputHandlerTest {

    @Test
    void numpadKeysMapToSlotsOneThroughNine() {
        for (int i = 1; i <= 9; i++) {
            int key = GLFW.GLFW_KEY_KP_1 + (i - 1);
            assertEquals(i, ControlGroupInputHandler.slotForKey(key));
        }
    }

    @Test
    void topRowNumberKeyIsNotMapped() {
        assertEquals(-1, ControlGroupInputHandler.slotForKey(GLFW.GLFW_KEY_1));
    }

    @Test
    void unrelatedKeyIsNotMapped() {
        assertEquals(-1, ControlGroupInputHandler.slotForKey(GLFW.GLFW_KEY_SPACE));
    }
}
