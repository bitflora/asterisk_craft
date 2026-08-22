package net.bitflora.asteriskcraft.building;

/**
 * An item that places a building the Pylon prerequisite applies to.
 *
 * <p>Which buildings are exempt is deliberately a flag on the placing item rather than a case inside
 * {@link PsiField} — a Nexus and a Pylon simply never ask, and nothing in the rule names a building.
 * This is the other half of that: it lets anything that cares ask "does what the player is holding
 * need power?" without naming an item class. That matters because there are <b>two</b> unrelated
 * ones and always will be — {@link BuildingKitItem} stamps a structure template, while the Photon
 * Cannon is a building that happens to be an entity and so is placed by
 * {@code entity/FactionSpawnEggItem}.
 *
 * <p>Its one client-side user is {@code client/PsiFieldOverlay}, and the Cannon is the case that
 * needs it most: a kit at least gets a coloured volume outline from {@code client/KitPlacementPreview},
 * but an egg draws nothing at all, so without the overlay its refusal has no warning in front of it.
 */
public interface PsiDependent {
    /** Whether this item's building may only be placed within {@link PsiField#RADIUS} of a Pylon. */
    boolean requiresPylon();
}
