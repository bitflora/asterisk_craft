package net.bitflora.asteriskcraft.combat;

import net.bitflora.asteriskcraft.AsteriskCraft;
import net.bitflora.asteriskcraft.entity.zerg.SunkenSpikeEntity;
import net.bitflora.asteriskcraft.faction.FactionAttachments;
import net.bitflora.asteriskcraft.stats.UnitStats;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;

/**
 * Corrects the damage a {@link SunkenSpikeEntity} deals. Vanilla's {@code EvokerFangs} hardcodes
 * {@code 6.0F} in a private method and spares only the owner and its vanilla-team allies, so both of
 * the Sunken Colony's rules have to be applied from outside the spike:
 *
 * <ul>
 *   <li>a hit is worth {@link net.bitflora.asteriskcraft.stats.UnitStats#SUNKEN_COLONY}'s attack
 *       damage, not 6;</li>
 *   <li>only enemies of the colony's faction are hurt — a strike aimed at the player must not
 *       shred the Zerglings standing next to them, which vanilla's team check knows nothing about.</li>
 * </ul>
 *
 * <p>Runs at {@link EventPriority#HIGHEST} deliberately: {@link ShieldEventHandler} listens on the
 * same event and subtracts shields from the amount, so the amount has to be raised to full before it
 * gets there or a shielded target would absorb against vanilla's 6.
 *
 * <p>Matching on the spike's <em>type</em> rather than on "is the owner a Sunken Colony" keeps the
 * rule intact even if the colony is gone by the time the spike bites; a spike that outlives its owner
 * entirely withdraws itself rather than reaching this handler (see {@code SunkenSpikeEntity.tick}).
 */
@EventBusSubscriber(modid = AsteriskCraft.MODID)
public final class SunkenSpikeDamageHandler {

    private SunkenSpikeDamageHandler() {
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onIncomingDamage(LivingIncomingDamageEvent event) {
        if (!(event.getSource().getDirectEntity() instanceof SunkenSpikeEntity spike)) {
            return;
        }
        LivingEntity owner = spike.getOwner();
        if (owner == null || !FactionAttachments.areEnemies(owner, event.getEntity())) {
            event.setCanceled(true);
            return;
        }
        event.setAmount((float) UnitStats.SUNKEN_COLONY.attackDamageOrThrow());
    }
}
