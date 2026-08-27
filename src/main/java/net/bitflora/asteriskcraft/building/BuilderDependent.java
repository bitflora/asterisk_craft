package net.bitflora.asteriskcraft.building;

/**
 * An item that places a building somebody has to come and build — the third sibling of
 * {@link PsiDependent} and {@link CreepDependent}, mirrored deliberately: which buildings need a
 * worker is a flag on the placing item rather than a case inside {@link ConstructionSite}, and there
 * are the same two unrelated implementors, {@link BuildingKitItem} and
 * {@code entity/FactionSpawnEggItem}.
 *
 * <p>Unlike the other two this is not a question about the <em>ground</em>, so it names no field to
 * be within — the answer is a worker, found by {@link ConstructionSite#callBuilder}. A placement
 * that finds none is refused outright, exactly as an unpowered one is.
 *
 * <p>It is the Terran mechanic (a Bunker and a Missile Turret are welded together by an SCV rather
 * than warped in), but nothing here says so: the flag is per item, so a Protoss or Zerg kit could
 * set it and a Terran structure later re-authored as an {@code .nbt} template already has it.
 */
public interface BuilderDependent {
    /** Whether this item's building has to be built by a worker rather than standing itself up. */
    boolean requiresBuilder();
}
