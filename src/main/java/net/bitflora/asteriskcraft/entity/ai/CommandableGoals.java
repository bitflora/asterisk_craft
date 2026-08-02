package net.bitflora.asteriskcraft.entity.ai;

import net.bitflora.asteriskcraft.command.CommandInputPacket;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.GoalSelector;

/**
 * Installs the standard "responds to player orders" goal pair on a combat unit: a
 * {@link CommandedMoveGoal} (move orders) and a {@link CommandedAttackGoal} (focus-fire orders).
 * Call from a unit's {@code registerGoals()}. Kept faction-generic so Zerg units can opt in later.
 */
public final class CommandableGoals {
    private CommandableGoals() {
    }

    public static void install(Mob mob, GoalSelector goalSelector, GoalSelector targetSelector) {
        configureNavigation(mob);
        goalSelector.addGoal(1, new CommandedMoveGoal(mob, 1.1));
        goalSelector.addGoal(5, new GuardGoal(mob, 1.0));
        targetSelector.addGoal(0, new CommandedAttackGoal(mob));
    }

    /**
     * Sizes a commandable unit's pathing to how far the player can actually click.
     * {@code PathNavigation.requiredPathLength} defaults to 16, and {@code getMaxPathLength()} is
     * {@code max(FOLLOW_RANGE, requiredPathLength)} — so an ordinary unit (FOLLOW_RANGE 32) could
     * never plan more than half a command click's reach, and re-pathed in 32-block hops the rest of
     * the way. The same value also sets the A* visited-node budget
     * ({@code updatePathfinderMaxVisitedNodes} = {@code floor(length * 16)}), which is the part that
     * actually mattered: a unit two pathfinder nodes wide exhausts the smaller budget quickly and
     * gets handed a truncated path.
     *
     * <p>Safe to call for the flyers too — {@code ScoutEntity}/{@code MutaliskEntity} already set
     * this to the same 64 in their {@code createNavigation}.
     *
     * <p>Separate from {@link #install} because {@code ProbeEntity} adds its own
     * {@link CommandedMoveGoal} rather than going through the full commandable set, but still takes
     * orders at the same range.
     */
    public static void configureNavigation(Mob mob) {
        mob.getNavigation().setRequiredPathLength((float) CommandInputPacket.REACH);
    }
}
