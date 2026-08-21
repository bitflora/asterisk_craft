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
 * One spine of a {@link LurkerEntity}'s volley. Mechanically identical to the Sunken Colony's spike
 * — both are {@link FangStrikeEntity} — and different only in what a hit is worth and in how many go
 * out at once: the Lurker plants a whole row of these marching away from itself along the bearing to
 * its target (see {@code entity.ai.zerg.LurkerSpineGoal}).
 *
 * <p>Every spine in a row deals full damage, deliberately. A unit standing across two of them, or
 * walking up the line, takes each hit — which is what makes the volley punish a target that stands in
 * it rather than a target that merely touches it, and is the payoff for a weapon that cannot fire at
 * all until the Lurker has spent three seconds digging in.
 */
public class LurkerSpineEntity extends FangStrikeEntity {

    public LurkerSpineEntity(EntityType<? extends LurkerSpineEntity> type, Level level) {
        super(type, level);
    }

    public LurkerSpineEntity(Level level, double x, double y, double z, float rotationRadians, LivingEntity owner) {
        this(AsteriskCraft.LURKER_SPINE.get(), level);
        this.placeAt(x, y, z, rotationRadians, owner);
    }

    @Override
    public ResourceKey<DamageType> damageType() {
        return AsteriskCraftDamageTypes.SUBTERRANEAN_SPINES;
    }

    @Override
    public float strikeDamage() {
        return (float) UnitStats.LURKER.attackDamageOrThrow();
    }
}
