package net.bitflora.asteriskcraft.building;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.util.Util;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

import java.util.ArrayList;
import java.util.List;

/**
 * The half-real structure a building stands as while it warps in: every block of its layout except
 * the core is held as warp glass, and the real stonework materialises pane by pane over the warp
 * countdown, so a building visibly builds itself rather than blinking into existence finished.
 *
 * <p>Panes go in a scrambled order, so the layout speckles into place from all over rather than
 * rising in tidy layers, and they are paced against the time left rather than against a fixed
 * schedule — the next one lands after {@link #paneInterval} ticks, recomputed each time — which is
 * what keeps the fill in step with a countdown that can grow.
 *
 * <p>Smashing a pane costs the warp twice. It adds {@value #BREAK_PENALTY_TICKS} ticks (ten seconds)
 * to the countdown, and it books {@value #SMASHED_PANE_DAMAGE} points of siege damage that land the
 * moment the building stands up — deliberately not while it is warping, where the halved pools would
 * double them (see {@link WarpInVulnerability}). A smashed pane is left as the hole the attacker made
 * and keeps its slot in the fill order, so its real block arrives exactly when it would have anyway;
 * marking it is what stops the same square being charged for on every sweep, and bounds what an
 * attacker can wring out of a warp by its pane count.
 *
 * <p>Only kit-warped buildings raise a scaffold. A pre-placed one (a Hive, the starting Nexus) has no
 * warp phase to fill in, and its {@link BuildingDefense} simply carries the empty scaffold every
 * building has.
 */
public final class WarpScaffold {
    /**
     * What an unfinished block of the layout looks like for a race that names nothing else — Protoss
     * blue, and cheap to smash. Which block a given scaffold actually stands as is per race
     * ({@code race.RaceProfile.scaffold}), handed in at {@link #raise} time: a Hive growing itself
     * out of Protoss warp glass reads as the wrong army's building.
     */
    public static final BlockState PANE = Blocks.LIGHT_BLUE_STAINED_GLASS.defaultBlockState();

    /** What one smashed pane adds to the warp countdown. */
    public static final int BREAK_PENALTY_TICKS = 200; // 10 seconds

    /** What one smashed pane costs the finished building in siege damage, collected on completion. */
    public static final int SMASHED_PANE_DAMAGE = 20;

    /** How often the standing panes are swept for smashed ones. */
    private static final int SCAN_INTERVAL_TICKS = 5;

    /**
     * Swapping a pane for its finished block must not run neighbour or shape updates: the states are
     * the template's own, and letting stairs re-derive their shape against half-built surroundings
     * would quietly rewrite the building as it goes up.
     */
    private static final int SWAP_FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;

    /**
     * One block of the layout: where it goes, what it becomes, and whether an attacker has already
     * knocked it out. A smashed pane stays in the list — its slot in the fill order is what says when
     * its real block arrives — and the flag is only there so it isn't charged for twice.
     */
    public record Pane(BlockPos pos, BlockState finished, boolean smashed) {
        public static final Codec<Pane> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                BlockPos.CODEC.fieldOf("pos").forGetter(Pane::pos),
                BlockState.CODEC.fieldOf("state").forGetter(Pane::finished),
                Codec.BOOL.optionalFieldOf("smashed", false).forGetter(Pane::smashed)
        ).apply(instance, Pane::new));

        public static final Codec<List<Pane>> LIST_CODEC = CODEC.listOf();

        public Pane(BlockPos pos, BlockState finished) {
            this(pos, finished, false);
        }

        public Pane smash() {
            return new Pane(this.pos, this.finished, true);
        }
    }

    /** Panes not yet materialised, in the order they will be; emptied as the warp runs. */
    private final List<Pane> pending = new ArrayList<>();
    /**
     * What this scaffold's unfinished blocks stand as. Saved, because {@link #collectSmashed} tests
     * against it on every sweep and a warp routinely outlives a reload.
     */
    private BlockState pane = PANE;
    /** Running count of panes smashed this warp, owed as siege damage once the building stands up. */
    private int smashedPanes;
    private int ticksToNextPane;
    private int ticksToNextScan;

    /**
     * Freezes a just-stamped structure into glass. Every non-air block of the template's box becomes
     * a pane except the core itself, which has to stay real from the first tick — it is the block
     * entity running the countdown.
     */
    public void raise(ServerLevel level, BuildingTemplates.Placed placed, BlockState pane) {
        this.pending.clear();
        this.pane = pane;
        this.smashedPanes = 0;
        this.ticksToNextPane = 0;
        this.ticksToNextScan = 0;
        Vec3i size = placed.size();
        for (int dx = 0; dx < size.getX(); dx++) {
            for (int dy = 0; dy < size.getY(); dy++) {
                for (int dz = 0; dz < size.getZ(); dz++) {
                    BlockPos pos = placed.min().offset(dx, dy, dz);
                    BlockState state = level.getBlockState(pos);
                    if (state.isAir() || pos.equals(placed.core())) {
                        continue;
                    }
                    this.pending.add(new Pane(pos, state));
                }
            }
        }
        scramble(this.pending, level.getRandom());
        for (Pane slot : this.pending) {
            level.setBlock(slot.pos(), this.pane, SWAP_FLAGS);
        }
    }

    /**
     * Shuffles the layout into the order it fills in, so a building speckles into place from all over
     * rather than rising in tidy layers. Rolled once, at raise time: the order is saved as a list and
     * has to come back off disk unchanged, so it can't be re-derived per tick.
     */
    static void scramble(List<Pane> panes, RandomSource random) {
        Util.shuffle(panes, random);
    }

    /**
     * Advances the fill-in by one tick, given how much of the warp is left to spread the remaining
     * panes over. Returns the extra warp ticks earned by panes smashed since the last sweep, which
     * the caller adds to the countdown.
     */
    public int tick(ServerLevel level, int warpTicksRemaining) {
        if (this.pending.isEmpty()) {
            return 0;
        }
        int penalty = 0;
        if (--this.ticksToNextScan <= 0) {
            this.ticksToNextScan = SCAN_INTERVAL_TICKS;
            penalty = this.collectSmashed(level);
        }
        if (this.pending.isEmpty()) {
            return penalty;
        }
        if (this.ticksToNextPane <= 0) {
            this.ticksToNextPane = paneInterval(warpTicksRemaining + penalty, this.pending.size());
        }
        if (--this.ticksToNextPane <= 0) {
            materialize(level, this.pending.removeFirst());
        }
        return penalty;
    }

    /** Ticks to wait before the next pane, so what's left spreads evenly over what's left of the warp. */
    static int paneInterval(int warpTicksRemaining, int panesRemaining) {
        if (panesRemaining <= 0) {
            return Integer.MAX_VALUE;
        }
        return Math.max(1, warpTicksRemaining / panesRemaining);
    }

    /** Stands the rest of the layout up at once — the warp finished, so nothing may still be glass. */
    public void finish(ServerLevel level) {
        for (Pane pane : this.pending) {
            materialize(level, pane);
        }
        this.pending.clear();
    }

    /**
     * Clears away what never materialised, for a building razed while it was still warping in. The
     * stonework it had already grown stays standing as the ruin; leaving the rest as glass would
     * read as a warp still in progress long after the core that was running it is gone.
     */
    public void collapse(ServerLevel level) {
        for (Pane slot : this.pending) {
            if (level.getBlockState(slot.pos()).is(this.pane.getBlock())) {
                level.setBlock(slot.pos(), Blocks.AIR.defaultBlockState(), SWAP_FLAGS);
            }
        }
        this.pending.clear();
    }

    /**
     * Books every pane that is no longer glass (mined, blown up, or battered by an attacker) and
     * returns what they add to the countdown. The hole is left standing — the pane keeps its slot, so
     * its real block turns up exactly when it would have anyway — and marking it is what keeps the
     * same square from being charged for on every sweep.
     */
    private int collectSmashed(ServerLevel level) {
        int penalty = 0;
        for (int i = 0; i < this.pending.size(); i++) {
            Pane pane = this.pending.get(i);
            if (pane.smashed() || level.getBlockState(pane.pos()).is(this.pane.getBlock())) {
                continue;
            }
            this.pending.set(i, pane.smash());
            this.smashedPanes++;
            penalty += BREAK_PENALTY_TICKS;
            level.playSound(null, pane.pos(), SoundEvents.BEACON_DEACTIVATE, SoundSource.BLOCKS, 0.6f, 1.6f);
        }
        return penalty;
    }

    /**
     * The siege damage owed for panes smashed during the warp, clearing the tally as it hands it over.
     * Collected when the building stands up rather than as the panes break, so the hits land on its
     * finished pools instead of being doubled by the mid-warp halving ({@link WarpInVulnerability}).
     */
    public int collectDamageOwed() {
        int owed = this.smashedPanes * SMASHED_PANE_DAMAGE;
        this.smashedPanes = 0;
        return owed;
    }

    private static void materialize(ServerLevel level, Pane pane) {
        level.setBlock(pane.pos(), pane.finished(), SWAP_FLAGS);
        level.sendParticles(ParticleTypes.END_ROD,
                pane.pos().getX() + 0.5, pane.pos().getY() + 0.5, pane.pos().getZ() + 0.5, 3, 0.3, 0.3, 0.3, 0.01);
    }

    public void save(ValueOutput output) {
        output.store("Scaffold", Pane.LIST_CODEC, List.copyOf(this.pending));
        output.store("ScaffoldPane", BlockState.CODEC, this.pane);
        output.putInt("ScaffoldNextPane", this.ticksToNextPane);
        output.putInt("ScaffoldSmashed", this.smashedPanes);
    }

    public void load(ValueInput input) {
        this.pending.clear();
        this.pending.addAll(input.read("Scaffold", Pane.LIST_CODEC).orElse(List.of()));
        this.pane = input.read("ScaffoldPane", BlockState.CODEC).orElse(PANE);
        this.ticksToNextPane = input.getIntOr("ScaffoldNextPane", 0);
        // The tally outlives the panes it came from: a pane smashed early is still owed for when the
        // building stands up, long after it materialised and left the list.
        this.smashedPanes = input.getIntOr("ScaffoldSmashed", 0);
        this.ticksToNextScan = 0;
    }
}
