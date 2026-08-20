package net.bitflora.asteriskcraft.entity.protoss;

import net.bitflora.asteriskcraft.AsteriskCraft;
import net.bitflora.asteriskcraft.combat.AsteriskCraftDamageTypes;
import net.bitflora.asteriskcraft.entity.Shielded;
import net.bitflora.asteriskcraft.entity.ai.CommandableGoals;
import net.bitflora.asteriskcraft.entity.ai.FactionTargetGoal;
import net.bitflora.asteriskcraft.entity.ai.MeleeAttacks;
import net.bitflora.asteriskcraft.entity.ai.RetaliateGoal;
import net.bitflora.asteriskcraft.entity.ai.SiegeBlockGoal;
import net.bitflora.asteriskcraft.faction.Cloaked;
import net.bitflora.asteriskcraft.stats.UnitAttributes;
import net.bitflora.asteriskcraft.stats.UnitStat;
import net.bitflora.asteriskcraft.stats.UnitStats;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

/**
 * The Dark Templar: a Protoss melee assassin, and the first unit in the mod to fight
 * {@link Cloaked}. Mechanically it is a {@link ZealotEntity} with different numbers — the same goal
 * set, the same strike animation plumbing, the same {@link Shielded} pool — plus that one marker
 * interface, which is the entire implementation of its cloak.
 *
 * <p>That is the point of how cloaking was built: the gate lives in
 * {@code FactionAttachments.isHostile} and the rendering in {@code client.CloakRenderStateModifier},
 * so a unit opts in by declaring itself and nothing here has any cloak state, tick work or special
 * case. What it buys is severe — while no enemy detector covers it, an enemy cannot acquire it,
 * cannot splash it, and <em>cannot retaliate</em> even as it is being cut down, because retaliation
 * resolves hostility through that same call.
 *
 * <p>The counter is already in the world and needs nothing from this class: a Sunken Colony sits
 * beside every Hive and detects at 16 blocks, which is further than it can strike. So a Dark Templar
 * cuts through an undefended flank with impunity and dies quickly once it walks into one — 40 HP
 * behind 20 shields is the most fragile thing the Gateway makes, and a Sunken spike hits for 20.
 *
 * <p>Its numbers live in {@link net.bitflora.asteriskcraft.stats.UnitStats#DARK_TEMPLAR} — not here.
 */
public class DarkTemplarEntity extends Monster implements Shielded, Cloaked {
    private static final UnitStat STAT = UnitStats.DARK_TEMPLAR;

    // Synced rather than broadcast as an entity event, for the same reason as the Zealot's: an int
    // carries the animation's progress rather than just its start, and can't collide with a vanilla
    // LivingEntity event byte.
    private static final EntityDataAccessor<Integer> ATTACK_TICKS =
            SynchedEntityData.defineId(DarkTemplarEntity.class, EntityDataSerializers.INT);

    public DarkTemplarEntity(EntityType<? extends DarkTemplarEntity> type, Level level) {
        super(type, level);
        this.setPersistenceRequired();
    }

    public static AttributeSupplier.Builder createAttributes() {
        return UnitAttributes.apply(Monster.createMonsterAttributes(), UnitStats.DARK_TEMPLAR);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(ATTACK_TICKS, 0);
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(0, new SiegeBlockGoal(this));
        this.goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.0, false));
        this.goalSelector.addGoal(8, new LookAtPlayerGoal(this, Player.class, 8.0f));
        this.goalSelector.addGoal(8, new RandomLookAroundGoal(this));
        this.targetSelector.addGoal(-1, new RetaliateGoal(this));
        this.targetSelector.addGoal(1, new FactionTargetGoal(this));
        CommandableGoals.install(this, this.goalSelector, this.targetSelector);
    }

    /**
     * The single hook for the strike animation: both {@code MeleeAttackGoal} and
     * {@code SiegeBlockGoal} reach the world through {@code swing()}, so hanging the animation here
     * covers battering a building as well as hitting a unit.
     */
    @Override
    public void swing(InteractionHand hand, boolean sendToSwingingEntity) {
        super.swing(hand, sendToSwingingEntity);
        if (!this.level().isClientSide()) {
            this.entityData.set(ATTACK_TICKS, STAT.attackAnimTicks());
        }
    }

    /**
     * No swing sound, so this takes {@code MeleeAttacks}' four-argument form rather than the
     * Zealot's five — there is no Dark Templar attack clip in the source audio, the same reason the
     * Zergling goes without one.
     */
    @Override
    public boolean doHurtTarget(ServerLevel level, Entity target) {
        return MeleeAttacks.doHurtTarget(this, level, target, AsteriskCraftDamageTypes.WARP_BLADE);
    }

    /** Ticks remaining in the strike animation; 0 when idle. Read by the renderer. */
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

    @Override
    public int getAmbientSoundInterval() {
        return 1200; // ~10x the vanilla default (80): ambient barks average ~1/minute, not ~1/6s.
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return AsteriskCraft.DARK_TEMPLAR_AMBIENT.get();
    }

    // No getHurtSound override: the source clips include no hurt bark, so it keeps the vanilla one —
    // the same call the Ultralisk makes.

    @Override
    protected SoundEvent getDeathSound() {
        return AsteriskCraft.DARK_TEMPLAR_DEATH.get();
    }

    @Override
    public int getShield() {
        return STAT.shield();
    }
}
