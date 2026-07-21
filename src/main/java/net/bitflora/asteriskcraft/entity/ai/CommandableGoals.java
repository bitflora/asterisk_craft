package net.bitflora.asteriskcraft.entity.ai;

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
        goalSelector.addGoal(1, new CommandedMoveGoal(mob, 1.1));
        goalSelector.addGoal(5, new GuardGoal(mob, 1.0));
        targetSelector.addGoal(0, new CommandedAttackGoal(mob));
    }
}
