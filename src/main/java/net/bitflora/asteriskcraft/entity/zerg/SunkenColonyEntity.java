package net.bitflora.asteriskcraft.entity.zerg;

import net.bitflora.asteriskcraft.building.ConstructionSite;
import net.bitflora.asteriskcraft.building.PrePlaced;
import net.bitflora.asteriskcraft.building.WarpInVulnerability;
import net.bitflora.asteriskcraft.entity.Altitude;
import net.bitflora.asteriskcraft.entity.CreepSource;
import net.bitflora.asteriskcraft.entity.Rooted;
import net.bitflora.asteriskcraft.entity.ai.FactionTargetGoal;
import net.bitflora.asteriskcraft.entity.ai.RetaliateGoal;
import net.bitflora.asteriskcraft.entity.ai.zerg.SunkenSpikeGoal;
import net.bitflora.asteriskcraft.entity.protoss.PhotonCannonEntity;
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
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * The Zerg static defence — the mirror of the Protoss {@link PhotonCannonEntity}: a rooted
 * structure-creature that plants beside a Hive and impales anything of an enemy faction (the player
 * included) that walks into range. It never moves.
 *
 * <p>Its attack is not the {@link net.bitflora.asteriskcraft.entity.ai.HitscanAttacks} beam the
 * mobile ranged units use — the tentacle whips forward and drives a {@link SunkenSpikeEntity} up out
 * of the ground under the target (see {@link SunkenSpikeGoal}). That means a moving target can
 * genuinely outrun the strike, which is the intended trade for hitting far harder than anything else
 * in the mod.
 *
 * <p>Targeting goes through {@link FactionTargetGoal} like every other combat unit, so nothing here
 * knows or cares which side is the enemy — narrowed by {@link #canStrike} to what a spike coming up
 * out of the ground can actually reach, so it is the exact complement of the {@link SporeColonyEntity}
 * beside it: ground only, air never. It is deliberately <em>not</em> commandable (no
 * {@code CommandableGoals}) — a rooted building has nowhere to be ordered to.
 *
 * <p>The colony itself is silent — no hurt or death sound — since a rooted structure grunting or
 * screaming like a creature doesn't fit. Its tentacle strike still makes noise (see
 * {@link SunkenSpikeGoal}/{@link SunkenSpikeEntity}).
 *
 * <p>Its numbers live in {@link net.bitflora.asteriskcraft.stats.UnitStats#SUNKEN_COLONY} — not here.
 */
public class SunkenColonyEntity extends Monster implements Rooted, CreepSource, PrePlaced {
    private static final UnitStat STAT = UnitStats.SUNKEN_COLONY;

    // Synced rather than broadcast as an entity event: an int carries the animation's progress (not
    // just its start) and can't collide with a vanilla LivingEntity event byte.
    private static final EntityDataAccessor<Integer> ATTACK_TICKS =
            SynchedEntityData.defineId(SunkenColonyEntity.class, EntityDataSerializers.INT);

    /**
     * 20 seconds to grow. Shorter than the Bunker's and the Missile Turret's thirty, because a
     * colony costs a whole Drone on top of its resources - the swarm pays for the difference in a
     * worker rather than in time.
     */
    public static final int BUILD_TICKS = 400;

    // Synced for the same reason ATTACK_TICKS is: the renderer reads it to sink the colony into the
    // ground, and a plain field is never restored client-side.
    private static final EntityDataAccessor<Integer> BUILD_TICKS_REMAINING =
            SynchedEntityData.defineId(SunkenColonyEntity.class, EntityDataSerializers.INT);

    /** The Drone that died to start this. Empty for one world generation planted - see {@link PrePlaced}. */
    private final ConstructionSite site = new ConstructionSite();

    public SunkenColonyEntity(EntityType<? extends SunkenColonyEntity> type, Level level) {
        super(type, level);
        this.setPersistenceRequired();
        // A fresh colony starts growing, so it starts on the halved pool. (Loading from disk
        // overwrites this with the saved health afterwards, half-grown or not.)
        this.setHealth(WarpInVulnerability.warpPool(this.getMaxHealth()));
    }

    public static AttributeSupplier.Builder createAttributes() {
        return UnitAttributes.apply(Monster.createMonsterAttributes(), UnitStats.SUNKEN_COLONY);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(ATTACK_TICKS, 0);
        builder.define(BUILD_TICKS_REMAINING, BUILD_TICKS);
    }

    @Override
    protected void registerGoals() {
        // No movement, look, or wander goals at all — like the Photon Cannon, this is a fixed turret.
        this.goalSelector.addGoal(1, new SunkenSpikeGoal(this));
        this.targetSelector.addGoal(-1, new RetaliateGoal(this, SunkenColonyEntity::canStrike));
        this.targetSelector.addGoal(1, new FactionTargetGoal(this, SunkenColonyEntity::canStrike));
    }

    /**
     * Whether the colony can reach {@code target} at all: its spike comes up out of the ground, so
     * anything {@link Altitude#isAirborne} is simply beyond it. The mirror of the Spore Colony's
     * filter, sharing the one definition of "air" with it, and passed to <em>both</em> target-selector
     * goals — retaliation included, since a Mutalisk strafing the colony would otherwise lock it onto
     * a target {@link SunkenSpikeGoal} must then refuse, leaving it inert against the ground units it
     * exists to stop.
     */
    private static boolean canStrike(LivingEntity target) {
        return !Altitude.isAirborne(target);
    }

    /** Starts the strike animation on every client tracking this colony. */
    public void triggerAttackAnimation() {
        this.entityData.set(ATTACK_TICKS, STAT.attackAnimTicks());
    }

    /** Ticks remaining in the strike animation; 0 when idle. Read by the renderer. */
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
            switch (this.site.tick(level, this.position())) {
                case ABANDONED -> {
                    ConstructionSite.razeUnbuilt(this, level);
                    return;
                }
                // Nothing has grown yet: the Drone is still on its way over to die here.
                case WAITING -> {
                    return;
                }
                case BUILDING -> {
                }
            }
            this.entityData.set(BUILD_TICKS_REMAINING, buildTicks - 1);
            level.sendParticles(ParticleTypes.CRIMSON_SPORE,
                    this.getX(), this.getY() + 0.6, this.getZ(), 6, 0.5, 0.6, 0.5, 0.01);
            if (buildTicks == 1) {
                this.finishBuild(level);
                this.site.release(level, this.blockPosition());
            }
        }
    }

    // --- Construction ---

    /** Whether this colony is still growing, and so may not fire. */
    public boolean isUnderConstruction() {
        return this.buildTicksRemaining() > 0;
    }

    private int buildTicksRemaining() {
        return this.entityData.get(BUILD_TICKS_REMAINING);
    }

    /** How far along the growth is: 0 on the first tick, 1 once it is standing. Read by the renderer. */
    public float buildProgress() {
        return 1.0f - (float) this.buildTicksRemaining() / BUILD_TICKS;
    }

    @Override
    public ConstructionSite constructionSite() {
        return this.site;
    }

    /**
     * Scales the halved growth pool back up. {@link #isUnderConstruction()} is already false by now,
     * so the setter clamps against the full maximum - and because it is what <em>survived</em> that
     * gets doubled, whatever damage the colony took on the way up is sustained twice over.
     */
    private void finishBuild(ServerLevel level) {
        this.setHealth(WarpInVulnerability.onWarpComplete(this.getHealth()));
        level.playSound(null, this.blockPosition(), SoundEvents.SCULK_CATALYST_BLOOM, SoundSource.HOSTILE, 0.9f, 0.7f);
    }

    /**
     * Stands the colony up at once, at full HP. A colony world generation planted beside a Hive was
     * never grown by any Drone, so it must not open the match half-formed and unable to fire.
     */
    @Override
    public void skipConstruction() {
        if (this.isUnderConstruction()) {
            this.entityData.set(BUILD_TICKS_REMAINING, 0);
            this.setHealth(WarpInVulnerability.onWarpComplete(this.getHealth()));
        }
    }

    /**
     * Lets the builder go if this colony is destroyed before it finished growing. A Drone that has
     * already morphed is long gone, so this only ever matters for one still walking over.
     */
    @Override
    public void remove(RemovalReason reason) {
        if (this.level() instanceof ServerLevel level) {
            this.site.release(level, this.blockPosition());
        }
        super.remove(reason);
    }

    // --- Rooted: can't be pushed or knocked around ---

    @Override
    public boolean isPushable() {
        return false;
    }

    // A rooted structure doesn't grunt when hit or die with a creature scream — only its tentacle
    // strike (see SunkenSpikeGoal/SunkenSpikeEntity) makes any sound.
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
        this.site.save(output);
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
        this.entityData.set(BUILD_TICKS_REMAINING, input.getIntOr("BuildTicks", 0));
        this.site.load(input);
    }
}
