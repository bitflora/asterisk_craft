package net.bitflora.asteriskcraft.director.script;

/**
 * One entry of a {@link UnitList}: a unit name (stored verbatim from the script so error messages
 * read naturally; resolved case-insensitively later by {@code ZergUnitCatalog}) and how many to
 * train. The {@code quantity} may be a range, rolled once when the owning command starts.
 */
public record UnitReq(String unitName, IntRange quantity) {
}
