package net.bitflora.asteriskcraft.combat;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Guards the pure shield-damage arithmetic (design constants + absorb-before-HP math).
 * Which entity types get a shield pool at all depends on faction data attachments and live
 * EntityType registry objects, so that part of {@link ShieldAttachments#maxShieldFor} is
 * exercised via runClient instead, following the pattern in ProbeEconomyTest.
 */
class ShieldAttachmentsTest {

    @Test
    void shieldFullyAbsorbsDamageWithinItsPool() {
        ShieldAttachments.DamageResult result = ShieldAttachments.resolveDamage(20.0f, 12.0f);
        assertEquals(8.0f, result.remainingShield());
        assertEquals(0.0f, result.remainingDamage());
    }

    @Test
    void overflowDamageSpillsThroughToHp() {
        ShieldAttachments.DamageResult result = ShieldAttachments.resolveDamage(5.0f, 12.0f);
        assertEquals(0.0f, result.remainingShield());
        assertEquals(7.0f, result.remainingDamage());
    }

    @Test
    void zeroShieldAbsorbsNothing() {
        ShieldAttachments.DamageResult result = ShieldAttachments.resolveDamage(0.0f, 12.0f);
        assertEquals(0.0f, result.remainingShield());
        assertEquals(12.0f, result.remainingDamage());
    }
}
