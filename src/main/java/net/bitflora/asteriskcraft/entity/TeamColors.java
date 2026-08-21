package net.bitflora.asteriskcraft.entity;

import net.bitflora.asteriskcraft.faction.Faction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.DyedItemColor;

/**
 * Faction team colors: the packed RGB via {@link #factionColor} still drives visible team identity
 * (e.g. the Nexus/Hive beacon beam sections). {@link #dyeArmor} dyes a leather chestplate onto a
 * unit, but every unit now has a bespoke {@code MobRenderer}/model (no reused vanilla
 * Zombie/Skeleton renderer), and those custom models don't render the armor slot — so {@code dyeArmor}
 * (and the {@code dyeArmor} flag threaded through {@code UnitSpawns}) is
 * currently a no-op, kept as the seam for the V5 "team-color visuals" feature that will wire real
 * team color into the renderers.
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
