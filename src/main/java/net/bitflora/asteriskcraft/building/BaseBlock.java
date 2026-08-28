package net.bitflora.asteriskcraft.building;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.bitflora.asteriskcraft.AsteriskCraft;
import net.bitflora.asteriskcraft.command.ControlledFaction;
import net.bitflora.asteriskcraft.faction.Faction;
import net.bitflora.asteriskcraft.faction.Race;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

/**
 * A base's core block — a Nexus core, a Hive core. One class registered once per race, since the
 * only thing that differs between them is which {@link Race} they belong to and the model and
 * template hung off that; all the behaviour is in {@link BaseBlockEntity}.
 *
 * <p>Right-clicking opens the production GUI when, and only when, the player <em>commands</em> this
 * base's army and that army's race has a command card. Ownership decides the interaction, not the
 * race: that is what lets a human-played Hive open a card the moment its race is given one, and
 * stops a player poking at the computer's base. When there is nothing to open the click is still
 * consumed, so it reads as a solid building rather than a no-op.
 *
 * <p>Destroying a base's core removes it from its army and, when it is that army's last, decides
 * the match — fired from {@link BaseBlockEntity#preRemoveSideEffects}.
 */
public class BaseBlock extends BaseEntityBlock {
    /**
     * Which side owns this base, mirrored out of {@link BaseBlockEntity} onto the blockstate — the
     * free server-to-client sync channel for a block-entity fact (the same trick
     * {@code PylonBlock.ONLINE} uses; see docs/neoforge-api-notes.md). The client has to agree with
     * the server about ownership because the beacon beam over a base is coloured from it, and in a
     * mirror match that beam is the <em>only</em> thing that says whose base you are looking at
     * (see {@code entity.TeamColors}). Nothing in the mod sends a block-entity update packet, so
     * without this the client answered from {@code MatchSetup}'s hardcoded default and painted
     * every base of the default player race blue — both armies' included.
     */
    public static final EnumProperty<Faction> FACTION = EnumProperty.create("faction", Faction.class);

    public static final MapCodec<BaseBlock> CODEC = RecordCodecBuilder.mapCodec(inst -> inst.group(
            Race.CODEC.fieldOf("race").forGetter(BaseBlock::race),
            propertiesCodec()
    ).apply(inst, BaseBlock::new));

    private final Race race;

    public BaseBlock(Race race, Properties properties) {
        super(properties);
        this.race = race;
        this.registerDefaultState(this.stateDefinition.any().setValue(FACTION, Faction.NEUTRAL));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACTION);
    }

    /** Which race's base this block is — the one thing {@link BaseBlockEntity} needs from it. */
    public Race race() {
        return this.race;
    }

    @Override
    protected MapCodec<BaseBlock> codec() {
        return CODEC;
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new BaseBlockEntity(pos, state);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        if (!level.isClientSide() && level.getBlockEntity(pos) instanceof BaseBlockEntity base
                && base.hasProduction()
                && ControlledFaction.of(player) == base.buildingFaction()) {
            ProductionMenu.open(player, base);
        }
        return InteractionResult.SUCCESS;
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide()) {
            return null;
        }
        return createTickerHelper(type, AsteriskCraft.BASE_BLOCK_ENTITY.get(), BaseBlockEntity::serverTick);
    }
}
