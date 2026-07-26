package net.bitflora.asteriskcraft.building;

import net.bitflora.asteriskcraft.AsteriskCraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Guards the Zerg Hive's code-defined multiblock layout and the site-preparation geometry every
 * building is placed on — including the {@code .nbt}-driven Protoss ones, whose own shapes are
 * covered by {@link BuildingTemplatesTest}. Runs under the bootstrapped NeoForge environment so
 * mod-registered blocks and vanilla blocks resolve.
 */
class BuildingLayoutsTest {

    @Test
    void hiveCoreSitsOnTopOfCenter() {
        Map<BlockPos, BlockState> layout = BuildingLayouts.hive();
        BlockState core = layout.get(BuildingLayouts.HIVE_CORE_OFFSET);
        assertNotNull(core, "expected a block at the declared Hive core offset");
        assertEquals(AsteriskCraft.HIVE_CORE.get(), core.getBlock(),
                "the declared offset must hold the interactive Hive core");
    }

    @Test
    void hiveHasAFullThreeByThreeMound() {
        Map<BlockPos, BlockState> layout = BuildingLayouts.hive();
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                assertTrue(layout.containsKey(new BlockPos(dx, 0, dz)),
                        "missing mound block at " + dx + "," + dz);
            }
        }
    }

    // --- place() geometry, exercised through the pure planPlacement (no live ServerLevel needed) ---
    // The probe is a Predicate<BlockPos> reporting whether an EXISTING world block is solid-render;
    // support fill treats anything non-solid (air OR fluids) as needing quartz footing.

    private static final BlockPos ORIGIN = new BlockPos(0, 64, 0);
    /** Flat ground: every block strictly below the origin is solid, so support fill stops immediately. */
    private static final Predicate<BlockPos> FLAT_GROUND = pos -> pos.getY() < ORIGIN.getY();
    /** Support material fed to planPlacement; the fill mechanism is material-agnostic. */
    private static final BlockState SUPPORT = Blocks.SMOOTH_QUARTZ.defaultBlockState();

    @Test
    void headroomIsClearedAboveFootprint() {
        Map<BlockPos, BlockState> edits = BuildingLayouts.planPlacement(ORIGIN, Map.of(), FLAT_GROUND, 6, SUPPORT);
        BlockState air = Blocks.AIR.defaultBlockState();
        for (int dx = -3; dx <= 3; dx++) {
            for (int dz = -3; dz <= 3; dz++) {
                for (int dy = 1; dy <= 5; dy++) {
                    assertEquals(air, edits.get(ORIGIN.offset(dx, dy, dz)),
                            "head-room not cleared at " + dx + "," + dy + "," + dz);
                }
            }
        }
    }

    @Test
    void sitePrepFollowsTheGivenFootprint() {
        // A wide, short template (the Nexus is 9x9): prep must reach its whole footprint, or the
        // outer ring would be left overhanging raw terrain.
        Predicate<BlockPos> probe = pos -> pos.getY() < ORIGIN.getY() - 1; // one air layer below
        Map<BlockPos, BlockState> edits = BuildingLayouts.planSitePrep(ORIGIN, probe, 4, 4, 3, 6, SUPPORT);
        BlockState air = Blocks.AIR.defaultBlockState();
        assertEquals(air, edits.get(ORIGIN.offset(4, 3, 4)), "head-room must cover the footprint corner");
        assertEquals(SUPPORT, edits.get(ORIGIN.offset(4, -1, 4)), "support must reach the footprint corner");
        assertNull(edits.get(ORIGIN.offset(5, 1, 0)), "prep must not spill outside the footprint");
        assertNull(edits.get(ORIGIN.offset(0, 4, 0)), "prep must not clear above the requested head-room");
    }

    @Test
    void nullSupportLeavesTheGroundAlone() {
        // The Protoss buildings sit on their own stonework, so nothing is stamped under them even
        // where the terrain falls away — head-room is still cleared.
        Predicate<BlockPos> probe = pos -> pos.getY() < ORIGIN.getY() - 3; // a three-deep gap below
        Map<BlockPos, BlockState> edits = BuildingLayouts.planSitePrep(ORIGIN, probe, 2, 2, 5, 6, null);
        for (int dy = -1; dy >= -6; dy--) {
            assertNull(edits.get(ORIGIN.offset(0, dy, 0)), "nothing may be placed below the platform");
        }
        assertEquals(Blocks.AIR.defaultBlockState(), edits.get(ORIGIN.above()), "head-room is still cleared");
    }

    @Test
    void flatGroundGetsNoSupportFill() {
        Map<BlockPos, BlockState> edits = BuildingLayouts.planPlacement(ORIGIN, Map.of(), FLAT_GROUND, 6, SUPPORT);
        assertFalse(edits.containsValue(Blocks.SMOOTH_QUARTZ.defaultBlockState()),
                "flat ground should need no quartz support (matches pre-change behavior)");
    }

    @Test
    void dipIsFilledDownToGround() {
        // Column at world (3,0) has its ground three blocks below the platform; everything else is flat.
        Predicate<BlockPos> probe = pos -> (pos.getX() == 3 && pos.getZ() == 0)
                ? pos.getY() <= 60 : pos.getY() <= 63;
        Map<BlockPos, BlockState> edits = BuildingLayouts.planPlacement(ORIGIN, Map.of(), probe, 6, SUPPORT);
        BlockState quartz = Blocks.SMOOTH_QUARTZ.defaultBlockState();
        assertEquals(quartz, edits.get(new BlockPos(3, 63, 0)));
        assertEquals(quartz, edits.get(new BlockPos(3, 62, 0)));
        assertEquals(quartz, edits.get(new BlockPos(3, 61, 0)));
        assertNull(edits.get(new BlockPos(3, 60, 0)), "fill must stop at the first solid ground block");
    }

    @Test
    void supportFillIsBoundedByMaxDepth() {
        // Column at world (3,3) is bottomless void; fill must cap at maxSupportDepth, never a runaway shaft.
        Predicate<BlockPos> probe = pos -> !(pos.getX() == 3 && pos.getZ() == 3) && pos.getY() <= 63;
        int maxDepth = 6;
        Map<BlockPos, BlockState> edits = BuildingLayouts.planPlacement(ORIGIN, Map.of(), probe, maxDepth, SUPPORT);
        BlockState quartz = Blocks.SMOOTH_QUARTZ.defaultBlockState();
        for (int dy = -1; dy >= -maxDepth; dy--) {
            assertEquals(quartz, edits.get(ORIGIN.offset(3, dy, 3)),
                    "expected quartz support at dy=" + dy);
        }
        assertNull(edits.get(ORIGIN.offset(3, -maxDepth - 1, 3)), "fill must not exceed maxSupportDepth");
    }

    @Test
    void fluidPocketUnderPlatformIsFilled() {
        // A fluid pocket reads as non-solid, so the two water blocks below (2,2) get quartz footing.
        Predicate<BlockPos> probe = pos -> (pos.getX() == 2 && pos.getZ() == 2)
                ? pos.getY() <= 61 : pos.getY() <= 63;
        Map<BlockPos, BlockState> edits = BuildingLayouts.planPlacement(ORIGIN, Map.of(), probe, 6, SUPPORT);
        BlockState quartz = Blocks.SMOOTH_QUARTZ.defaultBlockState();
        assertEquals(quartz, edits.get(new BlockPos(2, 63, 2)));
        assertEquals(quartz, edits.get(new BlockPos(2, 62, 2)));
        assertNull(edits.get(new BlockPos(2, 61, 2)), "fill stops at the solid floor under the water");
    }

    @Test
    void supportMaterialIsHonored() {
        // Zerg Hives fill their footing with mycelium (a mycelium base), not the Protoss quartz.
        BlockState mycelium = Blocks.MYCELIUM.defaultBlockState();
        Predicate<BlockPos> probe = pos -> pos.getY() <= 62; // one air block below the platform
        Map<BlockPos, BlockState> edits = BuildingLayouts.planPlacement(ORIGIN, Map.of(), probe, 6, mycelium);
        assertEquals(mycelium, edits.get(new BlockPos(0, 63, 0)), "support fill must use the given material");
        assertFalse(edits.containsValue(Blocks.SMOOTH_QUARTZ.defaultBlockState()),
                "no quartz should appear when mycelium is the support material");
    }

    @Test
    void layoutBlocksWinOverClearedPositions() {
        Map<BlockPos, BlockState> edits =
                BuildingLayouts.planPlacement(ORIGIN, BuildingLayouts.hive(), FLAT_GROUND, 6, SUPPORT);
        // The core sits at dy=1, inside the head-room band that was cleared to air — the layout must win.
        BlockPos core = ORIGIN.offset(BuildingLayouts.HIVE_CORE_OFFSET.getX(),
                BuildingLayouts.HIVE_CORE_OFFSET.getY(), BuildingLayouts.HIVE_CORE_OFFSET.getZ());
        assertEquals(AsteriskCraft.HIVE_CORE.get(), edits.get(core).getBlock(),
                "Hive core must override the head-room air clear at its position");
        assertEquals(Blocks.SLIME_BLOCK, edits.get(ORIGIN).getBlock(),
                "the slime pedestal must be stamped at the origin");
    }
}
