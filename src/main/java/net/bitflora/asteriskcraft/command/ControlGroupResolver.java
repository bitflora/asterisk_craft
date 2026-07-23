package net.bitflora.asteriskcraft.command;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Mob;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Server-side interpretation of a {@link ControlGroupPacket}: Ctrl+numpad snapshots the current
 * {@link PlayerSelection} into a {@link ControlGroups} slot; Shift+numpad recalls a slot back
 * into the current selection.
 */
public final class ControlGroupResolver {

    private ControlGroupResolver() {
    }

    public static void handle(ControlGroupPacket packet, ServerPlayer player) {
        // Re-validate the Cursor is actually held — the packet is client-asserted.
        if (!(player.getMainHandItem().getItem() instanceof CursorItem)) {
            return;
        }
        ServerLevel level = (ServerLevel) player.level();
        PlayerSelection selection = CommandAttachments.selection(player);
        ControlGroups groups = CommandAttachments.controlGroups(player);

        if (packet.save()) {
            handleSave(level, selection, groups, packet.slot());
        } else {
            handleRecall(level, selection, groups, packet.slot());
        }
    }

    private static void handleSave(ServerLevel level, PlayerSelection selection, ControlGroups groups, int slot) {
        List<Mob> units = selection.pruneAndGet(level);
        groups.save(slot, units.stream().map(Mob::getUUID).toList());
    }

    private static void handleRecall(ServerLevel level, PlayerSelection selection, ControlGroups groups, int slot) {
        List<UUID> stored = groups.get(slot);
        if (stored.isEmpty()) {
            return; // never saved, or saved empty — no-op
        }
        List<Mob> live = new ArrayList<>();
        List<UUID> liveIds = new ArrayList<>();
        for (UUID id : stored) {
            if (level.getEntity(id) instanceof Mob unit && unit.isAlive()) {
                live.add(unit);
                liveIds.add(id);
            }
        }
        if (liveIds.size() != stored.size()) {
            groups.save(slot, liveIds); // write back pruned membership
        }
        if (live.isEmpty()) {
            return; // whole group died — no-op, don't clear current selection
        }
        selection.replaceWith(level, live);
    }
}
