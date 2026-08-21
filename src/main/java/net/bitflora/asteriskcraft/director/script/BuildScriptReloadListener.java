package net.bitflora.asteriskcraft.director.script;

import net.bitflora.asteriskcraft.AsteriskCraft;
import net.bitflora.asteriskcraft.faction.Race;
import net.bitflora.asteriskcraft.race.RaceProfile;
import net.bitflora.asteriskcraft.race.Races;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Loads every race's build script from any datapack at {@code data/&lt;ns&gt;/build_scripts/&lt;race&gt;.txt}
 * — the path each {@link RaceProfile} declares. A datapack overrides a race's script simply by
 * shipping its own copy (highest-priority pack wins). Parsing never throws: a malformed script logs
 * its errors and the previous good script is kept, so a typo can't brick the AI or crash the server.
 *
 * <p>All races are loaded, not just the one currently playing the computer, because a reload
 * listener runs long before a match knows who is playing what — and loading them all is what makes
 * the script for a newly added race work with no further plumbing.
 */
public final class BuildScriptReloadListener extends SimplePreparableReloadListener<Map<Race, BuildScript>> {

    @Override
    protected Map<Race, BuildScript> prepare(ResourceManager manager, ProfilerFiller profiler) {
        Map<Race, BuildScript> loaded = new EnumMap<>(Race.class);
        for (RaceProfile profile : Races.all()) {
            loaded.put(profile.race(), read(manager, profile.buildScript()));
        }
        return loaded;
    }

    private static BuildScript read(ResourceManager manager, Identifier path) {
        // getResourceStack returns matching resources low→high priority; the last one wins, giving
        // datapack-override semantics for free.
        List<Resource> stack = manager.getResourceStack(path);
        if (stack.isEmpty()) {
            return BuildScript.EMPTY;
        }
        Resource top = stack.get(stack.size() - 1);
        try (BufferedReader reader = top.openAsReader()) {
            List<String> lines = new ArrayList<>();
            String line;
            while ((line = reader.readLine()) != null) {
                lines.add(line);
            }
            return BuildScriptParser.parse(lines);
        } catch (IOException e) {
            AsteriskCraft.LOGGER.error("Failed to read build script {}", path, e);
            return BuildScript.EMPTY;
        }
    }

    @Override
    protected void apply(Map<Race, BuildScript> scripts, ResourceManager manager, ProfilerFiller profiler) {
        scripts.forEach((race, script) -> {
            Identifier path = Races.of(race).buildScript();
            for (String error : script.errors()) {
                AsteriskCraft.LOGGER.warn("Build script {}: {}", path, error);
            }
            warnUnknownUnits(race, path, script);

            if (script.isEmpty()) {
                AsteriskCraft.LOGGER.warn("Build script {} produced no commands; keeping the previous script", path);
                return;
            }
            BuildScriptManager.install(race, script);
            AsteriskCraft.LOGGER.info("Loaded {} build script ({} commands) from {}",
                    race.getSerializedName(), script.size(), path);
        });
    }

    /** Logs a warning for any unit name this race's roster doesn't recognise (it's dropped at runtime). */
    private static void warnUnknownUnits(Race race, Identifier path, BuildScript script) {
        for (BuildCommand command : script.commands()) {
            UnitList units = switch (command) {
                case BuildCommand.Defence defence -> defence.units();
                case BuildCommand.Wave wave -> wave.units();
                default -> null;
            };
            if (units == null) {
                continue;
            }
            for (UnitReq req : units.reqs()) {
                if (Races.of(race).roster().resolve(req.unitName()).isEmpty()) {
                    AsteriskCraft.LOGGER.warn("Build script {}: unknown unit '{}' will be skipped", path, req.unitName());
                }
            }
        }
    }
}
