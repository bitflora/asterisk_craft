package net.bitflora.asteriskcraft.stats;

import net.bitflora.asteriskcraft.faction.Race;
import net.neoforged.fml.loading.FMLPaths;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Finds the balance CSV, reads it and holds the parsed table. {@link UnitStats} is the named-field
 * surface over one of these.
 *
 * <p><b>Read at class-init, not on a datapack reload</b>, and that is a constraint rather than a
 * preference. A unit's attributes are consumed once, at {@code EntityAttributeCreationEvent} during
 * mod construction; the command cards in {@code building.ProductionKind} bake their cost text at
 * class-init; {@code race.UnitRoster} memoises a unit's cost and build time on first resolve with no
 * invalidation hook; and a dozen entity and goal classes hold a {@code static final UnitStat}. A
 * table that changed underneath all of that would only appear to have changed. So the tweak loop is
 * "edit the file, relaunch" — which is still one step shorter than it was, since nothing recompiles.
 *
 * <p>Two places are looked in, in order:
 * <ol>
 *   <li>{@code <gamedir>/config/asteriskcraft/unit_stats.csv}, so an installed build can be balanced
 *       without rebuilding it;</li>
 *   <li>the copy shipped on the classpath, which is the source of truth in the repo.</li>
 * </ol>
 * The override is a <em>complete replacement</em>, not a merge: it must define every unit the
 * shipped copy does. A partial file silently leaving some units at their jar values is the worse
 * failure for a balance pass — half the table would be answering a question nobody asked. An
 * override that is malformed or incomplete is logged and ignored, because losing a hand-edited file
 * should not stop the game booting; a malformed <em>shipped</em> file throws, because that is a bug.
 */
public final class UnitStatTable {

    /** Not {@code AsteriskCraft.LOGGER}: {@code stats} must not gain a dependency on the mod root. */
    private static final Logger LOGGER = LoggerFactory.getLogger(UnitStatTable.class);

    private static final String CLASSPATH_RESOURCE = "/asteriskcraft/balance/unit_stats.csv";
    private static final String OVERRIDE_RELATIVE_PATH = "asteriskcraft/unit_stats.csv";

    private final Map<String, UnitStat> byId;
    private final Map<Race, List<UnitStat>> byRace;
    private final String source;

    private UnitStatTable(List<UnitStatCsv.Row> rows, String source) {
        Map<String, UnitStat> ids = new LinkedHashMap<>();
        Map<Race, List<UnitStat>> races = new EnumMap<>(Race.class);
        for (UnitStatCsv.Row row : rows) {
            ids.put(row.stat().id(), row.stat());
            races.computeIfAbsent(row.race(), r -> new ArrayList<>()).add(row.stat());
        }
        races.replaceAll((race, units) -> List.copyOf(units));
        // LinkedHashMap, not Map.copyOf: file order is the order a balance pass reads the table in,
        // and it is what the rosters and all() hand back.
        this.byId = Collections.unmodifiableMap(ids);
        this.byRace = Map.copyOf(races);
        this.source = source;
    }

    /** Loads the table: the config-directory override if there is a usable one, else the shipped copy. */
    public static UnitStatTable load() {
        UnitStatTable shipped = fromClasspath();
        return override(shipped).orElse(shipped);
    }

    private static UnitStatTable fromClasspath() {
        try (InputStream in = UnitStatTable.class.getResourceAsStream(CLASSPATH_RESOURCE)) {
            if (in == null) {
                throw new IllegalStateException("Missing balance table on the classpath: " + CLASSPATH_RESOURCE);
            }
            return new UnitStatTable(UnitStatCsv.parse(readAll(in), CLASSPATH_RESOURCE), CLASSPATH_RESOURCE);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read " + CLASSPATH_RESOURCE, e);
        }
    }

    private static Optional<UnitStatTable> override(UnitStatTable shipped) {
        Path path = overridePath();
        if (path == null || !Files.isRegularFile(path)) {
            return Optional.empty();
        }
        String name = path.toString();
        try {
            UnitStatTable table = new UnitStatTable(UnitStatCsv.parse(Files.readAllLines(path,
                    StandardCharsets.UTF_8), name), name);
            List<String> missing = shipped.byId.keySet().stream().filter(id -> !table.byId.containsKey(id)).toList();
            if (!missing.isEmpty()) {
                LOGGER.error("Ignoring {}: a balance override replaces the whole table and this one is"
                        + " missing {}", name, missing);
                return Optional.empty();
            }
            LOGGER.info("Loaded {} unit stats from {}", table.byId.size(), name);
            return Optional.of(table);
        } catch (IOException | IllegalStateException e) {
            LOGGER.error("Ignoring {}: {}", name, e.getMessage(), e);
            return Optional.empty();
        }
    }

    /**
     * Where a hand-edited override would be. Guarded because {@code FMLPaths} is only initialised
     * once the loader has run — in the JUnit bootstrap, or on any bare classpath, asking for it is
     * not an error, it just means there is no game directory to override from.
     */
    private static Path overridePath() {
        try {
            return FMLPaths.CONFIGDIR.get().resolve(OVERRIDE_RELATIVE_PATH);
        } catch (RuntimeException | LinkageError e) {
            return null;
        }
    }

    private static List<String> readAll(InputStream in) throws IOException {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            return reader.lines().toList();
        }
    }

    /** The stat for one unit id, as spelled in the {@code id} column. */
    public UnitStat get(String id) {
        UnitStat stat = this.byId.get(id);
        if (stat == null) {
            throw new IllegalStateException("No unit '" + id + "' in " + this.source);
        }
        return stat;
    }

    /** One race's units, in file order. Derived from the {@code race} column, so no roster is hand-listed. */
    public List<UnitStat> roster(Race race) {
        return this.byRace.getOrDefault(race, List.of());
    }

    /** Every unit, in file order. */
    public List<UnitStat> all() {
        return List.copyOf(this.byId.values());
    }
}
