package net.bitflora.asteriskcraft.building;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Guards the Photon Cannon's design constants: the kit costs 100 wood + 100 cobblestone
 * + 20 iron (docs/shaping.md V4), and it warps in and attacks on real cadences.
 */
class PhotonCannonEconomyTest {

    @Test
    void kitCostMatchesDesign() {
        assertEquals(100, PhotonCannonBlockEntity.WOOD_COST);
        assertEquals(100, PhotonCannonBlockEntity.COBBLE_COST);
        assertEquals(20, PhotonCannonBlockEntity.IRON_COST);
    }

    @Test
    void combatTimingIsSane() {
        assertTrue(PhotonCannonBlockEntity.WARP_TICKS > 0, "the cannon must take time to warp in");
        assertTrue(PhotonCannonBlockEntity.RANGE > 0, "the cannon must have a positive attack range");
        assertTrue(PhotonCannonBlockEntity.ATTACK_DAMAGE > 0, "each shot must deal damage");
        assertTrue(PhotonCannonBlockEntity.ATTACK_COOLDOWN > 0, "shots must be spaced out in time");
    }
}
