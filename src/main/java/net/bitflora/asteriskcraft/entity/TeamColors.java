package net.bitflora.asteriskcraft.entity;

import net.bitflora.asteriskcraft.faction.Faction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.DyedItemColor;

/**
 * Dyes a leather chestplate onto a combat unit so its faction is visible at a glance,
 * since Zealots/Dragoons reuse the vanilla Zombie/Skeleton renderer with no custom model.
 */
public final class TeamColors {
    private static final int PROTOSS_COLOR = 0x2050C8; // blue
    private static final int ZERG_COLOR = 0x8A1030; // dark red

    private TeamColors() {
    }

    /** Faction team color as a packed RGB int, or {@code -1} for factions with no color (NEUTRAL). */
    public static int factionColor(Faction faction) {
        return switch (faction) {
            case PROTOSS -> PROTOSS_COLOR;
            case ZERG -> ZERG_COLOR;
            case NEUTRAL -> -1;
        };
    }

    public static void dyeArmor(Mob entity, Faction faction) {
        int color = factionColor(faction);
        if (color < 0) {
            return;
        }
        ItemStack chestplate = new ItemStack(Items.LEATHER_CHESTPLATE);
        chestplate.set(DataComponents.DYED_COLOR, new DyedItemColor(color));
        entity.setItemSlot(EquipmentSlot.CHEST, chestplate);
        entity.setDropChance(EquipmentSlot.CHEST, 0.0f);
    }
}
