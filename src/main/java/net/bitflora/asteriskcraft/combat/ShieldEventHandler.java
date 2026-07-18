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
    private static final int REGEN_DELAY_TICKS = 60; // 3s of no damage before shields start recharging
    private static final float REGEN_PER_TICK = 0.5f; // 10 shield/s once recharging

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
        int delay = living.getData(ShieldAttachments.REGEN_DELAY);
        if (delay > 0) {
            living.setData(ShieldAttachments.REGEN_DELAY, delay - 1);
            return;
        }
        float shield = ShieldAttachments.get(living);
        if (shield < max) {
            ShieldAttachments.set(living, Math.min(max, shield + REGEN_PER_TICK));
        }
    }
}
