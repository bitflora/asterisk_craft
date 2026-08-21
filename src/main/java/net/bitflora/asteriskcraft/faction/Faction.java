package net.bitflora.asteriskcraft.faction;

import com.mojang.serialization.Codec;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.StringRepresentable;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * The sides of a AsteriskCraft match. Kept faction-generic so later versions can add
 * race selection and PvP: nothing outside the bootstrap should assume the player
 * is PROTOSS.
 */
public enum Faction implements StringRepresentable {
    NEUTRAL("neutral"),
    PROTOSS("protoss", WildKind.HOSTILE),
    ZERG("zerg", WildKind.CIVILIAN);

    public static final Codec<Faction> CODEC = StringRepresentable.fromEnum(Faction::values);
    public static final StreamCodec<ByteBuf, Faction> STREAM_CODEC =
            ByteBufCodecs.VAR_INT.map(id -> Faction.values()[id], Faction::ordinal);

    private final String name;
    private final Set<WildKind> wildTargets;

    Faction(String name, WildKind... wildTargets) {
        this.name = name;
        this.wildTargets = wildTargets.length == 0
                ? EnumSet.noneOf(WildKind.class)
                : EnumSet.copyOf(List.of(wildTargets));
    }

    @Override
    public String getSerializedName() {
        return this.name;
    }

    public boolean isEnemy(Faction other) {
        return this != NEUTRAL && other != NEUTRAL && this != other;
    }

    /**
     * Which parts of the unfactioned world this race picks a fight with — the per-race half of
     * {@link FactionAttachments#isHostile}, which is otherwise strictly cross-faction.
     *
     * <p>The Protoss defend themselves against wild hostiles and leave the settled world alone; the
     * Zerg are the mirror image, ignoring monsters (they are not what a swarm is hunting) and
     * overrunning villagers and golems. NEUTRAL's set is empty, which is what keeps
     * "an unfactioned unit starts no fights" true without a separate guard.
     */
    public boolean attacksWild(WildKind kind) {
        return this.wildTargets.contains(kind);
    }
}
