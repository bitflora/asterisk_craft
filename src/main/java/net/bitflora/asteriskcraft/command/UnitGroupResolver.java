package net.bitflora.asteriskcraft.command;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Mob;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Server-side interpretation of a {@link UnitGroupPacket}: the overlay's assign and recall halves.
 *
 * <p>The rule the client can't be trusted with lives here — picking a slot from the <b>select</b>
 * overlay recalls it, <em>unless</em> the slot is empty, in which case it assigns instead (so V can
 * fill a blank slot without switching keys). Whether a slot is empty is read from the live groups,
 * not from the packet.
 */
public final class UnitGroupResolver {

    private UnitGroupResolver() {
    }

    public static void handle(UnitGroupPacket packet, ServerPlayer player) {
        // Re-validate the Cursor is actually held — the packet is client-asserted.
        if (!(player.getMainHandItem().getItem() instanceof CursorItem)) {
            return;
        }
        if (!UnitGroups.isValidSlot(packet.slot())) {
            return;
        }
        ServerLevel level = (ServerLevel) player.level();
        PlayerSelection selection = CommandAttachments.selection(player);
        UnitGroups groups = CommandAttachments.unitGroups(player);

        if (packet.recallMode() && !groups.isEmpty(packet.slot())) {
            recall(level, selection, groups, packet.slot());
        } else {
            assign(level, selection, groups, packet.slot());
        }
        UnitGroupSync.sync(player);
    }

    /**
     * Snapshot the current selection into the slot. An empty selection is deliberately a no-op
     * rather than a slot wipe: a mis-aimed click should never cost the player a group they spent a
     * fight building.
     */
    private static void assign(ServerLevel level, PlayerSelection selection, UnitGroups groups, int slot) {
        List<Mob> units = selection.pruneAndGet(level);
        if (units.isEmpty()) {
            return;
        }
        groups.save(slot, units.stream().map(Mob::getUUID).toList());
    }

    /** Replace the current selection with the slot's still-living members. */
    private static void recall(ServerLevel level, PlayerSelection selection, UnitGroups groups, int slot) {
        List<UUID> stored = groups.get(slot);
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
            return; // whole group died — no-op, don't clear the current selection
        }
        selection.replaceWith(level, live);
    }
}
