package com.timja.starmine.building;

import com.timja.starmine.StarMine;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * Remembers which block stood here and puts it back once the regen cooldown expires.
 */
public class DepletedNodeBlockEntity extends BlockEntity {
    public static final int REGEN_TICKS = 600; // 30 seconds

    private BlockState originalState = Blocks.STONE.defaultBlockState();
    private int ticksRemaining = REGEN_TICKS;

    public DepletedNodeBlockEntity(BlockPos pos, BlockState state) {
        super(StarMine.DEPLETED_NODE_BLOCK_ENTITY.get(), pos, state);
    }

    public void setOriginalState(BlockState state) {
        this.originalState = state;
        this.ticksRemaining = REGEN_TICKS;
        this.setChanged();
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, DepletedNodeBlockEntity node) {
        if (--node.ticksRemaining <= 0) {
            level.setBlock(pos, node.originalState, Block.UPDATE_ALL);
        } else {
            node.setChanged();
        }
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.store("Original", BlockState.CODEC, this.originalState);
        output.putInt("Ticks", this.ticksRemaining);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        this.originalState = input.read("Original", BlockState.CODEC).orElse(Blocks.STONE.defaultBlockState());
        this.ticksRemaining = input.getIntOr("Ticks", REGEN_TICKS);
    }
}
