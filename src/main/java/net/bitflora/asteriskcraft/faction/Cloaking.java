package net.bitflora.asteriskcraft.faction;

import net.minecraft.world.entity.Entity;

/**
 * Whether a cloaked unit can be seen — the second half of the question
 * {@link FactionAttachments#isHostile(Entity, Entity)} answers.
 *
 * <p>Cloak is enforced here, in the targeting rule, and <em>not</em> through vanilla's invisibility
 * flag, because vanilla invisibility does not actually hide anything from mob AI. Verified against
 * this version's source: {@code TargetingConditions.test} only scales the acquisition range by
 * {@code LivingEntity.getVisibilityPercent}, which bottoms out at {@code 0.7 * 0.1 = 0.07} for an
 * unarmoured mob, and the result is then floored by
 * {@code Math.max(range * modifier, MIN_VISIBILITY_DISTANCE_FOR_INVISIBLE_TARGET)} where that
 * constant is <b>2.0</b>. An invisible unit is therefore still acquired by anything standing within
 * two blocks of it — useless as a cloak. See docs/neoforge-api-notes.md.
 *
 * <p>The rule is split pure-vs-live exactly like {@code FactionAttachments.isHostile} and
 * {@code building.PhotonCannonTargeting}, so the whole truth table is unit-testable without a
 * {@code ServerLevel}.
 */
public final class Cloaking {
    private Cloaking() {
    }

    /**
     * The pure rule: may a viewer of faction {@code viewer} see {@code candidate}?
     *
     * <p>Anything not cloaked is always visible. A cloaked unit is visible to its own faction (you
     * never lose track of your own units) and to any faction whose bit is set in {@code detectedBy};
     * to everyone else it does not exist. NEUTRAL is nobody's own faction and carries no detectors,
     * so wild hostiles are blind to cloaked units — deliberate, and consistent with the mod's units
     * being the only things that ever cloak.
     */
    public static boolean isVisibleTo(boolean candidateCloaked, Faction candidate, Faction viewer, byte detectedBy) {
        return !candidateCloaked || candidate == viewer || isDetectedBy(detectedBy, viewer);
    }

    /** Live-entity form of {@link #isVisibleTo(boolean, Faction, Faction, byte)}. */
    public static boolean isVisibleTo(Entity candidate, Faction viewer) {
        return isVisibleTo(Cloaked.isCloaked(candidate), FactionAttachments.get(candidate), viewer,
                DetectionAttachments.detectedBy(candidate));
    }

    /** Whether {@code entity} is currently revealed to {@code viewer}'s faction. */
    public static boolean isDetectedBy(Entity entity, Faction viewer) {
        return isDetectedBy(DetectionAttachments.detectedBy(entity), viewer);
    }

    // --- The bitmask, in one place so nothing else does bit arithmetic ---

    public static boolean isDetectedBy(byte mask, Faction viewer) {
        return (mask & bit(viewer)) != 0;
    }

    public static byte with(byte mask, Faction detector) {
        return (byte) (mask | bit(detector));
    }

    public static byte without(byte mask, Faction detector) {
        return (byte) (mask & ~bit(detector));
    }

    private static int bit(Faction faction) {
        return 1 << faction.ordinal();
    }
}
