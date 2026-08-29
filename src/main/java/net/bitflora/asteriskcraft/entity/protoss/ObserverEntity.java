package net.bitflora.asteriskcraft.entity.protoss;

import net.bitflora.asteriskcraft.AsteriskCraft;
import net.bitflora.asteriskcraft.entity.Detector;
import net.bitflora.asteriskcraft.entity.Flyer;
import net.bitflora.asteriskcraft.entity.Shielded;
import net.bitflora.asteriskcraft.entity.ai.CommandableGoals;
import net.bitflora.asteriskcraft.entity.ai.CommandedMoveGoal;
import net.bitflora.asteriskcraft.entity.ai.HoverFlyingNavigation;
import net.bitflora.asteriskcraft.entity.ai.HoverGoal;
import net.bitflora.asteriskcraft.entity.ai.StuckWanderGoal;
import net.bitflora.asteriskcraft.faction.Cloaked;
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
 * The Protoss eye: a Stargate-trained flyer that carries a detection bubble, has no attack at all,
 * and is permanently cloaked. It is what the Protoss had no answer to before — their only
 * {@link Detector} was the Photon Cannon, which is rooted, so an army could detect where it had
 * already built and nowhere else. An Observer walks that bubble into a Dark Templar's ambush
 * instead of waiting for one to wander into a colony.
 *
 * <p><b>It is the mod's first unit that is both {@link Cloaked} and a {@link Detector}, and the
 * pairing needed no new machinery.</b> The two mechanisms only meet through a reveal mask that
 * means "currently seen by this side", and {@code faction.Cloaking.isVisibleTo} short-circuits on
 * the viewer's own army — so an Observer lights enemies up for its side while remaining
 * unacquirable by theirs, and neither {@code combat.DetectionHandler} nor the {@code isHostile}
 * cloak gate knows the combination exists.
 *
 * <p>Structurally it is {@code entity.zerg.OverlordEntity} with two markers added, and both are
 * one-word opt-ins: {@link Cloaked}'s {@code default isCloakActive()} is the whole of a permanent
 * cloak (the Dark Templar takes it the same way), and {@link Shielded} is what
 * {@code combat.ShieldAttachments} gates the Protoss shield pool on.
 *
 * <p><b>It extends {@link PathfinderMob}, not {@code Monster}</b>, for the reason the Overlord's
 * class doc gives: a unit that never attacks must carry no {@code FactionTargetGoal},
 * {@code RetaliateGoal} or {@code SiegeBlockGoal}, because a target it could never act on is worse
 * than no target at all — holding one blocks re-acquisition and buys nothing. For the same reason
 * it takes move orders through a bare {@link CommandedMoveGoal} rather than
 * {@link CommandableGoals#install}, whose {@code CommandedAttackGoal} it could never honour.
 *
 * <p>Flight is the Scout's stack reused verbatim and is invisible to the goal library: a vanilla
 * {@link FlyingMoveControl} plus a {@link HoverFlyingNavigation} that lifts every destination to
 * cruising altitude <em>before</em> pathing, with a bottom-priority {@link HoverGoal} holding it up
 * in the gaps between orders. That last goal is also what gets it airborne at all — a freshly
 * produced unit is placed on the ground by {@code building.SpawnSpots}.
 *
 * <p>Its numbers live in {@link UnitStats#OBSERVER} — not here.
 */
public class ObserverEntity extends PathfinderMob implements Flyer, Detector, Shielded, Cloaked {
    private static final UnitStat STAT = UnitStats.OBSERVER;
    private static final UnitStat.Flight FLIGHT = STAT.flightOrThrow();
    private static final UnitStat.Detection DETECTION = STAT.detectionOrThrow();

    public ObserverEntity(EntityType<? extends ObserverEntity> type, Level level) {
        super(type, level);
        // hoversInPlace = true: the move control switches gravity off for us and, unlike the
        // ground-falling variant, never switches it back on when it runs out of somewhere to go.
        this.moveControl = new FlyingMoveControl(this, 20, true);
        this.setPersistenceRequired();
    }

    public static AttributeSupplier.Builder createAttributes() {
        return UnitAttributes.apply(PathfinderMob.createMobAttributes(), UnitStats.OBSERVER);
    }

    @Override
    protected PathNavigation createNavigation(Level level) {
        HoverFlyingNavigation navigation = new HoverFlyingNavigation(this, level, FLIGHT.hoverHeight());
        navigation.setCanOpenDoors(false);
        navigation.setCanFloat(true);
        // The default path length is far shorter than this unit's follow range, so an Observer
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
        // equivalent of reach — an Observer that drifts is still doing its job.
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

    // No isCloakActive() override: the Cloaked default is "always", which is the whole of a
    // permanent cloak. The Dark Templar opts in exactly the same way.

    @Override
    public boolean causeFallDamage(double distance, float multiplier, DamageSource source) {
        // Gravity is only switched off once the move control has somewhere to fly, so an Observer
        // produced over a drop would otherwise take fall damage before it ever got airborne.
        return false;
    }

    @Override
    public int getAmbientSoundInterval() {
        return 1200; // ~10x the vanilla default (80): ambient barks average ~1/minute, not ~1/6s.
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return AsteriskCraft.OBSERVER_AMBIENT.get();
    }

    // No getHurtSound override: the source clips include no hurt bark, so it keeps the vanilla one
    // rather than reusing another unit's voice. The Overlord is in the same position.

    @Override
    protected SoundEvent getDeathSound() {
        return AsteriskCraft.OBSERVER_DEATH.get();
    }

    @Override
    public int getShield() {
        return STAT.shield();
    }
}
