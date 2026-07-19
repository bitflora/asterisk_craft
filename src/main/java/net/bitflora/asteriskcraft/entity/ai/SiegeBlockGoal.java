package net.bitflora.asteriskcraft.entity.ai;

import net.bitflora.asteriskcraft.building.BuildingAggroAttachments;
import net.bitflora.asteriskcraft.building.FactionCore;
import net.bitflora.asteriskcraft.building.PhotonCannonBlockEntity;
import net.bitflora.asteriskcraft.command.CommandAttachments;
import net.bitflora.asteriskcraft.command.CommandOrder;
import net.bitflora.asteriskcraft.faction.FactionAttachments;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import java.util.EnumSet;
import java.util.Optional;
import org.jetbrains.annotations.Nullable;

/**
 * Lets a combat unit break blocks — the mechanism that makes buildings destroyable, since vanilla
 * mobs can't damage blocks. Three behaviours, faction-generic (it never checks Nexus-vs-Hive):
 * <ul>
 *   <li><b>Cannon retaliation</b>: when an enemy-faction {@link PhotonCannonBlockEntity} has just
 *       shot this unit (tracked via {@link BuildingAggroAttachments}), it beelines for the cannon
 *       and hits it back with its own attack damage, same spirit as {@link RetaliateGoal} but for
 *       a structure instead of a living attacker.</li>
 *   <li><b>Core assault</b>: when an enemy-faction {@link FactionCore} block entity is nearby, the
 *       unit walks up and hits it, draining its health until it collapses (firing the win/lose).</li>
 *   <li><b>Obstruction breaking</b>: when the unit is pursuing an order/target but its pathing has
 *       stalled, it digs the block directly in front of it so it can keep advancing.</li>
 * </ul>
 * Installed on Zealots, Dragoons, Zerglings and Hydralisks — never on workers, so miners don't grief.
 */
public class SiegeBlockGoal extends Goal {
    private static final int CORE_SCAN_RADIUS = 3;
    private static final double REACH_SQR = 6.25; // ~2.5 blocks
    private static final int ASSAULT_INTERVAL = 20; // one hit per second
    private static final int CORE_DAMAGE_PER_HIT = 20; // 300 hp -> ~15s for a lone unit, faster in a swarm
    private static final int DIG_TICKS = 40; // ~2s to break an obstructing block
    private static final int SCAN_COOLDOWN = 10;

    private enum Mode {CANNON, CORE, DIG}

    private final Mob mob;
    private Mode mode = Mode.CORE;
    @Nullable
    private BlockPos corePos;
    @Nullable
    private BlockPos cannonPos;
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
        this.cannonPos = enemyAttackerCannonPos();
        if (this.cannonPos != null) {
            this.mode = Mode.CANNON;
            return true;
        }
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
            case CANNON -> this.cannonPos != null && isEnemyCannon(this.cannonPos);
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
        this.cannonPos = null;
        this.digPos = null;
        this.mob.getNavigation().stop();
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public void tick() {
        switch (this.mode) {
            case CANNON -> tickCannonAssault();
            case CORE -> tickCoreAssault();
            case DIG -> tickDig();
        }
    }

    private void tickCannonAssault() {
        if (this.cannonPos == null) {
            return;
        }
        BlockPos pos = this.cannonPos;
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
                && serverLevel.getBlockEntity(pos) instanceof PhotonCannonBlockEntity cannon) {
            cannon.damage((float) this.mob.getAttributeValue(Attributes.ATTACK_DAMAGE), serverLevel, pos);
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
     * Whichever enemy-faction Photon Cannon last shot this unit, as tracked by
     * {@link BuildingAggroAttachments}, provided it's still alive, still hostile and still within
     * follow range. Clears the attachment once it stops being a valid target so a dead/friendly
     * cannon isn't checked again every scan.
     */
    @Nullable
    private BlockPos enemyAttackerCannonPos() {
        Optional<BlockPos> attacker = BuildingAggroAttachments.getAttackerBuilding(this.mob);
        if (attacker.isEmpty()) {
            return null;
        }
        BlockPos pos = attacker.get();
        double followRange = this.mob.getAttributeValue(Attributes.FOLLOW_RANGE);
        if (isEnemyCannon(pos) && pos.distSqr(this.mob.blockPosition()) <= followRange * followRange) {
            return pos;
        }
        BuildingAggroAttachments.clear(this.mob);
        return null;
    }

    private boolean isEnemyCannon(BlockPos pos) {
        return this.mob.level().getBlockEntity(pos) instanceof PhotonCannonBlockEntity cannon
                && cannon.isAlive() && !cannon.isWarping()
                && FactionAttachments.get(this.mob).isEnemy(cannon.faction());
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
        Direction facing = this.mob.getDirection();
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

    @Nullable
    private BlockPos destination() {
        CommandOrder order = CommandAttachments.getOrder(this.mob);
        if (order.kind() == CommandOrder.Kind.MOVE && order.pos().isPresent()) {
            return order.pos().get();
        }
        Entity target = this.mob.getTarget();
        return target != null ? target.blockPosition() : null;
    }

    /** A block the unit is allowed to smash: solid, not a fluid, not unbreakable, not a core/cannon. */
    private boolean isBreakable(BlockState state, BlockPos pos) {
        if (state.isAir() || !state.getFluidState().isEmpty()) {
            return false;
        }
        if (this.mob.level().getBlockEntity(pos) instanceof FactionCore
                || this.mob.level().getBlockEntity(pos) instanceof PhotonCannonBlockEntity) {
            return false; // cores/cannons are handled by the assault path (with health), never insta-dug
        }
        // Bedrock / barriers report a negative destroy speed.
        return state.getDestroySpeed(this.mob.level(), pos) >= 0;
    }
}
