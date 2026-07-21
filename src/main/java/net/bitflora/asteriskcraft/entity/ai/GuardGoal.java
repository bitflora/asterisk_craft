package net.bitflora.asteriskcraft.entity.ai;

import net.bitflora.asteriskcraft.command.CommandAttachments;
import net.bitflora.asteriskcraft.command.CommandOrder;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.EnumSet;

/**
 * Keeps a unit stationed near the {@link CommandOrder.Kind#GUARD} home anchor on its command
 * attachment: it strolls to random points within a short radius, and snaps back toward home if it
 * strays past the leash. It deliberately <b>yields</b> ({@link #canUse()} returns false) whenever
 * the unit has a live attack target, so the existing faction-targeting / melee / ranged / siege
 * goals handle the actual fighting; once the target is gone the unit wanders home again.
 *
 * <p>Faction-generic — installed on any commandable {@link Mob} at a priority below
 * {@link CommandedMoveGoal}, so a later move order (e.g. a wave attack) cleanly overrides the guard.
 * The order is persistent: this goal never clears it.
 */
public class GuardGoal extends Goal {
    private static final double WANDER_RADIUS = 6.0;
    private static final double LEASH_SQR = 12.0 * 12.0; // return home if dragged beyond ~12 blocks
    private static final int WANDER_COOLDOWN = 60;       // pick a new stroll point ~3s

    private final Mob mob;
    private final double speed;
    private int cooldown;
    @Nullable
    private Vec3 target;

    public GuardGoal(Mob mob, double speed) {
        this.mob = mob;
        this.speed = speed;
        this.setFlags(EnumSet.of(Flag.MOVE));
    }

    @Nullable
    private BlockPos home() {
        CommandOrder order = CommandAttachments.getOrder(this.mob);
        return order.kind() == CommandOrder.Kind.GUARD ? order.pos().orElse(null) : null;
    }

    @Override
    public boolean canUse() {
        BlockPos home = home();
        if (home == null || hasLiveTarget()) {
            return false;
        }
        Vec3 center = Vec3.atCenterOf(home);
        if (this.mob.position().distanceToSqr(center) > LEASH_SQR) {
            this.target = center; // dragged too far — head straight back
            return true;
        }
        if (--this.cooldown > 0) {
            return false;
        }
        this.cooldown = WANDER_COOLDOWN;
        double angle = this.mob.getRandom().nextDouble() * Math.PI * 2.0;
        double dist = this.mob.getRandom().nextDouble() * WANDER_RADIUS;
        this.target = center.add(Math.cos(angle) * dist, 0, Math.sin(angle) * dist);
        return true;
    }

    @Override
    public boolean canContinueToUse() {
        return home() != null && !hasLiveTarget() && !this.mob.getNavigation().isDone();
    }

    private boolean hasLiveTarget() {
        LivingEntity target = this.mob.getTarget();
        return target != null && target.isAlive();
    }

    @Override
    public void start() {
        if (this.target != null) {
            this.mob.getNavigation().moveTo(this.target.x, this.target.y, this.target.z, this.speed);
        }
    }

    @Override
    public void stop() {
        this.mob.getNavigation().stop();
        this.target = null;
    }
}
