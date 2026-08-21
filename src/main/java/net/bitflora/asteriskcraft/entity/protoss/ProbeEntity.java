package net.bitflora.asteriskcraft.entity.protoss;

import net.bitflora.asteriskcraft.AsteriskCraft;
import net.bitflora.asteriskcraft.entity.WorkerEntity;
import net.bitflora.asteriskcraft.stats.UnitAttributes;
import net.bitflora.asteriskcraft.stats.UnitStats;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.level.Level;

/**
 * The Protoss worker. The harvest economy itself — finding a node, mining it non-destructively,
 * delivering the yield into its home base — is all {@link WorkerEntity}, shared with every other
 * race's worker; a Probe is that plus its own numbers and its own voice.
 *
 * <p>Its stats live in {@link net.bitflora.asteriskcraft.stats.UnitStats#PROBE}.
 */
public class ProbeEntity extends WorkerEntity {

    public ProbeEntity(EntityType<? extends ProbeEntity> type, Level level) {
        super(type, level);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return UnitAttributes.apply(PathfinderMob.createMobAttributes(), UnitStats.PROBE);
    }

    @Override
    public int getShield() {
        return UnitStats.PROBE.shield();
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return AsteriskCraft.PROBE_AMBIENT.get();
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return AsteriskCraft.PROBE_HURT.get();
    }

    @Override
    protected SoundEvent getDeathSound() {
        return AsteriskCraft.PROBE_DEATH.get();
    }
}
