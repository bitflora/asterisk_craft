package net.bitflora.asteriskcraft.entity.terran;

import net.bitflora.asteriskcraft.AsteriskCraft;
import net.bitflora.asteriskcraft.combat.SupportPulse;
import net.bitflora.asteriskcraft.entity.Detector;
import net.bitflora.asteriskcraft.entity.Flyer;
import net.bitflora.asteriskcraft.entity.ai.CommandableGoals;
import net.bitflora.asteriskcraft.entity.ai.CommandedMoveGoal;
import net.bitflora.asteriskcraft.entity.ai.HoverFlyingNavigation;
import net.bitflora.asteriskcraft.entity.ai.HoverGoal;
import net.bitflora.asteriskcraft.entity.ai.StuckWanderGoal;
import net.bitflora.asteriskcraft.faction.Faction;
import net.bitflora.asteriskcraft.faction.FactionAttachments;
import net.bitflora.asteriskcraft.stats.UnitAttributes;
import net.bitflora.asteriskcraft.stats.UnitStat;
import net.bitflora.asteriskcraft.stats.UnitStats;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.control.FlyingMoveControl;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.CommonHooks;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * The Terran eye that walks: a Starport-trained flyer with no attack at all, carrying a detection
 * bubble and a support pulse. Structurally it is {@code entity.protoss.ObserverEntity} — the mod's
 * other unarmed flying {@link Detector} — with the cloak and the shields taken off and one ability
 * put on, and the two units are deliberately opposite bargains: an Observer is 20 HP that survives
 * by never being seen, this is 100 HP that survives by being difficult to kill.
 *
 * <p>Before it, the Terran answer to being cloaked was a Missile Turret, which is bolted to the
 * floor — so the race could detect where it had already built and nowhere else, and an army
 * marching across the map walked into a Dark Templar or a dug-in Lurker blind. A Vessel walks the
 * bubble along with the army.
 *
 * <p><b>It extends {@link PathfinderMob}, not {@code Monster}</b>, for the reason the Observer's
 * class doc gives: a unit that never attacks must carry no {@code FactionTargetGoal},
 * {@code RetaliateGoal} or {@code SiegeBlockGoal}, because a target it could never act on is worse
 * than no target at all — holding one blocks re-acquisition and buys nothing. For the same reason
 * it takes move orders through a bare {@link CommandedMoveGoal} rather than
 * {@link CommandableGoals#install}, whose {@code CommandedAttackGoal} it could never honour.
 *
 * <p>Flight is the Observer's stack reused verbatim and is invisible to the goal library: a vanilla
 * {@link FlyingMoveControl} plus a {@link HoverFlyingNavigation} that lifts every destination to
 * cruising altitude <em>before</em> pathing, with a bottom-priority {@link HoverGoal} holding it up
 * in the gaps between orders.
 *
 * <p>Its numbers live in {@link UnitStats#SCIENCE_VESSEL} — not here. The pulse constants below are
 * the exception, and are the same kind of exception {@code CloakClock.CLOAK_TICKS} is: an ability
 * duration is not a row in the balance table (see docs/balance-table.md).
 */
public class ScienceVesselEntity extends PathfinderMob implements Flyer, Detector {
    private static final UnitStat STAT = UnitStats.SCIENCE_VESSEL;
    private static final UnitStat.Flight FLIGHT = STAT.flightOrThrow();
    private static final UnitStat.Detection DETECTION = STAT.detectionOrThrow();

    /** Ticks between support pulses. */
    private static final int PULSE_INTERVAL = 20 * 45;
    /** How long an irradiated enemy stays poisoned. Poison floors its victim at 1 HP — see below. */
    private static final int POISON_TICKS = 20 * 15;
    /** How long a covered ally keeps its defensive matrix. */
    private static final int MATRIX_TICKS = 20 * 30;
    /** Resistance II: 40% flat reduction, which is what makes covering one ally worth a whole pulse. */
    private static final int MATRIX_AMPLIFIER = 1;

    /**
     * Ticks until the next support pulse.
     *
     * <p><b>A saved counter rather than {@code tickCount % PULSE_INTERVAL}</b>, which is what
     * {@code combat.DetectionHandler} keys its sweeps on. That trick is fine at a 20-tick period and
     * wrong at a 900-tick one: {@code Entity.tickCount} is never written by
     * {@code addAdditionalSaveData}, so it restarts at 0 on every world reload and every time this
     * entity's chunk cycles — a player who saves and quits, or flies out of range and back, more
     * often than 45 seconds would own a Vessel that never pulses at all. Seeded at random in the
     * constructor, which buys back the free per-entity stagger {@code tickCount} was giving, so four
     * Vessels out of one Starport do not fire in lockstep.
     */
    private int pulseTicks;

    public ScienceVesselEntity(EntityType<? extends ScienceVesselEntity> type, Level level) {
        super(type, level);
        // hoversInPlace = true: the move control switches gravity off for us and, unlike the
        // ground-falling variant, never switches it back on when it runs out of somewhere to go.
        this.moveControl = new FlyingMoveControl(this, 20, true);
        this.setPersistenceRequired();
        this.pulseTicks = this.random.nextInt(PULSE_INTERVAL);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return UnitAttributes.apply(PathfinderMob.createMobAttributes(), UnitStats.SCIENCE_VESSEL);
    }

    @Override
    protected PathNavigation createNavigation(Level level) {
        HoverFlyingNavigation navigation = new HoverFlyingNavigation(this, level, FLIGHT.hoverHeight());
        navigation.setCanOpenDoors(false);
        navigation.setCanFloat(true);
        // The default path length is far shorter than this unit's follow range, so a Vessel would
        // give up on a distant move order mid-flight. (Vanilla's Bee raises it for the same reason.)
        navigation.setRequiredPathLength(FLIGHT.requiredPathLength());
        return navigation;
    }

    @Override
    protected void registerGoals() {
        // No FloatGoal: it flies, and the navigation is allowed to float across water anyway.
        CommandableGoals.configureNavigation(this);
        // Priority -1, above everything: it exists to take a pinned unit off whichever goal has it
        // pinned, which it can only do from a lower priority number. See StuckWanderGoal. Its radius
        // is the detection envelope rather than a weapon range, for the reason the Observer's is —
        // that bubble is this unit's equivalent of reach, and a Vessel that drifts is still working.
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
    public void tick() {
        super.tick();
        if (this.level() instanceof ServerLevel level && this.isAlive() && --this.pulseTicks <= 0) {
            this.pulseTicks = PULSE_INTERVAL;
            this.pulse(level);
        }
    }

    /**
     * One support pulse: irradiate the nearest enemy in the detection bubble, or — seeing none —
     * cover a random ally in it. The choice itself is {@link SupportPulse}; everything here is the
     * part that needs a live world.
     */
    private void pulse(ServerLevel level) {
        Faction faction = FactionAttachments.get(this);
        double radius = DETECTION.radius();
        double radiusSqr = radius * radius;
        AABB box = this.getBoundingBox().inflate(radius);

        List<LivingEntity> hostiles = new ArrayList<>();
        List<LivingEntity> friends = new ArrayList<>();
        for (LivingEntity candidate : level.getEntitiesOfClass(LivingEntity.class, box, LivingEntity::isAlive)) {
            // Radius against the real distance, not the inflated box's corners — a box test alone
            // would reach up to sqrt(3) times further away on the diagonal.
            if (candidate == this || candidate.distanceToSqr(this) > radiusSqr) {
                continue;
            }
            if (FactionAttachments.isHostile(this, candidate)) {
                // Skip anything the poison could not land on. The Terran wild carve-out is HOSTILE,
                // so isHostile hands this unit zombies and skeletons — which sit in
                // #minecraft:ignores_poison_and_regen and are immune. Without this a Vessel spends a
                // 45-second pulse on a zombie while a Zergling stands next to it. Asked through
                // CommonHooks rather than LivingEntity.canBeAffected, which NeoForge marks
                // @Deprecated @ApiStatus.OverrideOnly and whose javadoc names this as the call-site
                // form; it is also what addEffect itself asks, so the two can never disagree.
                if (CommonHooks.canMobEffectBeApplied(candidate, poison(), this)) {
                    hostiles.add(candidate);
                }
            } else if (faction != Faction.NEUTRAL && FactionAttachments.get(candidate) == faction) {
                // Own side only, and deliberately not gated on cloak or garrison: Cloaking short-
                // circuits on the viewer's own army anyway, and a Marine inside a Bunker is exactly
                // who a defensive matrix is for.
                friends.add(candidate);
            }
        }
        // Nearest first: SupportPulse takes the head of this list, so the ordering is ours to own.
        hostiles.sort(Comparator.comparingDouble(candidate -> candidate.distanceToSqr(this)));

        Optional<SupportPulse.Choice<LivingEntity>> choice =
                SupportPulse.choose(hostiles, friends, this.getRandom());
        choice.ifPresent(picked -> {
            switch (picked.effect()) {
                case IRRADIATE -> this.apply(level, picked.target(), poison(),
                        AsteriskCraft.SCIENCE_VESSEL_IRRADIATE.get(), ParticleTypes.SNEEZE);
                case MATRIX -> this.apply(level, picked.target(), matrix(),
                        AsteriskCraft.SCIENCE_VESSEL_MATRIX.get(), ParticleTypes.ENCHANT);
            }
        });
    }

    /** Lands one effect on one target, with its bark over this unit and a plume over the target. */
    private void apply(ServerLevel level, LivingEntity target, MobEffectInstance effect,
                       SoundEvent sound, ParticleOptions particle) {
        // The attributed overload: the effect is credited to this Vessel. Poison's own damage is
        // still dealt unattributed by vanilla, so irradiating something never provokes it into
        // coming after the Vessel.
        target.addEffect(effect, this);
        level.playSound(null, this.blockPosition(), sound, SoundSource.HOSTILE, 1.0f, 1.0f);
        Vec3 center = target.getBoundingBox().getCenter();
        level.sendParticles(particle, center.x, center.y, center.z, 8, 0.3, 0.4, 0.3, 0.02);
    }

    private static MobEffectInstance poison() {
        return instance(MobEffects.POISON, POISON_TICKS, 0);
    }

    private static MobEffectInstance matrix() {
        return instance(MobEffects.RESISTANCE, MATRIX_TICKS, MATRIX_AMPLIFIER);
    }

    private static MobEffectInstance instance(Holder<MobEffect> effect, int duration, int amplifier) {
        return new MobEffectInstance(effect, duration, amplifier);
    }

    @Override
    public boolean causeFallDamage(double distance, float multiplier, DamageSource source) {
        // Gravity is only switched off once the move control has somewhere to fly, so a Vessel
        // produced over a drop would otherwise take fall damage before it ever got airborne.
        return false;
    }

    @Override
    public int getAmbientSoundInterval() {
        return 1200; // ~10x the vanilla default (80): ambient barks average ~1/minute, not ~1/6s.
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return AsteriskCraft.SCIENCE_VESSEL_AMBIENT.get();
    }

    // No getHurtSound override: the source clips include no hurt bark, so it keeps the vanilla one
    // rather than reusing another unit's voice. The Observer and Overlord are in the same position.

    @Override
    protected SoundEvent getDeathSound() {
        return AsteriskCraft.SCIENCE_VESSEL_DEATH.get();
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output);
        output.putInt("PulseTicks", this.pulseTicks);
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
        // Clamped rather than trusted: a hand-edited or pre-pulse save must not hand back a counter
        // that never reaches zero, or a longer wait than the ability is supposed to have.
        this.pulseTicks = Math.clamp(input.getIntOr("PulseTicks", PULSE_INTERVAL), 1, PULSE_INTERVAL);
    }
}
