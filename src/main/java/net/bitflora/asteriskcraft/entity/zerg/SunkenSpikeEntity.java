package net.bitflora.asteriskcraft.entity.zerg;

import net.bitflora.asteriskcraft.AsteriskCraft;
import net.bitflora.asteriskcraft.combat.AsteriskCraftDamageTypes;
import net.bitflora.asteriskcraft.stats.UnitStats;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;

/**
 * The single ground spike a {@link SunkenColonyEntity} drives up beneath its target. All the
 * behaviour is {@link FangStrikeEntity}'s; this subclass exists so the spike carries its own
 * {@code EntityType} and names the two things a Sunken Colony hit is worth.
 *
 * <p>The colony spawns exactly one of these per attack, which is what keeps an attack worth exactly
 * one {@link UnitStats#SUNKEN_COLONY}'s attack damage rather than a multiple of it — the Lurker's
 * row is the deliberate opposite.
 */
public class SunkenSpikeEntity extends FangStrikeEntity {

    public SunkenSpikeEntity(EntityType<? extends SunkenSpikeEntity> type, Level level) {
        super(type, level);
    }

    public SunkenSpikeEntity(Level level, double x, double y, double z, float rotationRadians, LivingEntity owner) {
        this(AsteriskCraft.SUNKEN_SPIKE.get(), level);
        this.placeAt(x, y, z, rotationRadians, owner);
    }

    @Override
    public ResourceKey<DamageType> damageType() {
        return AsteriskCraftDamageTypes.SUBTERRANEAN_TENTACLE;
    }

    @Override
    public float strikeDamage() {
        return (float) UnitStats.SUNKEN_COLONY.attackDamageOrThrow();
    }
}
