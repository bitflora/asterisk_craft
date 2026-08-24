package net.bitflora.asteriskcraft.entity.terran;

import net.bitflora.asteriskcraft.entity.WorkerEntity;
import net.bitflora.asteriskcraft.stats.UnitAttributes;
import net.bitflora.asteriskcraft.stats.UnitStats;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.level.Level;

/**
 * The Terran worker. As with the Probe and the Drone, the harvest economy itself — finding a node,
 * mining it non-destructively, delivering the yield into its home base — is all
 * {@link WorkerEntity}, so an SCV is that plus its own numbers.
 *
 * <p>No sound overrides: the Terran have no recorded voice yet, and a worker with none falls back
 * to {@code Mob}'s own defaults (silent while idle, generic hurt/death) rather than borrowing
 * another race's. It also has no {@code canHarvest} override — unlike the Drone, nothing the Terran
 * build turns a harvestable block into part of their own base.
 *
 * <p>Its stats live in {@link UnitStats#SCV}.
 */
public class ScvEntity extends WorkerEntity {

    public ScvEntity(EntityType<? extends ScvEntity> type, Level level) {
        super(type, level);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return UnitAttributes.apply(PathfinderMob.createMobAttributes(), UnitStats.SCV);
    }

    @Override
    public int getShield() {
        return UnitStats.SCV.shield();
    }
}
