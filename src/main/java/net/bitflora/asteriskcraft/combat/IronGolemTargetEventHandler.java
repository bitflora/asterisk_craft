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
 * ({@code ZealotEntity}, {@code DragoonEntity}, etc.) extend {@code Monster} directly
 * (see CLAUDE.md), so golems would otherwise swing at Protoss units regardless of
 * faction. This cancels that specific case: only entities explicitly tagged
 * {@code Faction.PROTOSS} are spared, so golems still defend villages against real
 * (faction-untagged, i.e. NEUTRAL) hostile mobs as normal.
 */
@EventBusSubscriber(modid = AsteriskCraft.MODID)
public final class IronGolemTargetEventHandler {
    private IronGolemTargetEventHandler() {
    }

    @SubscribeEvent
    public static void onChangeTarget(LivingChangeTargetEvent event) {
        if (!(event.getEntity() instanceof IronGolem)) {
            return;
        }
        LivingEntity target = event.getNewAboutToBeSetTarget();
        if (target != null && FactionAttachments.get(target) == Faction.PROTOSS) {
            event.setCanceled(true);
        }
    }
}
