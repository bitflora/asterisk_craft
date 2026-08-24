package net.bitflora.asteriskcraft.game;

import net.bitflora.asteriskcraft.faction.Faction;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Pure-logic guard for the win/lose decision. The world-facing side ({@code onCoreDestroyed} —
 * census reads, attachment writes, broadcasts) needs a live server, so it is exercised via
 * runClient; the decision itself is a pure function and fully testable here.
 *
 * <p>The rule under test is symmetric — a side is out when its last base falls, whichever side that
 * is — so each case is stated once and its mirror is the same call with the sides swapped.
 */
class GameOutcomeTest {
    private static final Faction PLAYER = MatchSetup.PLAYER_SIDE;
    private static final Faction AI = MatchSetup.AI_SIDE;

    @Test
    void losingTheLastPlayerBaseIsDefeat() {
        assertEquals(GameOutcome.Result.DEFEAT, GameOutcome.decide(PLAYER, 0, PLAYER, false));
    }

    @Test
    void razingTheLastEnemyBaseIsVictory() {
        assertEquals(GameOutcome.Result.VICTORY, GameOutcome.decide(AI, 0, PLAYER, false));
    }

    @Test
    void aSideWithBasesLeftIsStillInTheMatch() {
        // The whole point of the symmetry: an expansion base buys the player another life exactly
        // the way a second Hive always bought the swarm one.
        assertEquals(GameOutcome.Result.BASE_RAZED, GameOutcome.decide(AI, 2, PLAYER, false));
        assertEquals(GameOutcome.Result.BASE_RAZED, GameOutcome.decide(PLAYER, 1, PLAYER, false));
    }

    @Test
    void anUnownedCoreDecidesNothing() {
        assertEquals(GameOutcome.Result.NONE, GameOutcome.decide(Faction.NEUTRAL, 0, PLAYER, false));
    }

    @Test
    void nothingHappensOnceTheMatchIsOver() {
        assertEquals(GameOutcome.Result.NONE, GameOutcome.decide(PLAYER, 0, PLAYER, true));
        assertEquals(GameOutcome.Result.NONE, GameOutcome.decide(AI, 0, PLAYER, true));
    }

    @Test
    void theSameRuleAppliesWhenTheHumanPlaysTheOtherSide() {
        // Nothing in decide() knows which race is which — swap who the human commands and the
        // outcomes swap with them.
        assertEquals(GameOutcome.Result.DEFEAT, GameOutcome.decide(AI, 0, AI, false));
        assertEquals(GameOutcome.Result.VICTORY, GameOutcome.decide(PLAYER, 0, AI, false));
    }
}
