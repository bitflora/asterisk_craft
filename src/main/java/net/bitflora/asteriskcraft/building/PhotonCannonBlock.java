package net.bitflora.asteriskcraft.building;

import com.mojang.serialization.MapCodec;
import net.bitflora.asteriskcraft.AsteriskCraft;
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
 * The Photon Cannon core block. Unlike the Gateway it has no GUI — it's a purely
 * automatic defensive structure whose block entity ticker warps it in and then
 * attacks enemy-faction units in range.
 */
public class PhotonCannonBlock extends BaseEntityBlock {
    public static final MapCodec<PhotonCannonBlock> CODEC = simpleCodec(PhotonCannonBlock::new);

    public PhotonCannonBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<PhotonCannonBlock> codec() {
        return CODEC;
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new PhotonCannonBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide()) {
            return null;
        }
        return createTickerHelper(type, AsteriskCraft.PHOTON_CANNON_BLOCK_ENTITY.get(), PhotonCannonBlockEntity::serverTick);
    }
}
