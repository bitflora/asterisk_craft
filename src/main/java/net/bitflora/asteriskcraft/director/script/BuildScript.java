package net.bitflora.asteriskcraft.director.script;

import java.util.List;

/**
 * A parsed build script: the ordered {@link BuildCommand}s plus any human-readable parse
 * {@code errors} (bad lines are skipped, never fatal — see {@link BuildScriptParser}). An
 * {@link #isEmpty() empty} script means the AI has nothing to run; the loader keeps the previous
 * good script rather than installing an empty one.
 */
public record BuildScript(List<BuildCommand> commands, List<String> errors) {
    public static final BuildScript EMPTY = new BuildScript(List.of(), List.of());

    public BuildScript {
        commands = List.copyOf(commands);
        errors = List.copyOf(errors);
    }

    public boolean isEmpty() {
        return commands.isEmpty();
    }

    public int size() {
        return commands.size();
    }
}
