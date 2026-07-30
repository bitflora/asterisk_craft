package net.bitflora.asteriskcraft.building;

import net.bitflora.asteriskcraft.faction.Faction;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

/**
 * A building block entity an enemy army has to batter down rather than simply dig out: it holds real
 * siege HP (and, for a Protoss building, a shield buffer) in a {@link BuildingDefense}.
 * {@link net.bitflora.asteriskcraft.entity.ai.SiegeBlockGoal} only ever asks a target for its
 * {@link #buildingFaction()} and calls {@link #damageBuilding}, so it stays faction-generic and never
 * needs to know whether it is chewing on a Nexus, a Hive or a Gateway.
 *
 * <p>{@link FactionCore} is the narrower subset whose fall decides the match — a Gateway is a siege
 * target but not a core, so losing one costs production, not the game.
 */
public interface SiegeTarget {
    /** The faction this building belongs to. A unit only sieges buildings whose faction is its enemy. */
    Faction buildingFaction();

    /**
     * This building's HP/shield/warp state, for anything that only wants to read it — the Jade
     * tooltip ({@code compat/jade/BuildingDefenseProvider}) reports every building through this one
     * accessor instead of switching on Nexus-vs-Gateway-vs-Hive.
     */
    BuildingDefense defense();

    /**
     * Applies {@code amount} points of siege damage — shields first, then HP. When HP reaches zero
     * the building removes its own core block (which, for a {@link FactionCore}, fires the win/lose
     * through {@code preRemoveSideEffects}).
     */
    void damageBuilding(int amount, ServerLevel level, BlockPos pos);
}
