package net.bitflora.asteriskcraft.building;

import com.mojang.serialization.MapCodec;
import net.bitflora.asteriskcraft.AsteriskCraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import org.jetbrains.annotations.Nullable;

/**
 * The Pylon core block. No GUI — a Pylon produces nothing; it exists to power the ground around it,
 * which {@link PsiField} reads off {@link #ONLINE}.
 *
 * <p>That flag is a <b>blockstate property rather than a field on {@link PylonBlockEntity}</b>, and
 * deliberately so: the client-side placement outline has to give the same answer as the server's
 * refusal, and a blockstate is synced to every client that has the chunk for free. A block entity
 * field would need an update packet, which nothing else in this mod does.
 */
public class PylonBlock extends BaseEntityBlock {
    public static final MapCodec<PylonBlock> CODEC = simpleCodec(PylonBlock::new);

    /** Whether this Pylon has finished warping in and is powering the ground around it. */
    public static final BooleanProperty ONLINE = BooleanProperty.create("online");

    public PylonBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(ONLINE, false));
    }

    @Override
    protected MapCodec<PylonBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(ONLINE);
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new PylonBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide()) {
            return null;
        }
        return createTickerHelper(type, AsteriskCraft.PYLON_BLOCK_ENTITY.get(), PylonBlockEntity::serverTick);
    }
}
