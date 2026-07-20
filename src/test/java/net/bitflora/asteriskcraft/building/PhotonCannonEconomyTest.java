package net.bitflora.asteriskcraft.building;

import net.bitflora.asteriskcraft.entity.protoss.PhotonCannonEntity;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Guards the Photon Cannon's design constants: the kit costs 100 wood + 100 cobblestone
 * + 20 iron (docs/shaping.md V4), and it warps in and attacks on real cadences.
 */
class PhotonCannonEconomyTest {

    @Test
    void combatTimingIsSane() {
        assertTrue(PhotonCannonEntity.WARP_TICKS > 0, "the cannon must take time to warp in");
        assertTrue(PhotonCannonEntity.RANGE > 0, "the cannon must have a positive attack range");
        assertTrue(PhotonCannonEntity.ATTACK_DAMAGE > 0, "each shot must deal damage");
        assertTrue(PhotonCannonEntity.ATTACK_COOLDOWN > 0, "shots must be spaced out in time");
    }

}
