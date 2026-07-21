package net.bitflora.asteriskcraft.entity.ai;

import net.bitflora.asteriskcraft.building.FactionCore;
import net.bitflora.asteriskcraft.command.CommandAttachments;
import net.bitflora.asteriskcraft.command.CommandOrder;
import net.bitflora.asteriskcraft.faction.FactionAttachments;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import java.util.EnumSet;
import org.jetbrains.annotations.Nullable;

/**
 * Lets a combat unit break blocks — the mechanism that makes buildings destroyable, since vanilla
 * mobs can't damage blocks. Two behaviours, faction-generic (it never checks Nexus-vs-Hive):
 * <ul>
 *   <li><b>Core assault</b>: when an enemy-faction {@link FactionCore} block entity is nearby, the
 *       unit walks up and hits it, draining its health until it collapses (firing the win/lose).</li>
 *   <li><b>Obstruction breaking</b>: when the unit is pursuing an order/target but its pathing has
 *       stalled (navigation done, not yet arrived), it digs the block between it and its destination
 *       so it can keep advancing.</li>
 * </ul>
 * Installed at <b>priority 0</b> (above {@link CommandedMoveGoal}/{@code MeleeAttackGoal}) so it
 * cleanly preempts movement the moment the unit is stuck at a breakable block, holds the {@code MOVE}
 * flag for the whole dig, then hands it back — rather than fighting a lower-priority goal for a
 * too-short yield window. It stays out of the way while the unit is actually moving, because
 * obstruction-breaking only triggers once navigation reports it is done (see {@code obstructionAhead}).
 * Installed on Zealots, Dragoons, Zerglings and Hydralisks — never on workers, so miners don't grief.
 * (The Photon Cannon is an entity, so retaliating against it needs no special case here — units
 * target and hit it back through the normal {@link RetaliateGoal}/{@code FactionTargetGoal} path.)
 */
public class SiegeBlockGoal extends Goal {
    private static final int CORE_SCAN_RADIUS = 3;
    private static final double REACH_SQR = 6.25; // ~2.5 blocks
    private static final int ASSAULT_INTERVAL = 20; // one hit per second
    private static final int CORE_DAMAGE_PER_HIT = 20; // 300 hp -> ~15s for a lone unit, faster in a swarm
    private static final int DIG_TICKS = 40; // ~2s to break an obstructing block
    private static final int SCAN_COOLDOWN = 10;

    private enum Mode {CORE, DIG}

    private final Mob mob;
    private Mode mode = Mode.CORE;
    @Nullable
    private BlockPos corePos;
    @Nullable
    private BlockPos digPos;
    private int cooldown;
    private int attackTimer;
    private int digTimer;

    public SiegeBlockGoal(Mob mob) {
        this.mob = mob;
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        if (--this.cooldown > 0) {
            return false;
        }
        this.cooldown = SCAN_COOLDOWN;
        this.corePos = findEnemyCore();
        if (this.corePos != null) {
            this.mode = Mode.CORE;
            return true;
        }
        BlockPos obstruction = obstructionAhead();
        if (obstruction != null) {
            this.mode = Mode.DIG;
            this.digPos = obstruction;
            return true;
        }
        return false;
    }

    @Override
    public boolean canContinueToUse() {
        return switch (this.mode) {
            case CORE -> this.corePos != null && isEnemyCore(this.corePos);
            case DIG -> this.digPos != null && isBreakable(this.mob.level().getBlockState(this.digPos), this.digPos);
        };
    }

    @Override
    public void start() {
        this.attackTimer = 0;
        this.digTimer = 0;
    }

    @Override
    public void stop() {
        this.corePos = null;
        this.digPos = null;
        // Re-scan immediately next time we're eligible, so chewing through a thick wall doesn't stall
        // ~SCAN_COOLDOWN ticks between each block once movement has already stalled.
        this.cooldown = 0;
        this.mob.getNavigation().stop();
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public void tick() {
        switch (this.mode) {
            case CORE -> tickCoreAssault();
            case DIG -> tickDig();
        }
    }

    private void tickCoreAssault() {
        if (this.corePos == null) {
            return;
        }
        BlockPos pos = this.corePos;
        this.mob.getLookControl().setLookAt(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
        if (pos.distToCenterSqr(this.mob.position()) > REACH_SQR) {
            this.mob.getNavigation().moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, 1.1);
            return;
        }
        this.mob.getNavigation().stop();
        if (++this.attackTimer < ASSAULT_INTERVAL) {
            return;
        }
        this.attackTimer = 0;
        this.mob.swing(InteractionHand.MAIN_HAND);
        if (this.mob.level() instanceof ServerLevel serverLevel
                && serverLevel.getBlockEntity(pos) instanceof FactionCore core) {
            core.damageCore(CORE_DAMAGE_PER_HIT, serverLevel, pos);
        }
    }

    private void tickDig() {
        if (this.digPos == null) {
            return;
        }
        BlockPos pos = this.digPos;
        this.mob.getLookControl().setLookAt(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
        this.mob.swing(InteractionHand.MAIN_HAND);
        if (++this.digTimer < DIG_TICKS) {
            return;
        }
        this.digTimer = 0;
        if (this.mob.level() instanceof ServerLevel serverLevel && isBreakable(serverLevel.getBlockState(pos), pos)) {
            serverLevel.destroyBlock(pos, false);
        }
        this.digPos = null;
    }

    /** Nearest enemy-faction core block entity within scan range, or null. */
    @Nullable
    private BlockPos findEnemyCore() {
        Level level = this.mob.level();
        BlockPos origin = this.mob.blockPosition();
        BlockPos best = null;
        double bestDist = Double.MAX_VALUE;
        for (BlockPos pos : BlockPos.betweenClosed(
                origin.offset(-CORE_SCAN_RADIUS, -CORE_SCAN_RADIUS, -CORE_SCAN_RADIUS),
                origin.offset(CORE_SCAN_RADIUS, CORE_SCAN_RADIUS, CORE_SCAN_RADIUS))) {
            if (!isEnemyCore(pos)) {
                continue;
            }
            double dist = pos.distSqr(origin);
            if (dist < bestDist) {
                bestDist = dist;
                best = pos.immutable();
            }
        }
        return best;
    }

    private boolean isEnemyCore(BlockPos pos) {
        return this.mob.level().getBlockEntity(pos) instanceof FactionCore core
                && FactionAttachments.get(this.mob).isEnemy(core.coreFaction());
    }

    /**
     * The block directly ahead of a unit that is trying to reach a destination but whose pathing
     * has stalled (no active path). Returns null unless the unit is genuinely stuck en route.
     */
    @Nullable
    private BlockPos obstructionAhead() {
        BlockPos dest = destination();
        if (dest == null || !this.mob.getNavigation().isDone()) {
            return null; // still pathing, or nowhere to go — nothing to dig
        }
        if (this.mob.blockPosition().closerThan(dest, 2.0)) {
            return null; // basically arrived
        }
        // Dig toward the destination, not wherever the body happens to be facing: a mob pressed against
        // a wall can have its yaw drift off the obstruction, and getDirection() is cardinal-only anyway.
        Direction facing = horizontalDirectionTo(dest);
        BlockPos foot = this.mob.blockPosition().relative(facing);
        BlockPos head = foot.above();
        Level level = this.mob.level();
        if (isBreakable(level.getBlockState(head), head)) {
            return head;
        }
        if (isBreakable(level.getBlockState(foot), foot)) {
            return foot;
        }
        return null;
    }

    /** The cardinal direction from the mob toward {@code dest}, picking the dominant horizontal axis. */
    private Direction horizontalDirectionTo(BlockPos dest) {
        double dx = (dest.getX() + 0.5) - this.mob.getX();
        double dz = (dest.getZ() + 0.5) - this.mob.getZ();
        if (Math.abs(dx) >= Math.abs(dz)) {
            return dx >= 0 ? Direction.EAST : Direction.WEST;
        }
        return dz >= 0 ? Direction.SOUTH : Direction.NORTH;
    }

    @Nullable
    private BlockPos destination() {
        CommandOrder order = CommandAttachments.getOrder(this.mob);
        if (order.kind() == CommandOrder.Kind.MOVE && order.pos().isPresent()) {
            return order.pos().get();
        }
        Entity target = this.mob.getTarget();
        return target != null ? target.blockPosition() : null;
    }

    /** A block the unit is allowed to smash: solid, not a fluid, not unbreakable, not a core. */
    private boolean isBreakable(BlockState state, BlockPos pos) {
        if (state.isAir() || !state.getFluidState().isEmpty()) {
            return false;
        }
        if (this.mob.level().getBlockEntity(pos) instanceof FactionCore) {
            return false; // cores are handled by the assault path (with health), never insta-dug
        }
        // Bedrock / barriers report a negative destroy speed.
        return state.getDestroySpeed(this.mob.level(), pos) >= 0;
    }
}
