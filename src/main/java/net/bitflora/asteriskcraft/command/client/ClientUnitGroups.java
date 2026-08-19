package net.bitflora.asteriskcraft.command.client;

import net.bitflora.asteriskcraft.command.UnitGroupSnapshot;
import net.bitflora.asteriskcraft.command.UnitGroupSyncPacket;

/**
 * The client's copy of the player's unit-group composition, kept current by
 * {@link UnitGroupSyncPacket}. {@link UnitGroupScreen} reads it to label each block; nothing else
 * on the client knows what a group holds.
 *
 * <p>A plain static field, following {@code client/KitPlacementPreview} — only ever touched on the
 * render/client thread. Client-dist only: this class is the reason the sync payload's handler
 * indirects through a lambda body rather than naming the type at registration.
 */
public final class ClientUnitGroups {
    private static UnitGroupSnapshot snapshot = UnitGroupSnapshot.EMPTY;

    private ClientUnitGroups() {
    }

    public static void accept(UnitGroupSyncPacket packet) {
        snapshot = packet.snapshot();
    }

    public static UnitGroupSnapshot snapshot() {
        return snapshot;
    }
}
