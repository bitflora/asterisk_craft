package net.bitflora.asteriskcraft.combat;

import net.bitflora.asteriskcraft.AsteriskCraft;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;

/**
 * Drives the Protoss shield mechanic: shields absorb incoming damage before HP, then regenerate
 * on their own once a unit has gone a few seconds without being hit. HP itself is never touched
 * here and never regenerates.
 */
@EventBusSubscriber(modid = AsteriskCraft.MODID)
public final class ShieldEventHandler {
    // Public so a building's shield buffer recharges with exactly the same feel as a unit's, without
    // a second set of numbers to keep in step (see building/BuildingDefense).
    public static final int REGEN_DELAY_TICKS = 140; // 7s of no damage before shields start recharging
    public static final float REGEN_PER_TICK = 0.05f; // 1 shield/s once recharging

    private ShieldEventHandler() {
    }

    @SubscribeEvent
    public static void onIncomingDamage(LivingIncomingDamageEvent event) {
        LivingEntity entity = event.getEntity();
        float shield = ShieldAttachments.get(entity);
        if (shield <= 0.0f) {
            return;
        }
        ShieldAttachments.DamageResult result = ShieldAttachments.resolveDamage(shield, event.getAmount());
        ShieldAttachments.set(entity, result.remainingShield());
        entity.setData(ShieldAttachments.REGEN_DELAY, REGEN_DELAY_TICKS);
        event.setAmount(result.remainingDamage());
    }

    @SubscribeEvent
    public static void onEntityTick(EntityTickEvent.Post event) {
        if (event.getEntity().level().isClientSide() || !(event.getEntity() instanceof LivingEntity living)) {
            return;
        }
        float max = ShieldAttachments.maxShieldFor(living);
        if (max <= 0.0f) {
            return;
        }
        DelayedRegen.tick(living, ShieldAttachments.REGEN_DELAY, REGEN_PER_TICK,
                ShieldAttachments.get(living), max, value -> ShieldAttachments.set(living, value));
    }
}
