package net.bitflora.asteriskcraft.entity;

/**
 * Marks a unit that projects Zerg creep range for {@code building/CreepField} — the Sunken and
 * Spore Colonies. Deliberately its own marker rather than {@link Rooted}: a burrowed Lurker is
 * rooted too, but it is a mobile unit taking cover, not a structure, and must not count as a creep
 * source. Deliberately faction-generic and deliberately a marker, exactly like {@link Rooted} and
 * {@link Flyer} — {@code CreepField} tests it rather than the concrete entity classes, so a future
 * Zerg structure opts in with a bare {@code implements} and no change to the rule.
 */
public interface CreepSource {
}
