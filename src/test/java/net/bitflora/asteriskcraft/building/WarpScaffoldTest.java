package net.bitflora.asteriskcraft.building;

import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The pure rules behind the warp-in scaffold: how fast panes materialise, in what order, and what a
 * smashed one costs. Raising and filling a scaffold itself edits the world, so the fill and the
 * damage landing on completion are verified with {@code runClient}.
 */
class WarpScaffoldTest {
    private static final BlockState STONE = Blocks.STONE.defaultBlockState();
    private static final BlockPos CORE = new BlockPos(0, 0, 0);

    @Test
    void panesSpreadEvenlyOverTheRemainingWarp() {
        // A Gateway: 40-odd panes over its 1200-tick warp lands one roughly every 30 ticks.
        assertEquals(30, WarpScaffold.paneInterval(1200, 40));
        assertEquals(25, WarpScaffold.paneInterval(2400, 96));
    }

    @Test
    void smashedPanesStretchTheRemainingOnesOut() {
        // Same panes left, but ten seconds were added to the countdown: they take proportionally longer.
        int unhindered = WarpScaffold.paneInterval(600, 20);
        int delayed = WarpScaffold.paneInterval(600 + WarpScaffold.BREAK_PENALTY_TICKS, 20);
        assertTrue(delayed > unhindered, "a lengthened warp must slow the fill-in, not overrun it");
    }

    @Test
    void aWarpAlmostOverStillPlacesOnePanePerTick() {
        // Never zero: the countdown would otherwise finish with the interval stuck at "immediately"
        // and the fill would run as a single frame's worth of block edits.
        assertEquals(1, WarpScaffold.paneInterval(3, 40));
        assertEquals(1, WarpScaffold.paneInterval(0, 40));
    }

    @Test
    void noPanesLeftMeansNoNextPane() {
        assertEquals(Integer.MAX_VALUE, WarpScaffold.paneInterval(1200, 0));
    }

    @Test
    void theLayoutFillsInAScrambledOrder() {
        List<WarpScaffold.Pane> panes = layout(40);
        List<WarpScaffold.Pane> scrambled = new ArrayList<>(panes);
        WarpScaffold.scramble(scrambled, RandomSource.create(1234L));
        assertNotEquals(panes, scrambled, "panes must not go in the order the layout was walked in");
    }

    @Test
    void scramblingLosesNoPanes() {
        // Every square of the layout has to turn up exactly once: the scrambled list is the whole
        // record of what is still glass, so a pane dropped here would stay glass forever.
        List<WarpScaffold.Pane> panes = layout(40);
        List<WarpScaffold.Pane> scrambled = new ArrayList<>(panes);
        WarpScaffold.scramble(scrambled, RandomSource.create(1234L));
        assertEquals(new HashSet<>(panes), new HashSet<>(scrambled));
        assertEquals(panes.size(), scrambled.size());
    }

    @Test
    void aSmashedPaneIsMarkedRatherThanDropped() {
        // It keeps its place in the fill order — its real block is due when it was always due — and
        // the mark is only there so the same square isn't charged for on every sweep.
        WarpScaffold.Pane pane = new WarpScaffold.Pane(new BlockPos(1, 0, 0), STONE);
        assertFalse(pane.smashed());
        WarpScaffold.Pane smashed = pane.smash();
        assertTrue(smashed.smashed());
        assertEquals(pane.pos(), smashed.pos(), "a smashed pane still owns its square");
        assertEquals(pane.finished(), smashed.finished(), "and still knows what it becomes");
    }

    /**
     * What smashing the scaffold is worth, against the buildings it can be raised on. Both numbers are
     * design decisions rather than derived, so they're pinned here: the delay is what an attacker
     * buys, and the damage is what the building pays for it once it stands up.
     */
    @Test
    void smashingCostsMatchDesign() {
        assertEquals(20 * 10, WarpScaffold.BREAK_PENALTY_TICKS, "a smashed pane costs the warp 10 seconds");
        assertEquals(20, WarpScaffold.SMASHED_PANE_DAMAGE);

        // A Gateway's 40-odd panes against its 250 HP behind 250 shields: smashing about 25 of them
        // is enough to collapse it the moment it comes online.
        int gatewayPool = GatewayBlockEntity.MAX_HEALTH + GatewayBlockEntity.SHIELD;
        assertEquals(25, gatewayPool / WarpScaffold.SMASHED_PANE_DAMAGE);
    }

    /** A layout of {@code count} panes, in the order the template's box is walked. */
    private static List<WarpScaffold.Pane> layout(int count) {
        List<WarpScaffold.Pane> panes = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            panes.add(new WarpScaffold.Pane(CORE.offset(i % 5, i / 25, (i / 5) % 5), STONE));
        }
        return panes;
    }
}
