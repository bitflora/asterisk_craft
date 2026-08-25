package net.bitflora.asteriskcraft.combat;

import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The Firebat's cone is the mod's first attack that hurts several targets from one shot without
 * being a bounce chain or a detonation, and it is the only part of that unit that is not the
 * Marine's machinery reused. {@link FlameCone} is pure geometry precisely so this can be asserted
 * without a level, an entity or a registry.
 *
 * <p>What is pinned here is the <b>shape</b>, not any unit's numbers — the cone the Firebat actually
 * fires lives on {@code entity.terran.FirebatEntity} and is free to be tuned. So the shape below is
 * a local one chosen to make each property legible, and every assertion is about a relationship
 * (behind is never caught, the mouth is wider than the nozzle) rather than about a value.
 */
class FlameConeTest {

    /** Two blocks long, a quarter of a block across at the nozzle and a full block at the mouth. */
    private static final FlameCone.Shape CONE = new FlameCone.Shape(2.0, 0.25, 1.0, 1.0, 8);

    private static final Vec3 ORIGIN = Vec3.ZERO;
    /** Due north (-Z), so "along the axis" reads as negative Z below. */
    private static final Vec3 NORTH = new Vec3(0.0, 0.0, -1.0);

    /** A unit-sized box centred on a point — roughly what a Zergling occupies. */
    private static AABB unitAt(double x, double y, double z) {
        return new AABB(x - 0.3, y - 0.5, z - 0.3, x + 0.3, y + 0.5, z + 0.3);
    }

    @Test
    void somethingStandingDirectlyAheadIsCaught() {
        assertTrue(FlameCone.catches(ORIGIN, NORTH, CONE, unitAt(0.0, 0.0, -1.0)));
    }

    @Test
    void somethingPastTheReachIsNotCaught() {
        assertFalse(FlameCone.catches(ORIGIN, NORTH, CONE, unitAt(0.0, 0.0, -3.0)),
                "a target a block beyond the cone's reach must not burn");
    }

    @Test
    void somethingBehindIsNeverCaught() {
        // The wedge starts at the attacker and only ever runs forward, so an ally at its back is
        // never in its own flame however wide the mouth is opened.
        assertFalse(FlameCone.catches(ORIGIN, NORTH, CONE, unitAt(0.0, 0.0, 1.0)));
        assertFalse(FlameCone.catches(ORIGIN, NORTH, CONE, unitAt(0.0, 0.0, 2.0)));
    }

    @Test
    void theConeIsWiderAtTheMouthThanAtTheNozzle() {
        // The whole reason this is a cone and not a line: the same lateral offset misses close in and
        // connects at range, so a pack walking abreast is caught where a single file would not be.
        double offset = 0.9;
        assertFalse(FlameCone.catches(ORIGIN, NORTH, CONE, unitAt(offset, 0.0, -0.3)),
                "the nozzle is too tight to catch something this far off the axis");
        assertTrue(FlameCone.catches(ORIGIN, NORTH, CONE, unitAt(offset, 0.0, -2.0)),
                "the mouth has spread far enough to catch the same offset at full reach");
    }

    @Test
    void theBearingIsFlattenedOntoTheHorizontal() {
        // A flamethrower washes over the ground; aiming it up at a target standing on a hill must not
        // tilt the cone off the ground the attacker is standing on.
        Vec3 aimedUp = new Vec3(0.0, 4.0, -1.0);
        assertTrue(FlameCone.catches(ORIGIN, aimedUp, CONE, unitAt(0.0, 0.0, -1.5)));
    }

    @Test
    void somethingWellAboveOrBelowIsOutOfTheWash() {
        assertFalse(FlameCone.catches(ORIGIN, NORTH, CONE, unitAt(0.0, 4.0, -1.0)),
                "a flyer overhead is above the wash");
        assertFalse(FlameCone.catches(ORIGIN, NORTH, CONE, unitAt(0.0, -4.0, -1.0)),
                "something at the bottom of a drop is below the wash");
    }

    @Test
    void theEnvelopeContainsEverythingTheConeCanCatch() {
        // The envelope is the broad-phase query FlameAttacks runs before the exact test, so anything
        // it misses can never be hurt however squarely it sits in the wedge. Swept over a full turn,
        // because a bearing that is not axis-aligned is the case an axis-aligned envelope could get
        // wrong.
        for (int degrees = 0; degrees < 360; degrees += 15) {
            double radians = Math.toRadians(degrees);
            Vec3 bearing = new Vec3(Math.cos(radians), 0.0, Math.sin(radians));
            AABB envelope = FlameCone.envelope(ORIGIN, bearing, CONE);
            for (Vec3 corner : FlameCone.footprint(ORIGIN, bearing, CONE)) {
                assertTrue(envelope.minX - 1.0e-9 <= corner.x && corner.x <= envelope.maxX + 1.0e-9
                                && envelope.minZ - 1.0e-9 <= corner.z && corner.z <= envelope.maxZ + 1.0e-9,
                        "envelope does not contain a corner of its own wedge, bearing " + degrees);
            }
            // And a target squarely in the middle of the wedge has to survive the broad phase.
            Vec3 middle = ORIGIN.add(bearing.scale(1.0));
            assertTrue(envelope.intersects(unitAt(middle.x, middle.y, middle.z)),
                    "broad phase misses a target in the middle of the wedge, bearing " + degrees);
        }
    }

    @Test
    void theSpreadGrowsMonotonicallyFromNozzleToMouth() {
        double previous = 0.0;
        for (FlameCone.Slice slice : FlameCone.samples(ORIGIN, NORTH, CONE)) {
            assertTrue(slice.halfWidth() >= previous, "the cone narrows partway along its own axis");
            previous = slice.halfWidth();
        }
        assertTrue(previous <= CONE.mouthHalfWidth(), "the cone spreads past its declared mouth");
    }

    @Test
    void aTargetOffTheAxisAlongADiagonalBearingIsJudgedTheSameWay() {
        // The wedge is not axis-aligned here, which is the case a box-chain approximation gets wrong:
        // it would over-reach along the world axes and burn something safely past the mouth.
        Vec3 diagonal = new Vec3(1.0, 0.0, -1.0);
        Vec3 justInside = ORIGIN.add(diagonal.normalize().scale(1.8));
        Vec3 wellPast = ORIGIN.add(diagonal.normalize().scale(3.5));
        assertTrue(FlameCone.catches(ORIGIN, diagonal, CONE, unitAt(justInside.x, 0.0, justInside.z)));
        assertFalse(FlameCone.catches(ORIGIN, diagonal, CONE, unitAt(wellPast.x, 0.0, wellPast.z)));
    }
}
