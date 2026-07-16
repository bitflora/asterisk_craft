package com.timja.asteriskcraft.building;

import com.mojang.serialization.MapCodec;
import com.timja.asteriskcraft.AsteriskCraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * Placeholder left behind when a Probe/Drone harvests a resource block.
 * Restores the original block after a cooldown, making harvesting
 * non-destructive to the terrain.
 */
public class DepletedNodeBlock extends BaseEntityBlock {
    public static final MapCodec<DepletedNodeBlock> CODEC = simpleCodec(DepletedNodeBlock::new);

    public DepletedNodeBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<DepletedNodeBlock> codec() {
        return CODEC;
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new DepletedNodeBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide()) {
            return null;
        }
        return createTickerHelper(type, AsteriskCraft.DEPLETED_NODE_BLOCK_ENTITY.get(), DepletedNodeBlockEntity::serverTick);
    }
}
