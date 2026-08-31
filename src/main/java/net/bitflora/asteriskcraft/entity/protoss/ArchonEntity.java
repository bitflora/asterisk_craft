package net.bitflora.asteriskcraft.entity.protoss;

import net.bitflora.asteriskcraft.AsteriskCraft;
import net.bitflora.asteriskcraft.combat.AsteriskCraftDamageTypes;
import net.bitflora.asteriskcraft.entity.Shielded;
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
 * The Protoss answer to a pack: two Templar burned into one ball of psionic energy, whose shot
 * washes off its target onto everything standing with it and leaves the target itself alight. The
 * Gateway's other three units all answer a single body, which is the hole this fills — the Terran
 * got the same thing from the {@code FirebatEntity}'s cone.
 *
 * <p>{@code RangedAttackMob} at two blocks of reach, which is not the contradiction it looks like:
 * the range group is "how far it holds at and fires from", and an Archon holds at arm's length. The
 * goal ladder is the Firebat's for that reason rather than the Dragoon's — a two-block stand-off
 * closes and gets stuck exactly the way a flamethrower's does, hence {@link StuckWanderGoal}. Unlike
 * the Firebat it needs no distance guard in {@link #performRangedAttack}: the shot is a hitscan
 * aimed at a target that has already been picked, so firing early hits rather than belching into
 * empty air.
 *
 * <p>Plain {@code RangedAttackGoal} rather than the mod's {@code UnitRangedAttackGoal}: that one
 * exists to make the firing radius live for a unit shooting out of a Bunker's slit, and an Archon is
 * not {@code entity.Organic}, so it can never be in one.
 *
 * <p>Ambient bark and death line but <b>no hurt sound</b>, as with the Dark Templar: the archive
 * holds no Archon pain line, so {@code Mob}'s default stands in rather than an idle bark being
 * pressed into service as one.
 *
 * <p>Its numbers live in {@link UnitStats#ARCHON} — not here.
 */
public class ArchonEntity extends Monster implements Shielded, RangedAttackMob {
    private static final UnitStat STAT = UnitStats.ARCHON;
    private static final UnitStat.Ranged RANGED = STAT.rangedOrThrow();
    private static final UnitStat.Splash SPLASH = STAT.splashOrThrow();

    /**
     * How long the target burns after the shockwave lands. The Firebat's number, so the two units
     * that set things alight agree on what "on fire" is worth. Not a balance column: the Firebat
     * established that ignition is a fact about a unit's weapon rather than a dial a balance pass
     * sorts a spreadsheet by.
     */
    private static final int IGNITE_TICKS = 80;

    // Synced rather than broadcast as an entity event, for the Zealot's reason: an int carries the
    // animation's progress and not just its start, and can't collide with a vanilla LivingEntity
    // event byte. A unit that never swings can't carry it in getAttackAnim.
    private static final EntityDataAccessor<Integer> ATTACK_TICKS =
            SynchedEntityData.defineId(ArchonEntity.class, EntityDataSerializers.INT);

    public ArchonEntity(EntityType<? extends ArchonEntity> type, Level level) {
        super(type, level);
        this.setPersistenceRequired();
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(ATTACK_TICKS, 0);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return UnitAttributes.apply(Monster.createMonsterAttributes(), UnitStats.ARCHON);
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
        HitscanAttacks.fireSplash(this, target, this.getAttributeValue(Attributes.ATTACK_DAMAGE),
                AsteriskCraftDamageTypes.PSIONIC_SHOCKWAVE, ParticleTypes.SOUL_FIRE_FLAME,
                AsteriskCraft.ARCHON_ATTACK.get(), SPLASH, IGNITE_TICKS);
        // The single hook for the strike animation: the shot is instantaneous, so this is the only
        // moment the client can be told one happened.
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
            int remaining = this.entityData.get(ATTACK_TICKS);
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
        return AsteriskCraft.ARCHON_AMBIENT.get();
    }

    @Override
    protected SoundEvent getDeathSound() {
        return AsteriskCraft.ARCHON_DEATH.get();
    }

    public int getShield() {
        return STAT.shield();
    }
}
