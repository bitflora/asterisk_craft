package net.bitflora.asteriskcraft.entity.zerg;

import net.bitflora.asteriskcraft.director.script.ZergUnitCatalog;
import net.bitflora.asteriskcraft.entity.protoss.DragoonEntity;
import net.bitflora.asteriskcraft.entity.protoss.PhotonCannonEntity;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Guards the Mutalisk's design numbers and the geometric relationships that make it playable as the
 * mod's first air unit.
 *
 * <p>Flight itself is deliberately <b>not</b> covered here: hovering, path following over broken
 * terrain and "melee units genuinely cannot reach it" all need a live world with loaded chunks and a
 * running navigation, none of which the JUnit bootstrap provides (same limitation documented in
 * {@code ProbeEconomyTest}). Those are verified via {@code runClient}. What is pinned here is
 * everything that is pure arithmetic on the constants.
 */
class MutaliskTest {

    @Test
    void statsMatchDesign() {
        assertEquals(60, MutaliskEntity.MAX_HEALTH, "Mutalisk should have 60 HP");
        assertEquals(4.5f, MutaliskEntity.ATTACK_DAMAGE, "Each glave should deal 4.5 damage");
        assertEquals(9.0f, MutaliskEntity.ATTACK_RADIUS, "Mutalisk should reach 9 blocks");
        assertEquals(30, MutaliskEntity.ATTACK_COOLDOWN, "Mutalisk should fire every 30 ticks");
        assertEquals(6, MutaliskEntity.HOVER_HEIGHT, "Mutalisk should cruise 6 blocks up");
        assertEquals(100, ZergUnitCatalog.MUTALISK_COST, "Mutalisk should cost 100");
    }

    @Test
    void cruisingAltitudeLeavesRealHorizontalReach() {
        // ATTACK_RADIUS is a 3D distance, so hovering eats into the horizontal reach: a Mutalisk
        // directly overhead is already HOVER_HEIGHT blocks from its target. If the two ever met, the
        // unit could only attack things it was sitting on top of.
        assertTrue(MutaliskEntity.HOVER_HEIGHT < MutaliskEntity.ATTACK_RADIUS,
                "A Mutalisk must out-range its own cruising altitude");
        double horizontal = Math.sqrt(MutaliskEntity.ATTACK_RADIUS * MutaliskEntity.ATTACK_RADIUS
                - (double) MutaliskEntity.HOVER_HEIGHT * MutaliskEntity.HOVER_HEIGHT);
        assertTrue(horizontal >= 6.0,
                "Engaging from altitude must still leave a proper ranged stand-off, not near-melee");
    }

    @Test
    void outrangesTheGroundUnitsButNotStaticDefence() {
        assertTrue(MutaliskEntity.ATTACK_RADIUS > HydraliskEntity.ATTACK_RADIUS,
                "The air unit should out-range its own ground counterpart");
        assertTrue(MutaliskEntity.ATTACK_RADIUS > DragoonEntity.ATTACK_RADIUS,
                "...and the Protoss ranged unit it will be harassing");
        assertTrue(MutaliskEntity.ATTACK_RADIUS < PhotonCannonEntity.RANGE,
                "A Photon Cannon must still be able to open fire first — it is the answer to air");
    }

    @Test
    void tradesStayingPowerForMobility() {
        assertTrue(MutaliskEntity.ATTACK_COOLDOWN > HydraliskEntity.ATTACK_COOLDOWN,
                "The Mutalisk pays for its reach and untouchability with a slower cadence");
        assertTrue(MutaliskEntity.ATTACK_DAMAGE < SunkenColonyEntity.ATTACK_DAMAGE,
                "It is a harasser, not a brawler");
    }
}
