package net.bitflora.asteriskcraft.entity.protoss;

import net.bitflora.asteriskcraft.entity.zerg.HydraliskEntity;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Guards the Dragoon/Hydralisk hitscan ranged-attack cadence: Hydralisk fires much faster
 * (20-tick cooldown) than Dragoon (40-tick cooldown), and both have a positive attack range.
 */
class RangedUnitAttackTest {

    @Test
    void attackCooldownsMatchDesign() {
        assertEquals(40, DragoonEntity.ATTACK_COOLDOWN, "Dragoon should fire once every 40 ticks");
        assertEquals(20, HydraliskEntity.ATTACK_COOLDOWN, "Hydralisk should fire once every 20 ticks");
        assertTrue(HydraliskEntity.ATTACK_COOLDOWN < DragoonEntity.ATTACK_COOLDOWN, "Hydralisk must fire faster than Dragoon");
    }

    @Test
    void attackRadiusIsPositive() {
        assertTrue(DragoonEntity.ATTACK_RADIUS > 0, "Dragoon must have a positive attack range");
        assertTrue(HydraliskEntity.ATTACK_RADIUS > 0, "Hydralisk must have a positive attack range");
    }

    @Test
    void attackRadiusHoldsAtRealRange() {
        // The RangedAttackGoal stops advancing once within ATTACK_RADIUS, so it must be a genuine
        // ranged distance — not near-melee — or the units read as charging in instead of holding.
        assertTrue(DragoonEntity.ATTACK_RADIUS >= 6, "Dragoon must hold at a proper ranged distance");
        assertTrue(HydraliskEntity.ATTACK_RADIUS >= 5, "Hydralisk must hold at a proper ranged distance");
        assertTrue(DragoonEntity.ATTACK_RADIUS > HydraliskEntity.ATTACK_RADIUS,
                "Dragoon must out-range the Hydralisk");
    }
}
