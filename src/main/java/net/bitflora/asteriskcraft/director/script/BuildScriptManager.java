package net.bitflora.asteriskcraft.director.script;

/**
 * Holds the currently active Zerg build script, swapped in by the datapack reload listener and read
 * each tick by the director. The {@link #version()} bumps only when the command list actually
 * changes, which the interpreter uses to reset its cursor after a {@code /reload} (see
 * {@code ZergDirector}). Accessed from the server thread; fields are {@code volatile} for safety.
 */
public final class BuildScriptManager {
    private static volatile BuildScript active = BuildScript.EMPTY;
    private static volatile int version = 0;

    private BuildScriptManager() {
    }

    public static BuildScript active() {
        return active;
    }

    public static int version() {
        return version;
    }

    /** Installs a newly loaded script, bumping {@link #version()} if its commands differ. */
    public static synchronized void install(BuildScript script) {
        if (!script.commands().equals(active.commands())) {
            version++;
        }
        active = script;
    }
}
