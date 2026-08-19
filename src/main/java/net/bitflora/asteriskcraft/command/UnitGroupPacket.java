package net.bitflora.asteriskcraft.command;

import net.bitflora.asteriskcraft.AsteriskCraft;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * One slot picked out of the unit-group overlay. {@code slot} is a 0-based index into {@link
 * UnitGroups}; {@code recallMode} says which key opened the overlay — the select key (V) rather
 * than the assign key (C).
 *
 * <p>Note it carries the <em>mode</em>, not the decision. "Selecting an empty slot from the recall
 * overlay assigns to it instead" is resolved server-side in {@link UnitGroupResolver}, against the
 * real group contents, so a client cache that has gone a second stale can't overwrite a group the
 * player was trying to recall.
 */
public record UnitGroupPacket(int slot, boolean recallMode) implements CustomPacketPayload {

    public static final Type<UnitGroupPacket> TYPE = new Type<>(AsteriskCraft.id("unit_group"));

    public static final StreamCodec<RegistryFriendlyByteBuf, UnitGroupPacket> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, UnitGroupPacket::slot,
            ByteBufCodecs.BOOL, UnitGroupPacket::recallMode,
            UnitGroupPacket::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
