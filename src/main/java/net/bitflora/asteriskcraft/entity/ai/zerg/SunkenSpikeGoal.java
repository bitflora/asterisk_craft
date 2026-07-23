package net.bitflora.asteriskcraft.entity.ai.zerg;

import net.bitflora.asteriskcraft.entity.zerg.SunkenColonyEntity;
import net.bitflora.asteriskcraft.entity.zerg.SunkenSpikeEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.EnumSet;
import org.jetbrains.annotations.Nullable;

/**
 * The Sunken Colony's auto-fire, structurally the Zerg counterpart of
 * {@link net.bitflora.asteriskcraft.entity.ai.protoss.CannonFireGoal}: once per
 * {@link SunkenColonyEntity#ATTACK_COOLDOWN}, while the target chosen by the colony's target selector
 * is alive, within {@link SunkenColonyEntity#RANGE} and in line of sight, the tentacle whips forward
 * and a {@link SunkenSpikeEntity} erupts from the ground under the target.
 *
 * <p>Unlike the cannon, no damage is dealt here — the spike carries it (see
 * {@link net.bitflora.asteriskcraft.combat.SunkenSpikeDamageHandler}), and it lands ~8 ticks later at
 * wherever the spike was planted. A target that keeps moving can walk out from under the strike.
 * Exactly one spike goes out per attack, so a hit is worth exactly
 * {@link SunkenColonyEntity#ATTACK_DAMAGE}.
 */
public class SunkenSpikeGoal extends Goal {
    /** How far below the spike's spawn column to keep searching for solid footing before giving up. */
    private static final int GROUND_SEARCH_DEPTH = 4;

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
        this.cooldown = SunkenColonyEntity.ATTACK_COOLDOWN;
        strikeAt(target);
    }

    @Nullable
    private LivingEntity inRangeTarget() {
        LivingEntity target = this.colony.getTarget();
        if (target == null || !target.isAlive()) {
            return null;
        }
        double reachSq = SunkenColonyEntity.RANGE * SunkenColonyEntity.RANGE;
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
     * Places one spike standing on the surface below {@code (x, z)}. Ported from vanilla's
     * {@code Evoker$EvokerAttackSpellGoal.createSpellEntity}: walk down from {@code maxY} to the first
     * block whose top face is sturdy, then sit the spike on that block's collision height so it
     * doesn't sink into a slab or stair.
     */
    private void plantSpike(double x, double z, double minY, double maxY, float angle) {
        Level level = this.colony.level();
        BlockPos pos = BlockPos.containing(x, maxY, z);
        double topOffset = 0.0;
        boolean footed = false;

        while (pos.getY() >= Mth.floor(minY) - GROUND_SEARCH_DEPTH) {
            BlockPos below = pos.below();
            BlockState belowState = level.getBlockState(below);
            if (belowState.isFaceSturdy(level, below, Direction.UP)) {
                if (!level.isEmptyBlock(pos)) {
                    VoxelShape shape = level.getBlockState(pos).getCollisionShape(level, pos);
                    if (!shape.isEmpty()) {
                        topOffset = shape.max(Direction.Axis.Y);
                    }
                }
                footed = true;
                break;
            }
            pos = pos.below();
        }
        if (!footed) {
            return; // target is airborne or over a void — the strike simply misses
        }

        double y = pos.getY() + topOffset;
        level.addFreshEntity(new SunkenSpikeEntity(level, x, y, z, angle, this.colony));
        level.gameEvent(GameEvent.ENTITY_PLACE, new Vec3(x, y, z), GameEvent.Context.of(this.colony));
    }
}
