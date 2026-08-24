package net.bitflora.asteriskcraft.game;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.bitflora.asteriskcraft.AsteriskCraft;
import net.bitflora.asteriskcraft.faction.Faction;
import net.bitflora.asteriskcraft.faction.Race;
import net.bitflora.asteriskcraft.race.RaceProfile;
import net.bitflora.asteriskcraft.race.Races;
import net.minecraft.world.level.Level;

/**
 * Who is playing which side in this match: the side the human commands, and the side the computer
 * plays. Decided once at bootstrap and saved with the world, because everything downstream — where
 * the AI's bases are stamped, which bank the director spends, which core losing ends the match —
 * has to keep agreeing with it for the life of the world.
 *
 * <p>This is what replaces the mod's old "player = Protoss, enemy = Zerg" assumption. Nothing reads
 * a faction literal any more: {@code command.ControlledFaction} answers for the human,
 * {@code director.AiDirector} for the computer, and each looks its race's {@link RaceProfile} up
 * from here. Swapping the two sides, or pointing either at a third race, is a change to what
 * {@code GameBootstrap} writes and nothing else.
 *
 * <p>Deliberately two factions rather than a list: the match is one human side against one computer
 * side. PvP would grow this into a per-player mapping, which is why every caller asks this record a
 * question instead of reading a field off it.
 */
public record MatchSetup(Faction playerFaction, Faction aiFaction) {

    public MatchSetup {
        // Both sides are real armies with a race, a base and a bank. NEUTRAL is the unfactioned
        // world, which has none of those, so catching it here keeps every downstream profile
        // lookup non-null.
        if (playerFaction == Faction.NEUTRAL || aiFaction == Faction.NEUTRAL) {
            throw new IllegalArgumentException("a match side cannot be NEUTRAL");
        }
    }

    /** MVP: one human commanding the Protoss against a computer swarm. */
    public static final MatchSetup DEFAULT = new MatchSetup(Faction.PROTOSS, Faction.ZERG);

    public static final Codec<MatchSetup> CODEC = RecordCodecBuilder.create(inst -> inst.group(
            Faction.CODEC.fieldOf("player").forGetter(MatchSetup::playerFaction),
            Faction.CODEC.fieldOf("ai").forGetter(MatchSetup::aiFaction)
    ).apply(inst, MatchSetup::new));

    /**
     * The match two picked races produce: each takes the side that plays it. This is what
     * {@code GameBootstrap} writes from the {@code player_race} and {@code ai_race} game rules.
     *
     * <p>With two races the opponent used to be derivable — "the faction that isn't yours". A third
     * made that an arbitrary choice, so it became a second rule and a second picker rather than a
     * cleverer derivation.
     *
     * <p><b>A mirror match is not a match, and this is the one place that is enforced.</b> Two
     * rules are two ints, so a command or a hand-edited save can name the same race twice; a
     * dedicated server has no picker to stop it. Both sides would then draw on one bank
     * ({@code building.ArmyBank} keys one per {@link Race}) and neither would shoot the other
     * ({@link Faction#isEnemy} is strictly cross-faction), which reads as the game being broken
     * rather than as an unusual setting. So the opponent is re-derived rather than honoured.
     */
    public static MatchSetup forRaces(Race playerRace, Race aiRace) {
        Faction player = sideOf(playerRace);
        if (aiRace == playerRace) {
            Faction fallback = anyOtherSide(player);
            AsteriskCraft.LOGGER.warn(
                    "AsteriskCraft: both sides were set to {}; the computer plays {} instead",
                    playerRace.getSerializedName(), fallback.getSerializedName());
            return new MatchSetup(player, fallback);
        }
        return new MatchSetup(player, sideOf(aiRace));
    }

    private static Faction sideOf(Race race) {
        Faction faction = Faction.of(race);
        if (faction == null) {
            throw new IllegalArgumentException("no side plays " + race);
        }
        return faction;
    }

    private static Faction anyOtherSide(Faction taken) {
        for (Faction other : Faction.values()) {
            if (other != Faction.NEUTRAL && other != taken) {
                return other;
            }
        }
        throw new IllegalStateException("no opponent available for " + taken);
    }

    /**
     * This world's match setup. Read off the overworld wherever {@code level} sits, so a unit in
     * the nether asks the same question and gets the same answer.
     */
    public static MatchSetup of(Level level) {
        // Level-scoped attachments are not synced, and in single-player the integrated server's
        // data must not be read off the render thread — so the client answers with the default.
        // Nothing client-side needs more than "which race is which side" to draw with.
        if (level.isClientSide() || level.getServer() == null) {
            return DEFAULT;
        }
        return level.getServer().overworld().getData(GameAttachments.MATCH_SETUP);
    }

    /** The race the human is playing. */
    public RaceProfile playerProfile() {
        return Races.required(this.playerFaction);
    }

    /** The race the computer is playing. */
    public RaceProfile aiProfile() {
        return Races.required(this.aiFaction);
    }

    /**
     * The side {@code side} is fighting. With one human side against one computer side there is
     * exactly one answer; asking here rather than assuming is what keeps a caller from having to
     * name either race.
     */
    public Faction opponentOf(Faction side) {
        return side == this.aiFaction ? this.playerFaction : this.aiFaction;
    }
}
