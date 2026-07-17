package net.bitflora.asteriskcraft.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

/**
 * The Zerg worker. Mechanically identical to the {@link ProbeEntity} it extends — same
 * non-destructive harvest of {@code #asteriskcraft:harvestable} blocks — but it deposits its
 * yield straight into its home {@link net.bitflora.asteriskcraft.building.HiveBlockEntity}
 * (which exposes the item capability) instead of hunting for a chest.
 */
public class DroneEntity extends ProbeEntity {

    public DroneEntity(EntityType<? extends DroneEntity> type, Level level) {
        super(type, level);
    }

    @Nullable
    @Override
    protected BlockPos findDeliveryTarget() {
        // Home is set to the Hive core when the Hive spawns us; deliver back into it.
        return getHomePos().equals(BlockPos.ZERO) ? null : getHomePos();
    }
}
