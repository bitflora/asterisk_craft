package net.bitflora.asteriskcraft.entity.ai.zerg;

import net.bitflora.asteriskcraft.combat.AsteriskCraftDamageTypes;
import net.bitflora.asteriskcraft.entity.Altitude;
import net.bitflora.asteriskcraft.entity.ai.HitscanAttacks;
import net.bitflora.asteriskcraft.entity.zerg.SporeColonyEntity;
import net.bitflora.asteriskcraft.stats.UnitStat;
import net.bitflora.asteriskcraft.stats.UnitStats;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;

import java.util.EnumSet;
import org.jetbrains.annotations.Nullable;

/**
 * The Spore Colony's auto-fire: the same shape as the Photon Cannon's
 * {@code entity.ai.protoss.CannonFireGoal} — an instant hitscan spore burst once per cooldown at the
 * target its owner's target selector picked — with one extra condition.
 *
 * <p>That condition is {@link Altitude#isAirborne}, and it is <em>not</em> redundant with the same
 * check inside the colony's {@code FactionTargetGoal}. The altitude rule is positional, so a target
 * can descend after it was acquired; the target selector only re-runs on its own cadence while this
 * goal ticks every tick, so this is what actually makes the colony stop firing the moment a Mutalisk
 * drops out of the sky.
 */
public class SporeFireGoal extends Goal {
    private static final UnitStat STAT = UnitStats.SPORE_COLONY;
    private static final UnitStat.Ranged RANGED = STAT.rangedOrThrow();

    private final SporeColonyEntity colony;
    private int cooldown;

    public SporeFireGoal(SporeColonyEntity colony) {
        this.colony = colony;
        this.setFlags(EnumSet.of(Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        return inRangeTarget() != null;
    }

    @Override
    public boolean canContinueToUse() {
        return canUse();
    }

    @Override
    public void start() {
        this.cooldown = 0; // fire as soon as a target is acquired, then on cadence
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public void tick() {
        LivingEntity target = inRangeTarget();
        if (target == null) {
            return;
        }
        this.colony.getLookControl().setLookAt(target, 30.0f, 30.0f);
        if (--this.cooldown > 0) {
            return;
        }
        this.cooldown = RANGED.cooldown();
        fireAt(target);
    }

    @Nullable
    private LivingEntity inRangeTarget() {
        // A colony still growing out of the ground has no weapon yet - the Missile Turret's
        // rule, and checked here rather than in the target selector so a target acquired
        // before the Drone morphed is refused too.
        if (this.colony.isUnderConstruction()) {
            return null;
        }
        LivingEntity target = this.colony.getTarget();
        if (target == null || !target.isAlive() || !Altitude.isAirborne(target)) {
            return null;
        }
        double reachSq = (double) RANGED.range() * RANGED.range();
        if (this.colony.distanceToSqr(target) > reachSq) {
            return null;
        }
        return this.colony.getSensing().hasLineOfSight(target) ? target : null;
    }

    private void fireAt(LivingEntity target) {
        this.colony.triggerAttackAnimation();
        HitscanAttacks.fire(this.colony, target, STAT.attackDamageOrThrow(),
                AsteriskCraftDamageTypes.SEEKER_SPORES, ParticleTypes.ITEM_SLIME,
                SoundEvents.SHULKER_SHOOT, 0.8f);
    }
}
