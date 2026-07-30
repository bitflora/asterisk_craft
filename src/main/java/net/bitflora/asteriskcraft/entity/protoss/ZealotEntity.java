package net.bitflora.asteriskcraft.entity.protoss;

import net.bitflora.asteriskcraft.AsteriskCraft;
import net.bitflora.asteriskcraft.entity.Shielded;
import net.bitflora.asteriskcraft.entity.ai.CommandableGoals;
import net.bitflora.asteriskcraft.entity.ai.FactionTargetGoal;
import net.bitflora.asteriskcraft.entity.ai.RetaliateGoal;
import net.bitflora.asteriskcraft.entity.ai.SiegeBlockGoal;
import net.bitflora.asteriskcraft.stats.UnitAttributes;
import net.bitflora.asteriskcraft.stats.UnitStat;
import net.bitflora.asteriskcraft.stats.UnitStats;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

/**
 * The Protoss melee unit: a plain hostile mob (not a repurposed Zombie — see
 * docs/neoforge-api-notes.md for why) with vanilla player-aggression goals
 * replaced by pure faction targeting (see {@link FactionTargetGoal}). Sun-immune
 * because it's a distinct EntityType never added to {@code #minecraft:burn_in_daylight}.
 *
 * <p>Its numbers live in {@link net.bitflora.asteriskcraft.stats.UnitStats#ZEALOT} — not here.
 */
public class ZealotEntity extends Monster implements Shielded {
    private static final UnitStat STAT = UnitStats.ZEALOT;

    // Synced rather than broadcast as an entity event: an int carries the animation's progress (not
    // just its start) and can't collide with a vanilla LivingEntity event byte. Same shape as
    // SunkenColonyEntity, the mod's other animated attacker.
    private static final EntityDataAccessor<Integer> ATTACK_TICKS =
            SynchedEntityData.defineId(ZealotEntity.class, EntityDataSerializers.INT);

    public ZealotEntity(EntityType<? extends ZealotEntity> type, Level level) {
        super(type, level);
        this.setPersistenceRequired();
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(ATTACK_TICKS, 0);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return UnitAttributes.apply(Monster.createMonsterAttributes(), UnitStats.ZEALOT);
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        // Priority 0 (above the move/attack goals): the digger must be able to preempt movement to
        // batter through an obstruction, not merely fill a yield window. See SiegeBlockGoal.
        this.goalSelector.addGoal(0, new SiegeBlockGoal(this));
        this.goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.0, false));
        this.goalSelector.addGoal(8, new LookAtPlayerGoal(this, Player.class, 8.0f));
        this.goalSelector.addGoal(8, new RandomLookAroundGoal(this));
        this.targetSelector.addGoal(-1, new RetaliateGoal(this));
        this.targetSelector.addGoal(1, new FactionTargetGoal(this));
        CommandableGoals.install(this, this.goalSelector, this.targetSelector);
    }

    /**
     * The single hook for the strike animation. {@code MeleeAttackGoal} calls
     * {@code mob.swing(InteractionHand.MAIN_HAND)} on every attack and {@link SiegeBlockGoal} calls it
     * while battering through an obstruction; the one-argument {@code LivingEntity.swing} delegates
     * here, so overriding this form covers both without either goal knowing about the animation.
     */
    @Override
    public void swing(InteractionHand hand, boolean sendToSwingingEntity) {
        super.swing(hand, sendToSwingingEntity);
        if (!this.level().isClientSide()) {
            this.entityData.set(ATTACK_TICKS, STAT.attackAnimTicks());
        }
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

    @Override
    public int getAmbientSoundInterval() {
        return 1200; // ~10x the vanilla default (80): ambient barks average ~1/minute, not ~1/6s.
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return AsteriskCraft.ZEALOT_AMBIENT.get();
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return AsteriskCraft.ZEALOT_HURT.get();
    }

    @Override
    protected SoundEvent getDeathSound() {
        return AsteriskCraft.ZEALOT_DEATH.get();
    }

    public int getShield() {
        return STAT.shield();
    }
}
