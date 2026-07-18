package net.bitflora.asteriskcraft.faction;

import com.mojang.serialization.Codec;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.StringRepresentable;

/**
 * The sides of a AsteriskCraft match. Kept faction-generic so later versions can add
 * race selection and PvP: nothing outside the bootstrap should assume the player
 * is PROTOSS.
 */
public enum Faction implements StringRepresentable {
    NEUTRAL("neutral"),
    PROTOSS("protoss"),
    ZERG("zerg");

    public static final Codec<Faction> CODEC = StringRepresentable.fromEnum(Faction::values);
    public static final StreamCodec<ByteBuf, Faction> STREAM_CODEC =
            ByteBufCodecs.VAR_INT.map(id -> Faction.values()[id], Faction::ordinal);

    private final String name;

    Faction(String name) {
        this.name = name;
    }

    @Override
    public String getSerializedName() {
        return this.name;
    }

    public boolean isEnemy(Faction other) {
        return this != NEUTRAL && other != NEUTRAL && this != other;
    }
}
