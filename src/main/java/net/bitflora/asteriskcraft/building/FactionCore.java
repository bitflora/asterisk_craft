package net.bitflora.asteriskcraft.building;

/**
 * The {@link SiegeTarget} whose destruction decides the match — an army's bases. It adds no
 * behaviour of its own: everything about taking damage lives in {@link SiegeTarget}/
 * {@link BuildingDefense}, and this is purely the marker that separates "losing this loses the game"
 * from an ordinary production building like the Gateway. {@link CoreCensus} and {@code GameOutcome}
 * count and judge cores; {@link net.bitflora.asteriskcraft.entity.ai.SiegeBlockGoal} deliberately
 * doesn't know the difference, so a unit batters whichever enemy building it reaches.
 *
 * <p>How much a core can take is <em>not</em> here: it is a balance number that differs per race,
 * so it lives with the rest of them in {@code race.RaceProfile#baseHealth()}.
 */
public interface FactionCore extends SiegeTarget {
}
