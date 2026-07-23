package net.bitflora.asteriskcraft.command;

import net.bitflora.asteriskcraft.AsteriskCraft;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * One control-group hotkey press, captured client-side and sent to the server for resolution.
 * {@code slot} is 1-9 (numpad row); {@code save} is {@code true} for Ctrl+numpad (save the
 * current selection into the slot) or {@code false} for Shift+numpad (recall the slot into the
 * current selection). Resolved by {@link ControlGroupResolver}.
 */
public record ControlGroupPacket(int slot, boolean save) implements CustomPacketPayload {

    public static final Type<ControlGroupPacket> TYPE = new Type<>(AsteriskCraft.id("control_group"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ControlGroupPacket> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, ControlGroupPacket::slot,
            ByteBufCodecs.BOOL, ControlGroupPacket::save,
            ControlGroupPacket::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
