package net.bitflora.asteriskcraft.faction;

import com.mojang.serialization.Codec;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.StringRepresentable;

import java.util.EnumSet;
import java.util.Set;

/**
 * Which race an army is playing, as opposed to {@link Faction}, which is which <em>side</em> it is
 * on. They are independent: whether a unit has shields is a fact about its race, whether it will
 * shoot at you is a fact about its side. Either race can be the human's, either can be the
 * computer's, and both sides may play the same one — {@code game.MatchSetup} decides, and
 * {@link FactionAttachments#RACE} carries the answer on every unit beside its side.
 *
 * <p>The cheap racial traits live here as a per-race table, including the targeting one
 * ({@link #attacksWild}) — a bare enum with no dependencies, so a
 * pure rule like {@code combat.Infestation} can consult it with no live level. Everything a race
 * needs that is bound to the registries (its base block and template, its roster, its build script)
 * is too heavy for a leaf package and lives in {@code race.RaceProfile} instead.
 */
public enum Race implements StringRepresentable {
    /**
     * Regenerating shields in front of HP; no HP regen; leaves the settled world alone. The
     * Protoss defend themselves against the wild hostiles whether or not a human is commanding
     * them, so both of their wild-target sets are the same.
     */
    PROTOSS("protoss", true, false, false, false,
            Set.of(WildKind.HOSTILE),
            Set.of(WildKind.HOSTILE)),
    /**
     * No shields; HP itself regenerates out of combat; kills raise villagers as Infested.
     *
     * <p>A Drone does not put a building up, it <em>becomes</em> one: it walks to the site and dies
     * there, and the structure grows out of it. That is the swarm's whole construction doctrine and
     * it is a fact about the worker rather than about any one building, which is why it lives here
     * rather than beside {@code building.BuilderDependent}'s per-item flag.
     *
     * <p>The swarm's doctrine is to overrun the settled world and ignore monsters — monsters are
     * not what a swarm is hunting, and a scripted opponent parked across the map is better off not
     * wandering after them. Under <em>human</em> command that reads as a bug rather than a
     * doctrine: you are standing in this army, and it would let a creeper walk into your Hive. So
     * a commanded swarm adds the wild hostiles to what it already hunts.
     */
    ZERG("zerg", false, true, true, true,
            Set.of(WildKind.CIVILIAN),
            Set.of(WildKind.CIVILIAN, WildKind.HOSTILE)),
    /**
     * No shields, no HP regen, nothing raised from the dead — the Terran are what is left when the
     * other two races' cheap tricks are taken away, and they buy their staying power instead.
     *
     * <p>Their wild-target sets match the Protoss, and for the same reason: a Terran army shoots
     * the monsters that come at it whether or not a human is standing in it, so command widens
     * nothing here.
     *
     * <p>Appended, and every later race must be too: this ordinal is what the
     * {@code asteriskcraft:player_race} and {@code asteriskcraft:ai_race} game rules persist, so
     * inserting a race would re-side every existing world.
     */
    TERRAN("terran", false, false, false, false,
            Set.of(WildKind.HOSTILE),
            Set.of(WildKind.HOSTILE));

    public static final Codec<Race> CODEC = StringRepresentable.fromEnum(Race::values);
    public static final StreamCodec<ByteBuf, Race> STREAM_CODEC =
            ByteBufCodecs.VAR_INT.map(id -> Race.values()[id], Race::ordinal);

    private final String name;
    private final boolean shields;
    private final boolean regen;
    private final boolean infests;
    private final boolean consumesBuilders;
    private final Set<WildKind> wildTargets;
    private final Set<WildKind> commandedWildTargets;

    Race(String name, boolean shields, boolean regen, boolean infests, boolean consumesBuilders,
         Set<WildKind> wildTargets, Set<WildKind> commandedWildTargets) {
        this.name = name;
        this.shields = shields;
        this.regen = regen;
        this.infests = infests;
        this.consumesBuilders = consumesBuilders;
        this.wildTargets = copy(wildTargets);
        this.commandedWildTargets = copy(commandedWildTargets);
    }

    private static Set<WildKind> copy(Set<WildKind> kinds) {
        return kinds.isEmpty() ? EnumSet.noneOf(WildKind.class) : EnumSet.copyOf(kinds);
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
     * Whether this race's workers <em>become</em> the buildings they are called to rather than
     * putting them up and walking away. Read off the worker in hand by
     * {@code building.ConstructionSite}, which is the only caller: a race that consumes its builders
     * spends one the instant it arrives, and the structure finishes with nobody on the hook for it.
     */
    public boolean consumesBuilders() {
        return this.consumesBuilders;
    }

    /**
     * Which parts of the unfactioned world this race picks a fight with — reached through
     * {@link FactionAttachments#isHostile}, which is the only caller and adds the NEUTRAL handling.
     *
     * <p>Two sets rather than one because what an army hunts depends on whether a human is
     * commanding it (see {@link #ZERG}). {@code commanded} comes from
     * {@link FactionAttachments#isCommanded}, which is the leaf-package projection of
     * {@code game.MatchSetup.playerFaction()}. This is still a table, not a branch: nothing in
     * targeting code names a race, and changing what a race hunts — commanded or not — is an edit
     * to the two lines above.
     */
    public boolean attacksWild(WildKind kind, boolean commanded) {
        return (commanded ? this.commandedWildTargets : this.wildTargets).contains(kind);
    }
}
