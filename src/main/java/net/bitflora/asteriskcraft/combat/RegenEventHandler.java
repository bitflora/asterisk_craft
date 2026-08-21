package net.bitflora.asteriskcraft.combat;

import net.bitflora.asteriskcraft.AsteriskCraft;
import net.bitflora.asteriskcraft.faction.FactionAttachments;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;

/**
 * Drives health regen: a race that regenerates ({@code faction.Race.regen()}) heals HP itself,
 * slowly, once a unit has gone a few seconds without being hit — where a shielded race refills a
 * separate buffer instead. Mirrors {@link ShieldEventHandler}'s delay/tick shape but heals actual
 * health, and asks the unit's race rather than naming a faction, so which side regenerates follows
 * whoever is playing the swarm.
 */
@EventBusSubscriber(modid = AsteriskCraft.MODID)
public final class RegenEventHandler {
    private static final int REGEN_DELAY_TICKS = 60; // 3s of no damage before regen resumes
    private static final float REGEN_PER_TICK = 0.05f; // 1 HP/s once regenerating

    private RegenEventHandler() {
    }

    @SubscribeEvent
    public static void onIncomingDamage(LivingIncomingDamageEvent event) {
        LivingEntity entity = event.getEntity();
        if (FactionAttachments.get(entity).hasRegen()) {
            entity.setData(RegenAttachments.REGEN_DELAY, REGEN_DELAY_TICKS);
        }
    }

    @SubscribeEvent
    public static void onEntityTick(EntityTickEvent.Post event) {
        if (event.getEntity().level().isClientSide() || !(event.getEntity() instanceof LivingEntity living)) {
            return;
        }
        if (!FactionAttachments.get(living).hasRegen() || !living.isAlive()) {
            return;
        }
        // heal() rather than setHealth() so any heal listeners still fire; the helper hands us the
        // already-clamped target, so healing the delta up to it reproduces heal(REGEN_PER_TICK).
        DelayedRegen.tick(living, RegenAttachments.REGEN_DELAY, REGEN_PER_TICK,
                living.getHealth(), living.getMaxHealth(), target -> living.heal(target - living.getHealth()));
    }
}
