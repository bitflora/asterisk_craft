package net.bitflora.asteriskcraft.entity.ai;

import net.bitflora.asteriskcraft.building.FactionCore;
import net.bitflora.asteriskcraft.building.SiegeTarget;
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
 * mobs can't damage blocks. Two behaviours, faction-generic (it never checks Nexus-vs-Gateway-vs-Hive):
 * <ul>
 *   <li><b>Building assault</b>: when an enemy-faction {@link SiegeTarget} block entity is nearby, the
 *       unit walks up and hits it, draining its shields and health until it collapses (which, for a
 *       {@link FactionCore}, fires the win/lose).</li>
 *   <li><b>Obstruction breaking</b>: when the unit is pursuing an order/target but its pathing has
 *       stalled (navigation done, not yet arrived), it digs the block between it and its destination
 *       so it can keep advancing.</li>
 * </ul>
 * Installed at <b>priority 0</b> (above {@link CommandedMoveGoal}/{@code MeleeAttackGoal}) so it
 * cleanly preempts movement the moment the unit is stuck at a breakable block, holds the {@code MOVE}
 * flag for the whole dig, then hands it back — rather than fighting a lower-priority goal for a
 * too-short yield window. It stays out of the way while the unit is actually moving, because
 * obstruction-breaking only triggers once navigation reports it is done (see {@code obstructionAhead}).
 * Installed on Zealots, Dragoons, Zerglings, Hydralisks and Mutalisks — never on workers, so miners
 * don't grief. Reach is per-unit (see the two-argument constructor) because an air unit sieges from
 * altitude rather than from arm's length.
 * (The Photon Cannon is an entity, so retaliating against it needs no special case here — units
 * target and hit it back through the normal {@link RetaliateGoal}/{@code FactionTargetGoal} path.)
 */
public class SiegeBlockGoal extends Goal {
    private static final int BUILDING_SCAN_RADIUS = 3;
    /** Default melee reach: a ground unit has to walk up and put its hands on the block. */
    private static final double DEFAULT_REACH = 2.5;
    private static final int ASSAULT_INTERVAL = 20; // one hit per second
    private static final int SIEGE_DAMAGE_PER_HIT = 20; // a 300 hp Hive -> ~15s for a lone unit, faster in a swarm
    private static final int DIG_TICKS = 40; // ~2s to break an obstructing block
    private static final int SCAN_COOLDOWN = 10;

    private enum Mode {BUILDING, DIG}

    private final Mob mob;
    private final double reachSqr;
    private Mode mode = Mode.BUILDING;
    @Nullable
    private BlockPos buildingPos;
    @Nullable
    private BlockPos digPos;
    private int cooldown;
    private int attackTimer;
    private int digTimer;

    public SiegeBlockGoal(Mob mob) {
        this(mob, DEFAULT_REACH);
    }

    /**
     * @param reach how close the unit must be to a building before it starts hitting it. An air unit
     *              hovering above the battlefield can never satisfy the melee default, so it passes
     *              its own attack range instead and batters the building from where it flies.
     */
    public SiegeBlockGoal(Mob mob, double reach) {
        this.mob = mob;
        this.reachSqr = reach * reach;
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        if (--this.cooldown > 0) {
            return false;
        }
        this.cooldown = SCAN_COOLDOWN;
        // A building is an enemy like any other, so a fresh move order outranks battering one for the
        // length of its focus window (see CommandAttachments#isMoveFocused) — a unit ordered out of an
        // enemy base has to be able to leave it. Digging is exempt: that branch serves the march.
        this.buildingPos = CommandAttachments.isMoveFocused(this.mob) ? null : findEnemyBuilding();
        if (this.buildingPos != null) {
            this.mode = Mode.BUILDING;
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
            // An assault already under way is dropped by a move order too, not just prevented by one:
            // canUse() alone would leave a unit mid-siege hammering away until the building fell.
            case BUILDING -> this.buildingPos != null && isEnemyBuilding(this.buildingPos)
                    && !CommandAttachments.isMoveFocused(this.mob);
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
        this.buildingPos = null;
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
            case BUILDING -> tickBuildingAssault();
            case DIG -> tickDig();
        }
    }

    private void tickBuildingAssault() {
        if (this.buildingPos == null) {
            return;
        }
        BlockPos pos = this.buildingPos;
        this.mob.getLookControl().setLookAt(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
        if (pos.distToCenterSqr(this.mob.position()) > this.reachSqr) {
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
                && serverLevel.getBlockEntity(pos) instanceof SiegeTarget target) {
            target.damageBuilding(SIEGE_DAMAGE_PER_HIT, serverLevel, pos);
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
            // Drop the block's mined item form, as if a player had dug it.
            serverLevel.destroyBlock(pos, true);
        }
        this.digPos = null;
    }

    /** Nearest enemy-faction siegeable building within scan range, or null. */
    @Nullable
    private BlockPos findEnemyBuilding() {
        Level level = this.mob.level();
        BlockPos origin = this.mob.blockPosition();
        BlockPos best = null;
        double bestDist = Double.MAX_VALUE;
        for (BlockPos pos : BlockPos.betweenClosed(
                origin.offset(-BUILDING_SCAN_RADIUS, -BUILDING_SCAN_RADIUS, -BUILDING_SCAN_RADIUS),
                origin.offset(BUILDING_SCAN_RADIUS, BUILDING_SCAN_RADIUS, BUILDING_SCAN_RADIUS))) {
            if (!isEnemyBuilding(pos)) {
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

    private boolean isEnemyBuilding(BlockPos pos) {
        return this.mob.level().getBlockEntity(pos) instanceof SiegeTarget target
                && FactionAttachments.get(this.mob).isEnemy(target.buildingFaction());
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

    /** A block the unit is allowed to smash: solid, not a fluid, not unbreakable, not a building core. */
    private boolean isBreakable(BlockState state, BlockPos pos) {
        if (state.isAir() || !state.getFluidState().isEmpty()) {
            return false;
        }
        if (this.mob.level().getBlockEntity(pos) instanceof SiegeTarget) {
            return false; // buildings are handled by the assault path (with health), never insta-dug
        }
        // Bedrock / barriers report a negative destroy speed.
        return state.getDestroySpeed(this.mob.level(), pos) >= 0;
    }
}
