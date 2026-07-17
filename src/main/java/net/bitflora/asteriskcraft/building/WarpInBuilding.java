package net.bitflora.asteriskcraft.building;

import net.bitflora.asteriskcraft.faction.Faction;

/**
 * A building core block entity that a {@link BuildingKitItem} warps into the world.
 * The kit stamps the layout, then tags the freshly placed core with the placing
 * player's faction through this — so the kit stays generic across building types
 * (Gateway, Photon Cannon, ...) instead of switching on a concrete class.
 */
public interface WarpInBuilding {
    void setFaction(Faction faction);
}
