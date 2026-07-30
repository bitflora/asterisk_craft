package net.bitflora.asteriskcraft.entity.zerg;

import net.bitflora.asteriskcraft.AsteriskCraft;
import net.bitflora.asteriskcraft.entity.protoss.ProbeEntity;
import net.bitflora.asteriskcraft.stats.UnitAttributes;
import net.bitflora.asteriskcraft.stats.UnitStats;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The Zerg worker. Mechanically identical to the {@link ProbeEntity} it extends — same
 * non-destructive harvest of {@code #asteriskcraft:harvestable} blocks, same delivery straight
 * into its home core (a {@link net.bitflora.asteriskcraft.building.HiveBlockEntity} here) —
 * except it won't touch crimson hyphae, which the shared {@code #minecraft:logs} tag would
 * otherwise pull in as "wood".
 *
 * <p>Its numbers live in {@link net.bitflora.asteriskcraft.stats.UnitStats#DRONE} — its own entry,
 * not silently inherited from {@link ProbeEntity} despite the class extension.
 */
public class DroneEntity extends ProbeEntity {

    public DroneEntity(EntityType<? extends DroneEntity> type, Level level) {
        super(type, level);
    }

    /** Own entry, not {@link ProbeEntity#createAttributes()} — see the class javadoc. */
    public static AttributeSupplier.Builder createAttributes() {
        return UnitAttributes.apply(PathfinderMob.createMobAttributes(), UnitStats.DRONE);
    }

    @Override
    public int getShield() {
        return UnitStats.DRONE.shield();
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
