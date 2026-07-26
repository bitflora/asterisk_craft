package net.bitflora.asteriskcraft.entity.protoss;

import net.bitflora.asteriskcraft.building.GatewayBlockEntity;
import net.bitflora.asteriskcraft.entity.zerg.MutaliskEntity;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Guards the Scout's design numbers and the relationships that make it the Protoss answer to the
 * Mutalisk rather than a straight copy of it.
 *
 * <p>Flight itself is deliberately <b>not</b> covered here, for the same reason as {@code MutaliskTest}:
 * hovering, path following over broken terrain and "melee units genuinely cannot reach it" all need a
 * live world with loaded chunks and a running navigation, none of which the JUnit bootstrap provides.
 * Cost <em>payment</em> is likewise untestable here — {@code ItemTags} aren't bound in the bootstrap
 * (see {@code ProbeEconomyTest}). Both are verified via {@code runClient}. What is pinned here is
 * everything that is pure arithmetic on the constants.
 */
class ScoutTest {

    @Test
    void statsMatchDesign() {
        assertEquals(75, ScoutEntity.MAX_HEALTH, "Scout should have 75 HP");
        assertEquals(50, ScoutEntity.SHIELD, "Scout should have 50 shields");
        assertEquals(11.0f, ScoutEntity.ATTACK_DAMAGE, "Each blaster volley should deal 11 damage");
        assertEquals(9.0f, ScoutEntity.ATTACK_RADIUS, "Scout should reach 9 blocks");
        assertEquals(30, ScoutEntity.ATTACK_COOLDOWN, "Scout should fire every 30 ticks");
        assertEquals(6, ScoutEntity.HOVER_HEIGHT, "Scout should cruise 6 blocks up");
    }

    @Test
    void costMatchesDesign() {
        assertEquals(150, GatewayBlockEntity.SCOUT_COBBLE_COST, "Scout should cost 150 cobblestone");
        assertEquals(20, GatewayBlockEntity.SCOUT_IRON_COST, "...and 20 iron");
    }

    @Test
    void cruisingAltitudeLeavesRealHorizontalReach() {
        // ATTACK_RADIUS is a 3D distance, so hovering eats into the horizontal reach: a Scout directly
        // overhead is already HOVER_HEIGHT blocks from its target. If the two ever met, the unit could
        // only attack things it was sitting on top of.
        assertTrue(ScoutEntity.HOVER_HEIGHT < ScoutEntity.ATTACK_RADIUS,
                "A Scout must out-range its own cruising altitude");
        double horizontal = Math.sqrt(ScoutEntity.ATTACK_RADIUS * ScoutEntity.ATTACK_RADIUS
                - (double) ScoutEntity.HOVER_HEIGHT * ScoutEntity.HOVER_HEIGHT);
        assertTrue(horizontal >= 6.0,
                "Engaging from altitude must still leave a proper ranged stand-off, not near-melee");
    }

    @Test
    void staticDefenceRemainsTheAnswerToAir() {
        assertTrue(ScoutEntity.ATTACK_RADIUS < PhotonCannonEntity.RANGE,
                "A Photon Cannon must still open fire first — it is the counter to air on both sides");
    }

    @Test
    void winsTheAirDuelAgainstItsCounterpart() {
        // Same envelope as the Mutalisk — identical altitude, reach and cadence — so the two meet on
        // even terms and the fight is decided by staying power and punch, both of which the Scout has.
        assertEquals(MutaliskEntity.HOVER_HEIGHT, ScoutEntity.HOVER_HEIGHT,
                "The two air units should share a cruising altitude, so they can actually engage");
        assertEquals(MutaliskEntity.ATTACK_RADIUS, ScoutEntity.ATTACK_RADIUS,
                "...and a reach, so neither kites the other for free");
        assertEquals(MutaliskEntity.ATTACK_COOLDOWN, ScoutEntity.ATTACK_COOLDOWN,
                "...and a cadence");
        assertTrue(ScoutEntity.MAX_HEALTH + ScoutEntity.SHIELD > MutaliskEntity.MAX_HEALTH,
                "The Scout is the tougher of the two, shields included");
        assertTrue(ScoutEntity.ATTACK_DAMAGE > MutaliskEntity.ATTACK_DAMAGE,
                "...and hits harder");
    }

    @Test
    void isTheCostliestGatewayUnit() {
        // It flies and out-fights everything the Gateway makes, so it must not be the cheap option.
        assertTrue(GatewayBlockEntity.SCOUT_COBBLE_COST > GatewayBlockEntity.DRAGOON_COBBLE_COST,
                "A Scout should cost more stone than a Dragoon");
        assertTrue(GatewayBlockEntity.SCOUT_IRON_COST > 0,
                "...and be the one unit gated on refined metal");
    }
}
