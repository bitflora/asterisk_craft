package net.bitflora.asteriskcraft.entity.zerg;

import net.bitflora.asteriskcraft.AsteriskCraft;
import net.bitflora.asteriskcraft.entity.protoss.ProbeEntity;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The Zerg worker. Mechanically identical to the {@link ProbeEntity} it extends — same
 * non-destructive harvest of {@code #asteriskcraft:harvestable} blocks, same delivery straight
 * into its home core (a {@link net.bitflora.asteriskcraft.building.HiveBlockEntity} here) —
 * except it won't touch crimson hyphae, which the shared {@code #minecraft:logs} tag would
 * otherwise pull in as "wood".
 */
public class DroneEntity extends ProbeEntity {

    public DroneEntity(EntityType<? extends DroneEntity> type, Level level) {
        super(type, level);
    }

    @Override
    protected boolean canHarvest(BlockState state) {
        return super.canHarvest(state) && !state.is(Blocks.CRIMSON_HYPHAE);
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
