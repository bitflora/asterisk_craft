package net.bitflora.asteriskcraft.entity.terran;

import net.bitflora.asteriskcraft.AsteriskCraft;
import net.bitflora.asteriskcraft.combat.AsteriskCraftDamageTypes;
import net.bitflora.asteriskcraft.entity.ai.CommandableGoals;
import net.bitflora.asteriskcraft.entity.ai.FactionTargetGoal;
import net.bitflora.asteriskcraft.entity.ai.HitscanAttacks;
import net.bitflora.asteriskcraft.entity.ai.RetaliateGoal;
import net.bitflora.asteriskcraft.entity.ai.SiegeBlockGoal;
import net.bitflora.asteriskcraft.entity.ai.StuckWanderGoal;
import net.bitflora.asteriskcraft.stats.UnitAttributes;
import net.bitflora.asteriskcraft.stats.UnitStat;
import net.bitflora.asteriskcraft.stats.UnitStats;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.sounds.SoundEvent;
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
 * The Terran line infantry, and the first thing the race has that goes looking for a fight. The
 * shape is the Hydralisk's — a plain hostile mob (not a repurposed Skeleton, see
 * docs/neoforge-api-notes.md) with faction targeting, a {@link SiegeBlockGoal}, and a hitscan
 * fired on a fixed cadence by a plain {@link RangedAttackGoal}.
 *
 * <p>The difference from its {@link net.bitflora.asteriskcraft.entity.terran.ScvEntity} stablemate
 * is the whole worker/soldier line: an SCV carries a weapon and no {@link FactionTargetGoal}, so it
 * mines right past an enemy army; a Marine carries one and so goes and finds it.
 *
 * <p>It has an ambient bark and a death line but <b>no hurt sound</b>: as with the SCV, the archive
 * holds no Marine pain line, so {@code Mob}'s default stands in rather than an idle bark being
 * pressed into service as one.
 *
 * <p>Its numbers live in {@link UnitStats#MARINE} — not here.
 */
public class MarineEntity extends Monster implements RangedAttackMob {
    private static final UnitStat STAT = UnitStats.MARINE;
    private static final UnitStat.Ranged RANGED = STAT.rangedOrThrow();

    // Synced rather than broadcast as an entity event, for the same reason the Hydralisk's is: an
    // int carries the animation's progress and not just its start, and can't collide with a vanilla
    // LivingEntity event byte. A ranged unit never swings, so getAttackAnim can't carry it.
    private static final EntityDataAccessor<Integer> FIRE_TICKS =
            SynchedEntityData.defineId(MarineEntity.class, EntityDataSerializers.INT);

    public MarineEntity(EntityType<? extends MarineEntity> type, Level level) {
        super(type, level);
        this.setPersistenceRequired();
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(FIRE_TICKS, 0);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return UnitAttributes.apply(Monster.createMonsterAttributes(), UnitStats.MARINE);
    }

    @Override
    protected void registerGoals() {
        // Priority -1, above even the digger: it exists to take a pinned unit off whichever goal has
        // it pinned, which it can only do from a lower priority number. See StuckWanderGoal.
        this.goalSelector.addGoal(-1, new StuckWanderGoal(this, RANGED.range()));
        this.goalSelector.addGoal(0, new FloatGoal(this));
        // Priority 0 (above the move/attack goals): the digger must be able to preempt movement to
        // batter through an obstruction, not merely fill a yield window. See SiegeBlockGoal.
        this.goalSelector.addGoal(0, new SiegeBlockGoal(this));
        this.goalSelector.addGoal(4, new RangedAttackGoal(this, 1.0, RANGED.cooldown(), RANGED.range()));
        this.goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 8.0f));
        this.goalSelector.addGoal(6, new RandomLookAroundGoal(this));
        this.targetSelector.addGoal(-1, new RetaliateGoal(this));
        this.targetSelector.addGoal(1, new FactionTargetGoal(this));
        CommandableGoals.install(this, this.goalSelector, this.targetSelector);
    }

    @Override
    public void performRangedAttack(LivingEntity target, float power) {
        // CRIT rather than the Hydralisk's slime or the Dragoon's sculk: a hot metal slug, and the
        // one vanilla particle that reads as a tracer down the beam HitscanAttacks draws.
        HitscanAttacks.fire(this, target, this.getAttributeValue(Attributes.ATTACK_DAMAGE),
                AsteriskCraftDamageTypes.GAUSS_RIFLE, ParticleTypes.CRIT,
                AsteriskCraft.MARINE_ATTACK.get());
        // The single hook for the recoil animation: the hitscan is instantaneous, so this is the
        // only moment the client can be told a shot happened.
        this.entityData.set(FIRE_TICKS, STAT.attackAnimTicks());
    }

    /** Ticks remaining in the recoil animation; 0 when idle. Read by the renderer. */
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
        return AsteriskCraft.MARINE_AMBIENT.get();
    }

    @Override
    protected SoundEvent getDeathSound() {
        return AsteriskCraft.MARINE_DEATH.get();
    }
}
