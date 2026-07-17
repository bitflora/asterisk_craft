package net.bitflora.asteriskcraft.building;

import com.mojang.serialization.MapCodec;
import net.bitflora.asteriskcraft.AsteriskCraft;
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
 * The Zerg Hive core block. Unlike the Nexus/Gateway it has no player GUI — the Hive is
 * AI-operated (see {@link HiveBlockEntity}). Destroying it removes a Zerg base and, when it's
 * the last Hive, wins the match; that outcome fires from {@link HiveBlockEntity#preRemoveSideEffects}.
 */
public class HiveBlock extends BaseEntityBlock {
    public static final MapCodec<HiveBlock> CODEC = simpleCodec(HiveBlock::new);

    public HiveBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<HiveBlock> codec() {
        return CODEC;
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new HiveBlockEntity(pos, state);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        // No production GUI — the Hive runs itself. Consume the interaction so it isn't a no-op click.
        return InteractionResult.SUCCESS;
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide()) {
            return null;
        }
        return createTickerHelper(type, AsteriskCraft.HIVE_BLOCK_ENTITY.get(), HiveBlockEntity::serverTick);
    }
}
