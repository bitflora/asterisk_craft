package net.bitflora.asteriskcraft.entity.terran;

import net.bitflora.asteriskcraft.AsteriskCraft;
import net.bitflora.asteriskcraft.combat.AsteriskCraftDamageTypes;
import net.bitflora.asteriskcraft.combat.FlameCone;
import net.bitflora.asteriskcraft.entity.Organic;
import net.bitflora.asteriskcraft.entity.ai.CommandableGoals;
import net.bitflora.asteriskcraft.entity.ai.FactionTargetGoal;
import net.bitflora.asteriskcraft.entity.ai.FlameAttacks;
import net.bitflora.asteriskcraft.entity.ai.RetaliateGoal;
import net.bitflora.asteriskcraft.entity.ai.SiegeBlockGoal;
import net.bitflora.asteriskcraft.entity.ai.StuckWanderGoal;
import net.bitflora.asteriskcraft.entity.ai.UnitRangedAttackGoal;
import net.bitflora.asteriskcraft.faction.Garrison;
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
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.monster.RangedAttackMob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * The Terran shock trooper: a flamethrower that washes a widening cone over everything two blocks in
 * front of it and leaves the survivors burning. Where a Marine answers a body, a Firebat answers a
 * <em>pack</em> — it is the first thing the race has that gets better the more enemies are standing
 * together, which is the hole a roster of massed rifles left open.
 *
 * <p>Its goal ladder is the {@link MarineEntity}'s verbatim, down to {@link UnitRangedAttackGoal} and
 * the live radius that picks up a Bunker's firing-slit bonus. That is the point rather than a
 * shortcut: a two-block flamethrower is a stand-off weapon with a very short stand-off, so nothing
 * about how the unit closes, holds or retaliates is new. What is new is entirely inside
 * {@link #performRangedAttack}.
 *
 * <p>It is {@link Organic}, so it rides in a Bunker. That took one {@code implements} —
 * {@link BunkerEntity#boardable} asks a capability and names no unit.
 *
 * <p>Ambient bark and death line but <b>no hurt sound</b>, as with the Marine and the SCV: the
 * archive holds no Firebat pain line, so {@code Mob}'s default stands in rather than an idle bark
 * being pressed into service as one.
 *
 * <p>Its numbers live in {@link UnitStats#FIREBAT} — not here.
 */
public class FirebatEntity extends Monster implements RangedAttackMob, Organic {
    private static final UnitStat STAT = UnitStats.FIREBAT;
    private static final UnitStat.Ranged RANGED = STAT.rangedOrThrow();

    /**
     * The shape of one sweep, and the widening is the whole unit: half a block across at the nozzle
     * so it never catches something standing beside the Firebat, two blocks across at full reach so a
     * pack walking abreast is caught whole. The vertical wash is a block either way — enough to cover
     * anything standing on the same ground, not enough to lick a Mutalisk out of the sky.
     */
    private static final FlameCone.Shape CONE =
            new FlameCone.Shape(RANGED.range(), 0.25, 1.0, 1.0, 8);

    /** How long a victim burns after the flame passes over it. */
    private static final int IGNITE_TICKS = 80;

    // Synced rather than broadcast as an entity event, for the same reason the Marine's is: an int
    // carries the animation's progress and not just its start, and can't collide with a vanilla
    // LivingEntity event byte. A unit that never swings can't carry it in getAttackAnim.
    private static final EntityDataAccessor<Integer> FIRE_TICKS =
            SynchedEntityData.defineId(FirebatEntity.class, EntityDataSerializers.INT);

    public FirebatEntity(EntityType<? extends FirebatEntity> type, Level level) {
        super(type, level);
        this.setPersistenceRequired();
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(FIRE_TICKS, 0);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return UnitAttributes.apply(Monster.createMonsterAttributes(), UnitStats.FIREBAT);
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
        // The mod's own ranged goal rather than vanilla's, for the reason the Marine uses it: the
        // radius has to be live, so a Firebat burning out of a Bunker reaches a block further than
        // one standing beside it. See UnitRangedAttackGoal.
        this.goalSelector.addGoal(4, new UnitRangedAttackGoal(this, 1.0, RANGED.cooldown(),
                () -> RANGED.range() + Garrison.rangeBonusFor(this)));
        this.goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 8.0f));
        this.goalSelector.addGoal(6, new RandomLookAroundGoal(this));
        this.targetSelector.addGoal(-1, new RetaliateGoal(this));
        this.targetSelector.addGoal(1, new FactionTargetGoal(this));
        CommandableGoals.install(this, this.goalSelector, this.targetSelector);
    }

    @Override
    public void performRangedAttack(LivingEntity target, float power) {
        double reach = RANGED.range() + Garrison.rangeBonusFor(this);
        // The one guard the Marine does not need. UnitRangedAttackGoal fires on cadence whenever it
        // has line of sight, whatever the distance — harmless for a hitscan, which simply hits, and
        // wrong for a cone, which would belch flame and bark across the map at nothing while the unit
        // was still walking in. Skipping the sweep rather than the goal keeps the cadence intact: the
        // goal resets its own timer either way, so the Firebat fires the instant it arrives rather
        // than waiting out a fresh 1.5 seconds first.
        if (this.distanceToSqr(target) > reach * reach) {
            return;
        }
        Vec3 bearing = target.position().subtract(this.position());
        FlameAttacks.sweep(this, bearing, coneFor(reach),
                this.getAttributeValue(Attributes.ATTACK_DAMAGE), IGNITE_TICKS,
                AsteriskCraftDamageTypes.FLAME_THROWER, ParticleTypes.FLAME,
                AsteriskCraft.FIREBAT_ATTACK.get());
        // The single hook for the attack animation: the sweep is instantaneous, so this is the only
        // moment the client can be told a shot happened.
        this.entityData.set(FIRE_TICKS, STAT.attackAnimTicks());
    }

    /**
     * {@link #CONE} stretched to {@code reach}. A Bunker's firing slit is worth a block of reach, and
     * a cone that grew longer without its mouth growing with it would <em>narrow</em> as it
     * lengthened — so the spread scales by the same factor rather than staying where it was.
     */
    private static FlameCone.Shape coneFor(double reach) {
        if (reach == CONE.reach()) {
            return CONE;
        }
        double scale = reach / CONE.reach();
        return new FlameCone.Shape(reach, CONE.nozzleHalfWidth(), CONE.mouthHalfWidth() * scale,
                CONE.halfHeight(), CONE.steps());
    }

    /** Ticks remaining in the attack animation; 0 when idle. Read by the renderer. */
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
        return AsteriskCraft.FIREBAT_AMBIENT.get();
    }

    @Override
    protected SoundEvent getDeathSound() {
        return AsteriskCraft.FIREBAT_DEATH.get();
    }
}
