package net.bitflora.asteriskcraft.building;

/**
 * The {@link SiegeTarget} whose destruction decides the match — the Nexus and each Zerg Hive. It adds
 * no behaviour of its own: everything about taking damage lives in {@link SiegeTarget}/
 * {@link BuildingDefense}, and this is purely the marker that separates "losing this loses the game"
 * from an ordinary production building like the Gateway. {@link CoreCensus} and {@code GameOutcome}
 * count and judge cores; {@link net.bitflora.asteriskcraft.entity.ai.SiegeBlockGoal} deliberately
 * doesn't know the difference, so a unit batters whichever enemy building it reaches.
 */
public interface FactionCore extends SiegeTarget {
    /** Siege health of a core. At the default assault rate a lone unit needs a real fight to raze one. */
    int CORE_MAX_HEALTH = 300;
}
