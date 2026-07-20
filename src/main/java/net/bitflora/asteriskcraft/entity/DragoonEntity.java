package net.bitflora.asteriskcraft.entity;

import net.bitflora.asteriskcraft.entity.ai.CommandableGoals;
import net.bitflora.asteriskcraft.entity.ai.FactionTargetGoal;
import net.bitflora.asteriskcraft.entity.ai.HitscanAttacks;
import net.bitflora.asteriskcraft.entity.ai.RetaliateGoal;
import net.bitflora.asteriskcraft.entity.ai.SiegeBlockGoal;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.RangedAttackGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.monster.skeleton.Skeleton;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

/**
 * The Protoss ranged unit: a repurposed Skeleton with vanilla player-aggression goals
 * replaced by pure faction targeting. Its ranged attack is a custom hitscan (see
 * {@link HitscanAttacks}) fired on a fixed {@link #ATTACK_COOLDOWN} cadence via the plain
 * {@link RangedAttackGoal}, instead of vanilla {@code AbstractSkeleton} bow/arrow behavior —
 * {@link #reassessWeaponGoal()} is overridden to a no-op so that vanilla machinery never
 * installs a competing bow/melee goal.
 */
public class DragoonEntity extends Skeleton implements Protoss {
    public static final int SHIELD = 40;
    public static final int ATTACK_COOLDOWN = 40;
    public static final float ATTACK_RADIUS = 15.0f;

    public DragoonEntity(EntityType<? extends DragoonEntity> type, Level level) {
        super(type, level);
        this.setPersistenceRequired();
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 50.0)
                .add(Attributes.ARMOR, 0.5)
                .add(Attributes.MOVEMENT_SPEED, 0.25)
                .add(Attributes.ATTACK_DAMAGE, 10.0)
                .add(Attributes.FOLLOW_RANGE, 32.0);
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(3, new SiegeBlockGoal(this));
        this.goalSelector.addGoal(4, new RangedAttackGoal(this, 1.0, ATTACK_COOLDOWN, ATTACK_RADIUS));
        this.goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 8.0f));
        this.goalSelector.addGoal(6, new RandomLookAroundGoal(this));
        this.targetSelector.addGoal(-1, new RetaliateGoal(this));
        this.targetSelector.addGoal(1, new FactionTargetGoal(this));
        CommandableGoals.install(this, this.goalSelector, this.targetSelector);
    }

    @Override
    public void reassessWeaponGoal() {
        // No-op: skip AbstractSkeleton's bow/melee goal swap, this unit's ranged attack goal is fixed.
    }

    @Override
    public void performRangedAttack(LivingEntity target, float power) {
        HitscanAttacks.fire(this, target, this.getAttributeValue(Attributes.ATTACK_DAMAGE), ParticleTypes.END_ROD, SoundEvents.SKELETON_SHOOT);
    }

    public int getShield() {
        return SHIELD;
    }
}
