package net.bitflora.asteriskcraft.command;

import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pure-logic guard for control-group storage. Resolving slot contents to/from live {@code Mob}s
 * ({@link ControlGroupResolver}) needs a live {@code ServerLevel}, which the JUnit bootstrap does
 * not provide — that behavior is exercised via runClient, same as {@link PlayerSelection} and
 * {@link CommandInputResolver}.
 */
class ControlGroupsTest {

    private static ControlGroups roundTrip(ControlGroups groups) {
        Tag tag = ControlGroups.CODEC.encodeStart(NbtOps.INSTANCE, groups).getOrThrow();
        return ControlGroups.CODEC.parse(NbtOps.INSTANCE, tag).getOrThrow();
    }

    @Test
    void unsavedSlotReturnsEmpty() {
        ControlGroups groups = new ControlGroups();
        assertTrue(groups.get(1).isEmpty());
    }

    @Test
    void saveThenGetRoundTrips() {
        ControlGroups groups = new ControlGroups();
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        groups.save(3, List.of(a, b));
        assertEquals(List.of(a, b), groups.get(3));
    }

    @Test
    void saveOverwritesPreviousContents() {
        ControlGroups groups = new ControlGroups();
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        groups.save(5, List.of(a));
        groups.save(5, List.of(b));
        assertEquals(List.of(b), groups.get(5));
    }

    @Test
    void savingEmptyCollectionIsAllowed() {
        ControlGroups groups = new ControlGroups();
        groups.save(2, List.of());
        assertTrue(groups.get(2).isEmpty());
    }

    @Test
    void codecRoundTripsMultipleSlots() {
        ControlGroups groups = new ControlGroups();
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        UUID c = UUID.randomUUID();
        groups.save(1, List.of(a, b));
        groups.save(9, List.of(c));

        ControlGroups restored = roundTrip(groups);
        assertEquals(List.of(a, b), restored.get(1));
        assertEquals(List.of(c), restored.get(9));
        assertTrue(restored.get(4).isEmpty());
    }

    @Test
    void codecRoundTripsEmpty() {
        ControlGroups restored = roundTrip(new ControlGroups());
        assertTrue(restored.get(1).isEmpty());
    }
}
