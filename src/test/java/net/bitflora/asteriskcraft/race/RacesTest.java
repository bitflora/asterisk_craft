package net.bitflora.asteriskcraft.race;

import net.bitflora.asteriskcraft.faction.Faction;
import net.bitflora.asteriskcraft.faction.Race;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Guards the per-race table itself, so a race added half-configured fails the build rather than the
 * first world that draws it. Everything generic — the base block entity, the AI director, world
 * generation — reads a race through {@link RaceProfile} and would otherwise discover a missing
 * piece only at the moment it needed it, deep inside a running match.
 *
 * <p>Deliberately no assertions on any <em>value</em> in the table: what a base's HP is, or how many
 * bank slots a race gets, is a balance number that should be free to move (see CLAUDE.md). What is
 * pinned is that each entry is complete and internally consistent.
 */
class RacesTest {

    @Test
    void everyRaceHasAProfile() {
        for (Race race : Race.values()) {
            assertNotNull(Races.of(race), race + " has no profile");
        }
        assertEquals(Race.values().length, Races.all().size(), "a profile is registered under the wrong race");
    }

    @Test
    void everyProfileIsCompleteEnoughToPlay() {
        for (RaceProfile profile : Races.all()) {
            String race = profile.race().toString();
            assertNotNull(profile.coreBlock(), race + " has no base core block");
            assertNotNull(profile.baseTemplate(), race + " has no base structure template");
            assertNotNull(profile.buildScript(), race + " has no AI build script");
            assertTrue(profile.baseHealth() > 0, race + "'s base needs siege health");
            assertTrue(profile.bankSlots() > 0, race + " needs somewhere to bank its resources");
            assertTrue(profile.startingBank().stream().allMatch(stack -> stack.amount() > 0),
                    race + " seeds an empty starting stack");
            assertTrue(profile.baseDefences().stream().allMatch(defence -> defence.count() > 0),
                    race + " plants a static defence zero times");
        }
    }

    @Test
    void everyRosterNamesExactlyOneWorker() {
        // The AI director's standing worker floor has to know what to train, and UnitRoster.Builder
        // is what enforces the "exactly one" half — this pins that every registered race went
        // through it and that the worker is reachable by name like any other unit.
        for (RaceProfile profile : Races.all()) {
            assertNotNull(profile.worker(), profile.race() + " declares no worker");
            assertTrue(profile.worker().isWorker(), profile.race() + "'s worker is not flagged as one");
            assertTrue(profile.roster().names().contains(profile.race() == Race.PROTOSS ? "probe" : "drone"),
                    profile.race() + "'s roster does not list its own worker");
        }
    }

    @Test
    void aRosterResolvesItsOwnUnitsAndNobodyElses() {
        // Case and word separators are normalised, so a script may write a name the way a person
        // would type it. Resolution is per race, which is what keeps two races' rosters independent.
        assertTrue(Races.PROTOSS.roster().resolve("Dark Templar").isPresent());
        assertTrue(Races.PROTOSS.roster().resolve("dark_templar").isPresent());
        assertTrue(Races.ZERG.roster().resolve("  HYDRALISK ").isPresent());
        assertTrue(Races.PROTOSS.roster().resolve("hydralisk").isEmpty(),
                "the Protoss must not be able to build a Hydralisk");
        assertTrue(Races.ZERG.roster().resolve("zealot").isEmpty(),
                "the Zerg must not be able to build a Zealot");
    }

    @Test
    void everySideResolvesToItsRaceAndNeutralToNone() {
        // The one bridge between "which side" and "which race". NEUTRAL is the unfactioned world:
        // it is nobody's army, so it has no profile and every trait lookup on it must come back false.
        for (Faction faction : Faction.values()) {
            if (faction == Faction.NEUTRAL) {
                assertNull(faction.race(), "NEUTRAL must play no race");
                assertNull(Races.of(faction), "NEUTRAL must have no profile");
            } else {
                assertSame(Races.of(faction.race()), Races.of(faction),
                        faction + " resolves to a different profile than its race does");
            }
        }
    }
}
