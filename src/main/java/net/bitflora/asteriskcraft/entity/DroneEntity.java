package net.bitflora.asteriskcraft.entity;

import net.bitflora.asteriskcraft.AsteriskCraft;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;

/**
 * The Zerg worker. Mechanically identical to the {@link ProbeEntity} it extends — same
 * non-destructive harvest of {@code #asteriskcraft:harvestable} blocks, same delivery straight
 * into its home core (a {@link net.bitflora.asteriskcraft.building.HiveBlockEntity} here).
 */
public class DroneEntity extends ProbeEntity {

    public DroneEntity(EntityType<? extends DroneEntity> type, Level level) {
        super(type, level);
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return AsteriskCraft.DRONE_AMBIENT.get();
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return AsteriskCraft.DRONE_HURT.get();
    }

    @Override
    protected SoundEvent getDeathSound() {
        return AsteriskCraft.DRONE_DEATH.get();
    }
}
