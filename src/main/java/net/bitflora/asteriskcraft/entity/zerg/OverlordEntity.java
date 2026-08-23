package net.bitflora.asteriskcraft.entity.zerg;

import net.bitflora.asteriskcraft.AsteriskCraft;
import net.bitflora.asteriskcraft.entity.Detector;
import net.bitflora.asteriskcraft.entity.Flyer;
import net.bitflora.asteriskcraft.entity.ai.CommandableGoals;
import net.bitflora.asteriskcraft.entity.ai.CommandedMoveGoal;
import net.bitflora.asteriskcraft.entity.ai.HoverFlyingNavigation;
import net.bitflora.asteriskcraft.entity.ai.HoverGoal;
import net.bitflora.asteriskcraft.entity.ai.StuckWanderGoal;
import net.bitflora.asteriskcraft.stats.UnitAttributes;
import net.bitflora.asteriskcraft.stats.UnitStat;
import net.bitflora.asteriskcraft.stats.UnitStats;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.control.FlyingMoveControl;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

/**
 * The swarm's eye in the sky: a huge, slow, entirely unarmed flyer whose only job is to carry a
 * detection bubble. It is the mod's first <em>mobile</em> {@link Detector} — the Photon Cannon and
 * the Spore Colony are both rooted, so before this unit an army could only detect where it had
 * already built. An Overlord drifts its bubble forward, which is what lets the swarm walk into a
 * Dark Templar rather than wait for one to wander into a colony.
 *
 * <p>Being a detector costs it nothing in code: {@code combat.DetectionHandler} sweeps off the
 * detector's live position on a global tick handler, so a detector that moves needs no machinery a
 * rooted one doesn't — {@link #detection()} is the whole contract.
 *
 * <p><b>It extends {@link PathfinderMob}, not {@code Monster}.</b> Every other flyer in the mod is a
 * {@code Monster implements RangedAttackMob}; this one never attacks anything, so it follows
 * {@code entity.WorkerEntity}'s shape instead. That is why it carries no {@code FactionTargetGoal},
 * no {@code RetaliateGoal} and no {@code SiegeBlockGoal}: a target it could never act on is worse
 * than no target at all, because holding one blocks re-acquisition and buys nothing. For the same
 * reason it takes move orders through a bare {@link CommandedMoveGoal} rather than
 * {@link CommandableGoals#install}, whose {@code CommandedAttackGoal} it could never honour.
 *
 * <p>Flight is the Mutalisk's stack reused verbatim and is invisible to the goal library: a vanilla
 * {@link FlyingMoveControl} plus a {@link HoverFlyingNavigation} that lifts every destination to
 * cruising altitude <em>before</em> pathing, with a bottom-priority {@link HoverGoal} holding it up
 * in the gaps between orders. That last goal is also what gets it airborne at all — a freshly
 * spawned unit is placed on the ground by {@code building.UnitSpawns}.
 *
 * <p>Its numbers live in {@link UnitStats#OVERLORD} — not here.
 */
public class OverlordEntity extends PathfinderMob implements Flyer, Detector {
    private static final UnitStat STAT = UnitStats.OVERLORD;
    private static final UnitStat.Flight FLIGHT = STAT.flightOrThrow();
    private static final UnitStat.Detection DETECTION = STAT.detectionOrThrow();

    public OverlordEntity(EntityType<? extends OverlordEntity> type, Level level) {
        super(type, level);
        // hoversInPlace = true: the move control switches gravity off for us and, unlike the
        // ground-falling variant, never switches it back on when it runs out of somewhere to go.
        this.moveControl = new FlyingMoveControl(this, 20, true);
        this.setPersistenceRequired();
    }

    public static AttributeSupplier.Builder createAttributes() {
        return UnitAttributes.apply(PathfinderMob.createMobAttributes(), UnitStats.OVERLORD);
    }

    @Override
    protected PathNavigation createNavigation(Level level) {
        HoverFlyingNavigation navigation = new HoverFlyingNavigation(this, level, FLIGHT.hoverHeight());
        navigation.setCanOpenDoors(false);
        navigation.setCanFloat(true);
        // The default path length is far shorter than this unit's follow range, so an Overlord
        // would give up on a distant move order mid-flight. (Vanilla's Bee raises it for the same
        // reason.)
        navigation.setRequiredPathLength(FLIGHT.requiredPathLength());
        return navigation;
    }

    @Override
    protected void registerGoals() {
        // No FloatGoal: it flies, and the navigation is allowed to float across water anyway.
        CommandableGoals.configureNavigation(this);
        // Priority -1, above everything: it exists to take a pinned unit off whichever goal has it
        // pinned, which it can only do from a lower priority number. See StuckWanderGoal. Its radius
        // is the detection envelope rather than a weapon range, because that bubble is this unit's
        // equivalent of reach — an Overlord that drifts is still doing its job.
        this.goalSelector.addGoal(-1, new StuckWanderGoal(this, DETECTION.radius()));
        this.goalSelector.addGoal(1, new CommandedMoveGoal(this, 1.1));
        this.goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 12.0f));
        this.goalSelector.addGoal(6, new RandomLookAroundGoal(this));
        // Last: only holds altitude in the gaps between real orders.
        this.goalSelector.addGoal(8, new HoverGoal(this, FLIGHT.hoverHeight(), 1.0));
    }

    @Override
    public UnitStat.Detection detection() {
        return DETECTION;
    }

    @Override
    public boolean causeFallDamage(double distance, float multiplier, DamageSource source) {
        // Gravity is only switched off once the move control has somewhere to fly, so an Overlord
        // spawned over a drop would otherwise take fall damage before it ever got airborne.
        return false;
    }

    @Override
    public int getAmbientSoundInterval() {
        return 1200; // ~10x the vanilla default (80): ambient barks average ~1/minute, not ~1/6s.
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return AsteriskCraft.OVERLORD_AMBIENT.get();
    }

    // No getHurtSound override: the source clips include no hurt bark, so it keeps the vanilla one
    // rather than reusing another unit's voice. The Ultralisk and Lurker are in the same position.

    @Override
    protected SoundEvent getDeathSound() {
        return AsteriskCraft.OVERLORD_DEATH.get();
    }
}
