package net.bitflora.asteriskcraft.director.script;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Guards the pure build-script parser: the DSL must tokenize as documented and, crucially, must
 * turn every malformed line into a recoverable error rather than throwing — a datapack typo can
 * never be allowed to crash the server.
 */
class BuildScriptParserTest {

    private static BuildScript parse(String... lines) {
        return BuildScriptParser.parse(List.of(lines));
    }

    @Test
    void parsesEachCommandType() {
        BuildScript script = parse(
                "Workers: 6",
                "Defence: 2 Zergling",
                "Wait 45",
                "Wave: 3-5 Zergling, 1 Hydralisk",
                "Repeat 2");
        assertTrue(script.errors().isEmpty(), () -> "unexpected errors: " + script.errors());
        assertEquals(5, script.size());

        assertEquals(new BuildCommand.Workers(6), script.commands().get(0));

        BuildCommand.Defence defence = assertInstanceOf(BuildCommand.Defence.class, script.commands().get(1));
        assertEquals(1, defence.units().reqs().size());
        assertEquals("Zergling", defence.units().reqs().get(0).unitName());
        assertEquals(IntRange.single(2), defence.units().reqs().get(0).quantity());

        assertEquals(new BuildCommand.Wait(45), script.commands().get(2));

        BuildCommand.Wave wave = assertInstanceOf(BuildCommand.Wave.class, script.commands().get(3));
        assertEquals(2, wave.units().reqs().size());
        assertEquals(new IntRange(3, 5), wave.units().reqs().get(0).quantity());
        assertEquals("Hydralisk", wave.units().reqs().get(1).unitName());
        assertEquals(IntRange.single(1), wave.units().reqs().get(1).quantity());

        assertEquals(new BuildCommand.Repeat(2), script.commands().get(4));
    }

    @Test
    void ignoresBlankLinesAndComments() {
        BuildScript script = parse(
                "# a leading comment",
                "",
                "   ",
                "Workers: 4   # inline comment",
                "\t# indented comment");
        assertTrue(script.errors().isEmpty(), () -> "unexpected errors: " + script.errors());
        assertEquals(List.of(new BuildCommand.Workers(4)), script.commands());
    }

    @Test
    void isCaseInsensitiveAndWhitespaceTolerant() {
        BuildScript script = parse(
                "  WAVE:   3   zergling ,  2   HYDRALISK  ",
                "defense: 1 Zergling",
                "REPEAT 3");
        assertTrue(script.errors().isEmpty(), () -> "unexpected errors: " + script.errors());
        assertEquals(3, script.size());
        BuildCommand.Wave wave = assertInstanceOf(BuildCommand.Wave.class, script.commands().get(0));
        assertEquals("zergling", wave.units().reqs().get(0).unitName());
        // "defense" spelling is accepted as Defence
        assertInstanceOf(BuildCommand.Defence.class, script.commands().get(1));
    }

    @Test
    void toleratesTrailingCommasInUnitLists() {
        BuildScript script = parse("Wave: 3 Zergling, , 2 Hydralisk,");
        assertTrue(script.errors().isEmpty(), () -> "unexpected errors: " + script.errors());
        BuildCommand.Wave wave = assertInstanceOf(BuildCommand.Wave.class, script.commands().get(0));
        assertEquals(2, wave.units().reqs().size());
    }

    @Test
    void badLinesBecomeErrorsNotExceptions() {
        BuildScript script = parse(
                "Workers: 6",
                "Frobnicate 3",              // unknown command
                "Wave: five Zergling",       // non-numeric quantity
                "Wait later",                // non-numeric seconds
                "Repeat 0",                  // must be >= 1
                "Workers: 2-4",              // range not allowed for Workers
                "Defence:",                  // empty unit list
                "Wave: 3");                  // quantity with no unit name
        // Only the first valid line survives; every bad line produces at least one error.
        assertEquals(List.of(new BuildCommand.Workers(6)), script.commands());
        assertTrue(script.errors().size() >= 7, () -> "errors: " + script.errors());
        assertTrue(script.errors().stream().allMatch(e -> e.startsWith("line ")),
                "every error should carry its line number");
    }

    @Test
    void rangeNormalizesReversedBounds() {
        BuildScript script = parse("Wave: 5-3 Zergling");
        assertTrue(script.errors().isEmpty(), () -> "unexpected errors: " + script.errors());
        BuildCommand.Wave wave = assertInstanceOf(BuildCommand.Wave.class, script.commands().get(0));
        assertEquals(new IntRange(3, 5), wave.units().reqs().get(0).quantity());
    }

    @Test
    void emptyInputYieldsEmptyScript() {
        BuildScript script = parse();
        assertTrue(script.isEmpty());
        assertTrue(script.errors().isEmpty());
    }

    @Test
    void oneBadUnitDoesNotSinkTheWholeList() {
        BuildScript script = parse("Wave: 3 Zergling, bad, 2 Hydralisk");
        // The bad element errors, but the surrounding valid units still parse.
        assertFalse(script.errors().isEmpty());
        BuildCommand.Wave wave = assertInstanceOf(BuildCommand.Wave.class, script.commands().get(0));
        assertEquals(2, wave.units().reqs().size());
    }
}
