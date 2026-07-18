package net.bitflora.asteriskcraft.combat;

import net.bitflora.asteriskcraft.AsteriskCraft;
import net.bitflora.asteriskcraft.faction.Faction;
import net.bitflora.asteriskcraft.faction.FactionAttachments;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;

/**
 * Drives Zerg health regen: unlike Protoss shields, Zerg regenerate HP itself, slowly, once a
 * unit has gone a few seconds without being hit. Mirrors {@link ShieldEventHandler}'s delay/tick
 * shape but heals actual health instead of a separate buffer.
 */
@EventBusSubscriber(modid = AsteriskCraft.MODID)
public final class ZergRegenEventHandler {
    private static final int REGEN_DELAY_TICKS = 60; // 3s of no damage before regen resumes
    private static final float REGEN_PER_TICK = 0.05f; // 1 HP/s once regenerating

    private ZergRegenEventHandler() {
    }

    @SubscribeEvent
    public static void onIncomingDamage(LivingIncomingDamageEvent event) {
        LivingEntity entity = event.getEntity();
        if (FactionAttachments.get(entity) == Faction.ZERG) {
            entity.setData(ZergRegenAttachments.REGEN_DELAY, REGEN_DELAY_TICKS);
        }
    }

    @SubscribeEvent
    public static void onEntityTick(EntityTickEvent.Post event) {
        if (event.getEntity().level().isClientSide() || !(event.getEntity() instanceof LivingEntity living)) {
            return;
        }
        if (FactionAttachments.get(living) != Faction.ZERG || !living.isAlive()) {
            return;
        }
        int delay = living.getData(ZergRegenAttachments.REGEN_DELAY);
        if (delay > 0) {
            living.setData(ZergRegenAttachments.REGEN_DELAY, delay - 1);
            return;
        }
        if (living.getHealth() < living.getMaxHealth()) {
            living.heal(REGEN_PER_TICK);
        }
    }
}
