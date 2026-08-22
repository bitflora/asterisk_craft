package net.bitflora.asteriskcraft.faction;

import com.mojang.serialization.Codec;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.StringRepresentable;
import org.jetbrains.annotations.Nullable;

/**
 * The sides of a AsteriskCraft match. Kept faction-generic so later versions can add
 * race selection and PvP: nothing outside the bootstrap should assume the player
 * is PROTOSS.
 *
 * <p>A side is not a race. Each faction currently plays one fixed {@link Race} and {@link #race()}
 * is the bridge between the two, but every rule that is really about <em>what an army is</em>
 * rather than <em>whose side it is on</em> — shields, HP regen, infestation, what it hunts in the
 * wild — is a lookup on that race, so adding a third side, or letting the human play Zerg, does
 * not duplicate a single one of those rules.
 */
public enum Faction implements StringRepresentable {
    NEUTRAL("neutral", null),
    PROTOSS("protoss", Race.PROTOSS),
    ZERG("zerg", Race.ZERG);

    public static final Codec<Faction> CODEC = StringRepresentable.fromEnum(Faction::values);
    public static final StreamCodec<ByteBuf, Faction> STREAM_CODEC =
            ByteBufCodecs.VAR_INT.map(id -> Faction.values()[id], Faction::ordinal);

    private final String name;
    private final @Nullable Race race;

    Faction(String name, @Nullable Race race) {
        this.name = name;
        this.race = race;
    }

    @Override
    public String getSerializedName() {
        return this.name;
    }

    /**
     * Which race this side is playing, or null for {@link #NEUTRAL} — the unfactioned world is
     * nobody's army and so has no race. Null rather than an Optional because every caller is
     * either a per-tick combat check or a null-safe trait delegate below.
     */
    public @Nullable Race race() {
        return this.race;
    }

    /**
     * The side that plays {@code race}, or null if no side does. The inverse of {@link #race()},
     * and the one place a race is turned back into a side — {@code game.MatchSetup.forPlayerRace}
     * is what needs it, because the race is what a player picks and the side is what everything
     * downstream works in.
     */
    public static @Nullable Faction of(Race race) {
        for (Faction faction : values()) {
            if (faction.race == race) {
                return faction;
            }
        }
        return null;
    }

    public boolean isEnemy(Faction other) {
        return this != NEUTRAL && other != NEUTRAL && this != other;
    }

    /**
     * Which parts of the unfactioned world this side picks a fight with — the per-race half of
     * {@link FactionAttachments#isHostile}, which is otherwise strictly cross-faction.
     *
     * <p>The table itself is {@link Race#attacksWild}: the Protoss defend themselves against wild
     * hostiles and leave the settled world alone; the Zerg are the mirror image, ignoring monsters
     * (they are not what a swarm is hunting) and overrunning villagers and golems — except when a
     * human is commanding the swarm, when it hunts both. NEUTRAL has no race at all, which is what
     * keeps "an unfactioned unit starts no fights" true without a separate guard.
     *
     * @param commanded whether this side is the one a human is playing, from
     *                  {@link FactionAttachments#isCommanded}
     */
    public boolean attacksWild(WildKind kind, boolean commanded) {
        return this.race != null && this.race.attacksWild(kind, commanded);
    }

    /** Whether this side's units carry a shield pool — a fact about its {@link Race}. */
    public boolean hasShields() {
        return this.race != null && this.race.shields();
    }

    /** Whether this side's units regenerate HP out of combat — a fact about its {@link Race}. */
    public boolean hasRegen() {
        return this.race != null && this.race.regen();
    }

    /** Whether a kill by this side can raise its victim as one of its own — see {@link Race}. */
    public boolean infests() {
        return this.race != null && this.race.infests();
    }
}
