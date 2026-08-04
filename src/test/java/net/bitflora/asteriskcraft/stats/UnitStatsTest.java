package net.bitflora.asteriskcraft.stats;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Structural invariants over the whole {@link UnitStats} roster — the executable form of the
 * design rules a malformed entry could silently violate (a rooted unit that forgot {@code .rooted()},
 * a flyer whose path length is too short, a Protoss unit that snuck in an any-item cost).
 *
 * <p>Cross-unit balance relations (Scout vs Mutalisk, cannon out-ranging everything, etc.) live in
 * {@link UnitBalanceTest} instead — this class is about the shape of the table, not the design
 * choices behind individual numbers.
 */
class UnitStatsTest {

    @Test
    void rosterIsCompleteAndUnique() {
        assertEquals(11, UnitStats.all().size(), "one entry per unit type in the mod");
        Set<String> ids = new HashSet<>();
        for (UnitStat stat : UnitStats.all()) {
            assertFalse(stat.id().isBlank(), "id must not be blank");
            assertEquals(stat.id().toLowerCase(), stat.id(), stat.id() + ": id must be lower-case");
            assertTrue(ids.add(stat.id()), "duplicate id: " + stat.id());
        }
    }

    @Test
    void everyUnitHasSaneBaseNumbers() {
        for (UnitStat stat : UnitStats.all()) {
            assertTrue(stat.maxHealth() > 0, stat.id() + ": health must be positive");
            assertTrue(stat.followRange() > 0, stat.id() + ": follow range must be positive");
            assertTrue(stat.movementSpeed() >= 0, stat.id() + ": speed must not be negative");
            assertTrue(stat.armor() >= 0, stat.id() + ": armor must not be negative");
            assertTrue(stat.shield() >= 0, stat.id() + ": shield must not be negative");
            assertTrue(stat.knockbackResistance() >= 0 && stat.knockbackResistance() <= 1,
                    stat.id() + ": knockback resistance must be in [0,1]");
        }
    }

    @Test
    void onlyTheTwoWorkersLackAttackDamage() {
        for (UnitStat stat : UnitStats.all()) {
            boolean isWorker = stat == UnitStats.PROBE || stat == UnitStats.DRONE;
            assertEquals(isWorker, stat.attackDamage().isEmpty(),
                    stat.id() + ": attack damage presence should match worker status");
        }
    }

    @Test
    void onlyStaticDefenceIsRooted() {
        // The executable form of the MOVEMENT_SPEED-defaults-to-0.7 trap: exactly these two units
        // must be zero-speed / full-knockback-resistance, and nothing else should be.
        for (UnitStat stat : UnitStats.all()) {
            boolean isStaticDefence = stat == UnitStats.PHOTON_CANNON || stat == UnitStats.SUNKEN_COLONY;
            assertEquals(isStaticDefence, stat.movementSpeed() == 0.0,
                    stat.id() + ": zero movement speed should match static-defence status");
            assertEquals(isStaticDefence, stat.knockbackResistance() == 1.0,
                    stat.id() + ": full knockback resistance should match static-defence status");
        }
    }

    @Test
    void everyFlyerOutrangesItsOwnCruisingAltitude() {
        for (UnitStat stat : UnitStats.all()) {
            if (stat.flight().isEmpty()) {
                continue;
            }
            UnitStat.Flight flight = stat.flightOrThrow();
            UnitStat.Ranged ranged = stat.rangedOrThrow();
            assertTrue(flight.hoverHeight() < ranged.range(),
                    stat.id() + ": must out-range its own cruising altitude");
            double horizontal = Math.sqrt((double) ranged.range() * ranged.range()
                    - (double) flight.hoverHeight() * flight.hoverHeight());
            assertTrue(horizontal >= 6.0,
                    stat.id() + ": engaging from altitude must leave a proper ranged stand-off");
        }
    }

    @Test
    void everyFlyerCanPathToItsFullFollowRange() {
        // Unasserted anywhere before this table: a flyer whose navigation gives up short of its own
        // follow range will abandon distant targets mid-approach (see HoverFlyingNavigation).
        for (UnitStat stat : UnitStats.all()) {
            if (stat.flight().isEmpty()) {
                continue;
            }
            assertTrue(stat.flightOrThrow().requiredPathLength() >= stat.followRange(),
                    stat.id() + ": required path length must reach at least as far as follow range");
        }
    }

    @Test
    void strikeAnimationsFitInsideTheirCooldown() {
        for (UnitStat stat : UnitStats.all()) {
            if (stat.attackAnimTicks() <= 0 || stat.ranged().isEmpty()) {
                continue;
            }
            int cooldown = stat.rangedOrThrow().cooldown();
            assertTrue(stat.attackAnimTicks() > 0 && stat.attackAnimTicks() < cooldown,
                    stat.id() + ": strike animation should fit inside its own cooldown");
        }
    }

    @Test
    void bouncingUnitsAlwaysHaveARangedAttackToChainFrom() {
        for (UnitStat stat : UnitStats.all()) {
            if (stat.bounce().isEmpty()) {
                continue;
            }
            assertTrue(stat.ranged().isPresent(), stat.id() + ": a bouncing attack must first be a ranged one");
            assertTrue(stat.attackDamage().isPresent(), stat.id() + ": a bounce falls off a base attack damage");
            UnitStat.Bounce bounce = stat.bounceOrThrow();
            assertTrue(bounce.maxHits() >= 1, stat.id() + ": a chain must hit at least its primary target");
            assertTrue(bounce.damageFalloff() > 0 && bounce.damageFalloff() <= 1,
                    stat.id() + ": falloff must shrink damage, not grow or zero it");
            assertTrue(bounce.searchRadius() > 0, stat.id() + ": a chain needs a positive search radius");
        }
    }

    @Test
    void staticDefenceNeverAcquiresBeyondItsReach() {
        for (UnitStat stat : UnitStats.all()) {
            boolean isStaticDefence = stat == UnitStats.PHOTON_CANNON || stat == UnitStats.SUNKEN_COLONY;
            if (!isStaticDefence) {
                continue;
            }
            assertEquals(stat.followRange(), (double) stat.rangedOrThrow().range(), 0.0001,
                    stat.id() + ": a rooted attacker must never target something it can't reach");
        }
    }

    @Test
    void buildTimeExistsExactlyForTheUnitsThatAreTrained() {
        // Every producer reads buildTicks now — the Gateway and Nexus queues and the Zerg director's
        // training cadence — so a purchasable unit missing one would pop out on the tick it was
        // ordered, and a pre-placed unit carrying one would state a time nothing ever counts down.
        for (UnitStat stat : UnitStats.all()) {
            assertEquals(stat.cost().isPurchasable(), stat.buildTicks() > 0,
                    stat.id() + ": build time presence should match whether the unit is trained");
        }
    }

    @Test
    void everyPurchasableCostHasPositiveAmounts() {
        for (UnitStat stat : UnitStats.all()) {
            if (!stat.cost().isPurchasable()) {
                continue;
            }
            assertFalse(stat.cost().alternatives().isEmpty(), stat.id() + ": purchasable cost needs an alternative");
            for (var bundle : stat.cost().alternatives()) {
                assertFalse(bundle.isEmpty(), stat.id() + ": a cost bundle needs at least one line");
                for (ResourceAmount line : bundle) {
                    assertTrue(line.amount() > 0, stat.id() + ": every cost line must be a positive amount");
                }
            }
        }
    }

    @Test
    void probeCostIsWoodThenStoneInThatOrder() {
        // Load-bearing for the Nexus's button mapping: option 0 = Wood, option 1 = Stone.
        var alternatives = UnitStats.PROBE.cost().alternatives();
        assertEquals(2, alternatives.size());
        assertEquals(Resource.WOOD, alternatives.get(0).get(0).resource());
        assertEquals(Resource.STONE, alternatives.get(1).get(0).resource());
    }

    @Test
    void protossStaysPickyAndZergPaysInAnything() {
        for (UnitStat stat : UnitStats.PROTOSS_ROSTER) {
            if (!stat.cost().isPurchasable()) {
                continue;
            }
            for (var bundle : stat.cost().alternatives()) {
                for (ResourceAmount line : bundle) {
                    assertTrue(line.resource() != Resource.ANY, stat.id() + ": Protoss costs should never be ANY");
                }
            }
        }
        for (UnitStat stat : UnitStats.ZERG_ROSTER) {
            if (!stat.cost().isPurchasable()) {
                continue;
            }
            for (var bundle : stat.cost().alternatives()) {
                for (ResourceAmount line : bundle) {
                    assertEquals(Resource.ANY, line.resource(), stat.id() + ": Zerg costs should always be ANY");
                }
            }
        }
    }

    @Test
    void kitBoughtAndPrePlacedUnitsHaveNoDirectCost() {
        assertEquals(UnitCost.NONE, UnitStats.PHOTON_CANNON.cost());
        assertEquals(UnitCost.NONE, UnitStats.SUNKEN_COLONY.cost());
    }

    @Test
    void stoneLabelIsCobblestoneNotStone() {
        // Interpolated verbatim into message.asteriskcraft.nexus.cannot_afford — a label change is a
        // player-visible text change.
        assertEquals("cobblestone", Resource.STONE.label());
    }

}
