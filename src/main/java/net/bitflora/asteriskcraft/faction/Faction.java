package net.bitflora.asteriskcraft.faction;

import com.mojang.serialization.Codec;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.StringRepresentable;

/**
 * The sides of an AsteriskCraft match: which army an entity or building fights for, and nothing
 * else. {@link #isEnemy} is the whole of what a side means — strictly cross-faction, and NEUTRAL
 * (everything the mod doesn't own) fights no one.
 *
 * <p><b>A side is not a race, and it does not name one.</b> Which race a side is playing is a fact
 * about <em>this match</em>, not about the enum: it is chosen at world creation, frozen into
 * {@code game.MatchSetup}, and carried on every unit beside its side by
 * {@link FactionAttachments#RACE}. That is what makes a mirror match a real match — two sides both
 * playing the swarm are still two armies, with two banks, that shoot each other — and it is why the
 * constants below are colours rather than race names. A "Protoss side" would have had to be either
 * the human's or the computer's, and in a mirror it is both.
 *
 * <p>Two playable sides because a match is one army against one army; PvP grows the number of
 * <em>players</em>, not of sides. A third side is one entry here plus a colour in
 * {@code entity.TeamColors} — {@link Cloaking}'s per-side detection mask has room to spare.
 */
public enum Faction implements StringRepresentable {
    NEUTRAL("neutral"),
    /** The side a human commands in a single-player match — see {@code game.MatchSetup}. */
    BLUE("blue"),
    /** The side the computer plays in a single-player match. */
    RED("red");

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
