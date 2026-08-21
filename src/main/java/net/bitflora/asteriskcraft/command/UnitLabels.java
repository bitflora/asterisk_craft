package net.bitflora.asteriskcraft.command;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EntityType;

import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The one-letter code each unit shows up as in a unit-group label, and the formatter that turns a
 * group's composition into the text on its block — one Zealot and three Dragoons read {@code 1Z 3D}.
 *
 * <p>Keyed by the unit's stable id ({@code stats.UnitStat#id}), which is also its registry path, so
 * this table needs no {@code EntityType} constants and resolves with a single registry lookup.
 *
 * <p><b>Why the letters aren't a field on {@code UnitStat}:</b> that record is deliberately
 * registry-free and holds "everything a balance pass would tweak" (see its class doc). An
 * abbreviation is neither a balance number nor something a tuning pass touches, so it lives here
 * with the UI that consumes it.
 *
 * <p>Letters need only be unique <em>within a faction</em>, and {@code UnitLabelsTest} pins that:
 * {@code CommandInputResolver.friendlyUnitAt} admits only own-faction units into a selection, so a
 * group can never mix Zealots with Zerglings even though both are {@code Z}.
 */
public final class UnitLabels {
    /** How many type entries a label spells out before it collapses into a {@code +}. */
    public static final int MAX_ENTRIES = 3;
    /** Appended when a group holds more than {@link #MAX_ENTRIES} distinct unit types. */
    public static final String OVERFLOW = "+";

    private static final Map<String, String> LETTERS = Map.ofEntries(
            // Protoss
            Map.entry("probe", "P"),
            Map.entry("zealot", "Z"),
            Map.entry("dragoon", "D"),
            Map.entry("scout", "S"),
            Map.entry("photon_cannon", "C"),
            // T for Templar: D is the Dragoon's, and the letters only need to be unique per roster.
            Map.entry("dark_templar", "T"),
            // Zerg
            Map.entry("drone", "D"),
            Map.entry("zergling", "Z"),
            Map.entry("hydralisk", "H"),
            Map.entry("mutalisk", "M"),
            Map.entry("ultralisk", "U"),
            Map.entry("lurker", "L"),
            // Sunken Colony takes K, since S is the Scout's and both rosters are checked separately
            Map.entry("sunken_colony", "K"),
            // ...which leaves S free on the Zerg side for the Spore Colony
            Map.entry("spore_colony", "S"));

    private UnitLabels() {
    }

    /** The letter for a unit id, or empty if that id has none. Registry-free — what the test calls. */
    public static String letterForId(String unitId) {
        return LETTERS.getOrDefault(unitId, "");
    }

    /**
     * The letter for a live unit type. Anything not in the table (a vanilla mob that somehow got
     * grouped) falls back to the first letter of its registry path, so a label is never blank.
     */
    public static String letterFor(EntityType<?> type) {
        Identifier key = BuiltInRegistries.ENTITY_TYPE.getKey(type);
        String path = key == null ? "" : key.getPath();
        String letter = letterForId(path);
        if (!letter.isEmpty()) {
            return letter;
        }
        return path.isEmpty() ? "?" : path.substring(0, 1).toUpperCase(Locale.ROOT);
    }

    /**
     * A group's block label: {@code "1Z 3D"}. Entries keep the order they were given (selection
     * order), and beyond {@link #MAX_ENTRIES} types the rest collapse into a {@code +} rather than
     * running off the edge of the block.
     */
    public static String format(List<UnitGroupSnapshot.Entry> entries) {
        StringBuilder text = new StringBuilder();
        int shown = Math.min(entries.size(), MAX_ENTRIES);
        for (int i = 0; i < shown; i++) {
            UnitGroupSnapshot.Entry entry = entries.get(i);
            if (i > 0) {
                text.append(' ');
            }
            text.append(entry.count()).append(letterFor(entry.type()));
        }
        if (entries.size() > MAX_ENTRIES) {
            text.append(OVERFLOW);
        }
        return text.toString();
    }
}
