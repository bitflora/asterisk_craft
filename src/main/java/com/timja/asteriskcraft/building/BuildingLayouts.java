package com.timja.asteriskcraft.building;

import com.timja.asteriskcraft.AsteriskCraft;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Code-defined multiblock layouts (relative position -> state). Used by the world
 * bootstrap now and by V2's warp-in kits later. Origin is the block the structure
 * is anchored on; the interactive core block sits above the center.
 */
public final class BuildingLayouts {
    private BuildingLayouts() {
    }

    /** 5x5 quartz platform, purpur pillar corners, gold pedestal, Nexus core on top. */
    public static Map<BlockPos, BlockState> nexus() {
        Map<BlockPos, BlockState> layout = new LinkedHashMap<>();
        BlockState platform = Blocks.SMOOTH_QUARTZ.defaultBlockState();
        BlockState corner = Blocks.PURPUR_BLOCK.defaultBlockState();
        BlockState pillar = Blocks.PURPUR_PILLAR.defaultBlockState();

        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                boolean isCorner = Math.abs(dx) == 2 && Math.abs(dz) == 2;
                layout.put(new BlockPos(dx, 0, dz), isCorner ? corner : platform);
                if (isCorner) {
                    layout.put(new BlockPos(dx, 1, dz), pillar);
                    layout.put(new BlockPos(dx, 2, dz), Blocks.END_ROD.defaultBlockState());
                }
            }
        }
        layout.put(new BlockPos(0, 1, 0), Blocks.GOLD_BLOCK.defaultBlockState());
        layout.put(new BlockPos(0, 2, 0), AsteriskCraft.NEXUS_CORE.get().defaultBlockState());
        return layout;
    }

    /** Places a layout into the world, clearing head-room above the footprint first. */
    public static void place(ServerLevel level, BlockPos origin, Map<BlockPos, BlockState> layout) {
        for (int dx = -3; dx <= 3; dx++) {
            for (int dz = -3; dz <= 3; dz++) {
                for (int dy = 1; dy <= 5; dy++) {
                    level.setBlock(origin.offset(dx, dy, dz), Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
                }
                // Solid footing under the platform so it never floats over a dip.
                for (int dy = -2; dy <= -1; dy++) {
                    BlockPos support = origin.offset(dx, dy, dz);
                    if (!level.getBlockState(support).isSolidRender()) {
                        level.setBlock(support, Blocks.SMOOTH_QUARTZ.defaultBlockState(), Block.UPDATE_ALL);
                    }
                }
            }
        }
        layout.forEach((offset, state) -> level.setBlock(origin.offset(offset.getX(), offset.getY(), offset.getZ()), state, Block.UPDATE_ALL));
    }
}
