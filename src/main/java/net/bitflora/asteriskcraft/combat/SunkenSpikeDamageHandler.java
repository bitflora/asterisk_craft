package net.bitflora.asteriskcraft.combat;

import net.bitflora.asteriskcraft.AsteriskCraft;
import net.bitflora.asteriskcraft.entity.zerg.SunkenSpikeEntity;
import net.bitflora.asteriskcraft.faction.FactionAttachments;
import net.bitflora.asteriskcraft.stats.UnitStats;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;

/**
 * Replaces the damage a {@link SunkenSpikeEntity} deals. Vanilla's {@code EvokerFangs} hardcodes
 * {@code 6.0F} of {@code minecraft:indirect_magic} in a private method and spares only the owner and
 * its vanilla-team allies, so all three of the Sunken Colony's rules have to be applied from outside
 * the spike:
 *
 * <ul>
 *   <li>a hit is worth {@link net.bitflora.asteriskcraft.stats.UnitStats#SUNKEN_COLONY}'s attack
 *       damage, not 6;</li>
 *   <li>only enemies of the colony's faction are hurt — a strike aimed at the player must not
 *       shred the Zerglings standing next to them, which vanilla's team check knows nothing about;</li>
 *   <li>it is {@link AsteriskCraftDamageTypes#SUBTERRANEAN_SPINES}, not vanilla's magic.</li>
 * </ul>
 *
 * <p>That last one is why this cancels and re-deals rather than merely adjusting the amount:
 * {@code LivingIncomingDamageEvent} can rewrite the damage but <em>cannot substitute the
 * DamageSource</em>. Cancelling is safe to build on — {@code LivingEntity.hurtServer} returns before
 * it touches {@code invulnerableTime}, so the cancelled hit leaves no i-frames to block the real one
 * that follows (verified against the decompiled 26.1.2 source, see docs/neoforge-api-notes.md).
 *
 * <p>The replacement hit is attributed to the <b>colony</b>, not the spike. That is load-bearing in
 * two ways: it stops this handler recursing (its own guard matches a spike as direct entity, and the
 * new source has none), and it makes the knockback push away from the colony and
 * {@code getLastHurtByMob()} name the colony, so a unit spiked from cover turns on the thing that
 * actually hit it.
 *
 * <p>Runs at {@link EventPriority#HIGHEST} deliberately: {@link ShieldEventHandler} and
 * {@link ZergRegenEventHandler} listen on the same event, and cancelling here keeps them from acting
 * on a hit that never lands. They fire normally — with the full amount — on the re-dealt one.
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
        // Vanilla's own hit never lands: wrong amount, wrong damage type, wrong idea of who is allied.
        event.setCanceled(true);

        LivingEntity target = event.getEntity();
        LivingEntity owner = spike.getOwner();
        if (owner == null || !FactionAttachments.areEnemies(owner, target)) {
            return;
        }
        if (!(target.level() instanceof ServerLevel level)) {
            return;
        }
        target.hurtServer(level,
                level.damageSources().source(AsteriskCraftDamageTypes.SUBTERRANEAN_SPINES, owner),
                (float) UnitStats.SUNKEN_COLONY.attackDamageOrThrow());
    }
}
