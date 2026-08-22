package net.bitflora.asteriskcraft.game;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.serialization.Codec;
import net.bitflora.asteriskcraft.AsteriskCraft;
import net.bitflora.asteriskcraft.faction.Race;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.flag.FeatureFlagSet;
import net.minecraft.world.level.gamerules.GameRule;
import net.minecraft.world.level.gamerules.GameRuleCategory;
import net.minecraft.world.level.gamerules.GameRuleType;
import net.minecraft.world.level.gamerules.GameRuleTypeVisitor;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * The mod's game rules. There is one: which race the human plays.
 *
 * <p>A game rule rather than a config, because the choice is per <em>world</em> and has to be made
 * before the world exists. Game rules are a registry in 26.1 ({@link Registries#GAME_RULE}), and
 * the create-world screen already holds a live {@code GameRules} that flows into the level it
 * builds — so a rule is the one carrier that is set at creation, saved with the world, readable
 * server-side before the first player has finished logging in, and settable on a dedicated server
 * where no creation screen ever runs. {@code client.RacePickerOverlay} is only a front-end onto
 * it; the rule also shows up under More &rarr; Game Rules on its own.
 *
 * <p>The value is a {@link Race} ordinal. That is the convention {@code Race.STREAM_CODEC} already
 * uses, and the enum is a small hand-edited table — but it does mean reordering {@code Race} would
 * re-side existing worlds, so don't.
 */
public final class AsteriskCraftGameRules {
    public static final DeferredRegister<GameRule<?>> GAME_RULES =
            DeferredRegister.create(Registries.GAME_RULE, AsteriskCraft.MODID);

    private static final int MIN_RACE = 0;
    private static final int MAX_RACE = Race.values().length - 1;

    /**
     * Which race the human commands; the computer plays the other one (see
     * {@link MatchSetup#forPlayerRace}). Read once, at bootstrap — {@code GameBootstrap} freezes
     * the answer into {@link GameAttachments#MATCH_SETUP} so editing the rule mid-match can't swap
     * the sides out from under a world whose bases are already standing.
     *
     * <p>Built by hand rather than through {@code GameRules.registerInteger} so the id lands in
     * this mod's namespace; the arguments are otherwise exactly what that helper passes.
     */
    public static final DeferredHolder<GameRule<?>, GameRule<Integer>> PLAYER_RACE =
            GAME_RULES.register("player_race", () -> new GameRule<>(
                    GameRuleCategory.MISC,
                    GameRuleType.INT,
                    IntegerArgumentType.integer(MIN_RACE, MAX_RACE),
                    GameRuleTypeVisitor::visitInteger,
                    Codec.intRange(MIN_RACE, MAX_RACE),
                    value -> value,
                    Race.PROTOSS.ordinal(),
                    FeatureFlagSet.of()));

    private AsteriskCraftGameRules() {
    }

    /**
     * The race the human plays in {@code level}, clamped so a hand-edited save can't throw.
     * {@code ServerLevel} rather than {@code Level} because that is where {@code getGameRules}
     * lives in 26.1 — and the only caller (bootstrap) is server-side anyway.
     */
    public static Race playerRace(ServerLevel level) {
        return raceOf(level.getGameRules().get(PLAYER_RACE.get()));
    }

    /** A rule value as a race, clamped into range. */
    public static Race raceOf(int value) {
        Race[] races = Race.values();
        return races[Math.clamp(value, MIN_RACE, MAX_RACE)];
    }
}
