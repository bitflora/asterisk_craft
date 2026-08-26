package net.bitflora.asteriskcraft.stats;

import net.bitflora.asteriskcraft.faction.Race;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * The parser for the balance table's CSV form. Pure: it takes lines of text and hands back
 * {@link UnitStat}s, touching no registry, no resource manager and no file system — which is what
 * lets {@code UnitStatCsvTest} exercise the whole format against inline strings.
 *
 * <p>The format this parses is documented for its <em>readers</em> in docs/balance-table.md —
 * every column, its units, and the rules a row has to obey. Change one here and change it there.
 *
 * <p>It builds every row through {@link UnitStat#builder}, so all the design rules the builder
 * already enforces (health/speed/cost are required, build time exists exactly for a purchasable
 * unit, a blast needs damage to detonate for, a detector's reveal must outlast its sweep) apply
 * here with nothing restated. What this class adds on top are the rules that only exist because the
 * table is now a grid: the header must name exactly the known columns, ids must be unique, and a
 * column group that maps to one of {@code UnitStat}'s {@code Optional} sub-records is all-or-nothing
 * — a Mutalisk with a {@code bounce_hits} and no {@code bounce_radius} is a typo, not a design.
 *
 * <p>Every failure is an {@link IllegalStateException} naming {@code <source>:<line>: <column>}.
 * That matters more than it looks: the table is read from {@code UnitStats}' static initializer, so
 * a malformed file surfaces as an {@code ExceptionInInitializerError}, and the message on the cause
 * is the only thing that tells anyone which cell to go fix.
 */
public final class UnitStatCsv {

    /** One parsed line: the stat itself, plus the race column, which is not part of a stat. */
    public record Row(Race race, UnitStat stat) {
    }

    private static final String RACE = "race";
    private static final String ID = "id";
    private static final String HEALTH = "health";
    private static final String ARMOR = "armor";
    private static final String SPEED = "speed";
    private static final String KNOCKBACK = "knockback_resistance";
    private static final String STEP_HEIGHT = "step_height";
    private static final String FOLLOW_RANGE = "follow_range";
    private static final String SHIELD = "shield";
    private static final String ATTACK_DAMAGE = "attack_damage";
    private static final String ANTI_AIR = "anti_air_bonus";
    private static final String ATTACK_ANIM = "attack_anim_ticks";
    private static final String RANGE = "range";
    private static final String COOLDOWN = "cooldown";
    private static final String FLY_SPEED = "fly_speed";
    private static final String HOVER_HEIGHT = "hover_height";
    private static final String PATH_LENGTH = "path_length";
    private static final String DETECT_RADIUS = "detect_radius";
    private static final String DETECT_SWEEP = "detect_sweep";
    private static final String DETECT_REVEAL = "detect_reveal";
    private static final String BOUNCE_HITS = "bounce_hits";
    private static final String BOUNCE_FALLOFF = "bounce_falloff";
    private static final String BOUNCE_RADIUS = "bounce_radius";
    private static final String BLAST_RADIUS = "blast_radius";
    private static final String BLAST_FUSE = "blast_fuse";
    private static final String BUILD_TICKS = "build_ticks";
    private static final String COST = "cost";

    /**
     * Every column the format knows, in the order the shipped file writes them. Order is not
     * load-bearing — cells are read by header name, so a column may be moved to sit beside the one
     * a balance pass wants to compare it against — but the <em>set</em> is closed, so a misspelled
     * header is caught rather than silently ignored.
     */
    private static final List<String> COLUMNS = List.of(
            RACE, ID, HEALTH, ARMOR, SPEED, KNOCKBACK, STEP_HEIGHT, FOLLOW_RANGE, SHIELD,
            ATTACK_DAMAGE, ANTI_AIR, ATTACK_ANIM,
            RANGE, COOLDOWN,
            FLY_SPEED, HOVER_HEIGHT, PATH_LENGTH,
            DETECT_RADIUS, DETECT_SWEEP, DETECT_REVEAL,
            BOUNCE_HITS, BOUNCE_FALLOFF, BOUNCE_RADIUS,
            BLAST_RADIUS, BLAST_FUSE,
            BUILD_TICKS, COST);

    private UnitStatCsv() {
    }

    /**
     * Parses the whole table. Blank lines are skipped; the first non-blank line is the header.
     *
     * @param source what to name in an error message — the file this text came from
     * @return one row per unit, in file order
     */
    public static List<Row> parse(List<String> lines, String source) {
        Map<String, Integer> header = null;
        List<Row> rows = new ArrayList<>();
        Set<String> ids = new LinkedHashSet<>();
        for (int i = 0; i < lines.size(); i++) {
            String line = stripBom(lines.get(i));
            if (line.isBlank()) {
                continue;
            }
            int lineNumber = i + 1;
            List<String> cells = split(line);
            if (header == null) {
                header = readHeader(cells, source, lineNumber);
                continue;
            }
            Cursor cursor = new Cursor(header, cells, source, lineNumber);
            Row row = readRow(cursor);
            if (!ids.add(row.stat().id())) {
                throw cursor.fail(ID, "duplicate id '" + row.stat().id() + "'");
            }
            rows.add(row);
        }
        if (header == null) {
            throw new IllegalStateException(source + ": no header row");
        }
        if (rows.isEmpty()) {
            throw new IllegalStateException(source + ": header but no unit rows");
        }
        return List.copyOf(rows);
    }

    private static Map<String, Integer> readHeader(List<String> cells, String source, int lineNumber) {
        Map<String, Integer> header = new LinkedHashMap<>();
        for (int i = 0; i < cells.size(); i++) {
            String name = cells.get(i).toLowerCase(Locale.ROOT);
            if (!COLUMNS.contains(name)) {
                throw new IllegalStateException(source + ":" + lineNumber + ": unknown column '" + name + "'");
            }
            if (header.put(name, i) != null) {
                throw new IllegalStateException(source + ":" + lineNumber + ": duplicate column '" + name + "'");
            }
        }
        List<String> missing = COLUMNS.stream().filter(c -> !header.containsKey(c)).toList();
        if (!missing.isEmpty()) {
            throw new IllegalStateException(source + ":" + lineNumber + ": missing columns " + missing);
        }
        return header;
    }

    private static Row readRow(Cursor row) {
        String id = row.required(ID);
        UnitStat.Builder builder = UnitStat.builder(id)
                .health(row.number(HEALTH))
                .armor(row.number(ARMOR))
                .speed(row.number(SPEED))
                .knockbackResistance(row.number(KNOCKBACK))
                .stepHeight(row.number(STEP_HEIGHT))
                .followRange(row.number(FOLLOW_RANGE))
                .shield(row.integer(SHIELD))
                .antiAirBonus(row.number(ANTI_AIR))
                .attackAnimTicks(row.integer(ATTACK_ANIM))
                .buildTicks(row.integer(BUILD_TICKS))
                .cost(parseCost(row));
        if (row.present(ATTACK_DAMAGE)) {
            builder.attackDamage(row.number(ATTACK_DAMAGE));
        }
        if (row.group("a ranged attack", RANGE, COOLDOWN)) {
            builder.ranged((float) row.number(RANGE), row.integer(COOLDOWN));
        }
        if (row.group("flight", FLY_SPEED, HOVER_HEIGHT, PATH_LENGTH)) {
            builder.flight(row.number(FLY_SPEED), row.integer(HOVER_HEIGHT), (float) row.number(PATH_LENGTH));
        }
        if (row.group("detection", DETECT_RADIUS, DETECT_SWEEP, DETECT_REVEAL)) {
            builder.detector(row.number(DETECT_RADIUS), row.integer(DETECT_SWEEP), row.integer(DETECT_REVEAL));
        }
        if (row.group("a bouncing attack", BOUNCE_HITS, BOUNCE_FALLOFF, BOUNCE_RADIUS)) {
            builder.bounce(row.integer(BOUNCE_HITS), (float) row.number(BOUNCE_FALLOFF),
                    (float) row.number(BOUNCE_RADIUS));
        }
        if (row.group("a blast", BLAST_RADIUS, BLAST_FUSE)) {
            builder.blast((float) row.number(BLAST_RADIUS), row.integer(BLAST_FUSE));
        }
        UnitStat stat;
        try {
            stat = builder.build();
        } catch (IllegalStateException e) {
            // The builder's rules are the design rules; keep its wording and add the cell address.
            throw new IllegalStateException(row.where() + ": " + e.getMessage(), e);
        }
        return new Row(parseRace(row), stat);
    }

    private static Race parseRace(Cursor row) {
        String name = row.required(RACE).toLowerCase(Locale.ROOT);
        for (Race race : Race.values()) {
            if (race.getSerializedName().equals(name) || race.name().toLowerCase(Locale.ROOT).equals(name)) {
                return race;
            }
        }
        throw row.fail(RACE, "unknown race '" + name + "'");
    }

    /**
     * Parses the one cell that carries a whole {@link UnitCost}. {@code +} joins the lines of one
     * bundle that must <em>all</em> be paid; {@code |} separates alternative bundles, of which any
     * one may be. So {@code wood 100 + stone 50} is a Dragoon and {@code wood 50 | stone 50} is a
     * Probe — and alternative order is load-bearing, because it is the base's button order.
     *
     * <p>One cell rather than a column per resource, so a cost stays readable as the single thing it
     * is, and so the file never needs CSV quoting: neither separator is a comma.
     */
    private static UnitCost parseCost(Cursor row) {
        String raw = row.raw(COST);
        if (raw.isBlank() || raw.equalsIgnoreCase("none")) {
            return UnitCost.NONE;
        }
        List<List<ResourceAmount>> alternatives = new ArrayList<>();
        for (String alternative : raw.split("\\|")) {
            List<ResourceAmount> bundle = new ArrayList<>();
            for (String term : alternative.split("\\+")) {
                bundle.add(parseCostLine(row, term));
            }
            alternatives.add(List.copyOf(bundle));
        }
        return new UnitCost(List.copyOf(alternatives));
    }

    private static ResourceAmount parseCostLine(Cursor row, String term) {
        String[] parts = term.trim().split("\\s+");
        if (parts.length != 2) {
            throw row.fail(COST, "'" + term.trim() + "' is not a '<resource> <amount>' pair");
        }
        Resource resource = parseResource(row, parts[0]);
        int amount;
        try {
            amount = Integer.parseInt(parts[1]);
        } catch (NumberFormatException e) {
            throw row.fail(COST, "'" + parts[1] + "' is not a whole number");
        }
        return new ResourceAmount(resource, amount);
    }

    /**
     * Accepts either the enum name ({@code stone}) or the serialized, player-facing label
     * ({@code cobblestone}) — the two differ for exactly one resource, and a balance file should not
     * have to know which of the two spellings it is looking at.
     */
    private static Resource parseResource(Cursor row, String name) {
        String lower = name.toLowerCase(Locale.ROOT);
        for (Resource resource : Resource.values()) {
            if (resource.name().toLowerCase(Locale.ROOT).equals(lower)
                    || resource.getSerializedName().equals(lower)) {
                return resource;
            }
        }
        throw row.fail(COST, "unknown resource '" + name + "'");
    }

    private static List<String> split(String line) {
        String[] parts = line.split(",", -1);
        List<String> cells = new ArrayList<>(parts.length);
        for (String part : parts) {
            cells.add(part.trim());
        }
        return cells;
    }

    /** Drops a UTF-8 BOM, which a spreadsheet is liable to write on the first line. */
    private static String stripBom(String line) {
        return line.startsWith("\uFEFF") ? line.substring(1) : line;
    }

    /** One row being read, and the source of every error message about it. */
    private record Cursor(Map<String, Integer> header, List<String> cells, String source, int lineNumber) {

        String where() {
            return this.source + ":" + this.lineNumber;
        }

        IllegalStateException fail(String column, String message) {
            return new IllegalStateException(this.where() + ": " + column + ": " + message);
        }

        String raw(String column) {
            int index = this.header.get(column);
            if (index >= this.cells.size()) {
                throw this.fail(column, "row has only " + this.cells.size() + " cells");
            }
            return this.cells.get(index);
        }

        boolean present(String column) {
            return !this.raw(column).isBlank();
        }

        String required(String column) {
            String raw = this.raw(column);
            if (raw.isBlank()) {
                throw this.fail(column, "must not be empty");
            }
            return raw;
        }

        double number(String column) {
            String raw = this.required(column);
            try {
                return Double.parseDouble(raw);
            } catch (NumberFormatException e) {
                throw this.fail(column, "'" + raw + "' is not a number");
            }
        }

        int integer(String column) {
            String raw = this.required(column);
            try {
                return Integer.parseInt(raw);
            } catch (NumberFormatException e) {
                throw this.fail(column, "'" + raw + "' is not a whole number");
            }
        }

        /**
         * Whether an optional sub-record's columns are filled in — and all of them, or none. A half
         * filled group is always a typo, and the half that is missing would otherwise be silently
         * read as a default the design never stated.
         */
        boolean group(String what, String... columns) {
            List<String> filled = new ArrayList<>();
            List<String> empty = new ArrayList<>();
            for (String column : columns) {
                (this.present(column) ? filled : empty).add(column);
            }
            if (filled.isEmpty()) {
                return false;
            }
            if (!empty.isEmpty()) {
                throw this.fail(String.join("/", columns),
                        what + " needs every one of its columns or none of them; missing " + empty);
            }
            return true;
        }
    }
}
