package net.bitflora.asteriskcraft.entity.zerg;

import net.bitflora.asteriskcraft.AsteriskCraft;
import net.bitflora.asteriskcraft.combat.AsteriskCraftDamageTypes;
import net.bitflora.asteriskcraft.entity.ai.CommandableGoals;
import net.bitflora.asteriskcraft.entity.ai.FactionTargetGoal;
import net.bitflora.asteriskcraft.entity.ai.MeleeAttacks;
import net.bitflora.asteriskcraft.entity.ai.RetaliateGoal;
import net.bitflora.asteriskcraft.entity.ai.SiegeBlockGoal;
import net.bitflora.asteriskcraft.entity.ai.StuckWanderGoal;
import net.bitflora.asteriskcraft.entity.protoss.DragoonEntity;
import net.bitflora.asteriskcraft.stats.UnitAttributes;
import net.bitflora.asteriskcraft.stats.UnitStats;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
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
 * The Zerg heavy: a {@link ZerglingEntity} grown into something with the staying power to walk into a
 * defended base rather than swarm around it. Behaviourally it is a Zergling — same goal set, same
 * plain {@code Monster} base (not a repurposed Zombie; see docs/neoforge-api-notes.md) — and it
 * reuses the Zergling's model and texture, scaled up, so it needs no art of its own.
 *
 * <p><b>Nothing about its size scales uniformly, and that is deliberate.</b> The hitbox grows only
 * across (0.6 → 1.2) and keeps a height of 1.99 rather than the literal 3.9: a pathfinding node's
 * footprint is {@code floor(dim + 1)}, so 3.9 would demand four blocks of vertical clearance and shut
 * the unit out of every doorway, tunnel and overhang in the world. The rendered model in turn grows
 * far more across the ground than in height — a heavy is broad and long, not tall — and overhangs the
 * hitbox on every axis, the way the Zealot's pauldrons already do. See its registration in
 * {@code AsteriskCraft}, {@code client.zerg.UltraliskRenderer} and {@code entity.UnitFootprintTest}.
 *
 * <p>At 1.2 wide it is two pathfinding nodes across — the second unit in the mod after the
 * {@link DragoonEntity} to need a clear 2x2 per node. It carries no navigation code of its own for
 * that: wide-unit handling already lives in {@link CommandableGoals} (path-length and visited-node
 * budget), {@code CommandedMoveGoal} (per-tick destination re-read and sidestep), {@code MoveFormation}
 * (squad pitch taken from the widest member) and {@code SpawnSpots} (clears the whole spawn AABB).
 *
 * <p>Its numbers live in {@link net.bitflora.asteriskcraft.stats.UnitStats#ULTRALISK} — not here.
 */
public class UltraliskEntity extends Monster {

    public UltraliskEntity(EntityType<? extends UltraliskEntity> type, Level level) {
        super(type, level);
        this.setPersistenceRequired();
    }

    public static AttributeSupplier.Builder createAttributes() {
        return UnitAttributes.apply(Monster.createMonsterAttributes(), UnitStats.ULTRALISK);
    }

    @Override
    protected void registerGoals() {
        // Priority -1, above even the digger: it exists to take a pinned unit off whichever goal has
        // it pinned, which it can only do from a lower priority number. See StuckWanderGoal.
        this.goalSelector.addGoal(-1, new StuckWanderGoal(this));
        this.goalSelector.addGoal(0, new FloatGoal(this));
        // Priority 0 (above the move/attack goals): the digger must be able to preempt movement to
        // batter through an obstruction, not merely fill a yield window. See SiegeBlockGoal.
        this.goalSelector.addGoal(0, new SiegeBlockGoal(this));
        this.goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.0, false));
        this.goalSelector.addGoal(8, new LookAtPlayerGoal(this, Player.class, 8.0f));
        this.goalSelector.addGoal(8, new RandomLookAroundGoal(this));
        this.targetSelector.addGoal(-1, new RetaliateGoal(this));
        this.targetSelector.addGoal(1, new FactionTargetGoal(this));
        CommandableGoals.install(this, this.goalSelector, this.targetSelector);
    }

    /**
     * Kaiser blades rather than the generic {@code minecraft:mob_attack} vanilla would build. See
     * {@link MeleeAttacks} for why the whole method is reimplemented instead of wrapped.
     */
    @Override
    public boolean doHurtTarget(ServerLevel level, Entity target) {
        return MeleeAttacks.doHurtTarget(this, level, target, AsteriskCraftDamageTypes.KAISER_BLADES,
                AsteriskCraft.ULTRALISK_ATTACK.get());
    }

    @Override
    public int getAmbientSoundInterval() {
        return 1200; // ~10x the vanilla default (80): ambient barks average ~1/minute, not ~1/6s.
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return AsteriskCraft.ULTRALISK_AMBIENT.get();
    }

    // No getHurtSound override: the source clips include no hurt bark, so it keeps the vanilla one
    // rather than reusing another unit's voice. The Dragoon is in the same position.

    @Override
    protected SoundEvent getDeathSound() {
        return AsteriskCraft.ULTRALISK_DEATH.get();
    }
}
