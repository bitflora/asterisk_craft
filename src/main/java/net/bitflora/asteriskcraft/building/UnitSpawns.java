package net.bitflora.asteriskcraft.building;

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
 * Shared, faction-generic "produce a unit at a building" spawn recipe: find a safely-footed spot
 * near the building, warp the unit in, tag its faction, and play the teleport cue. Every place a
 * unit is created at a building routes through here — a base (its race's worker), the Gateway
 * (Zealots/Dragoons), {@code AiDirector} (the computer's workers and wave units), and the world
 * bootstrap's starting workers — so the recipe lives in exactly one spot. Callers set any
 * unit-specific state (e.g. a worker's home core) on the returned mob.
 */
public final class UnitSpawns {
    private UnitSpawns() {
    }

    @Nullable
    public static <T extends Mob> T spawn(ServerLevel level, BlockPos near, EntityType<T> type, Faction faction, boolean dyeArmor) {
        T unit = type.create(level, EntitySpawnReason.TRIGGERED);
        if (unit == null) {
            return null;
        }
        BlockPos spot = SpawnSpots.findGroundSpot(level, near, type);
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
