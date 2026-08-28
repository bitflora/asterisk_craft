package net.bitflora.asteriskcraft.building;

import net.bitflora.asteriskcraft.AsteriskCraft;
import net.bitflora.asteriskcraft.faction.Faction;
import net.bitflora.asteriskcraft.faction.Race;
import net.bitflora.asteriskcraft.game.MatchSetup;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jetbrains.annotations.Nullable;

/**
 * The block entity behind every plain {@link StructureBlock}: it runs the building's build time and
 * holds what it takes to knock the building down, and nothing else. The Pylon's shape minus the one
 * thing that made a Pylon special (its {@code ONLINE} blockstate), which is why this is a table
 * entry rather than a fifth copy of that class.
 *
 * <p>Its numbers come off the block it was placed as, so the buildings that share this class
 * differ only in what {@code AsteriskCraft} registered them with. A {@link SiegeTarget} but
 * deliberately not a {@link FactionCore}: an enemy army can raze one, which costs the player what it
 * was going to produce, but the match is decided by bases alone.
 *
 * <p>The build time is the ordinary warp-in countdown, so everything already built around one comes
 * for free — the glass {@link WarpScaffold} filling in over it, the halved pools of
 * {@link WarpInVulnerability}, and (for the races that require one) the worker who has to arrive and
 * stay for the {@link ConstructionSite} to make progress at all.
 */
public class StructureBlockEntity extends BlockEntity
        implements WarpInBuilding, SiegeTarget {

    private final StructureBlock.Defence numbers;
    private final BuildingDefense defense;
    private final UnderAttackAlert alert = new UnderAttackAlert();
    /**
     * Which side owns this building. Null until set at placement or resolved on first use — one
     * placed by hand (creative, {@code /setblock}) belongs to whichever side is playing its race,
     * which {@code MatchSetup.sidePlaying} answers even in a mirror.
     */
    private @Nullable Faction faction;
    /**
     * Whether this building has been entered in {@link TechCensus}. Not persisted, so a reload
     * re-enrols from the tick — which is exactly what back-fills a world saved before the census
     * existed. Mirrors {@code BaseBlockEntity}'s own enrolment flag.
     */
    private boolean enrolled;

    public StructureBlockEntity(BlockPos pos, BlockState state) {
        this(AsteriskCraft.STRUCTURE_BLOCK_ENTITY.get(), pos, state);
    }

    /**
     * For a structure that is more than one — {@link FactoryBlockEntity} produces units on top of
     * everything here and so registers under its own type, but is a structure in every other
     * respect.
     */
    protected StructureBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
        this.numbers = numbersOf(state);
        this.defense = new BuildingDefense(this.numbers.health(), this.numbers.shield(),
                this.numbers.warpTicks());
    }

    /** Whose building this is — what a subclass needs to find its army's bank and roster. */
    public Race race() {
        return this.numbers.race();
    }

    /** The numbers a structure block declares, defaulting defensively for a state that isn't one. */
    private static StructureBlock.Defence numbersOf(BlockState state) {
        return state.getBlock() instanceof StructureBlock structure
                ? structure.defence()
                : new StructureBlock.Defence(Race.PROTOSS, 1, 0, 0);
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state,
            StructureBlockEntity structure) {
        if (structure.defense.tickWarpIn(level, pos)) {
            structure.setChanged();
        }
        // Enrolled only once the countdown has run out, which is what makes "this army owns one"
        // mean a finished one: nothing at the query end has to ask about warp state.
        if (!structure.enrolled && !structure.defense.isWarping()) {
            TechCensus.ensureRegistered(level, structure.buildingFaction(),
                    BuiltInRegistries.BLOCK.getKey(state.getBlock()), pos);
            structure.enrolled = true;
        }
    }

    @Override
    public void setFaction(Faction faction) {
        this.faction = faction;
        this.setChanged();
    }

    // --- SiegeTarget ---

    @Override
    public Faction buildingFaction() {
        if (this.level == null) {
            // Asked before the block entity has a level (a freshly constructed one being loaded):
            // answer, but don't cache it, or the building would be stuck belonging to nobody.
            return this.faction == null ? Faction.NEUTRAL : this.faction;
        }
        if (this.faction == null) {
            this.faction = MatchSetup.of(this.level).sidePlaying(this.numbers.race());
        }
        return this.faction;
    }

    @Override
    public BuildingDefense defense() {
        return this.defense;
    }

    @Override
    public void damageBuilding(int amount, ServerLevel level, BlockPos pos) {
        this.defense.damage(amount, level, pos);
        this.setChanged();
        // Named from the block rather than from a per-building key: every plain structure shares
        // this class, and the only thing that differs between their alerts is which is being hit.
        this.alert.ping(level, buildingFaction(), Component.translatable(
                "message.asteriskcraft.structure.under_attack", getBlockState().getBlock().getName()));
    }

    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        super.preRemoveSideEffects(pos, state);
        if (this.level != null) {
            this.defense.collapseScaffold(this.level, pos);
            TechCensus.unregister(this.level, pos);
        }
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        this.defense.save(output);
        if (this.faction != null) {
            output.store("Faction", Faction.CODEC, this.faction);
        }
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        this.defense.load(input);
        this.faction = input.read("Faction", Faction.CODEC).orElse(null);
    }
}
