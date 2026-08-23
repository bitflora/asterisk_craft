package net.bitflora.asteriskcraft.entity.zerg;

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
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.Level;

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
public class SporeColonyEntity extends Monster implements Detector, Rooted, CreepSource {
    private static final UnitStat STAT = UnitStats.SPORE_COLONY;

    // Synced rather than broadcast as an entity event: an int carries the animation's progress (not
    // just its start) and can't collide with a vanilla LivingEntity event byte.
    private static final EntityDataAccessor<Integer> ATTACK_TICKS =
            SynchedEntityData.defineId(SporeColonyEntity.class, EntityDataSerializers.INT);

    public SporeColonyEntity(EntityType<? extends SporeColonyEntity> type, Level level) {
        super(type, level);
        this.setPersistenceRequired();
    }

    public static AttributeSupplier.Builder createAttributes() {
        return UnitAttributes.apply(Monster.createMonsterAttributes(), UnitStats.SPORE_COLONY);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(ATTACK_TICKS, 0);
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
        if (!this.level().isClientSide()) {
            int remaining = this.getAttackTicks();
            if (remaining > 0) {
                this.entityData.set(ATTACK_TICKS, remaining - 1);
            }
        }
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
}
