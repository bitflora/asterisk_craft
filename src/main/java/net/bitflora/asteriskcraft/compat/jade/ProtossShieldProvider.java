package net.bitflora.asteriskcraft.compat.jade;

import net.bitflora.asteriskcraft.AsteriskCraft;
import net.bitflora.asteriskcraft.combat.ShieldAttachments;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import snownee.jade.api.EntityAccessor;
import snownee.jade.api.IEntityComponentProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.config.IPluginConfig;

/**
 * Adds a {@code ⛨ current/max} Jade tooltip line for Protoss units, replacing the
 * {@code ShieldNameTagHandler} nameplate placeholder. Non-Protoss / unshielded entities (Zerg
 * units, vanilla mobs, buildings) get no line, since {@link ShieldAttachments#maxShieldFor} is
 * zero for them.
 */
public enum ProtossShieldProvider implements IEntityComponentProvider {
    INSTANCE;

    @Override
    public void appendTooltip(ITooltip tooltip, EntityAccessor accessor, IPluginConfig config) {
        Entity entity = accessor.getEntity();
        float max = ShieldAttachments.maxShieldFor(entity);
        if (max <= 0.0f) {
            return;
        }
        float current = ShieldAttachments.get(entity);
        tooltip.add(Component.literal("⛨ " + Math.round(current) + "/" + Math.round(max))
                .withStyle(ChatFormatting.AQUA));
    }

    @Override
    public Identifier getUid() {
        return AsteriskCraft.id("protoss_shield");
    }
}
