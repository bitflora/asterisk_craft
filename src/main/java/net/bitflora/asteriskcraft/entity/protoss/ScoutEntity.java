package net.bitflora.asteriskcraft.entity.protoss;

import net.bitflora.asteriskcraft.AsteriskCraft;
import net.bitflora.asteriskcraft.entity.Shielded;
import net.bitflora.asteriskcraft.entity.ai.CommandableGoals;
import net.bitflora.asteriskcraft.entity.ai.FactionTargetGoal;
import net.bitflora.asteriskcraft.entity.ai.HitscanAttacks;
import net.bitflora.asteriskcraft.entity.ai.HoverFlyingNavigation;
import net.bitflora.asteriskcraft.entity.ai.HoverGoal;
import net.bitflora.asteriskcraft.entity.ai.RetaliateGoal;
import net.bitflora.asteriskcraft.entity.ai.SiegeBlockGoal;
import net.bitflora.asteriskcraft.stats.UnitAttributes;
import net.bitflora.asteriskcraft.stats.UnitStat;
import net.bitflora.asteriskcraft.stats.UnitStats;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.FlyingMoveControl;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.RangedAttackGoal;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.monster.RangedAttackMob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

/**
 * The Protoss air unit and the answer to the {@link net.bitflora.asteriskcraft.entity.zerg.MutaliskEntity
 * Mutalisk}: a Gateway-trained fighter craft that cruises at the same altitude as the Mutalisk it
 * hunts, sharing its exact flight envelope (same altitude, same reach, same cadence) but out-lasting
 * and out-gunning it — 75 HP behind 50 shields, and a blaster shot worth more than twice a glave.
 * Like every flyer, melee ground units simply cannot reach it; a Photon Cannon still out-ranges it,
 * which keeps static defence the hard counter to air on both sides.
 *
 * <p>Flight is the existing stack used verbatim, and no goal knows about it: a vanilla
 * {@link FlyingMoveControl} for the airborne motion plus a {@link HoverFlyingNavigation} that lifts
 * every destination to cruising altitude <em>before</em> pathing (see that class for why the altitude
 * has to live in the path rather than the move control). A {@link HoverGoal} at the bottom of the goal
 * list holds it up in the gaps between orders.
 *
 * <p>Shields come from the {@link Shielded} marker alone — {@code combat/ShieldAttachments} grants the
 * pool to any Protoss-faction unit implementing it, and {@code combat/DelayedRegen} recharges it.
 *
 * <p>Its numbers live in {@link net.bitflora.asteriskcraft.stats.UnitStats#SCOUT} — not here.
 */
public class ScoutEntity extends Monster implements Shielded, RangedAttackMob {
    private static final UnitStat STAT = UnitStats.SCOUT;
    private static final UnitStat.Ranged RANGED = STAT.rangedOrThrow();
    private static final UnitStat.Flight FLIGHT = STAT.flightOrThrow();

    public ScoutEntity(EntityType<? extends ScoutEntity> type, Level level) {
        super(type, level);
        // hoversInPlace = true: the move control switches gravity off for us and, unlike the
        // ground-falling variant, never switches it back on when it runs out of somewhere to go.
        this.moveControl = new FlyingMoveControl(this, 20, true);
        this.setPersistenceRequired();
    }

    public static AttributeSupplier.Builder createAttributes() {
        return UnitAttributes.apply(Monster.createMonsterAttributes(), UnitStats.SCOUT);
    }

    @Override
    protected PathNavigation createNavigation(Level level) {
        HoverFlyingNavigation navigation = new HoverFlyingNavigation(this, level, FLIGHT.hoverHeight());
        navigation.setCanOpenDoors(false);
        navigation.setCanFloat(true);
        // The default path length is far shorter than this unit's follow range, so a Scout would give
        // up on distant targets mid-approach. (Vanilla's Bee raises it for the same reason.)
        navigation.setRequiredPathLength(FLIGHT.requiredPathLength());
        return navigation;
    }

    @Override
    protected void registerGoals() {
        // No FloatGoal: it flies, and the navigation is allowed to float across water anyway.
        // Priority 0, as on the ground units: the siege goal must be able to preempt movement. The
        // two-arg form gives it its full attack reach, so it can batter a core from where it hovers.
        this.goalSelector.addGoal(0, new SiegeBlockGoal(this, RANGED.range()));
        this.goalSelector.addGoal(4, new RangedAttackGoal(this, 1.0, RANGED.cooldown(), RANGED.range()));
        this.goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 12.0f));
        this.goalSelector.addGoal(6, new RandomLookAroundGoal(this));
        // Last: only holds altitude in the gaps between real orders.
        this.goalSelector.addGoal(8, new HoverGoal(this, FLIGHT.hoverHeight(), 1.0));
        this.targetSelector.addGoal(-1, new RetaliateGoal(this));
        this.targetSelector.addGoal(1, new FactionTargetGoal(this));
        CommandableGoals.install(this, this.goalSelector, this.targetSelector);
    }

    @Override
    public int getAmbientSoundInterval() {
        return 1200; // ~10x the vanilla default (80): ambient barks average ~1/minute, not ~1/6s.
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return AsteriskCraft.SCOUT_AMBIENT.get();
    }

    @Override
    public void performRangedAttack(LivingEntity target, float power) {
        HitscanAttacks.fire(this, target, this.getAttributeValue(Attributes.ATTACK_DAMAGE),
                ParticleTypes.ELECTRIC_SPARK, SoundEvents.SHULKER_SHOOT);
    }

    @Override
    public boolean causeFallDamage(double distance, float multiplier, DamageSource source) {
        // Gravity is only switched off once the move control has somewhere to fly, so a Scout warped
        // in over a drop would otherwise take fall damage before it ever got airborne.
        return false;
    }

    public int getShield() {
        return STAT.shield();
    }
}
