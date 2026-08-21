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
 * Which race an army is playing, as opposed to {@link Faction}, which is which <em>side</em> it is
 * on. The two are one-to-one today (the player is Protoss, the AI is Zerg) and {@link Faction#race}
 * is the bridge, but they are separate concepts: whether a unit has shields is a fact about its
 * race, whether it will shoot at you is a fact about its side.
 *
 * <p>The cheap racial traits live here as a per-race table, the same way
 * {@link Faction#attacksWild} holds the targeting one — a bare enum with no dependencies, so a
 * pure rule like {@code combat.Infestation} can consult it with no live level. Everything a race
 * needs that is bound to the registries (its base block and template, its roster, its build script)
 * is too heavy for a leaf package and lives in {@code race.RaceProfile} instead.
 */
public enum Race implements StringRepresentable {
    /** Regenerating shields in front of HP; no HP regen; leaves the settled world alone. */
    PROTOSS("protoss", true, false, false, WildKind.HOSTILE),
    /** No shields; HP itself regenerates out of combat; kills raise villagers as Infested. */
    ZERG("zerg", false, true, true, WildKind.CIVILIAN);

    public static final Codec<Race> CODEC = StringRepresentable.fromEnum(Race::values);
    public static final StreamCodec<ByteBuf, Race> STREAM_CODEC =
            ByteBufCodecs.VAR_INT.map(id -> Race.values()[id], Race::ordinal);

    private final String name;
    private final boolean shields;
    private final boolean regen;
    private final boolean infests;
    private final Set<WildKind> wildTargets;

    Race(String name, boolean shields, boolean regen, boolean infests, WildKind... wildTargets) {
        this.name = name;
        this.shields = shields;
        this.regen = regen;
        this.infests = infests;
        this.wildTargets = wildTargets.length == 0
                ? EnumSet.noneOf(WildKind.class)
                : EnumSet.copyOf(List.of(wildTargets));
    }

    @Override
    public String getSerializedName() {
        return this.name;
    }

    /** Whether this race's units carry a regenerating shield pool in front of their HP. */
    public boolean shields() {
        return this.shields;
    }

    /** Whether this race's units regenerate HP itself once they've gone a few seconds unhurt. */
    public boolean regen() {
        return this.regen;
    }

    /** Whether a kill by this race can raise its victim back up as one of its own. */
    public boolean infests() {
        return this.infests;
    }

    /**
     * Which parts of the unfactioned world this race picks a fight with — reached through
     * {@link Faction#attacksWild}, which is the only caller and adds the NEUTRAL handling.
     */
    public boolean attacksWild(WildKind kind) {
        return this.wildTargets.contains(kind);
    }
}
