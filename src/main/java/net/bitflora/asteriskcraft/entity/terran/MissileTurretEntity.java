package net.bitflora.asteriskcraft.entity.terran;

import net.bitflora.asteriskcraft.building.PrePlaced;
import net.bitflora.asteriskcraft.building.WarpInVulnerability;
import net.bitflora.asteriskcraft.entity.Altitude;
import net.bitflora.asteriskcraft.entity.Detector;
import net.bitflora.asteriskcraft.entity.Rooted;
import net.bitflora.asteriskcraft.entity.ai.FactionTargetGoal;
import net.bitflora.asteriskcraft.entity.ai.RetaliateGoal;
import net.bitflora.asteriskcraft.entity.ai.terran.MissileTurretFireGoal;
import net.bitflora.asteriskcraft.stats.UnitAttributes;
import net.bitflora.asteriskcraft.stats.UnitStat;
import net.bitflora.asteriskcraft.stats.UnitStats;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * The Terran Missile Turret: the race's static anti-air, and the race's detector. Before it, a
 * Terran base had no answer at all to two things — a flyer holding altitude, and anything cloaked —
 * and it closes both with one building.
 *
 * <p>It is {@code entity.zerg.SporeColonyEntity}'s opposite number rather than its copy. What they
 * share is the rule: the only thing either will shoot at is an <em>airborne</em> target, where
 * "airborne" is positional ({@link Altitude#isAirborne} — four or more blocks of clear space above
 * solid footing) rather than a unit type, so a Mutalisk cruising is a target and the same Mutalisk
 * sitting on the ground is not. That filter is passed to {@link FactionTargetGoal}, which keeps
 * hostility (and with it the cloak gate) resolving in {@code FactionAttachments.isHostile} exactly
 * as it does for every other unit; the filter only ever subtracts.
 *
 * <p>What they do not share is the trade. The Spore Colony is a wall that dribbles; this is a gun
 * with a thin skin, and it is why a Terran base wants a Bunker beside it rather than instead of it —
 * the turret takes the air and the infantry inside the Bunker take the ground.
 *
 * <p><b>No {@link RetaliateGoal}</b>, for the Spore Colony's documented reason: retaliation would
 * hand it whatever last hit it, which for a ground attacker is a target its
 * {@link MissileTurretFireGoal} must then refuse, leaving it locked onto something it will never
 * shoot. Being helpless against ground is the trade the unit makes.
 *
 * <p>Like {@code entity.protoss.PhotonCannonEntity} and {@link BunkerEntity} it is a {@link Mob}
 * rather than a block entity, for the reason stated there: being a living entity it reuses the whole
 * combat stack — HP as an attribute, and automatic retaliation <em>from</em> everything else, since
 * attacking units acquire and hit it through the ordinary targeting path.
 *
 * <p>Its numbers live in {@link UnitStats#MISSILE_TURRET} — not here. The construction countdown
 * does live here, exactly as the Bunker's and the Photon Cannon's do: standing a building up is a
 * building mechanic rather than a combat stat. Half HP throughout ({@link WarpInVulnerability}),
 * scaled back up on completion, so damage landed on a half-built turret is sustained twice over.
 */
public class MissileTurretEntity extends Mob implements Detector, Rooted, PrePlaced {
    /** 30 seconds to put up — the Bunker's countdown, for a building of the same kind of weight. */
    public static final int BUILD_TICKS = 600;

    private static final UnitStat STAT = UnitStats.MISSILE_TURRET;

    // Synced for the same reason the Bunker's is: the renderer reads it, and a plain field is never
    // restored client-side, so every turret on screen would look half-built.
    private static final EntityDataAccessor<Integer> BUILD_TICKS_REMAINING =
            SynchedEntityData.defineId(MissileTurretEntity.class, EntityDataSerializers.INT);

    // Synced rather than broadcast as an entity event: an int carries the animation's progress (not
    // just its start) and can't collide with a vanilla LivingEntity event byte.
    private static final EntityDataAccessor<Integer> ATTACK_TICKS =
            SynchedEntityData.defineId(MissileTurretEntity.class, EntityDataSerializers.INT);

    public MissileTurretEntity(EntityType<? extends MissileTurretEntity> type, Level level) {
        super(type, level);
        this.setPersistenceRequired();
        // A fresh turret starts building, so it starts on the halved pool. (Loading from disk
        // overwrites this with the saved health afterwards, half-built or not.)
        this.setHealth(WarpInVulnerability.warpPool(this.getMaxHealth()));
    }

    public static AttributeSupplier.Builder createAttributes() {
        return UnitAttributes.apply(Mob.createMobAttributes(), UnitStats.MISSILE_TURRET);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(BUILD_TICKS_REMAINING, BUILD_TICKS);
        builder.define(ATTACK_TICKS, 0);
    }

    @Override
    protected void registerGoals() {
        // No movement, look, or wander goals at all — like the two colonies, this is a fixed turret.
        this.goalSelector.addGoal(1, new MissileTurretFireGoal(this));
        this.targetSelector.addGoal(1, new FactionTargetGoal(this, Altitude::isAirborne));
    }

    /** A Missile Turret is the Terran detector — its only one; the envelope is in the balance table. */
    @Override
    public UnitStat.Detection detection() {
        return STAT.detectionOrThrow();
    }

    // --- Construction ---

    public boolean isUnderConstruction() {
        return this.buildTicksRemaining() > 0;
    }

    private int buildTicksRemaining() {
        return this.entityData.get(BUILD_TICKS_REMAINING);
    }

    /** How far along the build is: 0 on the first tick, 1 once it is standing. Read by the renderer. */
    public float buildProgress() {
        return 1.0f - (float) this.buildTicksRemaining() / BUILD_TICKS;
    }

    /**
     * Scales the halved construction pool back up. {@link #isUnderConstruction()} is already false by
     * now, so the setter clamps against the full maximum — and because it is what <em>survived</em>
     * that gets doubled, whatever damage the turret took while going up is sustained twice over.
     */
    private void finishBuild(ServerLevel level) {
        this.setHealth(WarpInVulnerability.onWarpComplete(this.getHealth()));
        level.playSound(null, this.blockPosition(), SoundEvents.ANVIL_LAND, SoundSource.HOSTILE, 0.8f, 0.6f);
    }

    /**
     * Stands the turret up at once, at full HP — the counterpart of
     * {@code BunkerEntity.skipConstruction}. A turret placed by world generation was never built by
     * anyone, so it must not open the match half-built and refusing to fire.
     */
    @Override
    public void skipConstruction() {
        if (this.isUnderConstruction()) {
            this.entityData.set(BUILD_TICKS_REMAINING, 0);
            this.setHealth(WarpInVulnerability.onWarpComplete(this.getHealth()));
        }
    }

    // --- Firing animation ---

    /** Starts the firing animation on every client tracking this turret. */
    public void triggerAttackAnimation() {
        this.entityData.set(ATTACK_TICKS, STAT.attackAnimTicks());
    }

    /** Ticks remaining in the firing animation; 0 when idle. Read by the renderer. */
    public int getAttackTicks() {
        return this.entityData.get(ATTACK_TICKS);
    }

    @Override
    public void tick() {
        super.tick();
        if (!(this.level() instanceof ServerLevel level)) {
            return;
        }
        int remaining = this.getAttackTicks();
        if (remaining > 0) {
            this.entityData.set(ATTACK_TICKS, remaining - 1);
        }
        int buildTicks = this.buildTicksRemaining();
        if (buildTicks > 0) {
            this.entityData.set(BUILD_TICKS_REMAINING, buildTicks - 1);
            level.sendParticles(ParticleTypes.SMOKE,
                    this.getX(), this.getY() + 0.6, this.getZ(), 4, 0.5, 0.7, 0.5, 0.01);
            if (buildTicks == 1) {
                this.finishBuild(level);
            }
        }
    }

    // --- Rooted: can't be pushed or knocked around ---

    @Override
    public boolean isPushable() {
        return false;
    }

    // A rooted structure doesn't grunt when hit or die with a creature scream — only its shot
    // (see MissileTurretFireGoal) makes any sound.
    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return null;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return null;
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output);
        output.putInt("BuildTicks", this.buildTicksRemaining());
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
        this.entityData.set(BUILD_TICKS_REMAINING, input.getIntOr("BuildTicks", 0));
    }
}
