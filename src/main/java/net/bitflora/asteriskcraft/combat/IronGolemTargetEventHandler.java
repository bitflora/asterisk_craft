package net.bitflora.asteriskcraft.combat;

import net.bitflora.asteriskcraft.AsteriskCraft;
import net.bitflora.asteriskcraft.faction.Faction;
import net.bitflora.asteriskcraft.faction.FactionAttachments;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.golem.IronGolem;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingChangeTargetEvent;

/**
 * Vanilla iron golems target any {@code Monster} near them, and our combat units
 * ({@code ZealotEntity}, {@code DragoonEntity}, etc.) extend {@code Monster} directly (see
 * CLAUDE.md), so a golem would otherwise swing at every unit in the mod regardless of faction.
 *
 * <p>The rule is "a golem fights back, and starts nothing": a unit of ours is spared unless it
 * would attack the golem itself, which is the same question all targeting asks —
 * {@link FactionAttachments#isHostile} — read in the other direction. So the swarm, whose race
 * hunts civilians, still gets golems swinging at it, while a race that leaves the settled world
 * alone walks past. Both halves fall out of the per-race table with no race named here.
 *
 * <p>Gated on the target actually being one of ours: an unfactioned zombie is hostile to nobody by
 * the mod's rules, so without that guard golems would stop defending villages entirely.
 */
@EventBusSubscriber(modid = AsteriskCraft.MODID)
public final class IronGolemTargetEventHandler {
    private IronGolemTargetEventHandler() {
    }

    @SubscribeEvent
    public static void onChangeTarget(LivingChangeTargetEvent event) {
        if (!(event.getEntity() instanceof IronGolem golem)) {
            return;
        }
        LivingEntity target = event.getNewAboutToBeSetTarget();
        if (target == null || FactionAttachments.get(target) == Faction.NEUTRAL) {
            return; // not one of ours: vanilla's village defence, untouched
        }
        if (!FactionAttachments.isHostile(target, golem)) {
            event.setCanceled(true);
        }
    }
}
