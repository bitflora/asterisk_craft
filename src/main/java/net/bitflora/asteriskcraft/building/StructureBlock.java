package net.bitflora.asteriskcraft.building;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.bitflora.asteriskcraft.AsteriskCraft;
import net.bitflora.asteriskcraft.faction.Race;
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
 * The core block of a plain <b>structure</b>: a building that is stamped from a template, spends a
 * build time going up behind its scaffold, and can then be battered down — and does nothing else.
 * The Barracks, the Stargate, the Spawning Pool and the Spire are all this block registered four
 * times, the way {@link BaseBlock} is one block registered once per race.
 *
 * <p>One class rather than four because nothing separates them but their numbers. A structure that
 * later gains a command card graduates to its own block and {@link ProductionBuilding} block entity
 * (the Gateway's shape) — that is a building acquiring behaviour, not this table growing a case for
 * it.
 *
 * <p>The three numbers and the owning race come from the registration site, where the buildings are
 * named anyway, and reach {@link StructureBlockEntity} through {@link #defence()}. The race is here
 * for the same reason {@link BaseBlock} carries one: a building placed by hand belongs to whichever
 * side is playing that race, which is the only answer that survives a human picking it.
 */
public class StructureBlock extends BaseEntityBlock {

    /**
     * What a structure is worth in a fight, and how long it takes to stand up.
     *
     * @param race       whose building this is, for resolving an owner that was never set
     * @param health     siege HP once built
     * @param shield     shield buffer once built; zero for a race without shields
     * @param warpTicks  the build time — ticks between the kit going down and the building standing
     */
    public record Defence(Race race, int health, int shield, int warpTicks) {
    }

    public static final MapCodec<StructureBlock> CODEC = RecordCodecBuilder.mapCodec(inst -> inst.group(
            Race.CODEC.fieldOf("race").forGetter(block -> block.defence.race()),
            Codec.INT.fieldOf("health").forGetter(block -> block.defence.health()),
            Codec.INT.fieldOf("shield").forGetter(block -> block.defence.shield()),
            Codec.INT.fieldOf("warp_ticks").forGetter(block -> block.defence.warpTicks()),
            propertiesCodec()
    ).apply(inst, (race, health, shield, warpTicks, properties) ->
            new StructureBlock(new Defence(race, health, shield, warpTicks), properties)));

    private final Defence defence;

    public StructureBlock(Defence defence, Properties properties) {
        super(properties);
        this.defence = defence;
    }

    /** This building's staying power and build time — the only thing its block entity needs here. */
    public Defence defence() {
        return this.defence;
    }

    @Override
    protected MapCodec<? extends StructureBlock> codec() {
        return CODEC;
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new StructureBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state,
            BlockEntityType<T> type) {
        if (level.isClientSide()) {
            return null;
        }
        return createTickerHelper(type, AsteriskCraft.STRUCTURE_BLOCK_ENTITY.get(),
                StructureBlockEntity::serverTick);
    }
}
