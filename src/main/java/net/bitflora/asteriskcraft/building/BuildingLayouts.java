package net.bitflora.asteriskcraft.building;

import net.bitflora.asteriskcraft.AsteriskCraft;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Predicate;

/**
 * The Zerg Hive's code-defined multiblock layout (relative position -> state), plus the site
 * preparation — head-room clearing and support fill — shared with the {@code .nbt}-driven
 * Protoss buildings (see {@link BuildingTemplates}). Origin is the block the structure is
 * anchored on; the interactive core block sits above the center.
 */
public final class BuildingLayouts {
    private BuildingLayouts() {
    }

    /**
     * 3x3 organic Zerg mound: sculk platform, dripstone spikes on the corners, and the Hive core
     * raised on a slime pedestal. Deliberately unlike the crisp Protoss quartz/purpur look.
     */
    public static Map<BlockPos, BlockState> hive() {
        Map<BlockPos, BlockState> layout = new LinkedHashMap<>();
        BlockState floor = Blocks.MYCELIUM.defaultBlockState();
        BlockState spike = Blocks.DRIPSTONE_BLOCK.defaultBlockState();

        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                boolean isCorner = Math.abs(dx) == 1 && Math.abs(dz) == 1;
                layout.put(new BlockPos(dx, 0, dz), floor);
                if (isCorner) {
                    layout.put(new BlockPos(dx, 1, dz), spike);
                }
            }
        }
        layout.put(new BlockPos(0, 0, 0), Blocks.SLIME_BLOCK.defaultBlockState());
        layout.put(new BlockPos(0, 1, 0), AsteriskCraft.HIVE_CORE.get().defaultBlockState());
        return layout;
    }

    /** Relative offset of the Hive's core block within {@link #hive()}. */
    public static final BlockPos HIVE_CORE_OFFSET = new BlockPos(0, 1, 0);

    /** Bounds the support fill so a deep or void column can't carve a shaft down to bedrock. */
    private static final int MAX_SUPPORT_DEPTH = 6;

    /** Footprint radius and head-room the code-defined Hive layout is prepared with. */
    private static final int HIVE_SITE_RADIUS = 3;
    private static final int HIVE_SITE_HEADROOM = 5;

    /**
     * Computes every site-preparation edit without touching the world, so the geometry (head-room
     * clear, gap-filling support) is unit-testable. The prepared footprint spans
     * {@code -radiusX..radiusX} by {@code -radiusZ..radiusZ} around the origin — templates derive
     * that from their own size, so a wide building is never left overhanging raw terrain.
     * {@code isSolid} reports whether the EXISTING world block at a position is solid-render;
     * support fill runs from just under the platform down to the first solid block in each column
     * (fluids and air are not solid, so they get filled) with {@code support}, which keeps the
     * platform on solid footing even when it was raised onto the highest ground in a dip. Pass a
     * null {@code support} to leave the ground alone entirely and only clear head-room — the
     * Protoss buildings do, so no foreign plinth is stamped under their own stonework.
     */
    static Map<BlockPos, BlockState> planSitePrep(BlockPos origin, Predicate<BlockPos> isSolid,
            int radiusX, int radiusZ, int headroom, int maxSupportDepth, @Nullable BlockState support) {
        Map<BlockPos, BlockState> edits = new LinkedHashMap<>();
        BlockState air = Blocks.AIR.defaultBlockState();
        for (int dx = -radiusX; dx <= radiusX; dx++) {
            for (int dz = -radiusZ; dz <= radiusZ; dz++) {
                for (int dy = 1; dy <= headroom; dy++) {
                    edits.put(origin.offset(dx, dy, dz), air);
                }
                for (int dy = -1; support != null && dy >= -maxSupportDepth; dy--) {
                    BlockPos pos = origin.offset(dx, dy, dz);
                    if (isSolid.test(pos)) {
                        break; // reached solid ground; stop filling this column
                    }
                    edits.put(pos, support);
                }
            }
        }
        return edits;
    }

    /**
     * Computes every block edit {@link #place} will apply for a code-defined layout. Edits are
     * inserted site-prep (air -> support) then layout, so the layout wins on any shared position
     * when applied in order.
     */
    static Map<BlockPos, BlockState> planPlacement(BlockPos origin, Map<BlockPos, BlockState> layout,
            Predicate<BlockPos> isSolid, int maxSupportDepth, BlockState support) {
        Map<BlockPos, BlockState> edits = planSitePrep(origin, isSolid,
                HIVE_SITE_RADIUS, HIVE_SITE_RADIUS, HIVE_SITE_HEADROOM, maxSupportDepth, support);
        layout.forEach((offset, state) ->
                edits.put(origin.offset(offset.getX(), offset.getY(), offset.getZ()), state));
        return edits;
    }

    /**
     * Clears head-room over a footprint and, when {@code support} is non-null, fills it beneath —
     * leaving the site ready for a structure to be stamped on top. Called by
     * {@link BuildingTemplates#place} BEFORE the template goes in; running it afterwards would
     * clear the building away again.
     */
    public static void prepareSite(ServerLevel level, BlockPos origin, int radiusX, int radiusZ,
            int headroom, @Nullable BlockState support) {
        planSitePrep(origin, pos -> level.getBlockState(pos).isSolidRender(),
                radiusX, radiusZ, headroom, MAX_SUPPORT_DEPTH, support)
                .forEach((pos, state) -> level.setBlock(pos, state, Block.UPDATE_ALL));
    }

    /** Places a layout, filling support beneath the footprint with smooth quartz. */
    public static void place(ServerLevel level, BlockPos origin, Map<BlockPos, BlockState> layout) {
        place(level, origin, layout, Blocks.SMOOTH_QUARTZ.defaultBlockState());
    }

    /**
     * Places a layout into the world, clearing head-room and filling support beneath the footprint
     * with {@code support} — Zerg Hives pass mycelium so the mound sits on a mycelium base rather
     * than the Protoss quartz.
     */
    public static void place(ServerLevel level, BlockPos origin, Map<BlockPos, BlockState> layout, BlockState support) {
        planPlacement(origin, layout, pos -> level.getBlockState(pos).isSolidRender(), MAX_SUPPORT_DEPTH, support)
                .forEach((pos, state) -> level.setBlock(pos, state, Block.UPDATE_ALL));
    }
}
