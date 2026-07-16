package net.bitflora.asteriskcraft.command;

import io.netty.buffer.ByteBuf;
import net.bitflora.asteriskcraft.AsteriskCraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * One command click, captured client-side (the only side that knows whether Ctrl is held) and
 * sent to the server for resolution. {@code button} is a GLFW mouse code (0 = left/select,
 * 1 = right/order); {@code modifiers} is the raw GLFW modifier bitmask (Ctrl/Shift). The
 * client classifies what was under the crosshair into {@link HitKind}; for BLOCK/MISS the
 * chosen destination is carried in {@code pos} (for MISS, the block at the far end of view),
 * and for ENTITY the target is {@code entityId}. Resolved by {@link CommandInputResolver}.
 */
public record CommandInputPacket(int button, int modifiers, HitKind kind, int entityId, BlockPos pos)
        implements CustomPacketPayload {

    public enum HitKind {
        ENTITY, BLOCK, MISS;

        public static HitKind byId(int id) {
            return values()[id];
        }
    }

    public static final Type<CommandInputPacket> TYPE = new Type<>(AsteriskCraft.id("command_input"));

    private static final StreamCodec<ByteBuf, HitKind> HIT_KIND_CODEC =
            ByteBufCodecs.VAR_INT.map(HitKind::byId, HitKind::ordinal);

    public static final StreamCodec<RegistryFriendlyByteBuf, CommandInputPacket> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, CommandInputPacket::button,
            ByteBufCodecs.VAR_INT, CommandInputPacket::modifiers,
            HIT_KIND_CODEC, CommandInputPacket::kind,
            ByteBufCodecs.VAR_INT, CommandInputPacket::entityId,
            BlockPos.STREAM_CODEC, CommandInputPacket::pos,
            CommandInputPacket::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
