package net.bitflora.asteriskcraft.command;

import net.bitflora.asteriskcraft.AsteriskCraft;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * The player's current unit-group composition, pushed to their client so the overlay can label each
 * block. Sent by {@link UnitGroupSync} on login, whenever a group changes, and on a slow sweep that
 * keeps labels honest as grouped units die. The mod's only server-to-client payload.
 */
public record UnitGroupSyncPacket(UnitGroupSnapshot snapshot) implements CustomPacketPayload {

    public static final Type<UnitGroupSyncPacket> TYPE = new Type<>(AsteriskCraft.id("unit_group_sync"));

    public static final StreamCodec<RegistryFriendlyByteBuf, UnitGroupSyncPacket> STREAM_CODEC =
            UnitGroupSnapshot.STREAM_CODEC.map(UnitGroupSyncPacket::new, UnitGroupSyncPacket::snapshot);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
