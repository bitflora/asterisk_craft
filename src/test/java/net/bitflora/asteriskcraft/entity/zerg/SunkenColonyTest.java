package net.bitflora.asteriskcraft.entity.zerg;

import net.bitflora.asteriskcraft.entity.protoss.PhotonCannonEntity;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Guards the Sunken Colony's design numbers — the Zerg static defence is deliberately the hardest
 * hitter and longest reach in the mod, trading a slow cadence and a dodgeable ground strike for it.
 * Behaviour that needs a live world (the spike actually erupting, the faction damage filter) is
 * verified via {@code runClient}; only the constants are pinned here.
 */
class SunkenColonyTest {

    @Test
    void statsMatchDesign() {
        assertEquals(150, SunkenColonyEntity.MAX_HEALTH, "Sunken Colony should have 150 HP");
        assertEquals(20.0f, SunkenColonyEntity.ATTACK_DAMAGE, "Each strike should deal 20 damage");
        assertEquals(11.0, SunkenColonyEntity.RANGE, "Sunken Colony should reach 11 blocks");
        assertEquals(32, SunkenColonyEntity.ATTACK_COOLDOWN, "Sunken Colony should strike every 32 ticks");
    }

    @Test
    void outrangesAndOutHitsTheProtossStaticDefence() {
        assertTrue(SunkenColonyEntity.RANGE < PhotonCannonEntity.RANGE,
                "Photon Cannon must keep the range advantage over its Zerg counterpart");
        assertTrue(SunkenColonyEntity.ATTACK_DAMAGE > PhotonCannonEntity.ATTACK_DAMAGE,
                "Sunken Colony trades that range back for far heavier hits");
        assertTrue(SunkenColonyEntity.ATTACK_COOLDOWN > PhotonCannonEntity.ATTACK_COOLDOWN,
                "...and for a slower cadence");
    }

    @Test
    void outrangesTheUnitsItDefendsAgainst() {
        assertTrue(SunkenColonyEntity.RANGE > HydraliskEntity.ATTACK_RADIUS,
                "A rooted defender can't close the gap, so it must open fire first");
    }

    @Test
    void strikeAnimationFitsInsideTheCooldown() {
        assertTrue(SunkenColonyEntity.ATTACK_ANIM_TICKS > 0
                        && SunkenColonyEntity.ATTACK_ANIM_TICKS < SunkenColonyEntity.ATTACK_COOLDOWN,
                "The tentacle must finish its whip before the next strike begins");
    }
}
