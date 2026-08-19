package net.bitflora.asteriskcraft.command;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Builds the {@link UnitGroupSnapshot} a player's client needs to label the overlay, and pushes it
 * when it has actually changed. Membership is resolved fresh every time rather than tracked through
 * death events: a group's units can die anywhere, at any time, and a periodic rebuild is both
 * cheaper to reason about and impossible to leak stale entries through.
 */
public final class UnitGroupSync {

    private UnitGroupSync() {
    }

    /** Recompute this player's snapshot and send it if it differs from the last one they were sent. */
    public static void sync(ServerPlayer player) {
        UnitGroupSnapshot snapshot = snapshotFor(player);
        if (snapshot.equals(player.getData(CommandAttachments.LAST_SYNCED_GROUPS))) {
            return;
        }
        player.setData(CommandAttachments.LAST_SYNCED_GROUPS, snapshot);
        PacketDistributor.sendToPlayer(player, new UnitGroupSyncPacket(snapshot));
    }

    /** Send unconditionally — for a fresh client that has no cache yet (login). */
    public static void forceSync(ServerPlayer player) {
        UnitGroupSnapshot snapshot = snapshotFor(player);
        player.setData(CommandAttachments.LAST_SYNCED_GROUPS, snapshot);
        PacketDistributor.sendToPlayer(player, new UnitGroupSyncPacket(snapshot));
    }

    private static UnitGroupSnapshot snapshotFor(ServerPlayer player) {
        ServerLevel level = (ServerLevel) player.level();
        UnitGroups groups = CommandAttachments.unitGroups(player);
        List<UnitGroupSnapshot.Slot> slots = new ArrayList<>(UnitGroups.SLOTS);
        for (int slot = 0; slot < UnitGroups.SLOTS; slot++) {
            slots.add(slotFor(level, groups.get(slot)));
        }
        return new UnitGroupSnapshot(List.copyOf(slots));
    }

    /** Aggregate live members by type, keeping first-seen (selection) order. */
    private static UnitGroupSnapshot.Slot slotFor(ServerLevel level, List<UUID> ids) {
        if (ids.isEmpty()) {
            return UnitGroupSnapshot.Slot.EMPTY;
        }
        Map<EntityType<?>, Integer> counts = new LinkedHashMap<>();
        for (UUID id : ids) {
            if (level.getEntity(id) instanceof Mob unit && unit.isAlive()) {
                counts.merge(unit.getType(), 1, Integer::sum);
            }
        }
        List<UnitGroupSnapshot.Entry> entries = counts.entrySet().stream()
                .map(e -> new UnitGroupSnapshot.Entry(e.getKey(), e.getValue()))
                .toList();
        return entries.isEmpty() ? UnitGroupSnapshot.Slot.EMPTY : new UnitGroupSnapshot.Slot(entries);
    }
}
