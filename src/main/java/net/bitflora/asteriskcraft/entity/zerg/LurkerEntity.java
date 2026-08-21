package net.bitflora.asteriskcraft.entity.zerg;

import net.bitflora.asteriskcraft.AsteriskCraft;
import net.bitflora.asteriskcraft.entity.Altitude;
import net.bitflora.asteriskcraft.entity.Rooted;
import net.bitflora.asteriskcraft.entity.ai.CommandableGoals;
import net.bitflora.asteriskcraft.entity.ai.FactionTargetGoal;
import net.bitflora.asteriskcraft.entity.ai.RetaliateGoal;
import net.bitflora.asteriskcraft.entity.ai.SiegeBlockGoal;
import net.bitflora.asteriskcraft.entity.ai.StuckWanderGoal;
import net.bitflora.asteriskcraft.entity.ai.zerg.LurkerApproachGoal;
import net.bitflora.asteriskcraft.entity.ai.zerg.LurkerBurrowGoal;
import net.bitflora.asteriskcraft.entity.ai.zerg.LurkerHoldGroundGoal;
import net.bitflora.asteriskcraft.entity.ai.zerg.LurkerSpineGoal;
import net.bitflora.asteriskcraft.entity.protoss.DarkTemplarEntity;
import net.bitflora.asteriskcraft.faction.Cloaked;
import net.bitflora.asteriskcraft.stats.UnitAttributes;
import net.bitflora.asteriskcraft.stats.UnitStat;
import net.bitflora.asteriskcraft.stats.UnitStats;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;

/**
 * The Zerg cloaked unit, and the mirror of the Protoss {@link DarkTemplarEntity} only in role: where
 * a Dark Templar is cloaked always and everywhere, a Lurker is cloaked only once it has spent three
 * seconds digging itself into the ground — and it can neither move while buried nor shoot while
 * standing. Getting anywhere therefore costs it two full transitions, and being caught halfway costs
 * it everything: mid-dig it is visible, immobile and unarmed all at once.
 *
 * <p>All of that is one number, {@link BurrowClock}'s depth, synced so the client can sink the model
 * and answer {@link #isCloakActive()} the same way the server does. The state reaches the rest of the
 * mod through exactly three seams and no more:
 *
 * <ul>
 *   <li>{@link Cloaked#isCloakActive()} — the entire implementation of its cloak. The gate lives in
 *       {@code FactionAttachments.isHostile}, so acquisition, retaliation and splash chains all
 *       follow with nothing written here.</li>
 *   <li>{@link Rooted#isRootedNow()} — so {@code building.SpawnSpots} won't hand the block a buried
 *       Lurker is standing in to the next unit off the Hive.</li>
 *   <li>the {@code MOVE} flag held by {@link LurkerBurrowGoal}, plus the hard stop in
 *       {@code customServerAiStep} — which together are why no goal in the library needed a line
 *       of burrow-awareness.</li>
 * </ul>
 *
 * <p>Its attack is not a hitscan: {@link LurkerSpineGoal} plants a marching row of
 * {@link LurkerSpineEntity}s along the bearing to its target, each dealing full damage, so a unit
 * that stands in the line takes several. It is ground-only for the same reason the Sunken Colony is —
 * a spine coming up out of the dirt cannot reach the air — and the filter is applied to both target
 * goals, retaliation included, so a Mutalisk strafing it can't pin it onto something it must then
 * refuse to shoot.
 *
 * <p>Its numbers live in {@link net.bitflora.asteriskcraft.stats.UnitStats#LURKER} — not here.
 */
public class LurkerEntity extends Monster implements Cloaked, Rooted {
    private static final UnitStat STAT = UnitStats.LURKER;
    private static final UnitStat.Ranged RANGED = STAT.rangedOrThrow();

    /**
     * Synced rather than a plain field, for the same reason {@code PhotonCannonEntity}'s warp
     * countdown is: two client-side readers depend on it. {@code CloakRenderStateModifier} asks
     * {@link Cloaked#isCloaked} every frame, and the renderer sinks the model by the same fraction —
     * and a field the client never loads from NBT would tell it every Lurker is standing on the
     * surface.
     */
    private static final EntityDataAccessor<Integer> BURROW_DEPTH =
            SynchedEntityData.defineId(LurkerEntity.class, EntityDataSerializers.INT);

    // Synced rather than broadcast as an entity event: an int carries the animation's progress (not
    // just its start) and can't collide with a vanilla LivingEntity event byte. Same shape as the
    // Hydralisk's — a unit that never swings can't hang this off getAttackAnim.
    private static final EntityDataAccessor<Integer> ATTACK_TICKS =
            SynchedEntityData.defineId(LurkerEntity.class, EntityDataSerializers.INT);

    /** Server-side authority; the client only ever reads the synced depth this produces. */
    private final BurrowClock burrow = new BurrowClock();

    public LurkerEntity(EntityType<? extends LurkerEntity> type, Level level) {
        super(type, level);
        this.setPersistenceRequired();
    }

    public static AttributeSupplier.Builder createAttributes() {
        return UnitAttributes.apply(Monster.createMonsterAttributes(), UnitStats.LURKER);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(BURROW_DEPTH, 0);
        builder.define(ATTACK_TICKS, 0);
    }

    @Override
    protected void registerGoals() {
        // Priority -1, above even the digger, for the reason StuckWanderGoal documents. The extra
        // busy predicate is this unit's whole reason for that constructor existing: a burrowed Lurker
        // is motionless by design and must not be judged stuck for it.
        this.goalSelector.addGoal(-1, new StuckWanderGoal(this, RANGED.range(), () -> !this.isSurfaced()));
        this.goalSelector.addGoal(0, new FloatGoal(this));
        // The predicate is why a dug-in Lurker doesn't chew through the terrain in front of
        // it: its navigation is stopped every tick on purpose, so the digger's "navigation done and
        // not arrived" test would read as stuck continuously. Building assault still preempts.
        this.goalSelector.addGoal(0, new SiegeBlockGoal(this, () -> !this.isSurfaced()));
        // LOOK only, so it runs alongside the burrow goal holding MOVE rather than competing with it.
        this.goalSelector.addGoal(1, new LurkerSpineGoal(this));
        // Above the burrow goal so it can take MOVE off a dug-in Lurker; CommandedMoveGoal (1, from
        // CommandableGoals) sits above it again so a player's order outranks chasing.
        this.goalSelector.addGoal(2, new LurkerApproachGoal(this));
        // The resting state, deliberately last of the MOVE holders: it runs only when nothing above
        // wants the unit walking. See its class docs — the priority ladder is the arbitration.
        this.goalSelector.addGoal(3, new LurkerBurrowGoal(this));
        this.goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 8.0f));
        this.goalSelector.addGoal(6, new RandomLookAroundGoal(this));
        // Above retaliation, and the only thing that is: a Lurker with something already in reach
        // holds the TARGET flag against being lured out of the ground by a distant attacker.
        this.targetSelector.addGoal(-2, new LurkerHoldGroundGoal(this));
        this.targetSelector.addGoal(-1, new RetaliateGoal(this, LurkerEntity::canStrike));
        this.targetSelector.addGoal(1, new FactionTargetGoal(this, LurkerEntity::canStrike));
        CommandableGoals.install(this, this.goalSelector, this.targetSelector);
    }

    /**
     * Whether the Lurker can reach {@code target} at all: its spines come up out of the ground, so
     * anything {@link Altitude#isAirborne} is simply beyond it — the Sunken Colony's rule, sharing the
     * one definition of "air" with it, and passed to <em>both</em> target-selector goals for the same
     * reason it is there: a flyer that pinned this unit's target would leave it inert against the
     * ground army it exists to stop.
     *
     * <p>Public because {@link net.bitflora.asteriskcraft.entity.ai.zerg.LurkerHoldGroundGoal} asks
     * the same question of entities that are not its target yet, and there must be exactly one answer
     * to what this unit's spines can reach.
     */
    public static boolean canStrike(LivingEntity target) {
        return !Altitude.isAirborne(target);
    }

    // --- Burrow state ---

    /** Sets what the unit is digging toward. Goals call this; the clock does the travelling. */
    public void setWantsBurrowed(boolean wantsBurrowed) {
        this.burrow.setWantsBurrowed(wantsBurrowed);
    }

    /** Fully dug in: hidden, rooted, and the only state in which it can attack. */
    public boolean isBurrowed() {
        return burrowDepth() == BurrowClock.TRANSITION_TICKS;
    }

    /** Fully out: the only state in which it can move. */
    public boolean isSurfaced() {
        return burrowDepth() == 0;
    }

    /** How far down as 0→1. Read by the renderer to sink the model. */
    public float burrowFraction() {
        return (float) burrowDepth() / BurrowClock.TRANSITION_TICKS;
    }

    private int burrowDepth() {
        return this.entityData.get(BURROW_DEPTH);
    }

    /**
     * Cloak comes up only at the bottom of the dig, and drops on the first tick of climbing out. That
     * asymmetry-free rule is what makes the transition a real cost: there is no moment where the unit
     * is both moving and hidden.
     */
    @Override
    public boolean isCloakActive() {
        return isBurrowed();
    }

    /** A Lurker with any part of itself underground cannot step aside. See {@link Rooted}. */
    @Override
    public boolean isRootedNow() {
        return !isSurfaced();
    }

    // --- Attack animation ---

    /** Starts the strike animation on every client tracking this Lurker. */
    public void triggerAttackAnimation() {
        this.entityData.set(ATTACK_TICKS, STAT.attackAnimTicks());
    }

    /** Ticks remaining in the strike animation; 0 when idle. Read by the renderer. */
    public int getAttackTicks() {
        return this.entityData.get(ATTACK_TICKS);
    }

    // --- Ticking ---

    @Override
    public void tick() {
        super.tick();
        if (this.level().isClientSide()) {
            return;
        }
        int remaining = this.getAttackTicks();
        if (remaining > 0) {
            this.entityData.set(ATTACK_TICKS, remaining - 1);
        }
        tickBurrow();
    }

    private void tickBurrow() {
        // Read before ticking: the clock reports the depth it is about to leave, which is what makes
        // this fire exactly once per direction change rather than every tick of the dig.
        boolean starting = this.burrow.isAboutToStartDigging();
        if (!this.burrow.tick()) {
            return;
        }
        if (starting) {
            this.level().playSound(null, this.blockPosition(), AsteriskCraft.LURKER_BURROW.get(),
                    SoundSource.HOSTILE, 1.0f, 1.0f);
        }
        this.entityData.set(BURROW_DEPTH, this.burrow.depth());
    }

    /**
     * Pins the unit for as long as any part of it is underground. {@link LurkerBurrowGoal} holding
     * {@code MOVE} stops the goal library steering it; this stops the physics — a path issued on the
     * tick before the dig started, or a shove from something walking into it, would otherwise drag a
     * half-buried Lurker across the ground.
     */
    @Override
    protected void customServerAiStep(ServerLevel level) {
        super.customServerAiStep(level);
        if (isSurfaced()) {
            return;
        }
        this.getNavigation().stop();
        Vec3 movement = this.getDeltaMovement();
        this.setDeltaMovement(0.0, movement.y, 0.0); // y left alone so it still falls if the ground goes
    }

    @Override
    public boolean isPushable() {
        return isSurfaced();
    }

    @Override
    public void knockback(double strength, double x, double z) {
        if (isSurfaced()) {
            super.knockback(strength, x, z);
        }
    }

    // --- Persistence ---

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output);
        output.putInt("BurrowDepth", this.burrow.depth());
        output.putBoolean("WantsBurrowed", this.burrow.wantsBurrowed());
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
        this.burrow.restore(input.getIntOr("BurrowDepth", 0), input.getBooleanOr("WantsBurrowed", false));
        // The synced value is the client's only source for depth, so it has to be written here too —
        // a reloaded Lurker that was buried must come back buried, not standing.
        this.entityData.set(BURROW_DEPTH, this.burrow.depth());
    }

    // --- Sounds ---

    @Override
    public int getAmbientSoundInterval() {
        return 1200; // ~10x the vanilla default (80): ambient barks average ~1/minute, not ~1/6s.
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return AsteriskCraft.LURKER_AMBIENT.get();
    }

    // No hurt or death override: no such clip exists in the source audio, so vanilla's are kept —
    // the same call the Dark Templar and Scout made.
}
