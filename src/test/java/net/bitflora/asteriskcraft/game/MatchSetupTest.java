package net.bitflora.asteriskcraft.game;

import net.bitflora.asteriskcraft.faction.Faction;
import net.bitflora.asteriskcraft.faction.Race;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * Guards how a picked matchup becomes two sides. With two races the opponent was a fact — "the
 * other one" — and needed no test; with three it is a rule, and the rule has one edge worth
 * pinning: the two rules behind it are independent ints, so nothing outside this method stops them
 * naming the same race.
 *
 * <p>Deliberately no assertion on <em>which</em> race the mirror-match fallback picks. That it
 * produces a real opponent is the rule; which one it settles on is a value, free to change.
 */
class MatchSetupTest {

    @Test
    void eachRaceTakesTheSideThatPlaysIt() {
        for (Race player : Race.values()) {
            for (Race ai : Race.values()) {
                if (ai == player) {
                    continue;
                }
                MatchSetup setup = MatchSetup.forRaces(player, ai);
                assertSame(player, setup.playerFaction().race(), "the human is not playing " + player);
                assertSame(ai, setup.aiFaction().race(), "the computer is not playing " + ai);
            }
        }
    }

    @Test
    void aMirrorMatchIsTurnedIntoARealOne() {
        // Both sides on one race would share a bank (ArmyBank keys one per race) and refuse to
        // fight each other (Faction.isEnemy is strictly cross-faction), so it must never survive.
        for (Race race : Race.values()) {
            MatchSetup setup = MatchSetup.forRaces(race, race);
            assertSame(race, setup.playerFaction().race(), "the human must still get the race they picked");
            assertNotEquals(setup.playerFaction(), setup.aiFaction(), "both sides ended up on " + race);
            assertNotEquals(Faction.NEUTRAL, setup.aiFaction(), "the fallback opponent must be a real army");
        }
    }

    @Test
    void eachSideNamesTheOtherAsItsOpponent() {
        MatchSetup setup = MatchSetup.forRaces(Race.TERRAN, Race.ZERG);
        assertEquals(setup.aiFaction(), setup.opponentOf(setup.playerFaction()));
        assertEquals(setup.playerFaction(), setup.opponentOf(setup.aiFaction()));
    }
}
