package net.bitflora.asteriskcraft.building;

import net.bitflora.asteriskcraft.AsteriskCraft;
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
