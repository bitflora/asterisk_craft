package net.bitflora.asteriskcraft.stats;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Cross-unit balance relations — the design intent behind the numbers in {@link UnitStats}, kept
 * separate from {@link UnitStatsTest}'s structural invariants. These are the assertions worth
 * having once the numbers themselves live in one readable table: they catch a tweak that has a
 * consequence (e.g. buffing the Mutalisk past the Scout it's meant to lose to), rather than
 * duplicating a number that's already sitting in {@code UnitStats} three lines away.
 */
class UnitBalanceTest {

    @Nested
    class PhotonCannon {
        // The Protoss static defence: the hard counter to air on both sides, so it must out-range
        // every mobile unit in the mod.
        @Test
        void outrangesEveryMobileUnit() {
            float cannonRange = UnitStats.PHOTON_CANNON.rangedOrThrow().range();
            assertTrue(cannonRange > UnitStats.SCOUT.rangedOrThrow().range());
            assertTrue(cannonRange > UnitStats.MUTALISK.rangedOrThrow().range());
            assertTrue(cannonRange > UnitStats.DRAGOON.rangedOrThrow().range());
            assertTrue(cannonRange > UnitStats.HYDRALISK.rangedOrThrow().range());
        }
    }

    @Nested
    class StaticDefenceTradeoff {
        // The two static defences trade cadence against damage: the Sunken Colony hits far harder
        // but on a much slower cadence than the Photon Cannon.
        @Test
        void sunkenColonyHitsHarderButSlowerThanPhotonCannon() {
            assertTrue(UnitStats.SUNKEN_COLONY.attackDamageOrThrow() > UnitStats.PHOTON_CANNON.attackDamageOrThrow());
            assertTrue(UnitStats.SUNKEN_COLONY.rangedOrThrow().cooldown() > UnitStats.PHOTON_CANNON.rangedOrThrow().cooldown());
        }

        @Test
        void sunkenColonyOutrangesHydralisk() {
            assertTrue(UnitStats.SUNKEN_COLONY.rangedOrThrow().range() > UnitStats.HYDRALISK.rangedOrThrow().range());
        }
    }

    @Nested
    class AirDuel {
        // The Scout is the Protoss answer to the Mutalisk: same envelope (altitude, reach, cadence)
        // so the two meet on even terms, decided by staying power and punch, both of which the Scout
        // has — and it costs more to reflect that.
        @Test
        void scoutAndMutaliskShareTheSameFlightEnvelope() {
            assertTrue(UnitStats.SCOUT.flightOrThrow().hoverHeight() == UnitStats.MUTALISK.flightOrThrow().hoverHeight());
            assertTrue(UnitStats.SCOUT.rangedOrThrow().range() == UnitStats.MUTALISK.rangedOrThrow().range());
            assertTrue(UnitStats.SCOUT.rangedOrThrow().cooldown() == UnitStats.MUTALISK.rangedOrThrow().cooldown());
        }

        @Test
        void scoutWinsTheDuelOnStayingPowerAndPunch() {
            double scoutEffectiveHp = UnitStats.SCOUT.maxHealth() + UnitStats.SCOUT.shield();
            assertTrue(scoutEffectiveHp > UnitStats.MUTALISK.maxHealth());
            assertTrue(UnitStats.SCOUT.attackDamageOrThrow() > UnitStats.MUTALISK.attackDamageOrThrow());
        }

        @Test
        void scoutIsTheCostliestGatewayUnit() {
            // More stone than a Dragoon, and the only unit gated on refined metal.
            assertTrue(UnitStats.SCOUT.cost().amountOf(Resource.STONE) > UnitStats.DRAGOON.cost().amountOf(Resource.STONE));
            assertTrue(UnitStats.SCOUT.cost().amountOf(Resource.IRON) > 0);
        }
    }

    @Nested
    class RangedUnitEnvelopes {
        // Cadence: Hydralisk and Scout both fire faster than a Dragoon.
        @Test
        void hydraliskAndScoutOutpaceDragoon() {
            assertTrue(UnitStats.HYDRALISK.rangedOrThrow().cooldown() < UnitStats.DRAGOON.rangedOrThrow().cooldown());
            assertTrue(UnitStats.SCOUT.rangedOrThrow().cooldown() < UnitStats.DRAGOON.rangedOrThrow().cooldown());
        }

        // Reach: Dragoon out-reaches Hydralisk (its Zerg mirror); Scout out-reaches Dragoon.
        @Test
        void dragoonOutreachesHydraliskAndScoutOutreachesDragoon() {
            assertTrue(UnitStats.DRAGOON.rangedOrThrow().range() > UnitStats.HYDRALISK.rangedOrThrow().range());
            assertTrue(UnitStats.SCOUT.rangedOrThrow().range() > UnitStats.DRAGOON.rangedOrThrow().range());
        }

        // Floors: neither ranged unit is so short-ranged it reads as near-melee.
        @Test
        void radiusFloors() {
            assertTrue(UnitStats.DRAGOON.rangedOrThrow().range() >= 6.0f);
            assertTrue(UnitStats.HYDRALISK.rangedOrThrow().range() >= 5.0f);
        }
    }

    @Nested
    class MutaliskVsSunkenColony {
        @Test
        void mutaliskHitsSofterThanASunkenColony() {
            assertTrue(UnitStats.MUTALISK.attackDamageOrThrow() < UnitStats.SUNKEN_COLONY.attackDamageOrThrow());
        }
    }

    @Nested
    class ZergCosts {
        @Test
        void mutaliskAndHydraliskCostTheSameAndZerglingIsCheapest() {
            int mutaliskCost = UnitStats.MUTALISK.cost().amountOf(Resource.ANY);
            int hydraliskCost = UnitStats.HYDRALISK.cost().amountOf(Resource.ANY);
            int zerglingCost = UnitStats.ZERGLING.cost().amountOf(Resource.ANY);
            assertTrue(mutaliskCost == hydraliskCost);
            assertTrue(zerglingCost < hydraliskCost);
            assertTrue(zerglingCost < mutaliskCost);
        }
    }
}
