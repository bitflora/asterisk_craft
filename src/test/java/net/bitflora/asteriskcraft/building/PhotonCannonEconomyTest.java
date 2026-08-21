package net.bitflora.asteriskcraft.building;

import net.bitflora.asteriskcraft.entity.protoss.PhotonCannonEntity;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Guards the Photon Cannon's warp-in timing. It has no direct cost of its own — the kit that warps
 * it in is bought at a base for {@code BaseBlockEntity.BUILDING_COST} (150 wood or 150
 * cobblestone). Its combat stats (range, damage, cooldown) now live in
 * {@code net.bitflora.asteriskcraft.stats.UnitStats.PHOTON_CANNON} — see {@code stats.UnitStatsTest}.
 */
class PhotonCannonEconomyTest {

    @Test
    void combatTimingIsSane() {
        assertTrue(PhotonCannonEntity.WARP_TICKS > 0, "the cannon must take time to warp in");
    }

}
