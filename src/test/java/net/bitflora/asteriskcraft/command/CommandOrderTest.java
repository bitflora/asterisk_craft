package net.bitflora.asteriskcraft.command;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pure-logic guard for the command order model. The selection ops ({@link PlayerSelection}) and
 * the click resolver ({@link CommandInputResolver}) both need a live {@code ServerLevel} (entity
 * lookup, block tags, glow flags) which the JUnit bootstrap does not provide — that behavior is
 * exercised via runClient, per the R5 plan's verification section.
 */
class CommandOrderTest {

    private static CommandOrder roundTrip(CommandOrder order) {
        Tag tag = CommandOrder.CODEC.encodeStart(NbtOps.INSTANCE, order).getOrThrow();
        return CommandOrder.CODEC.parse(NbtOps.INSTANCE, tag).getOrThrow();
    }

    @Test
    void factoriesSetKindAndPayload() {
        CommandOrder move = CommandOrder.move(new BlockPos(1, 2, 3));
        assertEquals(CommandOrder.Kind.MOVE, move.kind());
        assertEquals(new BlockPos(1, 2, 3), move.pos().orElseThrow());
        assertTrue(move.target().isEmpty());

        CommandOrder mine = CommandOrder.mine(new BlockPos(4, 5, 6));
        assertEquals(CommandOrder.Kind.MINE, mine.kind());
        assertEquals(new BlockPos(4, 5, 6), mine.pos().orElseThrow());
        assertTrue(mine.target().isEmpty());

        UUID id = UUID.randomUUID();
        CommandOrder attack = CommandOrder.attack(id);
        assertEquals(CommandOrder.Kind.ATTACK, attack.kind());
        assertEquals(id, attack.target().orElseThrow());
        assertTrue(attack.pos().isEmpty());

        UUID transport = UUID.randomUUID();
        CommandOrder load = CommandOrder.load(transport);
        assertEquals(CommandOrder.Kind.LOAD, load.kind());
        assertEquals(transport, load.target().orElseThrow());
        assertTrue(load.pos().isEmpty(), "a transport is aimed at as an entity, never as a block");
    }

    @Test
    void codecRoundTrips() {
        assertEquals(CommandOrder.NONE, roundTrip(CommandOrder.NONE));
        assertEquals(CommandOrder.move(new BlockPos(10, -3, 7)), roundTrip(CommandOrder.move(new BlockPos(10, -3, 7))));
        assertEquals(CommandOrder.mine(new BlockPos(0, 64, 0)), roundTrip(CommandOrder.mine(new BlockPos(0, 64, 0))));
        CommandOrder attack = CommandOrder.attack(UUID.randomUUID());
        assertEquals(attack, roundTrip(attack));
        CommandOrder load = CommandOrder.load(UUID.randomUUID());
        assertEquals(load, roundTrip(load));
    }

    @Test
    void noneIsTheEmptyDefault() {
        assertTrue(CommandOrder.NONE.isNone());
        assertEquals(CommandOrder.Kind.NONE, CommandOrder.NONE.kind());
        assertTrue(CommandOrder.NONE.pos().isEmpty());
        assertTrue(CommandOrder.NONE.target().isEmpty());
    }

    @Test
    void kindSerializedNamesAreStable() {
        // Persisted on units via the command_order attachment — changing these breaks saves.
        assertEquals("none", CommandOrder.Kind.NONE.getSerializedName());
        assertEquals("move", CommandOrder.Kind.MOVE.getSerializedName());
        assertEquals("attack", CommandOrder.Kind.ATTACK.getSerializedName());
        assertEquals("mine", CommandOrder.Kind.MINE.getSerializedName());
        assertEquals("guard", CommandOrder.Kind.GUARD.getSerializedName());
        assertEquals("load", CommandOrder.Kind.LOAD.getSerializedName());
    }
}
