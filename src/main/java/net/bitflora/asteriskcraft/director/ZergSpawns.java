package net.bitflora.asteriskcraft.director;

import net.bitflora.asteriskcraft.building.SpawnSpots;
import net.bitflora.asteriskcraft.entity.TeamColors;
import net.bitflora.asteriskcraft.faction.Faction;
import net.bitflora.asteriskcraft.faction.FactionAttachments;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.neoforged.neoforge.event.EventHooks;
import org.jetbrains.annotations.Nullable;

/**
 * Shared "produce a unit at a building" spawn recipe, factored out of the pattern in
 * {@code GatewayBlockEntity.spawnUnit} so the Hive (Drones) and the {@link ZergDirector}
 * (wave units) both use it. Faction-generic: pass the owning faction and whether to apply
 * team-color armour (workers skip it).
 */
public final class ZergSpawns {
    private ZergSpawns() {
    }

    @Nullable
    public static <T extends Mob> T spawn(ServerLevel level, BlockPos near, EntityType<T> type, Faction faction, boolean dyeArmor) {
        T unit = type.create(level, EntitySpawnReason.TRIGGERED);
        if (unit == null) {
            return null;
        }
        BlockPos spot = SpawnSpots.findGroundSpot(level, near);
        unit.snapTo(spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5, level.getRandom().nextFloat() * 360f, 0f);
        EventHooks.finalizeMobSpawn(unit, level, level.getCurrentDifficultyAt(spot), EntitySpawnReason.TRIGGERED, null);
        FactionAttachments.set(unit, faction);
        if (dyeArmor) {
            TeamColors.dyeArmor(unit, faction);
        }
        level.addFreshEntity(unit);
        level.playSound(null, spot, SoundEvents.PLAYER_TELEPORT, SoundSource.BLOCKS, 0.8f, 1.6f);
        return unit;
    }
}
