package net.bitflora.asteriskcraft.director.script;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Turns the plain-text build-script DSL into a {@link BuildScript}. Pure and total: it
 * <b>never throws</b> — every malformed line becomes an entry in {@link BuildScript#errors()}
 * (prefixed with its 1-based line number) and is skipped, so a single typo can never crash the
 * server or brick the AI. Unit names are kept verbatim; resolution to an entity type happens later.
 *
 * <p>Grammar (case-insensitive keywords, {@code #} starts a comment, blank lines ignored):
 * <pre>
 *   Workers: N
 *   Defence: [UnitList]      (also spelled "Defense")
 *   Wave: [UnitList]
 *   Wait N
 *   Repeat N
 *   [UnitList] = Qty Unit, Qty Unit, ...   where Qty is "N" or "N-M"
 * </pre>
 */
public final class BuildScriptParser {
    private BuildScriptParser() {
    }

    public static BuildScript parse(List<String> lines) {
        List<BuildCommand> commands = new ArrayList<>();
        List<String> errors = new ArrayList<>();

        for (int i = 0; i < lines.size(); i++) {
            int lineNo = i + 1;
            String line = stripComment(lines.get(i)).trim();
            if (line.isEmpty()) {
                continue;
            }
            String keyword = keywordOf(line).toLowerCase(Locale.ROOT);
            String arg = argOf(line);

            BuildCommand command = switch (keyword) {
                case "workers" -> parseWorkers(arg, lineNo, errors);
                case "defence", "defense" -> parseUnitCommand(arg, lineNo, errors, false);
                case "wave" -> parseUnitCommand(arg, lineNo, errors, true);
                case "wait" -> parseWait(arg, lineNo, errors);
                case "repeat" -> parseRepeat(arg, lineNo, errors);
                default -> {
                    errors.add("line " + lineNo + ": unknown command '" + keywordOf(line) + "'");
                    yield null;
                }
            };
            if (command != null) {
                commands.add(command);
            }
        }
        return new BuildScript(commands, errors);
    }

    /** The leading command word, up to the first whitespace or colon. */
    private static String keywordOf(String line) {
        int end = 0;
        while (end < line.length() && !Character.isWhitespace(line.charAt(end)) && line.charAt(end) != ':') {
            end++;
        }
        return line.substring(0, end);
    }

    /** Everything after the keyword, with a single leading colon (if any) consumed and trimmed. */
    private static String argOf(String line) {
        String rest = line.substring(keywordOf(line).length()).trim();
        if (rest.startsWith(":")) {
            rest = rest.substring(1).trim();
        }
        return rest;
    }

    private static String stripComment(String line) {
        int hash = line.indexOf('#');
        return hash < 0 ? line : line.substring(0, hash);
    }

    private static BuildCommand parseWorkers(String arg, int lineNo, List<String> errors) {
        if (arg.indexOf('-') >= 0) {
            errors.add("line " + lineNo + ": Workers takes a single number, not a range");
            return null;
        }
        Integer count = parseNonNegative(arg, lineNo, "Workers count", errors);
        return count == null ? null : new BuildCommand.Workers(count);
    }

    private static BuildCommand parseWait(String arg, int lineNo, List<String> errors) {
        Integer seconds = parseNonNegative(arg, lineNo, "Wait seconds", errors);
        return seconds == null ? null : new BuildCommand.Wait(seconds);
    }

    private static BuildCommand parseRepeat(String arg, int lineNo, List<String> errors) {
        Integer count = parseNonNegative(arg, lineNo, "Repeat count", errors);
        if (count == null) {
            return null;
        }
        if (count == 0) {
            errors.add("line " + lineNo + ": Repeat count must be at least 1");
            return null;
        }
        return new BuildCommand.Repeat(count);
    }

    private static BuildCommand parseUnitCommand(String arg, int lineNo, List<String> errors, boolean wave) {
        UnitList units = parseUnitList(arg, lineNo, errors);
        if (units == null) {
            return null;
        }
        return wave ? new BuildCommand.Wave(units) : new BuildCommand.Defence(units);
    }

    private static UnitList parseUnitList(String arg, int lineNo, List<String> errors) {
        List<UnitReq> reqs = new ArrayList<>();
        for (String element : arg.split(",")) {
            String entry = element.trim();
            if (entry.isEmpty()) {
                continue; // tolerate trailing/extra commas
            }
            UnitReq req = parseUnitReq(entry, lineNo, errors);
            if (req != null) {
                reqs.add(req);
            }
        }
        if (reqs.isEmpty()) {
            errors.add("line " + lineNo + ": empty or invalid unit list");
            return null;
        }
        return new UnitList(reqs);
    }

    /** Parses one "Qty Unit" entry, e.g. "3 Zergling" or "3-5 Hydralisk". */
    private static UnitReq parseUnitReq(String entry, int lineNo, List<String> errors) {
        int split = 0;
        while (split < entry.length() && !Character.isWhitespace(entry.charAt(split))) {
            split++;
        }
        if (split >= entry.length()) {
            errors.add("line " + lineNo + ": '" + entry + "' is missing a unit name");
            return null;
        }
        String qtyToken = entry.substring(0, split);
        String unitName = entry.substring(split).trim();
        if (unitName.isEmpty()) {
            errors.add("line " + lineNo + ": '" + entry + "' is missing a unit name");
            return null;
        }
        IntRange quantity = parseRange(qtyToken, lineNo, errors);
        return quantity == null ? null : new UnitReq(unitName, quantity);
    }

    private static IntRange parseRange(String token, int lineNo, List<String> errors) {
        int dash = token.indexOf('-');
        if (dash < 0) {
            Integer value = parseNonNegative(token, lineNo, "quantity", errors);
            return value == null ? null : IntRange.single(value);
        }
        Integer min = parseNonNegative(token.substring(0, dash), lineNo, "range minimum", errors);
        Integer max = parseNonNegative(token.substring(dash + 1), lineNo, "range maximum", errors);
        if (min == null || max == null) {
            return null;
        }
        return new IntRange(min, max);
    }

    private static Integer parseNonNegative(String text, int lineNo, String what, List<String> errors) {
        String trimmed = text.trim();
        try {
            int value = Integer.parseInt(trimmed);
            if (value < 0) {
                errors.add("line " + lineNo + ": " + what + " must not be negative ('" + trimmed + "')");
                return null;
            }
            return value;
        } catch (NumberFormatException e) {
            errors.add("line " + lineNo + ": expected a number for " + what + " but got '" + trimmed + "'");
            return null;
        }
    }
}
