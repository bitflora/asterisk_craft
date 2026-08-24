package net.bitflora.asteriskcraft.combat;

import net.bitflora.asteriskcraft.AsteriskCraft;
import net.bitflora.asteriskcraft.faction.Garrison;
import net.minecraft.tags.DamageTypeTags;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;

/**
 * The floor under {@link Garrison}: a unit riding inside one takes nothing at all, from anything, until
 * the shell it is in comes down.
 *
 * <p><b>Why the targeting gate isn't enough on its own.</b> {@code FactionAttachments.isHostile}
 * stops a garrisoned unit being <em>chosen</em>, and {@code TargetRetentionHandler} stops one that is
 * already held from staying held — but choosing is not the only way damage arrives. A suicide blast
 * deliberately resolves no hostility at all ({@code combat.SuicideBlast} leaves
 * {@code shouldDamageEntity} at its default, because a detonation is not targeting); the fang strikes
 * plant themselves at a spot and hit whatever is standing on it; a vehicle propagates its own fall
 * damage to its riders ({@code Entity.propagateFallToPassengers}); and nothing about suffocation or
 * fire ever asked a faction. Every one of those would reach through a bunker wall.
 *
 * <p>It <b>cancels</b> rather than zeroing the amount, and it does so at {@link EventPriority#HIGHEST}.
 * A cancelled event skips its remaining listeners (NeoForge's default {@code receiveCanceled} is
 * false), so {@link ShieldEventHandler} and {@link RegenEventHandler} never see a hit that did not
 * land — in particular the shield pool is not spent and the regen delay is not restarted on a unit
 * that was never actually touched.
 *
 * <p>Damage aimed at the <em>Bunker</em> is untouched and behaves like damage to any other unit: it
 * is a living entity with its own HP, so the shell soaks everything through the ordinary stack. That
 * asymmetry is the whole mechanic.
 *
 * <p>The one carve-out is {@code #minecraft:bypasses_invulnerability} — the void and {@code /kill}.
 * Those already ignore creative mode and every i-frame in the game, and a shelter that could stop
 * them would be a way to make a unit genuinely unkillable, which is a bug rather than a mechanic.
 */
@EventBusSubscriber(modid = AsteriskCraft.MODID)
public final class GarrisonDamageHandler {

    private GarrisonDamageHandler() {
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onIncomingDamage(LivingIncomingDamageEvent event) {
        if (Garrison.isGarrisoned(event.getEntity())
                && !event.getSource().is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            event.setCanceled(true);
        }
    }
}
