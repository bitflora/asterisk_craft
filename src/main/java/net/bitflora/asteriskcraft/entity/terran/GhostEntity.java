package net.bitflora.asteriskcraft.entity.terran;

import net.bitflora.asteriskcraft.AsteriskCraft;
import net.bitflora.asteriskcraft.combat.AsteriskCraftDamageTypes;
import net.bitflora.asteriskcraft.entity.Organic;
import net.bitflora.asteriskcraft.entity.ai.CommandableGoals;
import net.bitflora.asteriskcraft.entity.ai.FactionTargetGoal;
import net.bitflora.asteriskcraft.entity.ai.HitscanAttacks;
import net.bitflora.asteriskcraft.entity.ai.RetaliateGoal;
import net.bitflora.asteriskcraft.entity.ai.SiegeBlockGoal;
import net.bitflora.asteriskcraft.entity.ai.StuckWanderGoal;
import net.bitflora.asteriskcraft.entity.ai.UnitRangedAttackGoal;
import net.bitflora.asteriskcraft.faction.Cloaked;
import net.bitflora.asteriskcraft.faction.Garrison;
import net.bitflora.asteriskcraft.stats.UnitAttributes;
import net.bitflora.asteriskcraft.stats.UnitStat;
import net.bitflora.asteriskcraft.stats.UnitStats;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.monster.RangedAttackMob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * The Terran marksman: the race's answer to being out-ranged, and the first unit in the mod whose
 * cloak is <b>reactive</b>. Everything from the neck down is a {@link MarineEntity} — the same goal
 * ladder verbatim, the same {@link UnitRangedAttackGoal} with the live radius that picks up a
 * Bunker's firing-slit bonus, the same hitscan. Only two things are new, and they are the unit:
 *
 * <ul>
 *   <li><b>Seven blocks.</b> Two further than a rifle, which is the whole reason to pay for one.</li>
 *   <li><b>It vanishes when it is shot at.</b> {@link CloakClock} holds the rule; this class holds
 *       one hook into it ({@link #hurtServer}) and one synced int carrying it to the client.</li>
 * </ul>
 *
 * <p><b>The cloak costs no combat code at all.</b> {@link Cloaked#isCloakActive()} is the single
 * seam every consumer already reaches cloak through — {@code faction.FactionAttachments.isHostile}
 * for acquisition, {@code combat.TargetRetentionHandler} for a target already held when the cloak
 * comes up, {@code combat.DetectionHandler} for the detector sweep, and
 * {@code client.CloakRenderStateModifier} for what each viewer sees. The Lurker is why that third
 * one exists: it was the first unit whose cloak arrived <em>after</em> acquisition, and a Ghost's
 * arrives at exactly the same moment in a fight, so it needed nothing written for it.
 *
 * <p>The clock is synced rather than server-only because that seam is consulted on both sides: the
 * gate runs on the server and the render decision on the client, and a Ghost the client thought
 * was solid would be a unit nothing could shoot and everyone could see.
 *
 * <p>It is {@link Organic}, so it rides in a Bunker — which is also the one place its cloak does
 * nothing, since {@code faction.Garrison} already hides a rider from everything.
 *
 * <p>Ambient bark and death line but <b>no hurt sound</b>, as with the Marine, the Firebat and the
 * SCV: the archive holds no Ghost pain line, so {@code Mob}'s default stands in.
 *
 * <p>Its numbers live in {@link UnitStats#GHOST} — not here.
 */
public class GhostEntity extends Monster implements RangedAttackMob, Organic, Cloaked {
    private static final UnitStat STAT = UnitStats.GHOST;
    private static final UnitStat.Ranged RANGED = STAT.rangedOrThrow();

    // Synced rather than broadcast as an entity event, for the reason the Marine's is: an int
    // carries the animation's progress and not just its start, and can't collide with a vanilla
    // LivingEntity event byte. A ranged unit never swings, so getAttackAnim can't carry it.
    private static final EntityDataAccessor<Integer> FIRE_TICKS =
            SynchedEntityData.defineId(GhostEntity.class, EntityDataSerializers.INT);

    /**
     * The whole cloak state, as {@link CloakClock#value()}'s single signed int. Synced because
     * {@link #isCloakActive()} is asked on both sides — see the class doc.
     */
    private static final EntityDataAccessor<Integer> CLOAK_STATE =
            SynchedEntityData.defineId(GhostEntity.class, EntityDataSerializers.INT);

    private final CloakClock cloak = new CloakClock();

    public GhostEntity(EntityType<? extends GhostEntity> type, Level level) {
        super(type, level);
        this.setPersistenceRequired();
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(FIRE_TICKS, 0);
        builder.define(CLOAK_STATE, 0);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return UnitAttributes.apply(Monster.createMonsterAttributes(), UnitStats.GHOST);
    }

    @Override
    protected void registerGoals() {
        // The Marine's ladder verbatim — see MarineEntity for why each priority is where it is.
        this.goalSelector.addGoal(-1, new StuckWanderGoal(this, RANGED.range()));
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(0, new SiegeBlockGoal(this));
        this.goalSelector.addGoal(4, new UnitRangedAttackGoal(this, 1.0, RANGED.cooldown(),
                () -> RANGED.range() + Garrison.rangeBonusFor(this)));
        this.goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 8.0f));
        this.goalSelector.addGoal(6, new RandomLookAroundGoal(this));
        this.targetSelector.addGoal(-1, new RetaliateGoal(this));
        this.targetSelector.addGoal(1, new FactionTargetGoal(this));
        CommandableGoals.install(this, this.goalSelector, this.targetSelector);
    }

    // --- Cloak ---

    /**
     * The entire implementation of the cloak, read off the synced value rather than off the clock
     * so the client answers it identically — the clock itself only ticks on the server.
     */
    @Override
    public boolean isCloakActive() {
        return this.entityData.get(CLOAK_STATE) > 0;
    }

    /**
     * The one trigger. {@code super} is asked first so a hit that was blocked, absorbed or landed on
     * an already-dead Ghost doesn't spend the cloak, and a Ghost that dies to the hit is left alone
     * for the same reason.
     */
    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float damage) {
        boolean hurt = super.hurtServer(level, source, damage);
        if (hurt && this.isAlive() && this.cloak.onDamaged()) {
            this.entityData.set(CLOAK_STATE, this.cloak.value());
        }
        return hurt;
    }

    @Override
    public void performRangedAttack(LivingEntity target, float power) {
        // CRIT, as the Marine's: the one vanilla particle that reads as a tracer down the beam
        // HitscanAttacks draws. Firing does not break the cloak — the Dark Templar and the Lurker
        // both shoot from under theirs, and a sniper that uncloaked to shoot would never get to.
        HitscanAttacks.fire(this, target, this.getAttributeValue(Attributes.ATTACK_DAMAGE),
                AsteriskCraftDamageTypes.C10_CANISTER_RIFLE, ParticleTypes.CRIT,
                AsteriskCraft.GHOST_ATTACK.get());
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
            // Writes synced data only on the ticks the clock actually moved, which is none of them
            // for a Ghost that has never been shot at.
            if (this.cloak.tick()) {
                this.entityData.set(CLOAK_STATE, this.cloak.value());
            }
        }
    }

    // --- Persistence ---

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output);
        output.putInt("CloakState", this.cloak.value());
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
        this.cloak.restore(input.getIntOr("CloakState", 0));
        // The synced value is the client's only source for the cloak, so it has to be written here
        // too — a reloaded Ghost that was hidden must come back hidden, not standing in the open.
        this.entityData.set(CLOAK_STATE, this.cloak.value());
    }

    // --- Sounds ---

    @Override
    public int getAmbientSoundInterval() {
        return 1200; // ~10x the vanilla default (80): ambient barks average ~1/minute, not ~1/6s.
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return AsteriskCraft.GHOST_AMBIENT.get();
    }

    @Override
    protected SoundEvent getDeathSound() {
        return AsteriskCraft.GHOST_DEATH.get();
    }
}
