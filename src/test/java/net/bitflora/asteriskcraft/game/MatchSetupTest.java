package net.bitflora.asteriskcraft.game;

import net.bitflora.asteriskcraft.faction.Faction;
import net.bitflora.asteriskcraft.faction.Race;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * Guards how a picked matchup becomes two sides. The two game rules behind it are independent ints,
 * so every pair of races has to come out as a real match — including a pair naming one race twice,
 * which is an ordinary mirror match and not a setting to be corrected.
 */
class MatchSetupTest {

    @Test
    void everyPairOfRacesIsAMatch() {
        for (Race player : Race.values()) {
            for (Race ai : Race.values()) {
                MatchSetup setup = MatchSetup.forRaces(player, ai);
                assertSame(player, setup.playerRace(), "the human is not playing " + player);
                assertSame(ai, setup.aiRace(), "the computer is not playing " + ai);
                assertNotEquals(setup.playerFaction(), setup.aiFaction(),
                        player + " vs " + ai + " put both armies on one side");
            }
        }
    }

    @Test
    void aMirrorMatchIsTwoArmiesOfOneRace() {
        // The whole point of separating side from race: both sides play the swarm, and they are
        // still two sides — which is what gives them two banks and makes them enemies.
        for (Race race : Race.values()) {
            MatchSetup setup = MatchSetup.forRaces(race, race);
            assertSame(race, setup.playerRace(), "the human must get the race they picked");
            assertSame(race, setup.aiRace(), "and so must the computer");
            assertSame(setup.aiFaction(), setup.opponentOf(setup.playerFaction()));
        }
    }

    @Test
    void eachSideNamesTheRaceItDrew() {
        MatchSetup setup = MatchSetup.forRaces(Race.TERRAN, Race.ZERG);
        assertSame(Race.TERRAN, setup.raceOf(setup.playerFaction()));
        assertSame(Race.ZERG, setup.raceOf(setup.aiFaction()));
        assertNull(setup.raceOf(Faction.NEUTRAL), "the unfactioned world is nobody's army");
    }

    @Test
    void aHandPlacedBuildingGoesToTheSidePlayingItsRace() {
        MatchSetup setup = MatchSetup.forRaces(Race.TERRAN, Race.ZERG);
        assertSame(setup.playerFaction(), setup.sidePlaying(Race.TERRAN));
        assertSame(setup.aiFaction(), setup.sidePlaying(Race.ZERG));
        assertSame(Faction.NEUTRAL, setup.sidePlaying(Race.PROTOSS),
                "a race sitting out the match owns nothing");

        // In a mirror there is no fact of the matter, and the person who placed it is the better
        // guess than the computer.
        MatchSetup mirror = MatchSetup.forRaces(Race.ZERG, Race.ZERG);
        assertSame(mirror.playerFaction(), mirror.sidePlaying(Race.ZERG));
    }

    @Test
    void eachSideNamesTheOtherAsItsOpponent() {
        MatchSetup setup = MatchSetup.forRaces(Race.TERRAN, Race.ZERG);
        assertEquals(setup.aiFaction(), setup.opponentOf(setup.playerFaction()));
        assertEquals(setup.playerFaction(), setup.opponentOf(setup.aiFaction()));
    }
}
