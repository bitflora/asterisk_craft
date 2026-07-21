package net.bitflora.asteriskcraft.director.script;

import net.bitflora.asteriskcraft.AsteriskCraft;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Loads the enemy's build script from any datapack at {@code data/&lt;ns&gt;/build_scripts/zerg.txt}.
 * The canonical path is {@code asteriskcraft:build_scripts/zerg.txt}; a datapack overrides the mod's
 * default simply by shipping its own copy (highest-priority pack wins). Parsing never throws — a
 * malformed script logs its errors and the previous good script is kept, so a typo can't brick the
 * AI or crash the server.
 */
public final class BuildScriptReloadListener extends SimplePreparableReloadListener<BuildScript> {
    /** The single script the Zerg director runs. */
    public static final Identifier SCRIPT_PATH = AsteriskCraft.id("build_scripts/zerg.txt");

    @Override
    protected BuildScript prepare(ResourceManager manager, ProfilerFiller profiler) {
        // getResourceStack returns matching resources low→high priority; the last one wins, giving
        // datapack-override semantics for free.
        List<Resource> stack = manager.getResourceStack(SCRIPT_PATH);
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
            AsteriskCraft.LOGGER.error("Failed to read build script {}", SCRIPT_PATH, e);
            return BuildScript.EMPTY;
        }
    }

    @Override
    protected void apply(BuildScript script, ResourceManager manager, ProfilerFiller profiler) {
        for (String error : script.errors()) {
            AsteriskCraft.LOGGER.warn("Build script {}: {}", SCRIPT_PATH, error);
        }
        warnUnknownUnits(script);

        if (script.isEmpty()) {
            AsteriskCraft.LOGGER.warn("Build script {} produced no commands; keeping the previous script", SCRIPT_PATH);
            return;
        }
        BuildScriptManager.install(script);
        AsteriskCraft.LOGGER.info("Loaded Zerg build script ({} commands) from {}", script.size(), SCRIPT_PATH);
    }

    /** Logs a warning for any unit name the catalog doesn't recognise (it's dropped at runtime). */
    private static void warnUnknownUnits(BuildScript script) {
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
                if (ZergUnitCatalog.resolve(req.unitName()).isEmpty()) {
                    AsteriskCraft.LOGGER.warn("Build script {}: unknown unit '{}' will be skipped", SCRIPT_PATH, req.unitName());
                }
            }
        }
    }
}
