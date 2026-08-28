package net.bitflora.asteriskcraft.entity.zerg;

import net.bitflora.asteriskcraft.building.ConstructionSite;
import net.bitflora.asteriskcraft.building.PrePlaced;
import net.bitflora.asteriskcraft.building.WarpInVulnerability;
import net.bitflora.asteriskcraft.entity.Altitude;
import net.bitflora.asteriskcraft.entity.CreepSource;
import net.bitflora.asteriskcraft.entity.Detector;
import net.bitflora.asteriskcraft.entity.Rooted;
import net.bitflora.asteriskcraft.entity.ai.FactionTargetGoal;
import net.bitflora.asteriskcraft.entity.ai.RetaliateGoal;
import net.bitflora.asteriskcraft.entity.ai.zerg.SporeFireGoal;
import net.bitflora.asteriskcraft.stats.UnitAttributes;
import net.bitflora.asteriskcraft.stats.UnitStat;
import net.bitflora.asteriskcraft.stats.UnitStats;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * The Zerg anti-air defence — the Sunken Colony's sibling, planted beside every Hive by
 * {@code game.GameBootstrap} and rooted in exactly the same way. What makes it different is the one
 * thing it will shoot at: an <em>airborne</em> target, and nothing else.
 *
 * <p>"Airborne" is positional, not a unit type: {@link Altitude#isAirborne} — four or more blocks of
 * clear space above solid footing. So a Mutalisk cruising at altitude is a target and the same
 * Mutalisk sitting on the ground is not, while a Zealot knocked off a cliff briefly is. That filter is
 * passed to {@link FactionTargetGoal}, which keeps hostility (and with it the cloak gate) resolving in
 * {@code FactionAttachments.isHostile} as it does for every other unit; the filter only subtracts.
 *
 * <p><b>No {@link RetaliateGoal}</b>, unlike the Sunken Colony — and that is the design, not an
 * oversight. Retaliation would hand it whatever last hit it, which for a ground attacker is a target
 * its {@link SporeFireGoal} must then refuse, leaving it locked onto something it will never shoot.
 * Being helpless against ground is the whole trade the unit makes; anything airborne that shoots it is
 * inside its own acquisition range anyway, since follow range equals attack range.
 *
 * <p>It is deliberately not commandable (no {@code CommandableGoals}) and, like the Sunken, silent —
 * only its shot makes noise.
 *
 * <p>Its numbers live in {@link UnitStats#SPORE_COLONY} — not here.
 */
public class SporeColonyEntity extends Monster implements Detector, Rooted, CreepSource, PrePlaced {
    private static final UnitStat STAT = UnitStats.SPORE_COLONY;

    // Synced rather than broadcast as an entity event: an int carries the animation's progress (not
    // just its start) and can't collide with a vanilla LivingEntity event byte.
    private static final EntityDataAccessor<Integer> ATTACK_TICKS =
            SynchedEntityData.defineId(SporeColonyEntity.class, EntityDataSerializers.INT);

    /**
     * 20 seconds to grow. Shorter than the Bunker's and the Missile Turret's thirty, because a
     * colony costs a whole Drone on top of its resources - the swarm pays for the difference in a
     * worker rather than in time.
     */
    public static final int BUILD_TICKS = 400;

    // Synced for the same reason ATTACK_TICKS is: the renderer reads it to sink the colony into the
    // ground, and a plain field is never restored client-side.
    private static final EntityDataAccessor<Integer> BUILD_TICKS_REMAINING =
            SynchedEntityData.defineId(SporeColonyEntity.class, EntityDataSerializers.INT);

    /** The Drone that died to start this. Empty for one world generation planted - see {@link PrePlaced}. */
    private final ConstructionSite site = new ConstructionSite();

    public SporeColonyEntity(EntityType<? extends SporeColonyEntity> type, Level level) {
        super(type, level);
        this.setPersistenceRequired();
        // A fresh colony starts growing, so it starts on the halved pool. (Loading from disk
        // overwrites this with the saved health afterwards, half-grown or not.)
        this.setHealth(WarpInVulnerability.warpPool(this.getMaxHealth()));
    }

    public static AttributeSupplier.Builder createAttributes() {
        return UnitAttributes.apply(Monster.createMonsterAttributes(), UnitStats.SPORE_COLONY);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(ATTACK_TICKS, 0);
        builder.define(BUILD_TICKS_REMAINING, BUILD_TICKS);
    }

    @Override
    protected void registerGoals() {
        // No movement, look, or wander goals at all — like the Sunken Colony, this is a fixed turret.
        this.goalSelector.addGoal(1, new SporeFireGoal(this));
        this.targetSelector.addGoal(1, new FactionTargetGoal(this, Altitude::isAirborne));
    }

    /** A Spore Colony is the Zerg's detector — its only one; the envelope is in the balance table. */
    @Override
    public UnitStat.Detection detection() {
        return STAT.detectionOrThrow();
    }

    /** Starts the firing animation on every client tracking this colony. */
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
            ConstructionSite.plume(level, this, 6, 0.5, 0.6);
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

    // A rooted structure doesn't grunt when hit or die with a creature scream — only its shot
    // (see SporeFireGoal) makes any sound.
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
