package net.bitflora.asteriskcraft.entity.terran;

import net.bitflora.asteriskcraft.AsteriskCraft;
import net.bitflora.asteriskcraft.combat.AsteriskCraftDamageTypes;
import net.bitflora.asteriskcraft.entity.Flyer;
import net.bitflora.asteriskcraft.entity.Organic;
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
 * The Terran walking mech, and the first thing the race has that can look up <em>while moving</em>.
 * A Missile Turret is bolted to the floor and a Wraith has to be in the air itself, so until this a
 * Mutalisk flock over a marching column was answered by walking the column home.
 *
 * <p><b>It writes almost nothing of its own.</b> The chassis is {@link MarineEntity}'s goal ladder
 * verbatim, and the gun is the {@link WraithEntity}'s: {@link #performRangedAttack} branches once on
 * {@code Flyer.isAir} and that one test picks both the damage and which of the two firing clips
 * plays, so a player hears whether a Goliath is shooting at the sky. Air-ness is the {@link Flyer}
 * unit-type marker, never a concrete entity class and never {@code entity.Altitude} — a landed
 * Mutalisk still takes the full burst (see CLAUDE.md).
 *
 * <p>Deliberately <b>not</b> {@link Organic}: a Goliath is a machine, so it cannot be crammed into a
 * Bunker the way the race's infantry can. That is the entire cost of the rule — the door is
 * {@code entity.terran.BunkerEntity.boardable} and it names no unit. It follows that this uses
 * vanilla's {@link RangedAttackGoal} rather than {@code entity.ai.UnitRangedAttackGoal}: the live
 * radius that goal exists for is the firing-slit bonus, and nothing that cannot board ever earns one.
 *
 * <p>Ambient bark and death line but <b>no hurt sound</b>, as with every other Terran unit: the
 * archive holds no Goliath pain line, so {@code Mob}'s default stands in.
 *
 * <p>Its numbers live in {@link UnitStats#GOLIATH} — not here.
 */
public class GoliathEntity extends Monster implements RangedAttackMob {
    private static final UnitStat STAT = UnitStats.GOLIATH;
    private static final UnitStat.Ranged RANGED = STAT.rangedOrThrow();

    // Synced rather than broadcast as an entity event, for the Marine's reason: an int carries the
    // animation's progress and not just its start, and can't collide with a vanilla LivingEntity
    // event byte. A ranged unit never swings, so getAttackAnim can't carry it.
    private static final EntityDataAccessor<Integer> FIRE_TICKS =
            SynchedEntityData.defineId(GoliathEntity.class, EntityDataSerializers.INT);

    public GoliathEntity(EntityType<? extends GoliathEntity> type, Level level) {
        super(type, level);
        this.setPersistenceRequired();
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(FIRE_TICKS, 0);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return UnitAttributes.apply(Monster.createMonsterAttributes(), UnitStats.GOLIATH);
    }

    @Override
    protected void registerGoals() {
        // The Marine's ladder verbatim — see MarineEntity for why each priority is where it is.
        this.goalSelector.addGoal(-1, new StuckWanderGoal(this, RANGED.range()));
        this.goalSelector.addGoal(0, new FloatGoal(this));
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
        // One test, two consequences — the Wraith's shape. The anti-air bonus rides on top of the
        // attribute rather than replacing it, so one attribute still describes the shot, and the
        // same branch picks which of the two firing clips plays.
        boolean air = Flyer.isAir(target);
        double damage = this.getAttributeValue(Attributes.ATTACK_DAMAGE)
                + (air ? STAT.antiAirBonus() : 0.0);
        // CRIT, as the Marine's rifle uses: a hot metal slug, and the one vanilla particle that
        // reads as a tracer down the beam HitscanAttacks draws.
        HitscanAttacks.fire(this, target, damage, AsteriskCraftDamageTypes.TWIN_AUTOCANNONS,
                ParticleTypes.CRIT,
                (air ? AsteriskCraft.GOLIATH_ATTACK_AIR : AsteriskCraft.GOLIATH_ATTACK_GROUND).get());
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
        return AsteriskCraft.GOLIATH_AMBIENT.get();
    }

    @Override
    protected SoundEvent getDeathSound() {
        return AsteriskCraft.GOLIATH_DEATH.get();
    }
}
