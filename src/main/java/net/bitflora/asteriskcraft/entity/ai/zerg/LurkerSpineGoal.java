package net.bitflora.asteriskcraft.entity.ai.zerg;

import net.bitflora.asteriskcraft.AsteriskCraft;
import net.bitflora.asteriskcraft.entity.Altitude;
import net.bitflora.asteriskcraft.entity.zerg.LurkerEntity;
import net.bitflora.asteriskcraft.entity.zerg.LurkerSpineEntity;
import net.bitflora.asteriskcraft.stats.UnitStat;
import net.bitflora.asteriskcraft.stats.UnitStats;
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
 * The Lurker's attack: a row of {@link LurkerSpineEntity}s that erupts out of the ground and marches
 * away from the Lurker along the bearing to its target, out to the unit's full range.
 *
 * <p>Structurally {@link SunkenSpikeGoal}'s sibling — same cooldown-and-line-of-sight shape, same
 * {@link Altitude#isAirborne} exclusion for a strike that comes up out of the dirt, same
 * {@link GroundStrike} footing search — with two differences that are the whole unit.
 *
 * <p><b>It only fires while fully burrowed.</b> Not "while burrowing": the whole cost of the
 * mechanic is that a Lurker caught mid-dig can neither move nor shoot, and this goal holding only
 * {@link Flag#LOOK} is what lets it run <em>concurrently</em> with the burrow goal that is holding
 * {@code MOVE} rather than fighting it for a flag.
 *
 * <p><b>The bearing is latched when the volley starts, not re-read per spine.</b> The row is a line
 * drawn where the target was standing, and a target that runs perpendicular walks out of it — the
 * same trade the Sunken makes, and what a weapon that hits this hard is paying for. Spines go out one
 * every {@link #SPINE_INTERVAL} ticks so the row visibly races outward instead of appearing whole,
 * and each is footed independently, so a row crossing a step follows the ground and a row running
 * off a ledge simply stops.
 *
 * <p>Every spine deals full damage (see {@link net.bitflora.asteriskcraft.combat.FangStrikeDamageHandler}).
 * A unit standing across two of them takes both.
 */
public class LurkerSpineGoal extends Goal {
    /** How many spines a volley plants. Reaches the unit's range at {@link #SPINE_SPACING} apart. */
    private static final int SPINE_COUNT = 6;
    /** Blocks between one spine and the next. */
    private static final double SPINE_SPACING = 1.0;
    /** Ticks between one spine erupting and the next, so the row marches rather than appearing. */
    private static final int SPINE_INTERVAL = 2;

    private static final UnitStat STAT = UnitStats.LURKER;
    private static final UnitStat.Ranged RANGED = STAT.rangedOrThrow();

    private final LurkerEntity lurker;
    private int cooldown;
    /** Which spine of the current volley goes out next; {@code SPINE_COUNT} means the volley is done. */
    private int spinesPlanted = SPINE_COUNT;
    private int nextSpineIn;
    private float bearing;
    private double originX;
    private double originZ;

    public LurkerSpineGoal(LurkerEntity lurker) {
        this.lurker = lurker;
        this.setFlags(EnumSet.of(Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        return this.lurker.isBurrowed() && inRangeTarget() != null;
    }

    @Override
    public boolean canContinueToUse() {
        // A volley already in flight finishes even if the target dies or the Lurker starts digging
        // out: the spines are in the ground, and stopping halfway would leave a row that visibly
        // stops in mid-air.
        return this.spinesPlanted < SPINE_COUNT || canUse();
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
        advanceVolley();

        LivingEntity target = inRangeTarget();
        if (target == null || !this.lurker.isBurrowed()) {
            return;
        }
        this.lurker.getLookControl().setLookAt(target, 30.0f, 30.0f);
        if (--this.cooldown > 0) {
            return;
        }
        this.cooldown = RANGED.cooldown();
        beginVolley(target);
    }

    @Nullable
    private LivingEntity inRangeTarget() {
        LivingEntity target = this.lurker.getTarget();
        if (target == null || !target.isAlive() || Altitude.isAirborne(target)) {
            return null;
        }
        double reachSq = (double) RANGED.range() * RANGED.range();
        if (this.lurker.distanceToSqr(target) > reachSq) {
            return null;
        }
        return this.lurker.getSensing().hasLineOfSight(target) ? target : null;
    }

    /** Latches the line the row will follow and lets {@link #advanceVolley()} walk down it. */
    private void beginVolley(LivingEntity target) {
        this.bearing = (float) Mth.atan2(target.getZ() - this.lurker.getZ(), target.getX() - this.lurker.getX());
        this.originX = this.lurker.getX();
        this.originZ = this.lurker.getZ();
        this.spinesPlanted = 0;
        this.nextSpineIn = 0;

        this.lurker.triggerAttackAnimation();
        this.lurker.level().playSound(null, this.lurker.blockPosition(), AsteriskCraft.LURKER_ATTACK.get(),
                SoundSource.HOSTILE, 1.0f, 1.0f);
    }

    /** Plants the next spine of the row when its turn comes round. */
    private void advanceVolley() {
        if (this.spinesPlanted >= SPINE_COUNT || --this.nextSpineIn > 0) {
            return;
        }
        this.nextSpineIn = SPINE_INTERVAL;
        double distance = SPINE_SPACING * (this.spinesPlanted + 1);
        this.spinesPlanted++;
        plantSpine(this.originX + Mth.cos(this.bearing) * distance,
                this.originZ + Mth.sin(this.bearing) * distance);
    }

    /**
     * Places one spine on whatever ground is under {@code (x, z)}, or nothing if there is none —
     * a row that runs out over a ledge or a ravine loses the spines past the edge rather than
     * hanging them in the air.
     */
    private void plantSpine(double x, double z) {
        Level level = this.lurker.level();
        double lurkerY = this.lurker.getY();
        OptionalDouble footing = GroundStrike.footingY(level, x, z, lurkerY - 1.0, lurkerY + 1.0);
        if (footing.isEmpty()) {
            return;
        }
        double y = footing.getAsDouble();
        level.addFreshEntity(new LurkerSpineEntity(level, x, y, z, this.bearing, this.lurker));
        level.gameEvent(GameEvent.ENTITY_PLACE, new Vec3(x, y, z), GameEvent.Context.of(this.lurker));
    }
}
