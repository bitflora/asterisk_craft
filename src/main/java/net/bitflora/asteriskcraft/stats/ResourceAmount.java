package net.bitflora.asteriskcraft.stats;

/** One resource requirement inside a {@link UnitCost} bundle: a kind and an amount. */
public record ResourceAmount(Resource resource, int amount) {
}
