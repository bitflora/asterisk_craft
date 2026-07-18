package net.bitflora.asteriskcraft.client;

import net.bitflora.asteriskcraft.AsteriskCraft;
import net.bitflora.asteriskcraft.combat.ShieldAttachments;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.util.TriState;
import net.minecraft.world.entity.Entity;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderNameTagEvent;

/**
 * Stands in for a proper Jade tooltip integration (not wired up yet, see docs/todo): shows a
 * Protoss unit's current/max shield as its nameplate, since shielded units otherwise carry no
 * vanilla name tag at all.
 */
@EventBusSubscriber(modid = AsteriskCraft.MODID, value = Dist.CLIENT)
public final class ShieldNameTagHandler {
    private ShieldNameTagHandler() {
    }

    @SubscribeEvent
    static void onCanRender(RenderNameTagEvent.CanRender event) {
        Entity entity = event.getEntity();
        float max = ShieldAttachments.maxShieldFor(entity);
        if (max <= 0.0f) {
            return;
        }
        float current = ShieldAttachments.get(entity);
        event.setContent(Component.literal("⛨ " + Math.round(current) + "/" + Math.round(max))
                .withStyle(ChatFormatting.AQUA));
        event.setCanRender(TriState.TRUE);
    }
}
