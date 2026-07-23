package net.bitflora.asteriskcraft.entity.zerg;

import net.bitflora.asteriskcraft.AsteriskCraft;
import net.bitflora.asteriskcraft.entity.ai.FactionTargetGoal;
import net.bitflora.asteriskcraft.entity.ai.RetaliateGoal;
import net.bitflora.asteriskcraft.entity.ai.zerg.SunkenSpikeGoal;
import net.bitflora.asteriskcraft.entity.protoss.PhotonCannonEntity;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.Level;

/**
 * The Zerg static defence — the mirror of the Protoss {@link PhotonCannonEntity}: a rooted
 * structure-creature that plants beside a Hive and impales anything of an enemy faction (the player
 * included) that walks into range. It never moves.
 *
 * <p>Its attack is not the {@link net.bitflora.asteriskcraft.entity.ai.HitscanAttacks} beam the
 * mobile ranged units use — the tentacle whips forward and drives a {@link SunkenSpikeEntity} up out
 * of the ground under the target (see {@link SunkenSpikeGoal}). That means a moving target can
 * genuinely outrun the strike, which is the intended trade for hitting far harder than anything else
 * in the mod.
 *
 * <p>Targeting goes through {@link FactionTargetGoal} like every other combat unit, so nothing here
 * knows or cares which side is the enemy. It is deliberately <em>not</em> commandable (no
 * {@code CommandableGoals}) — a rooted building has nowhere to be ordered to.
 *
 * <p>Sounds are borrowed from the Hydralisk: no Sunken Colony audio was authored, and a silent
 * structure reads as broken.
 */
public class SunkenColonyEntity extends Monster {
    public static final int MAX_HEALTH = 150;
    public static final float ATTACK_DAMAGE = 20.0f;
    /** Reach of the tentacle, in blocks — comfortably out-ranging every mobile unit in the mod. */
    public static final double RANGE = 11.0;
    /** One strike every 1.6s. */
    public static final int ATTACK_COOLDOWN = 32;
    /** Length of the rear-back-and-whip animation the client plays on each strike. */
    public static final int ATTACK_ANIM_TICKS = 12;

    // Synced rather than broadcast as an entity event: an int carries the animation's progress (not
    // just its start) and can't collide with a vanilla LivingEntity event byte.
    private static final EntityDataAccessor<Integer> ATTACK_TICKS =
            SynchedEntityData.defineId(SunkenColonyEntity.class, EntityDataSerializers.INT);

    public SunkenColonyEntity(EntityType<? extends SunkenColonyEntity> type, Level level) {
        super(type, level);
        this.setPersistenceRequired();
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, MAX_HEALTH)
                .add(Attributes.MOVEMENT_SPEED, 0.0)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0)
                .add(Attributes.ATTACK_DAMAGE, ATTACK_DAMAGE)
                // Equal to RANGE so the target selector never acquires something the tentacle can't
                // reach — a rooted attacker can't close the gap.
                .add(Attributes.FOLLOW_RANGE, RANGE);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(ATTACK_TICKS, 0);
    }

    @Override
    protected void registerGoals() {
        // No movement, look, or wander goals at all — like the Photon Cannon, this is a fixed turret.
        this.goalSelector.addGoal(1, new SunkenSpikeGoal(this));
        this.targetSelector.addGoal(-1, new RetaliateGoal(this));
        this.targetSelector.addGoal(1, new FactionTargetGoal(this));
    }

    /** Starts the strike animation on every client tracking this colony. */
    public void triggerAttackAnimation() {
        this.entityData.set(ATTACK_TICKS, ATTACK_ANIM_TICKS);
    }

    /** Ticks remaining in the strike animation; 0 when idle. Read by the renderer. */
    public int getAttackTicks() {
        return this.entityData.get(ATTACK_TICKS);
    }

    @Override
    public void tick() {
        super.tick();
        if (!this.level().isClientSide()) {
            int remaining = this.getAttackTicks();
            if (remaining > 0) {
                this.entityData.set(ATTACK_TICKS, remaining - 1);
            }
        }
    }

    // --- Rooted: can't be pushed or knocked around ---

    @Override
    public boolean isPushable() {
        return false;
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
