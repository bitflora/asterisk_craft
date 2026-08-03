package net.bitflora.asteriskcraft.building;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The two pure rules behind the warp-in scaffold: how fast panes materialise, and in what order.
 * Raising and filling a scaffold itself edits the world, so it is verified with {@code runClient}.
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
    void layoutFillsBottomUp() {
        List<WarpScaffold.Pane> panes = sorted(
                new BlockPos(0, 2, 0), new BlockPos(0, 0, 0), new BlockPos(0, 1, 0));
        assertEquals(List.of(0, 1, 2), panes.stream().map(pane -> pane.pos().getY()).toList());
    }

    @Test
    void aLayerFillsOutwardFromTheCoreColumn() {
        List<WarpScaffold.Pane> panes = sorted(
                new BlockPos(4, 0, 0), new BlockPos(1, 0, 0), new BlockPos(2, 0, 0));
        assertEquals(List.of(1, 2, 4), panes.stream().map(pane -> pane.pos().getX()).toList());
    }

    @Test
    void tiedPanesHaveOneFixedOrder() {
        // The order is saved as a list and has to survive a reload, so equidistant panes can't be
        // left to sort however the input happened to arrive.
        BlockPos north = new BlockPos(0, 0, -1);
        BlockPos east = new BlockPos(1, 0, 0);
        assertEquals(sorted(north, east), sorted(east, north));
    }

    private static List<WarpScaffold.Pane> sorted(BlockPos... positions) {
        List<WarpScaffold.Pane> panes = new ArrayList<>();
        for (BlockPos pos : positions) {
            panes.add(new WarpScaffold.Pane(pos, STONE));
        }
        panes.sort(WarpScaffold.fillOrder(CORE));
        return panes;
    }
}
