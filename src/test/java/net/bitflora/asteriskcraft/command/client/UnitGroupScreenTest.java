package net.bitflora.asteriskcraft.command.client;

import net.bitflora.asteriskcraft.command.UnitGroups;
import org.junit.jupiter.api.Test;
import org.lwjgl.glfw.GLFW;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The pure pieces of {@link UnitGroupScreen}: grid geometry, the digit each slot is labelled with,
 * and the number-key mapping. Drawing and the packet dispatch touch {@code Minecraft.getInstance()}
 * and a GL context, so they stay covered by the manual runClient verification script instead — the
 * same split {@code ModelBakeTest} and the old input-handler test used.
 */
class UnitGroupScreenTest {

    @Test
    void twoColumnsOfFiveCoverEverySlot() {
        assertEquals(2, UnitGroupScreen.COLUMNS);
        assertEquals(5, UnitGroupScreen.ROWS);
        assertEquals(UnitGroups.SLOTS, UnitGroupScreen.COLUMNS * UnitGroupScreen.ROWS);
    }

    @Test
    void layoutIsColumnMajor() {
        // Slots 0-4 stack down the left column...
        assertEquals(0, UnitGroupScreen.slotBounds(0).x());
        assertEquals(0, UnitGroupScreen.slotBounds(0).y());
        assertEquals(0, UnitGroupScreen.slotBounds(4).x());
        assertEquals(4 * (UnitGroupScreen.BLOCK_H + UnitGroupScreen.GAP), UnitGroupScreen.slotBounds(4).y());
        // ...and 5-9 down the right one, starting back at the top.
        assertEquals(UnitGroupScreen.BLOCK_W + UnitGroupScreen.GAP, UnitGroupScreen.slotBounds(5).x());
        assertEquals(0, UnitGroupScreen.slotBounds(5).y());
        assertEquals(4 * (UnitGroupScreen.BLOCK_H + UnitGroupScreen.GAP), UnitGroupScreen.slotBounds(9).y());
    }

    @Test
    void noTwoBlocksOverlap() {
        for (int a = 0; a < UnitGroups.SLOTS; a++) {
            for (int b = a + 1; b < UnitGroups.SLOTS; b++) {
                assertTrue(disjoint(UnitGroupScreen.slotBounds(a), UnitGroupScreen.slotBounds(b)),
                        "slots " + a + " and " + b + " overlap");
            }
        }
    }

    private static boolean disjoint(UnitGroupScreen.Rect a, UnitGroupScreen.Rect b) {
        return a.x() + a.width() <= b.x() || b.x() + b.width() <= a.x()
                || a.y() + a.height() <= b.y() || b.y() + b.height() <= a.y();
    }

    @Test
    void slotsOutsideTheRangeAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> UnitGroupScreen.slotBounds(-1));
        assertThrows(IllegalArgumentException.class, () -> UnitGroupScreen.slotBounds(UnitGroups.SLOTS));
    }

    @Test
    void blocksAreNumberedOneThroughNineThenZero() {
        assertEquals("1", UnitGroupScreen.displayDigit(0));
        assertEquals("9", UnitGroupScreen.displayDigit(8));
        assertEquals("0", UnitGroupScreen.displayDigit(9));
        Set<String> digits = new HashSet<>();
        for (int slot = 0; slot < UnitGroups.SLOTS; slot++) {
            digits.add(UnitGroupScreen.displayDigit(slot));
        }
        assertEquals(UnitGroups.SLOTS, digits.size());
    }

    @Test
    void numberKeysPickTheSlotTheyLabel() {
        for (int slot = 0; slot < UnitGroups.SLOTS; slot++) {
            int digit = Integer.parseInt(UnitGroupScreen.displayDigit(slot));
            int numberRowKey = digit == 0 ? GLFW.GLFW_KEY_0 : GLFW.GLFW_KEY_0 + digit;
            int numpadKey = digit == 0 ? GLFW.GLFW_KEY_KP_0 : GLFW.GLFW_KEY_KP_0 + digit;
            assertEquals(slot, UnitGroupScreen.slotForKey(numberRowKey), "number row " + digit);
            assertEquals(slot, UnitGroupScreen.slotForKey(numpadKey), "numpad " + digit);
        }
    }

    @Test
    void otherKeysPickNothing() {
        assertEquals(-1, UnitGroupScreen.slotForKey(GLFW.GLFW_KEY_A));
        assertEquals(-1, UnitGroupScreen.slotForKey(GLFW.GLFW_KEY_ESCAPE));
        assertEquals(-1, UnitGroupScreen.slotForKey(GLFW.GLFW_KEY_F3));
    }
}
