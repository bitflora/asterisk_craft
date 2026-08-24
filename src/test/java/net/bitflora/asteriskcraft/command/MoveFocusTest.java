package net.bitflora.asteriskcraft.command;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Guards the window during which a fresh move order outranks combat. What the goals do with it
 * (FactionTargetGoal and RetaliateGoal standing down, CommandedMoveGoal refusing to yield and
 * dropping any target set anyway, SiegeBlockGoal breaking off a building assault) can't be unit
 * tested — that needs live Mobs, a level and a GoalSelector driving start/tick/stop, so it is
 * verified via {@code runClient}, same reasoning as {@code ProbeEconomyTest}. What is pinned here is
 * the requirement itself: how long the window lasts, and that it is a deadline rather than a flag
 * that could be left stuck on.
 */
class MoveFocusTest {

    @Test
    void windowIsAtLeastFiveSeconds() {
        // The requirement is "at least 5 seconds"; 20 ticks to the second.
        assertTrue(CommandAttachments.MOVE_FOCUS_TICKS >= 5 * 20,
                "a move order must override targeting for at least five seconds");
    }

    @Test
    void focusHoldsForTheWholeWindowAndThenLapses() {
        long issuedAt = 1_000L;
        long until = issuedAt + CommandAttachments.MOVE_FOCUS_TICKS;

        assertTrue(CommandAttachments.isMoveFocused(until, issuedAt),
                "the order must take effect on the tick it lands");
        assertTrue(CommandAttachments.isMoveFocused(until, until - 1),
                "the last tick of the window is still focused");
        assertFalse(CommandAttachments.isMoveFocused(until, until),
                "the window ends on its deadline, so the unit fights again");
        assertFalse(CommandAttachments.isMoveFocused(until, until + 10_000L),
                "and stays ended — nothing ticks a unit's command state down");
    }

    @Test
    void exactlyTheOrdersThatWalkSomewhereOverrideCombat() {
        // Which kinds open the window is the half of the rule that isn't about time. GUARD is the
        // interesting exclusion: it also names a position, but holding a station means fighting for
        // it, so it must not suppress targeting even briefly.
        assertTrue(CommandOrder.Kind.MOVE.isMarch());
        assertTrue(CommandOrder.Kind.LOAD.isMarch(), "getting into cover is a march");
        assertFalse(CommandOrder.Kind.GUARD.isMarch(), "a station is held, not marched to");
        assertFalse(CommandOrder.Kind.ATTACK.isMarch(), "an attack order asks for the opposite");
        assertFalse(CommandOrder.Kind.MINE.isMarch());
        assertFalse(CommandOrder.Kind.NONE.isMarch());
    }

    @Test
    void theClearedValueIsNeverFocused() {
        // clearOrder and a non-MOVE order both store 0. On a fresh world getGameTime() is 0 too, so
        // the comparison has to be strict: a >= here would leave every unit ignoring enemies until
        // the first tick elapsed.
        assertFalse(CommandAttachments.isMoveFocused(0L, 0L),
                "no order means no focus, including at game time zero");
    }
}
