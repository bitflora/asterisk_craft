package net.bitflora.asteriskcraft.entity.terran;

import net.bitflora.asteriskcraft.AsteriskCraft;
import net.bitflora.asteriskcraft.combat.AsteriskCraftDamageTypes;
import net.bitflora.asteriskcraft.entity.WorkerEntity;
import net.bitflora.asteriskcraft.entity.ai.MeleeAttacks;
import net.bitflora.asteriskcraft.entity.ai.RetaliateGoal;
import net.bitflora.asteriskcraft.stats.UnitAttributes;
import net.bitflora.asteriskcraft.stats.UnitStats;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.level.Level;

/**
 * The Terran worker. As with the Probe and the Drone, the harvest economy itself — finding a node,
 * mining it non-destructively, delivering the yield into its home base — is all
 * {@link WorkerEntity}, so an SCV is that plus its own numbers.
 *
 * <p><b>Unlike them, it is armed.</b> It is the only worker in the mod carrying an attack, and the
 * distinction that keeps it a worker rather than a cheap soldier is that it has no
 * {@code FactionTargetGoal}: it never acquires a target of its own, so an SCV crew mines right past
 * an enemy army until one of them hits it. {@link RetaliateGoal} is the whole of its aggression.
 *
 * <p>It has no {@code canHarvest} override — unlike the Drone, nothing the Terran build turns a
 * harvestable block into part of their own base. It also has no hurt sound: the archive has two
 * acknowledgement lines and a death line for the SCV and no pain line, so {@code Mob}'s default
 * stands in rather than an idle bark being pressed into service as one.
 *
 * <p>Its stats live in {@link UnitStats#SCV}.
 */
public class ScvEntity extends WorkerEntity {

    public ScvEntity(EntityType<? extends ScvEntity> type, Level level) {
        super(type, level);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return UnitAttributes.apply(PathfinderMob.createMobAttributes(), UnitStats.SCV);
    }

    @Override
    protected void registerGoals() {
        super.registerGoals();
        // Priority 2 puts fighting back above harvesting (3) and waiting for a regen (4), so being
        // shot at genuinely interrupts the economy, but below a move order (1) — the same doctrine
        // RetaliateGoal itself follows, where a retreat outranks the urge to turn and swing.
        //
        // It ties with WorkerEntity's DeliverGoal, also at 2, and the tie resolves in DeliverGoal's
        // favour because super installed it first. That is the behaviour we want: a loaded SCV
        // finishes its drop before turning around, since a load abandoned mid-trip is simply lost,
        // while an empty one turns immediately.
        this.goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.0, false));
        this.targetSelector.addGoal(-1, new RetaliateGoal(this));
    }

    /**
     * The welding torch rather than the generic {@code minecraft:mob_attack} vanilla would build.
     * See {@link MeleeAttacks} for why the whole method is reimplemented instead of wrapped.
     */
    @Override
    public boolean doHurtTarget(ServerLevel level, Entity target) {
        return MeleeAttacks.doHurtTarget(this, level, target, AsteriskCraftDamageTypes.FUSION_CUTTER);
    }

    @Override
    public int getShield() {
        return UnitStats.SCV.shield();
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return AsteriskCraft.SCV_AMBIENT.get();
    }

    @Override
    protected SoundEvent getDeathSound() {
        return AsteriskCraft.SCV_DEATH.get();
    }
}
