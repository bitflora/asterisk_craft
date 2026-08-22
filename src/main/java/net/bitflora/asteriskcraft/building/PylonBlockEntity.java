package net.bitflora.asteriskcraft.building;

import net.bitflora.asteriskcraft.AsteriskCraft;
import net.bitflora.asteriskcraft.faction.Faction;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * The Pylon: the cheapest Protoss building and the one every other Protoss building needs nearby.
 * It produces nothing and holds no inventory — its whole job is to be standing, so that
 * {@link PsiField} answers yes within {@link PsiField#RADIUS} blocks of it.
 *
 * <p>A {@link SiegeTarget} but deliberately not a {@link FactionCore}: an enemy army can raze one,
 * which costs the player the ground it was powering, but the match is decided by cores alone.
 *
 * <p>{@link #serverTick} reflects the warp-in countdown onto {@link PylonBlock#ONLINE} every tick
 * rather than flipping it once when the warp ends. That is what makes the flag self-healing: a
 * structure template carries the blockstate <em>and</em> the block-entity NBT captured in the design
 * world, so a stamped Pylon can arrive claiming it is online with a spent countdown (see
 * docs/neoforge-api-notes.md). Driving the state off the live {@link BuildingDefense} covers kit
 * placement, a hand-placed core and a reload alike.
 */
public class PylonBlockEntity extends BlockEntity implements WarpInBuilding, SiegeTarget {
    /** Half a minute — cheaper and quicker than a Gateway, since the rest of the base waits on it. */
    public static final int WARP_TICKS = 20 * 30;
    /** Softer than a Gateway: a Pylon is the cheap thing an attacker goes for first. */
    public static final int MAX_HEALTH = 150;
    public static final int SHIELD = 150;

    private final BuildingDefense defense = new BuildingDefense(MAX_HEALTH, SHIELD, WARP_TICKS);
    private final UnderAttackAlert alert = new UnderAttackAlert();
    private Faction faction = Faction.PROTOSS;

    public PylonBlockEntity(BlockPos pos, BlockState state) {
        super(AsteriskCraft.PYLON_BLOCK_ENTITY.get(), pos, state);
    }

    @Override
    public void setFaction(Faction faction) {
        this.faction = faction;
        this.setChanged();
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, PylonBlockEntity pylon) {
        boolean warping = pylon.defense.tickWarpIn(level, pos);
        boolean online = !warping;
        if (state.getValue(PylonBlock.ONLINE) != online) {
            level.setBlock(pos, state.setValue(PylonBlock.ONLINE, online), Block.UPDATE_ALL);
        }
        if (warping) {
            pylon.setChanged();
        }
    }

    // --- SiegeTarget ---

    @Override
    public Faction buildingFaction() {
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
        this.alert.ping(level, buildingFaction(), "message.asteriskcraft.pylon.under_attack");
    }

    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        super.preRemoveSideEffects(pos, state);
        if (this.level != null) {
            this.defense.collapseScaffold(this.level);
        }
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        this.defense.save(output);
        output.store("Faction", Faction.CODEC, this.faction);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        this.defense.load(input);
        this.faction = input.read("Faction", Faction.CODEC).orElse(Faction.PROTOSS);
    }
}
