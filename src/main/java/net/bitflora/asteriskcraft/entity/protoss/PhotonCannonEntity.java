package net.bitflora.asteriskcraft.entity.protoss;

import net.bitflora.asteriskcraft.building.PhotonCannonTargeting;
import net.bitflora.asteriskcraft.building.WarpInVulnerability;
import net.bitflora.asteriskcraft.combat.ShieldAttachments;
import net.bitflora.asteriskcraft.entity.Shielded;
import net.bitflora.asteriskcraft.entity.ai.protoss.CannonFireGoal;
import net.bitflora.asteriskcraft.faction.FactionAttachments;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * The Photon Cannon: a stationary Protoss defensive structure, now a proper entity rather than a
 * block entity. As a {@link LivingEntity} it reuses the whole unit-combat stack instead of
 * re-implementing it: HP is {@link Attributes#MAX_HEALTH}, shields come for free from being a
 * {@link Shielded} (see {@code ShieldEventHandler}), and — crucially — retaliation is automatic,
 * since attacking units acquire and hit it back through the same {@code RetaliateGoal}/
 * {@code FactionTargetGoal} path they use against any living enemy (no special-case building-aggro
 * bookkeeping needed).
 *
 * <p>It never moves — no movement goals, zero movement speed, unpushable, full knockback resistance.
 * It warps in over {@link #WARP_TICKS} — inert but <em>not</em> invulnerable: while warping it stands
 * at half its HP and shields, and finishing the warp scales both pools back up, so damage landed
 * mid-warp is sustained twice over (see {@link WarpInVulnerability}). After that it auto-fires an
 * instant energy bolt at the nearest enemy-faction unit — or any vanilla monster — in range via
 * {@link CannonFireGoal}. Hostility is resolved purely through the faction attachment.
 */
public class PhotonCannonEntity extends Mob implements Shielded {
    public static final double RANGE = 14.0;          // StarCraft Photon Cannon range
    public static final float ATTACK_DAMAGE = 10.0f;
    public static final int ATTACK_COOLDOWN = 20;    // one shot per second
    // Far quicker than a Gateway or Nexus: a cannon is what you throw up when a wave is already coming.
    public static final int WARP_TICKS = 200;        // 10 seconds to warp in

    public static final int MAX_HEALTH = 50;
    public static final int SHIELD = 50;

    // Synced, because getShield() varies with it and is read on the client too: the Jade tooltip
    // computes max shields there, and a plain field (never restored by readAdditionalSaveData
    // client-side) would tell it every cannon it looks at is forever mid-warp.
    private static final EntityDataAccessor<Integer> WARP_TICKS_REMAINING =
            SynchedEntityData.defineId(PhotonCannonEntity.class, EntityDataSerializers.INT);

    public PhotonCannonEntity(EntityType<? extends PhotonCannonEntity> type, Level level) {
        super(type, level);
        this.setPersistenceRequired();
        // A fresh cannon starts its warp-in, so it starts at the halved pool. (Loading from disk
        // overwrites this with the saved health afterwards, warping or not.)
        this.setHealth(WarpInVulnerability.warpPool(this.getMaxHealth()));
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, MAX_HEALTH)
                .add(Attributes.MOVEMENT_SPEED, 0.0)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0)
                .add(Attributes.FOLLOW_RANGE, RANGE);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(WARP_TICKS_REMAINING, WARP_TICKS);
    }

    @Override
    protected void registerGoals() {
        // Fires the bolt; there are deliberately no movement goals — the cannon is a fixed turret.
        this.goalSelector.addGoal(1, new CannonFireGoal(this));
        // Targets the nearest enemy-faction unit OR any living vanilla monster in range, reusing the
        // pure PhotonCannonTargeting rule so the "also defend against wild monsters" logic and its
        // unit test stay in one place. Follow range is the cannon's attack range, so it never
        // acquires anything it couldn't shoot.
        this.targetSelector.addGoal(1, new NearestAttackableTargetGoal<>(this, LivingEntity.class, 10, true, false,
                (target, level) -> PhotonCannonTargeting.isTargetable(
                        FactionAttachments.get(this), FactionAttachments.get(target),
                        target.isAlive(), target instanceof Monster)));
    }

    public boolean isWarping() {
        return this.warpTicksRemaining() > 0;
    }

    private int warpTicksRemaining() {
        return this.entityData.get(WARP_TICKS_REMAINING);
    }

    /** Half the finished shield pool while the cannon is still warping in. */
    @Override
    public int getShield() {
        return this.isWarping() ? (int) WarpInVulnerability.warpPool(SHIELD) : SHIELD;
    }

    @Override
    public void tick() {
        super.tick();
        int warpTicks = this.warpTicksRemaining();
        if (this.level() instanceof ServerLevel level && warpTicks > 0) {
            this.entityData.set(WARP_TICKS_REMAINING, warpTicks - 1);
            level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME,
                    this.getX(), this.getY() + 0.6, this.getZ(), 6, 0.4, 0.6, 0.4, 0.02);
            if (warpTicks == 1) {
                this.finishWarp(level);
            }
        }
    }

    /**
     * Scales the halved warp-in pools back up to the finished ones. {@link #isWarping()} is already
     * false by now, so both setters clamp against the full maxima — and because it is what
     * <em>survived</em> that gets doubled, whatever damage the cannon took mid-warp is sustained
     * twice over out of its finished pool.
     */
    private void finishWarp(ServerLevel level) {
        this.setHealth(WarpInVulnerability.onWarpComplete(this.getHealth()));
        ShieldAttachments.set(this, WarpInVulnerability.onWarpComplete(ShieldAttachments.get(this)));
        level.playSound(null, this.blockPosition(), SoundEvents.BEACON_ACTIVATE, SoundSource.HOSTILE, 1.0f, 1.0f);
    }

    // --- Immovable turret: can't be pushed or knocked back ---

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output);
        output.putInt("WarpTicks", this.warpTicksRemaining());
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
        this.entityData.set(WARP_TICKS_REMAINING, input.getIntOr("WarpTicks", 0));
    }
}
