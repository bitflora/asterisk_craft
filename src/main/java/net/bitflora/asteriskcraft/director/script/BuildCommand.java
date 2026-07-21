package net.bitflora.asteriskcraft.director.script;

/**
 * One line of a parsed build script. The {@code ScriptInterpreter} walks a {@link BuildScript}'s
 * commands in order:
 * <ul>
 *   <li>{@link Workers} — a standing floor: maintain at least this many workers from here on.</li>
 *   <li>{@link Defence} — train these units and leave them guarding near the base.</li>
 *   <li>{@link Wave} — train these units, then send the whole batch to attack once complete.</li>
 *   <li>{@link Wait} — pause this many seconds before the next command.</li>
 *   <li>{@link Repeat} — loop the previous {@code count} commands endlessly.</li>
 * </ul>
 */
public sealed interface BuildCommand
        permits BuildCommand.Workers, BuildCommand.Defence, BuildCommand.Wave,
                BuildCommand.Wait, BuildCommand.Repeat {

    record Workers(int count) implements BuildCommand {
    }

    record Defence(UnitList units) implements BuildCommand {
    }

    record Wave(UnitList units) implements BuildCommand {
    }

    record Wait(int seconds) implements BuildCommand {
    }

    record Repeat(int count) implements BuildCommand {
    }
}
