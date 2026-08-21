package net.bitflora.asteriskcraft.entity.ai.zerg;

import net.bitflora.asteriskcraft.command.CommandAttachments;
import net.bitflora.asteriskcraft.command.CommandOrder;
import net.bitflora.asteriskcraft.entity.zerg.LurkerEntity;
import net.bitflora.asteriskcraft.faction.FactionAttachments;
import net.bitflora.asteriskcraft.stats.UnitStat;
import net.bitflora.asteriskcraft.stats.UnitStats;
import net.minecraft.world.entity.LivingEntity;

import java.util.Comparator;
import java.util.List;
import org.jetbrains.annotations.Nullable;

/**
 * One question, asked by every goal that could pull a Lurker out of the ground: <b>is there anything
 * I could shoot from right here?</b>
 *
 * <p>It exists because the answer has to be the same in two places that arbitrate through different
 * mechanisms. {@link LurkerHoldGroundGoal} uses it in the target selector, to claim the near enemy and
 * hold the {@code TARGET} flag against retaliation; {@link LurkerApproachGoal} uses it in the goal
 * selector, to refuse to start at all. Either alone leaves the unit exploitable: the target selector
 * cannot see who else calls {@code setTarget}, and the approach goal cannot see who else digs. Asked
 * from both, "a Lurker with something in reach does not climb out" holds regardless of what put the
 * target there.
 *
 * <p>Reach is the unit's full spine range and nothing shorter. An earlier version used the approach
 * goal's inner distance so the two could not argue over a target on the boundary, which quietly left
 * a ring — between that distance and the real range — where the Lurker could shoot something but
 * would still dig out to chase a sniper, exactly the bug this is here to stop. The goals do not need
 * to be kept apart, because they now ask the same question and their answers are complementary by
 * construction: nothing is both in reach and worth walking to.
 */
final class LurkerReach {
    private static final UnitStat.Ranged RANGED = UnitStats.LURKER.rangedOrThrow();

    /** As far as the spines go: what "in reach" means, everywhere. */
    static final double RANGE = RANGED.range();

    private LurkerReach() {
    }

    /**
     * Whether the unit should be standing its ground: something strikeable is in reach <em>and</em> no
     * player order says otherwise. This is the form the two goals that would otherwise dig ask, because
     * an order — to move, or to focus a specific distant enemy — is exactly the case where digging out
     * is what the player asked for.
     */
    static boolean holdingGround(LurkerEntity lurker) {
        return commandFree(lurker) && anythingInReach(lurker);
    }

    /**
     * The bare question, with no deference to orders: is there anything down here to shoot? What
     * {@link LurkerBurrowGoal} asks, and the difference from {@link #holdingGround} is deliberate — a
     * unit ordered onto a distant enemy is walked out of the ground by the goal that holds {@code MOVE}
     * outranking the digger, not by the digger refusing to bury a unit with a target in front of it.
     */
    static boolean anythingInReach(LurkerEntity lurker) {
        return nearestInReach(lurker) != null;
    }

    /** The closest thing worth staying buried for, or null if nothing is. */
    @Nullable
    static LivingEntity nearestInReach(LurkerEntity lurker) {
        List<LivingEntity> candidates = lurker.level().getEntitiesOfClass(LivingEntity.class,
                lurker.getBoundingBox().inflate(RANGE),
                candidate -> strikeable(lurker, candidate) && within(lurker, candidate, RANGE));
        return candidates.stream().min(Comparator.comparingDouble(lurker::distanceToSqr)).orElse(null);
    }

    /**
     * Everything that has to be true of a target for staying buried to beat walking: alive, an enemy
     * <em>right now</em> (so a unit that cloaks stops holding the Lurker in place), on the ground —
     * {@link LurkerEntity#canStrike}, the one definition of what the spines can reach — and actually
     * visible, since a Lurker is not kept dug in by an enemy behind a wall.
     */
    static boolean strikeable(LurkerEntity lurker, LivingEntity candidate) {
        return candidate.isAlive()
                && FactionAttachments.isHostile(lurker, candidate)
                && LurkerEntity.canStrike(candidate)
                && lurker.getSensing().hasLineOfSight(candidate);
    }

    static boolean within(LurkerEntity lurker, LivingEntity candidate, double distance) {
        return lurker.distanceToSqr(candidate) <= distance * distance;
    }

    /** False while the player is steering this unit, by either kind of order. */
    static boolean commandFree(LurkerEntity lurker) {
        return !CommandAttachments.isMoveFocused(lurker)
                && CommandAttachments.getOrder(lurker).kind() != CommandOrder.Kind.ATTACK;
    }
}
