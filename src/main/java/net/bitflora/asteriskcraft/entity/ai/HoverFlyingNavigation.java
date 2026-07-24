package net.bitflora.asteriskcraft.entity.ai;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.navigation.FlyingPathNavigation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.pathfinder.Path;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Flying navigation that keeps a unit a fixed height above the terrain: every destination handed to it
 * is lifted to {@code ground + hoverHeight} before the path is computed. This is what makes an air
 * unit fly <em>over</em> the battlefield rather than swoop down to it, and it does so without any
 * movement goal knowing about flight — {@link CommandedMoveGoal}, {@link GuardGoal},
 * {@link SiegeBlockGoal} and vanilla's {@code RangedAttackGoal} all reach the world through
 * {@code getNavigation().moveTo(...)}, so lifting the path here lifts all of them at once.
 *
 * <p><b>Why the path target and not the move control.</b> The obvious alternative — leave the path on
 * the ground and raise the wanted position inside a {@code FlyingMoveControl} — does not work.
 * {@code PathNavigation.followThePath()} only advances to the next node when the mob is within
 * {@code 1.0} on Y of the current node (its fallback, {@code shouldTargetNextNodeInDirection}, first
 * demands the mob be within 2 blocks of that node). A mob flying six blocks above a ground-hugging
 * path therefore never advances a node: {@code doStuckDetection}/{@code timeoutPath} tear the path
 * down and rebuild it, forever. Elevating the destination instead puts the whole path in open air,
 * where node advancement behaves normally.
 *
 * <p>Every path-creating entry point on {@code PathNavigation} — {@code moveTo(x,y,z,speed)},
 * {@code moveTo(entity,speed)}, {@code createPath(BlockPos,int)}, and
 * {@code FlyingPathNavigation.createPath(Entity,int)} — funnels into the one protected overload
 * below, so overriding it covers them all.
 */
public class HoverFlyingNavigation extends FlyingPathNavigation {
    private final int hoverHeight;

    public HoverFlyingNavigation(Mob mob, Level level, int hoverHeight) {
        super(mob, level);
        this.hoverHeight = hoverHeight;
    }

    /** The cruising altitude for the column containing {@code pos}, in absolute world Y. */
    public int hoverYAt(BlockPos pos) {
        int ground = this.level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, pos).getY();
        // max(...) rather than a flat offset: a target already above the terrain (on a tower, or
        // another flyer) must not make us dive below it.
        return Math.max(ground + this.hoverHeight, pos.getY());
    }

    @Override
    @Nullable
    protected Path createPath(Set<BlockPos> targets, int radiusOffset, boolean above, int reachRange, float maxPathLength) {
        Set<BlockPos> lifted = new LinkedHashSet<>(targets.size());
        for (BlockPos target : targets) {
            lifted.add(new BlockPos(target.getX(), hoverYAt(target), target.getZ()));
        }
        return super.createPath(lifted, radiusOffset, above, reachRange, maxPathLength);
    }

    @Override
    public boolean isStableDestination(BlockPos pos) {
        // The vanilla flying version requires a block that can be stood on below the destination,
        // which is never true of a point in open air — the only kind of destination this navigation
        // ever produces.
        return true;
    }
}
