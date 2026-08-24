package net.bitflora.asteriskcraft.game;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.bitflora.asteriskcraft.faction.Faction;
import net.bitflora.asteriskcraft.faction.Race;
import net.bitflora.asteriskcraft.race.RaceProfile;
import net.bitflora.asteriskcraft.race.Races;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

/**
 * Who is playing what in this match: the race the human commands and the race the computer plays.
 * Decided once at bootstrap and saved with the world, because everything downstream — where the
 * AI's bases are stamped, which bank the director spends, which core losing ends the match — has to
 * keep agreeing with it for the life of the world.
 *
 * <p><b>Two races, not two sides.</b> The sides are fixed labels ({@link #PLAYER_SIDE} /
 * {@link #AI_SIDE}) and this record says which race each of them drew. That is what makes a mirror
 * match an ordinary match: {@link Faction#BLUE} playing the swarm against {@link Faction#RED}
 * playing the swarm is still two armies, with two banks, that are enemies — where a side that
 * <em>was</em> a race could only ever be one of them.
 *
 * <p>It is also why nothing here has to be re-derived: the two game rules behind it
 * ({@link AsteriskCraftGameRules}) are independent ints and every pair of them, mirror included, is
 * a real match.
 *
 * <p>Nothing reads a faction literal to mean a race any more: {@code command.ControlledFaction}
 * answers which side a human commands, {@code faction.FactionAttachments.RACE} says what any given
 * army is, and {@code director.AiDirector} asks this record for the computer's.
 *
 * <p>Deliberately one human side against one computer side. PvP would grow this into a per-player
 * mapping, which is why every caller asks this record a question instead of reading a field off it.
 */
public record MatchSetup(Race playerRace, Race aiRace) {

    /** The side a human commands. Fixed, because "which side" is no longer a choice — only race is. */
    public static final Faction PLAYER_SIDE = Faction.BLUE;
    /** The side the computer plays. */
    public static final Faction AI_SIDE = Faction.RED;

    /** MVP: one human commanding the Protoss against a computer swarm. */
    public static final MatchSetup DEFAULT = new MatchSetup(Race.PROTOSS, Race.ZERG);

    public static final Codec<MatchSetup> CODEC = RecordCodecBuilder.create(inst -> inst.group(
            Race.CODEC.fieldOf("player_race").forGetter(MatchSetup::playerRace),
            Race.CODEC.fieldOf("ai_race").forGetter(MatchSetup::aiRace)
    ).apply(inst, MatchSetup::new));

    /**
     * The match two picked races produce. This is what {@code GameBootstrap} writes from the
     * {@code player_race} and {@code ai_race} game rules, and it honours both of them as given —
     * including a mirror, which is a legitimate matchup and not a setting to be corrected.
     */
    public static MatchSetup forRaces(Race playerRace, Race aiRace) {
        return new MatchSetup(playerRace, aiRace);
    }

    /**
     * This world's match setup. Read off the overworld wherever {@code level} sits, so a unit in
     * the nether asks the same question and gets the same answer.
     */
    public static MatchSetup of(Level level) {
        // Level-scoped attachments are not synced, and in single-player the integrated server's
        // data must not be read off the render thread — so the client answers with the default.
        // Which side is which is still exact there (the sides are constants); only the races are a
        // guess, and the client-side callers that would care carry their own synced race instead
        // (faction.FactionAttachments.RACE).
        if (level.isClientSide() || level.getServer() == null) {
            return DEFAULT;
        }
        return level.getServer().overworld().getData(GameAttachments.MATCH_SETUP);
    }

    /** The side the human commands. */
    public Faction playerFaction() {
        return PLAYER_SIDE;
    }

    /** The side the computer plays. */
    public Faction aiFaction() {
        return AI_SIDE;
    }

    /** The race the human is playing. */
    public RaceProfile playerProfile() {
        return Races.of(this.playerRace);
    }

    /** The race the computer is playing. */
    public RaceProfile aiProfile() {
        return Races.of(this.aiRace);
    }

    /** Which race {@code side} drew, or null for {@link Faction#NEUTRAL}, which is nobody's army. */
    public @Nullable Race raceOf(Faction side) {
        if (side == PLAYER_SIDE) {
            return this.playerRace;
        }
        return side == AI_SIDE ? this.aiRace : null;
    }

    /** The profile of whichever race {@code side} drew, or null for {@link Faction#NEUTRAL}. */
    public @Nullable RaceProfile profileOf(Faction side) {
        Race race = raceOf(side);
        return race == null ? null : Races.of(race);
    }

    /**
     * Which side is playing {@code race} — the fallback owner for a building placed by hand rather
     * than warped in by an army (creative, {@code /setblock}).
     *
     * <p>The human's side wins a tie, which is what a mirror match makes possible: with both sides
     * on one race there is no fact of the matter, and the person who placed the block is the better
     * guess than the computer. NEUTRAL when neither side plays it — a Hive sitting out a
     * Protoss-vs-Terran match belongs to no army, which keeps it out of both.
     */
    public Faction sidePlaying(Race race) {
        if (this.playerRace == race) {
            return PLAYER_SIDE;
        }
        return this.aiRace == race ? AI_SIDE : Faction.NEUTRAL;
    }

    /**
     * The side {@code side} is fighting. With one human side against one computer side there is
     * exactly one answer; asking here rather than assuming is what keeps a caller from having to
     * name either side.
     */
    public Faction opponentOf(Faction side) {
        return side == AI_SIDE ? PLAYER_SIDE : AI_SIDE;
    }
}
