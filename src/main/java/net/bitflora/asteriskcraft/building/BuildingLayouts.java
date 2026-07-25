package net.bitflora.asteriskcraft.building;

import net.bitflora.asteriskcraft.AsteriskCraft;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Predicate;

/**
 * Code-defined multiblock layouts (relative position -> state). Used by the world
 * bootstrap now and by V2's warp-in kits later. Origin is the block the structure
 * is anchored on; the interactive core block sits above the center.
 */
public final class BuildingLayouts {
    private BuildingLayouts() {
    }

    /** 3x3 quartz pedestal, purpur pillar corners capped with end rods, gold pedestal, Nexus core on top. */
    public static Map<BlockPos, BlockState> nexus() {
        Map<BlockPos, BlockState> layout = new LinkedHashMap<>();
        BlockState platform = Blocks.SMOOTH_QUARTZ.defaultBlockState();
        BlockState corner = Blocks.PURPUR_BLOCK.defaultBlockState();
        BlockState pillar = Blocks.PURPUR_PILLAR.defaultBlockState();

        // 3x3 quartz pedestal — the wider 5x5 platform that used to surround it is gone.
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                layout.put(new BlockPos(dx, 0, dz), platform);
            }
        }
        // Four purpur corner pillars at the 5x5 corners (step by 4), capped with end rods.
        for (int dx = -2; dx <= 2; dx += 4) {
            for (int dz = -2; dz <= 2; dz += 4) {
                layout.put(new BlockPos(dx, 0, dz), corner);
                layout.put(new BlockPos(dx, 1, dz), pillar);
                layout.put(new BlockPos(dx, 2, dz), Blocks.END_ROD.defaultBlockState());
            }
        }
        layout.put(new BlockPos(0, 1, 0), Blocks.GOLD_BLOCK.defaultBlockState());
        layout.put(new BlockPos(0, 2, 0), AsteriskCraft.NEXUS_CORE.get().defaultBlockState());
        return layout;
    }

    /** Relative offset of the Nexus's interactive core block within {@link #nexus()}. */
    public static final BlockPos NEXUS_CORE_OFFSET = new BlockPos(0, 2, 0);

    /** 3x3 cobblestone platform with two purpur pillars framing the Gateway core arch. */
    public static Map<BlockPos, BlockState> gateway() {
        Map<BlockPos, BlockState> layout = new LinkedHashMap<>();
        BlockState platform = Blocks.COBBLESTONE.defaultBlockState();
        BlockState pillar = Blocks.PURPUR_PILLAR.defaultBlockState();

        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                layout.put(new BlockPos(dx, 0, dz), platform);
            }
        }
        layout.put(new BlockPos(-1, 1, 0), pillar);
        layout.put(new BlockPos(-1, 2, 0), pillar);
        layout.put(new BlockPos(1, 1, 0), pillar);
        layout.put(new BlockPos(1, 2, 0), pillar);
        layout.put(new BlockPos(0, 1, 0), AsteriskCraft.GATEWAY_CORE.get().defaultBlockState());
        return layout;
    }

    /** Relative offset of the Gateway's interactive core block within {@link #gateway()}. */
    public static final BlockPos GATEWAY_CORE_OFFSET = new BlockPos(0, 1, 0);

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

    /** Bounds the support fill so a deep or void column can't carve a quartz shaft down to bedrock. */
    private static final int MAX_SUPPORT_DEPTH = 6;

    /**
     * Computes every block edit {@link #place} will apply, without touching the world, so the
     * geometry (head-room clear, gap-filling support, layout stamp) is unit-testable. {@code isSolid}
     * reports whether the EXISTING world block at a position is solid-render; support fill runs from
     * just under the platform down to the first solid block in each column (fluids and air are not
     * solid, so they get filled) with {@code support}, which keeps the platform on solid footing even
     * when it was raised onto the highest ground in a dip. Edits are inserted air -> support -> layout
     * so the layout wins on any shared position when applied in order.
     */
    static Map<BlockPos, BlockState> planPlacement(BlockPos origin, Map<BlockPos, BlockState> layout,
            Predicate<BlockPos> isSolid, int maxSupportDepth, BlockState support) {
        Map<BlockPos, BlockState> edits = new LinkedHashMap<>();
        BlockState air = Blocks.AIR.defaultBlockState();
        for (int dx = -3; dx <= 3; dx++) {
            for (int dz = -3; dz <= 3; dz++) {
                for (int dy = 1; dy <= 5; dy++) {
                    edits.put(origin.offset(dx, dy, dz), air);
                }
                for (int dy = -1; dy >= -maxSupportDepth; dy--) {
                    BlockPos pos = origin.offset(dx, dy, dz);
                    if (isSolid.test(pos)) {
                        break; // reached solid ground; stop filling this column
                    }
                    edits.put(pos, support);
                }
            }
        }
        layout.forEach((offset, state) ->
                edits.put(origin.offset(offset.getX(), offset.getY(), offset.getZ()), state));
        return edits;
    }

    /** Places a layout, filling support beneath the footprint with smooth quartz (the Protoss look). */
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
