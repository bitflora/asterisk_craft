package net.bitflora.asteriskcraft.entity;

import net.bitflora.asteriskcraft.building.PhotonCannonTargeting;
import net.bitflora.asteriskcraft.entity.ai.CannonFireGoal;
import net.bitflora.asteriskcraft.faction.FactionAttachments;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
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
 * {@link Protoss} (see {@code ShieldEventHandler}), and — crucially — retaliation is automatic,
 * since attacking units acquire and hit it back through the same {@code RetaliateGoal}/
 * {@code FactionTargetGoal} path they use against any living enemy (no special-case building-aggro
 * bookkeeping needed).
 *
 * <p>It never moves — no movement goals, zero movement speed, unpushable, full knockback resistance.
 * It warps in over {@link #WARP_TICKS} (invulnerable and inert until then), then auto-fires an
 * instant energy bolt at the nearest enemy-faction unit — or any vanilla monster — in range via
 * {@link CannonFireGoal}. Hostility is resolved purely through the faction attachment.
 */
public class PhotonCannonEntity extends Mob implements Protoss {
    public static final double RANGE = 7.0;          // StarCraft Photon Cannon range
    public static final float ATTACK_DAMAGE = 10.0f;
    public static final int ATTACK_COOLDOWN = 20;    // one shot per second
    public static final int WARP_TICKS = 200;        // 10 seconds to warp in (mirrors the Gateway)

    public static final int MAX_HEALTH = 50;
    public static final int SHIELD = 50;

    // Economy figures (docs/shaping.md V4). The crafting recipe is the real item sink; these are the
    // design source of truth, guarded by PhotonCannonEconomyTest.
    public static final int WOOD_COST = 100;
    public static final int COBBLE_COST = 100;
    public static final int IRON_COST = 20;

    private int warpTicksRemaining = WARP_TICKS;

    public PhotonCannonEntity(EntityType<? extends PhotonCannonEntity> type, Level level) {
        super(type, level);
        this.setPersistenceRequired();
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, MAX_HEALTH)
                .add(Attributes.MOVEMENT_SPEED, 0.0)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0)
                .add(Attributes.FOLLOW_RANGE, RANGE);
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
        return this.warpTicksRemaining > 0;
    }

    @Override
    public int getShield() {
        return SHIELD;
    }

    @Override
    public void tick() {
        super.tick();
        if (this.level() instanceof ServerLevel level && this.warpTicksRemaining > 0) {
            this.warpTicksRemaining--;
            level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME,
                    this.getX(), this.getY() + 0.6, this.getZ(), 6, 0.4, 0.6, 0.4, 0.02);
            if (this.warpTicksRemaining == 0) {
                level.playSound(null, this.blockPosition(), SoundEvents.BEACON_ACTIVATE, SoundSource.HOSTILE, 1.0f, 1.0f);
            }
        }
    }

    // --- Immovable turret: can't be pushed, knocked back, or damaged mid-warp ---

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public boolean isInvulnerableTo(ServerLevel level, DamageSource source) {
        return this.isWarping() || super.isInvulnerableTo(level, source);
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output);
        output.putInt("WarpTicks", this.warpTicksRemaining);
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
        this.warpTicksRemaining = input.getIntOr("WarpTicks", 0);
    }
}
