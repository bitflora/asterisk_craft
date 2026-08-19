package net.bitflora.asteriskcraft.command;

import com.mojang.serialization.JsonOps;
import com.google.gson.JsonElement;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pure storage tests for {@link UnitGroups}: slot range, save/get, and codec round-trip. The parts
 * that need a live world — resolving UUIDs to units, pruning the dead — belong to
 * {@link UnitGroupResolver}/{@code UnitGroupSync} and are covered by the manual runClient script.
 */
class UnitGroupsTest {
    private static final UUID A = UUID.fromString("00000000-0000-0000-0000-00000000000a");
    private static final UUID B = UUID.fromString("00000000-0000-0000-0000-00000000000b");

    @Test
    void tenSlotsMatchTheTwoByFiveOverlay() {
        assertEquals(10, UnitGroups.SLOTS);
        assertTrue(UnitGroups.isValidSlot(0));
        assertTrue(UnitGroups.isValidSlot(9));
        assertFalse(UnitGroups.isValidSlot(-1));
        assertFalse(UnitGroups.isValidSlot(10));
    }

    @Test
    void anUntouchedSlotIsEmpty() {
        UnitGroups groups = new UnitGroups();
        assertTrue(groups.isEmpty(3));
        assertEquals(List.of(), groups.get(3));
    }

    @Test
    void savePreservesOrderAndOverwrites() {
        UnitGroups groups = new UnitGroups();
        groups.save(0, List.of(A, B));
        assertEquals(List.of(A, B), groups.get(0));
        assertFalse(groups.isEmpty(0));

        groups.save(0, List.of(B));
        assertEquals(List.of(B), groups.get(0));
    }

    @Test
    void savingAnEmptyCollectionClearsTheSlot() {
        UnitGroups groups = new UnitGroups();
        groups.save(7, List.of(A));
        groups.save(7, List.of());
        assertTrue(groups.isEmpty(7));
    }

    @Test
    void slotsOutsideTheRangeAreRejected() {
        UnitGroups groups = new UnitGroups();
        assertThrows(IllegalArgumentException.class, () -> groups.get(10));
        assertThrows(IllegalArgumentException.class, () -> groups.save(-1, List.of(A)));
    }

    @Test
    void codecRoundTripsSparseGroups() {
        UnitGroups groups = new UnitGroups();
        groups.save(0, List.of(A, B));
        groups.save(9, List.of(B));

        JsonElement json = UnitGroups.CODEC.encodeStart(JsonOps.INSTANCE, groups).getOrThrow();
        UnitGroups restored = UnitGroups.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow();

        assertEquals(List.of(A, B), restored.get(0));
        assertEquals(List.of(B), restored.get(9));
        assertTrue(restored.isEmpty(4));
    }
}
