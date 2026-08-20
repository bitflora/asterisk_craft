package net.bitflora.asteriskcraft.building;

import net.bitflora.asteriskcraft.AsteriskCraft;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Guards the spawn-spot search that places freshly produced units around a building, exercised
 * through the pure {@code findGroundSpot(BlockPos, Predicate)} overload so no live ServerLevel is
 * needed. The world lookups it stands in for (air/solid-render/collision-box queries) are the same
 * ones {@code runClient} verifies end to end.
 *
 * <p>The case that matters is a wide unit at a Hive: a Sunken Colony is 1.4 blocks across, so
 * centred on a block it overhangs into the neighbouring column on <em>every</em> side. Spots on the
 * mound's sloping shoulder pass a bare "this column is clear" test yet bury the colony in the mound
 * — and being rooted, it can't walk out, so it suffocates.
 */
class SpawnSpotsTest {

    /** The Hive core, and the mound's extent around it, taken from the hive template (8x7x7, core at 4,2,3). */
    private static final BlockPos CORE = new BlockPos(0, 64, 0);
    private static final int MOUND_MIN_DX = -4;
    private static final int MOUND_MAX_DX = 3;
    private static final int MOUND_MIN_DZ = -3;
    private static final int MOUND_MAX_DZ = 3;

    /** Ground around the mound: a unit's feet rest one block below the core's layer. */
    private static final int GROUND_DY = -1;

    /** A block column occupied by the Hive mound, so nothing can stand in it or overhang into it. */
    private static boolean inMound(int dx, int dz) {
        return dx >= MOUND_MIN_DX && dx <= MOUND_MAX_DX && dz >= MOUND_MIN_DZ && dz <= MOUND_MAX_DZ;
    }

    /**
     * A Hive on flat ground, probed the way the real check does for a unit wider than a block: the
     * body spans the spot's own column plus one to either side, and every one of those nine columns
     * has to be clear of the mound.
     */
    private static final Predicate<BlockPos> WIDE_UNIT_AT_HIVE = spot -> {
        if (spot.getY() - CORE.getY() != GROUND_DY) {
            return false; // only the flat ground around the mound has footing at this height
        }
        for (int ox = -1; ox <= 1; ox++) {
            for (int oz = -1; oz <= 1; oz++) {
                if (inMound(spot.getX() - CORE.getX() + ox, spot.getZ() - CORE.getZ() + oz)) {
                    return false;
                }
            }
        }
        return true;
    };

    @Test
    void wideUnitIsPlacedClearOfTheBuildingItSpawnsAt() {
        BlockPos spot = SpawnSpots.findGroundSpot(CORE, WIDE_UNIT_AT_HIVE);

        assertNotEquals(CORE.above(), spot,
                "the search must find a real spot, not fall back to the core's own column (inside the mound)");
        assertTrue(WIDE_UNIT_AT_HIVE.test(spot), "the chosen spot must be one the unit actually fits in: " + spot);
    }

    @Test
    void narrowUnitStillHugsTheBuilding() {
        // A 1-block-wide unit only needs its own column, so it should land on the innermost searched
        // ring rather than being pushed out with the wide ones.
        Predicate<BlockPos> narrowUnit = spot -> spot.getY() - CORE.getY() == GROUND_DY
                && !inMound(spot.getX() - CORE.getX(), spot.getZ() - CORE.getZ());
        BlockPos spot = SpawnSpots.findGroundSpot(CORE, narrowUnit);

        assertTrue(narrowUnit.test(spot), "the chosen spot must be one the unit actually fits in: " + spot);
        int ring = Math.max(Math.abs(spot.getX() - CORE.getX()), Math.abs(spot.getZ() - CORE.getZ()));
        assertTrue(ring <= 4, "a narrow unit should stay close to its building, was " + ring + " blocks out");
    }

    @Test
    void fallsBackWhenNothingFits() {
        assertNotEquals(null, SpawnSpots.findGroundSpot(CORE, spot -> false));
        assertTrue(CORE.above().equals(SpawnSpots.findGroundSpot(CORE, spot -> false)),
                "with nowhere to stand the unit still has to go somewhere");
    }

    @Test
    void asecondRootedUnitDoesNotLandOnTheFirst() {
        // The Hive plants a Sunken Colony and then a Spore Colony, both through this same search.
        // It is deterministic, so without the occupancy half of the real `fits` check the second one
        // is handed the identical block and — being rooted — stands inside the first forever.
        BlockPos first = SpawnSpots.findGroundSpot(CORE, WIDE_UNIT_AT_HIVE);

        // What the live check adds: a spot whose body overlaps an already-placed rooted unit is not
        // open. Both colonies are 1.4 wide, so their bodies overlap at one block apart too.
        Predicate<BlockPos> clearOfTheFirst = spot -> WIDE_UNIT_AT_HIVE.test(spot)
                && (Math.abs(spot.getX() - first.getX()) > 1 || Math.abs(spot.getZ() - first.getZ()) > 1);
        BlockPos second = SpawnSpots.findGroundSpot(CORE, clearOfTheFirst);

        assertNotEquals(first, second, "the second rooted unit must not be placed on top of the first");
        assertTrue(clearOfTheFirst.test(second), "the chosen spot must clear the unit already standing: " + second);
    }

    @Test
    void bothColoniesOverhangEachOtherAtOneBlockApart() {
        // Why the occupancy test uses the whole body and not just the centre column: two 1.4-wide
        // colonies on neighbouring blocks still intersect, so "a different block" isn't far enough.
        float span = (AsteriskCraft.SUNKEN_COLONY.get().getWidth() + AsteriskCraft.SPORE_COLONY.get().getWidth()) / 2.0f;
        assertTrue(span > 1.0f, "adjacent blocks would still bury one colony in the other");
    }

    @Test
    void sunkenColonyIsWiderThanItsOwnColumn() {
        // The reason the spot search is given the entity type at all. If this ever drops to <= 1.0
        // the overhang goes away, but so does the mound-sized silhouette the colony is drawn with.
        assertTrue(AsteriskCraft.SUNKEN_COLONY.get().getWidth() > 1.0f,
                "a Sunken Colony overhangs its column, so its whole body must be checked for clearance");
    }
}
