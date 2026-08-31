package net.bitflora.asteriskcraft.stats;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Structural invariants over the whole {@link UnitStats} roster — the executable form of the
 * design rules a malformed entry could silently violate (a rooted unit that forgot {@code .rooted()},
 * a flyer whose path length is too short, a Protoss unit that snuck in an any-item cost).
 *
 * <p>This class is about the shape of the table, not the design choices behind individual numbers.
 * Since the numbers moved into {@code asteriskcraft/balance/unit_stats.csv} it is also what proves
 * the shipped file parses at all: every test here reads the real table through {@link UnitStats}.
 * The <em>format</em> those numbers are written in is {@link UnitStatCsvTest}'s subject instead.
 */
class UnitStatsTest {

    @Test
    void rosterIsCompleteAndUnique() {
        assertEquals(26, UnitStats.all().size(), "one entry per unit type in the mod");
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
    void onlyNonCombatUnitsLackAttackDamage() {
        for (UnitStat stat : UnitStats.all()) {
            assertEquals(isNonCombatant(stat), stat.attackDamage().isEmpty(),
                    stat.id() + ": attack damage presence should match non-combatant status");
        }
    }

    @Test
    void onlyStructuresAreRooted() {
        // The executable form of the MOVEMENT_SPEED-defaults-to-0.7 trap: exactly the rooted
        // structures must be zero-speed / full-knockback-resistance, and nothing else should be.
        for (UnitStat stat : UnitStats.all()) {
            boolean isStructure = isRootedStructure(stat);
            assertEquals(isStructure, stat.movementSpeed() == 0.0,
                    stat.id() + ": zero movement speed should match rooted-structure status");
            assertEquals(isStructure, stat.knockbackResistance() == 1.0,
                    stat.id() + ": full knockback resistance should match rooted-structure status");
        }
    }

    @Test
    void everyFlyerOutrangesItsOwnCruisingAltitude() {
        for (UnitStat stat : UnitStats.all()) {
            if (stat.flight().isEmpty()) {
                continue;
            }
            if (stat.ranged().isEmpty()) {
                // An unarmed flyer (the Overlord) has no stand-off to check: it never engages from
                // altitude, so there is no range for its cruising height to have to clear.
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
    void everyFlyerSharesTheOneHoverHeight() {
        // A rule about the set rather than about any row: a Zerg player learns one altitude, not
        // one per unit. It used to hold because every entry interpolated the same constant; in a
        // grid each flyer writes its own cell, so the agreement has to be asserted.
        for (UnitStat stat : UnitStats.all()) {
            if (stat.flight().isEmpty()) {
                continue;
            }
            assertEquals(UnitStats.HOVER_HEIGHT, stat.flightOrThrow().hoverHeight(),
                    stat.id() + ": every flyer cruises at the one shared altitude");
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
            if (!isStaticDefence(stat)) {
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
    void protossAndTerranStayPickyAndZergPaysInAnything() {
        for (UnitStat stat : Stream.concat(UnitStats.PROTOSS_ROSTER.stream(), UnitStats.TERRAN_ROSTER.stream())
                .toList()) {
            if (!stat.cost().isPurchasable()) {
                continue;
            }
            for (var bundle : stat.cost().alternatives()) {
                for (ResourceAmount line : bundle) {
                    assertTrue(line.resource() != Resource.ANY,
                            stat.id() + ": only Zerg costs may be ANY");
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
        assertEquals(UnitCost.NONE, UnitStats.SPORE_COLONY.cost());
        assertEquals(UnitCost.NONE, UnitStats.BUNKER.cost());
        assertEquals(UnitCost.NONE, UnitStats.MISSILE_TURRET.cost());
    }

    /**
     * The units that never attack anything: two of the three workers, and the two detectors that
     * fly — the Overlord and the Observer — whose whole job is to carry a detection bubble around
     * and which pay for that by being unable to defend themselves.
     *
     * <p>The Bunker is the odd one: it is a structure rather than a unit, and it has no attack because
     * everything it does to an enemy is done by whatever is inside it. Which is also why it is the
     * only entry here that is dangerous to walk up to.
     *
     * <p>The SCV is deliberately not among them — it is the one worker in the mod that is armed, and
     * that is a statement about the Terran rather than an oversight. It still never picks a fight:
     * what keeps it a worker is the absence of a target-acquisition goal, not the absence of a
     * weapon. See {@code entity.terran.ScvEntity}.
     */
    private static boolean isNonCombatant(UnitStat stat) {
        return stat == UnitStats.PROBE
                || stat == UnitStats.DRONE
                || stat == UnitStats.OVERLORD
                || stat == UnitStats.OBSERVER
                || stat == UnitStats.BUNKER
                || stat == UnitStats.SCIENCE_VESSEL;
    }

    /**
     * Every structure that stands where it is put: the three armed defences below, plus the Bunker.
     *
     * <p>Kept separate from {@link #isStaticDefence} because the Bunker split the two ideas apart.
     * Until it existed, "rooted" and "a static defence" named the same three entries, and one
     * predicate covered both the movement-speed invariant and the reach invariant. A Bunker is rooted
     * and has no weapon at all, so it belongs to the first and not the second.
     */
    private static boolean isRootedStructure(UnitStat stat) {
        return isStaticDefence(stat) || stat == UnitStats.BUNKER;
    }

    /**
     * The rooted defences that <em>shoot</em>: the Protoss Photon Cannon, which answers both layers
     * of the sky on its own, and the two pairs the other races split that job across — the Zerg's
     * Sunken and Spore Colonies, and the Terran's Bunker-and-turret, of which only the turret is a
     * gun. The Bunker itself is deliberately not among them: it is a rooted structure with no weapon,
     * so it has no reach for {@link #staticDefenceNeverAcquiresBeyondItsReach} to check.
     */
    private static boolean isStaticDefence(UnitStat stat) {
        return stat == UnitStats.PHOTON_CANNON
                || stat == UnitStats.SUNKEN_COLONY
                || stat == UnitStats.SPORE_COLONY
                || stat == UnitStats.MISSILE_TURRET;
    }

    @Test
    void stoneLabelIsCobblestoneNotStone() {
        // Interpolated verbatim into message.asteriskcraft.nexus.cannot_afford — a label change is a
        // player-visible text change.
        assertEquals("cobblestone", Resource.STONE.label());
    }

}
