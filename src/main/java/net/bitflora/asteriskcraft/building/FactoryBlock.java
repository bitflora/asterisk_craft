package net.bitflora.asteriskcraft.building;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.bitflora.asteriskcraft.AsteriskCraft;
import net.bitflora.asteriskcraft.command.ControlledFaction;
import net.bitflora.asteriskcraft.faction.Race;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

import java.util.function.Supplier;

/**
 * The core block of a <b>unit factory</b>: a {@link StructureBlock} that also holds a command card.
 * The Barracks is the first, and adding a second (a Stargate, a Spawning Pool that morphs) is a
 * card in {@link ProductionKind} plus this block registered with it — no new class.
 *
 * <p>Right-clicking opens that card when, and only when, the player <em>commands</em> the building's
 * army, which is {@link BaseBlock}'s rule verbatim and for the same reasons: ownership decides the
 * interaction, so a player cannot poke at the computer's Barracks, and the click is still consumed
 * when there is nothing to open so the building reads as solid rather than as a no-op.
 *
 * <p>The card is a {@link Supplier} because blocks are registered before {@code ProductionKind}'s
 * constants — which name blocks themselves — can be resolved.
 */
public class FactoryBlock extends StructureBlock {

    public static final MapCodec<FactoryBlock> CODEC = RecordCodecBuilder.mapCodec(inst -> inst.group(
            Race.CODEC.fieldOf("race").forGetter(block -> block.defence().race()),
            Codec.INT.fieldOf("health").forGetter(block -> block.defence().health()),
            Codec.INT.fieldOf("shield").forGetter(block -> block.defence().shield()),
            Codec.INT.fieldOf("warp_ticks").forGetter(block -> block.defence().warpTicks()),
            ProductionKind.CODEC.fieldOf("production").forGetter(FactoryBlock::production),
            propertiesCodec()
    ).apply(inst, (race, health, shield, warpTicks, production, properties) ->
            new FactoryBlock(new Defence(race, health, shield, warpTicks), () -> production, properties)));

    private final Supplier<ProductionKind> production;

    public FactoryBlock(Defence defence, Supplier<ProductionKind> production, Properties properties) {
        super(defence, properties);
        this.production = production;
    }

    /** The command card this factory opens. */
    public ProductionKind production() {
        return this.production.get();
    }

    @Override
    protected MapCodec<FactoryBlock> codec() {
        return CODEC;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new FactoryBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state,
            BlockEntityType<T> type) {
        if (level.isClientSide()) {
            return null;
        }
        return createTickerHelper(type, AsteriskCraft.FACTORY_BLOCK_ENTITY.get(),
                FactoryBlockEntity::serverTick);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
            BlockHitResult hit) {
        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        if (level.getBlockEntity(pos) instanceof FactoryBlockEntity factory
                && ControlledFaction.of(player) == factory.buildingFaction()) {
            ProductionMenu.open(player, factory);
        }
        // Consumed either way: a building the player doesn't command is still a solid building.
        return InteractionResult.SUCCESS;
    }
}
