package net.bitflora.asteriskcraft.building;

import net.bitflora.asteriskcraft.AsteriskCraft;
import net.bitflora.asteriskcraft.entity.TeamColors;
import net.bitflora.asteriskcraft.entity.ZealotEntity;
import net.bitflora.asteriskcraft.faction.Faction;
import net.bitflora.asteriskcraft.faction.FactionAttachments;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.event.EventHooks;

/**
 * Debug-only item that spawns a ZERG-tagged Zealot, standing in for the real Zerg
 * enemy that arrives in V3. Lets faction targeting be exercised in-game before real
 * enemies exist. Remove once V3 provides real Zerg units.
 */
public class DebugZergSpawnerItem extends Item {
    public DebugZergSpawnerItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        if (!(level instanceof ServerLevel serverLevel)) {
            return InteractionResult.SUCCESS;
        }
        BlockPos pos = context.getClickedPos().relative(context.getClickedFace());
        ZealotEntity zealot = AsteriskCraft.ZEALOT.get().create(serverLevel, EntitySpawnReason.SPAWN_ITEM_USE);
        if (zealot == null) {
            return InteractionResult.FAIL;
        }
        zealot.snapTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, serverLevel.getRandom().nextFloat() * 360f, 0f);
        EventHooks.finalizeMobSpawn(zealot, serverLevel, serverLevel.getCurrentDifficultyAt(pos), EntitySpawnReason.SPAWN_ITEM_USE, null);
        FactionAttachments.set(zealot, Faction.ZERG);
        TeamColors.dyeArmor(zealot, Faction.ZERG);
        serverLevel.addFreshEntity(zealot);
        return InteractionResult.SUCCESS;
    }
}
