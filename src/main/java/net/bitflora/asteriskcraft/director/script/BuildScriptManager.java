package net.bitflora.asteriskcraft.director.script;

import net.bitflora.asteriskcraft.faction.Race;

import java.util.EnumMap;
import java.util.Map;

/**
 * Holds the active build script for each race, swapped in by the datapack reload listener and read
 * each tick by the AI director for whichever race it happens to be playing. A race's
 * {@link #version(Race)} bumps only when its command list actually changes, which the interpreter
 * uses to reset its cursor after a {@code /reload} (see {@code director.AiDirector}).
 *
 * <p>Per race rather than one global script because the computer player is no longer necessarily
 * the swarm — a race is picked at match setup, and each ships its own script. Accessed from the
 * server thread; the map is guarded by {@code synchronized} on every path for safety.
 */
public final class BuildScriptManager {
    private record Loaded(BuildScript script, int version) {
        static final Loaded NONE = new Loaded(BuildScript.EMPTY, 0);
    }

    private static final Map<Race, Loaded> BY_RACE = new EnumMap<>(Race.class);

    private BuildScriptManager() {
    }

    public static synchronized BuildScript active(Race race) {
        return BY_RACE.getOrDefault(race, Loaded.NONE).script();
    }

    public static synchronized int version(Race race) {
        return BY_RACE.getOrDefault(race, Loaded.NONE).version();
    }

    /** Installs a newly loaded script for a race, bumping its version if its commands differ. */
    public static synchronized void install(Race race, BuildScript script) {
        Loaded current = BY_RACE.getOrDefault(race, Loaded.NONE);
        int version = script.commands().equals(current.script().commands())
                ? current.version()
                : current.version() + 1;
        BY_RACE.put(race, new Loaded(script, version));
    }
}
