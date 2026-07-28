package net.bitflora.asteriskcraft.entity.zerg;

import net.bitflora.asteriskcraft.AsteriskCraft;
import net.bitflora.asteriskcraft.entity.protoss.DragoonEntity;
import net.bitflora.asteriskcraft.entity.ai.CommandableGoals;
import net.bitflora.asteriskcraft.entity.ai.FactionTargetGoal;
import net.bitflora.asteriskcraft.entity.ai.HitscanAttacks;
import net.bitflora.asteriskcraft.entity.ai.RetaliateGoal;
import net.bitflora.asteriskcraft.entity.ai.SiegeBlockGoal;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.RangedAttackGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.monster.RangedAttackMob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

/**
 * The Zerg ranged unit — the mirror of {@link DragoonEntity}: a plain hostile mob (not a
 * repurposed Skeleton — see docs/neoforge-api-notes.md) with faction targeting and a
 * {@link SiegeBlockGoal}. Its ranged attack is a custom hitscan (see {@link HitscanAttacks}) fired
 * on a fixed {@link #ATTACK_COOLDOWN} cadence via the plain {@link RangedAttackGoal}.
 */
public class HydraliskEntity extends Monster implements RangedAttackMob {
    public static final int ATTACK_COOLDOWN = 20;
    /**
     * Range the {@link RangedAttackGoal} holds at: once within it the unit stops advancing and fires
     * in place instead of closing to melee. Shorter-ranged than the {@link DragoonEntity}.
     */
    public static final float ATTACK_RADIUS = 6.0f;
    /** Length of the spine volley the client plays on each shot; half the cooldown, so shots read as discrete. */
    public static final int FIRE_ANIM_TICKS = 10;

    // Synced rather than broadcast as an entity event: an int carries the animation's progress (not
    // just its start) and can't collide with a vanilla LivingEntity event byte. Same shape as
    // ZealotEntity's. A ranged unit never swings, so getAttackAnim can't carry this the way it does
    // for the Zergling.
    private static final EntityDataAccessor<Integer> FIRE_TICKS =
            SynchedEntityData.defineId(HydraliskEntity.class, EntityDataSerializers.INT);

    public HydraliskEntity(EntityType<? extends HydraliskEntity> type, Level level) {
        super(type, level);
        this.setPersistenceRequired();
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(FIRE_TICKS, 0);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 40.0)
                .add(Attributes.ARMOR, 0.0)
                .add(Attributes.MOVEMENT_SPEED, 0.25)
                .add(Attributes.ATTACK_DAMAGE, 5.0)
                .add(Attributes.FOLLOW_RANGE, 32.0);
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        // Priority 0 (above the move/attack goals): the digger must be able to preempt movement to
        // batter through an obstruction, not merely fill a yield window. See SiegeBlockGoal.
        this.goalSelector.addGoal(0, new SiegeBlockGoal(this));
        this.goalSelector.addGoal(4, new RangedAttackGoal(this, 1.0, ATTACK_COOLDOWN, ATTACK_RADIUS));
        this.goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 8.0f));
        this.goalSelector.addGoal(6, new RandomLookAroundGoal(this));
        this.targetSelector.addGoal(-1, new RetaliateGoal(this));
        this.targetSelector.addGoal(1, new FactionTargetGoal(this));
        CommandableGoals.install(this, this.goalSelector, this.targetSelector);
    }

    @Override
    public void performRangedAttack(LivingEntity target, float power) {
        HitscanAttacks.fire(this, target, this.getAttributeValue(Attributes.ATTACK_DAMAGE), ParticleTypes.ITEM_SLIME, SoundEvents.SKELETON_SHOOT);
        // The single hook for the volley animation: the hitscan is instantaneous, so this is the only
        // moment the client can be told a shot happened.
        this.entityData.set(FIRE_TICKS, FIRE_ANIM_TICKS);
    }

    /** Ticks remaining in the spine volley animation; 0 when idle. Read by the renderer. */
    public int getFireTicks() {
        return this.entityData.get(FIRE_TICKS);
    }

    @Override
    public void tick() {
        super.tick();
        if (!this.level().isClientSide()) {
            int remaining = this.entityData.get(FIRE_TICKS);
            if (remaining > 0) {
                this.entityData.set(FIRE_TICKS, remaining - 1);
            }
        }
    }

    @Override
    public int getAmbientSoundInterval() {
        return 1200; // ~10x the vanilla default (80): ambient barks average ~1/minute, not ~1/6s.
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return AsteriskCraft.HYDRALISK_AMBIENT.get();
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return AsteriskCraft.HYDRALISK_HURT.get();
    }

    @Override
    protected SoundEvent getDeathSound() {
        return AsteriskCraft.HYDRALISK_DEATH.get();
    }
}
