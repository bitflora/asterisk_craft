package net.bitflora.asteriskcraft.entity.ai.zerg;

import net.bitflora.asteriskcraft.entity.Altitude;
import net.bitflora.asteriskcraft.entity.zerg.SunkenColonyEntity;
import net.bitflora.asteriskcraft.entity.zerg.SunkenSpikeEntity;
import net.bitflora.asteriskcraft.stats.UnitStat;
import net.bitflora.asteriskcraft.stats.UnitStats;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;
import java.util.OptionalDouble;
import org.jetbrains.annotations.Nullable;

/**
 * The Sunken Colony's auto-fire, structurally the Zerg counterpart of
 * {@link net.bitflora.asteriskcraft.entity.ai.protoss.CannonFireGoal}: once per cooldown, while the
 * target chosen by the colony's target selector is alive, within range and in line of sight (all
 * from {@link UnitStats#SUNKEN_COLONY}), the tentacle whips forward and a {@link SunkenSpikeEntity}
 * erupts from the ground under the target.
 *
 * <p>The extra condition, the mirror of {@link SporeFireGoal}'s, is that the target is not
 * {@link Altitude#isAirborne}: a spike erupting from the ground cannot reach something in the air.
 * Checking it here as well as in the colony's target selector is not redundant — the rule is
 * positional, so a target can climb after it was acquired, and this goal ticks every tick while the
 * selector only re-runs on its own cadence.
 *
 * <p>Unlike the cannon, no damage is dealt here — the spike carries it (see
 * {@link net.bitflora.asteriskcraft.combat.FangStrikeDamageHandler}), and it lands ~8 ticks later at
 * wherever the spike was planted. A target that keeps moving can walk out from under the strike.
 * Exactly one spike goes out per attack, so a hit is worth exactly one
 * {@link UnitStats#SUNKEN_COLONY}'s worth of attack damage.
 */
public class SunkenSpikeGoal extends Goal {
    private static final UnitStat STAT = UnitStats.SUNKEN_COLONY;
    private static final UnitStat.Ranged RANGED = STAT.rangedOrThrow();

    private final SunkenColonyEntity colony;
    private int cooldown;

    public SunkenSpikeGoal(SunkenColonyEntity colony) {
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
        this.cooldown = 0; // strike as soon as a target is acquired, then on cadence
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
        strikeAt(target);
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
        if (target == null || !target.isAlive() || Altitude.isAirborne(target)) {
            return null;
        }
        double reachSq = (double) RANGED.range() * RANGED.range();
        if (this.colony.distanceToSqr(target) > reachSq) {
            return null;
        }
        return this.colony.getSensing().hasLineOfSight(target) ? target : null;
    }

    private void strikeAt(LivingEntity target) {
        Level level = this.colony.level();
        float angle = (float) Mth.atan2(target.getZ() - this.colony.getZ(), target.getX() - this.colony.getX());
        double minY = Math.min(target.getY(), this.colony.getY());
        double maxY = Math.max(target.getY(), this.colony.getY()) + 1.0;

        this.colony.triggerAttackAnimation();
        level.playSound(null, this.colony.blockPosition(), SoundEvents.EVOKER_PREPARE_ATTACK,
                SoundSource.HOSTILE, 1.0f, 0.6f);
        plantSpike(target.getX(), target.getZ(), minY, maxY, angle);
    }

    /**
     * Places one spike standing on the surface below {@code (x, z)}, or nothing at all if there is no
     * footing there — the target is airborne, or over a void, and the strike simply misses. The
     * search itself is {@link GroundStrike}'s, shared with the Lurker's row.
     */
    private void plantSpike(double x, double z, double minY, double maxY, float angle) {
        Level level = this.colony.level();
        OptionalDouble footing = GroundStrike.footingY(level, x, z, minY, maxY);
        if (footing.isEmpty()) {
            return;
        }
        double y = footing.getAsDouble();
        level.addFreshEntity(new SunkenSpikeEntity(level, x, y, z, angle, this.colony));
        level.gameEvent(GameEvent.ENTITY_PLACE, new Vec3(x, y, z), GameEvent.Context.of(this.colony));
    }
}
