package com.timja.starmine.building;

import com.mojang.serialization.MapCodec;
import com.timja.starmine.StarMine;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

/**
 * The Nexus core block. Right-click to queue a Probe; destroying it loses the game
 * (defeat wiring lands in V3, for now it just reports).
 */
public class NexusBlock extends BaseEntityBlock {
    public static final MapCodec<NexusBlock> CODEC = simpleCodec(NexusBlock::new);

    public NexusBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<NexusBlock> codec() {
        return CODEC;
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new NexusBlockEntity(pos, state);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        if (!level.isClientSide() && level.getBlockEntity(pos) instanceof NexusBlockEntity nexus) {
            nexus.tryQueueProbe(player);
        }
        return InteractionResult.SUCCESS;
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide()) {
            return null;
        }
        return createTickerHelper(type, StarMine.NEXUS_BLOCK_ENTITY.get(), NexusBlockEntity::serverTick);
    }
}
