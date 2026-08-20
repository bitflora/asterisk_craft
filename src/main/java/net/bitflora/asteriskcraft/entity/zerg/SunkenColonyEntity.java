package net.bitflora.asteriskcraft.entity.zerg;

import net.bitflora.asteriskcraft.entity.Rooted;
import net.bitflora.asteriskcraft.entity.ai.FactionTargetGoal;
import net.bitflora.asteriskcraft.entity.ai.RetaliateGoal;
import net.bitflora.asteriskcraft.entity.ai.zerg.SunkenSpikeGoal;
import net.bitflora.asteriskcraft.entity.protoss.PhotonCannonEntity;
import net.bitflora.asteriskcraft.stats.UnitAttributes;
import net.bitflora.asteriskcraft.stats.UnitStat;
import net.bitflora.asteriskcraft.stats.UnitStats;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
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
 * <p>The colony itself is silent — no hurt or death sound — since a rooted structure grunting or
 * screaming like a creature doesn't fit. Its tentacle strike still makes noise (see
 * {@link SunkenSpikeGoal}/{@link SunkenSpikeEntity}).
 *
 * <p>Its numbers live in {@link net.bitflora.asteriskcraft.stats.UnitStats#SUNKEN_COLONY} — not here.
 */
public class SunkenColonyEntity extends Monster implements Rooted {
    private static final UnitStat STAT = UnitStats.SUNKEN_COLONY;

    // Synced rather than broadcast as an entity event: an int carries the animation's progress (not
    // just its start) and can't collide with a vanilla LivingEntity event byte.
    private static final EntityDataAccessor<Integer> ATTACK_TICKS =
            SynchedEntityData.defineId(SunkenColonyEntity.class, EntityDataSerializers.INT);

    public SunkenColonyEntity(EntityType<? extends SunkenColonyEntity> type, Level level) {
        super(type, level);
        this.setPersistenceRequired();
    }

    public static AttributeSupplier.Builder createAttributes() {
        return UnitAttributes.apply(Monster.createMonsterAttributes(), UnitStats.SUNKEN_COLONY);
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
        this.entityData.set(ATTACK_TICKS, STAT.attackAnimTicks());
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

    // A rooted structure doesn't grunt when hit or die with a creature scream — only its tentacle
    // strike (see SunkenSpikeGoal/SunkenSpikeEntity) makes any sound.
    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return null;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return null;
    }
}
