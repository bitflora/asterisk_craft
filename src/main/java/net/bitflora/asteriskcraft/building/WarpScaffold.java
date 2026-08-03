package net.bitflora.asteriskcraft.building;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;

/**
 * The half-real structure a building stands as while it warps in: every block of its layout except
 * the core is held as warp glass, and the real stonework materialises pane by pane over the warp
 * countdown, so a building visibly builds itself rather than blinking into existence finished.
 *
 * <p>The panes are paced against the time left rather than against a fixed schedule — the next one
 * lands after {@link #paneInterval} ticks, recomputed each time — which is what keeps the fill in
 * step with a countdown that can grow: <b>smashing a pane costs the warp {@value #BREAK_PENALTY_TICKS}
 * ticks</b> (ten seconds). A smashed pane is materialised on the spot rather than re-glazed, so the
 * building is never left with a hole in it and the same square can't be farmed for penalty after
 * penalty; the total an attacker can wring out of a warp is bounded by its pane count.
 *
 * <p>Only kit-warped buildings raise a scaffold. A pre-placed one (a Hive, the starting Nexus) has no
 * warp phase to fill in, and its {@link BuildingDefense} simply carries the empty scaffold every
 * building has.
 */
public final class WarpScaffold {
    /** What an unfinished block of the layout looks like — Protoss blue, and cheap to smash. */
    public static final Block PANE = Blocks.LIGHT_BLUE_STAINED_GLASS;

    /** What one smashed pane adds to the warp countdown. */
    public static final int BREAK_PENALTY_TICKS = 200; // 10 seconds

    /** How often the standing panes are swept for smashed ones. */
    private static final int SCAN_INTERVAL_TICKS = 5;

    /**
     * Swapping a pane for its finished block must not run neighbour or shape updates: the states are
     * the template's own, and letting stairs re-derive their shape against half-built surroundings
     * would quietly rewrite the building as it goes up.
     */
    private static final int SWAP_FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;

    /** One block of the layout: where it goes and what it becomes. */
    public record Pane(BlockPos pos, BlockState finished) {
        public static final Codec<Pane> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                BlockPos.CODEC.fieldOf("pos").forGetter(Pane::pos),
                BlockState.CODEC.fieldOf("state").forGetter(Pane::finished)
        ).apply(instance, Pane::new));

        public static final Codec<List<Pane>> LIST_CODEC = CODEC.listOf();
    }

    /** Still-glass panes, in the order they materialise; emptied as the warp runs. */
    private final List<Pane> pending = new ArrayList<>();
    private int ticksToNextPane;
    private int ticksToNextScan;

    /**
     * Freezes a just-stamped structure into glass. Every non-air block of the template's box becomes
     * a pane except the core itself, which has to stay real from the first tick — it is the block
     * entity running the countdown.
     */
    public void raise(ServerLevel level, BuildingTemplates.Placed placed) {
        this.pending.clear();
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
        this.pending.sort(fillOrder(placed.core()));
        for (Pane pane : this.pending) {
            level.setBlock(pane.pos(), PANE.defaultBlockState(), SWAP_FLAGS);
        }
    }

    /**
     * The order a layout fills in: bottom layer first, and within a layer from the core's own column
     * outwards, so the building grows up out of the ground instead of appearing in scattered patches.
     */
    static Comparator<Pane> fillOrder(BlockPos core) {
        return Comparator.<Pane>comparingInt(pane -> pane.pos().getY())
                .thenComparingLong(pane -> horizontalDistanceSq(pane.pos(), core))
                // Ties broken on the coordinates themselves so the order is fully determined —
                // it is saved as a list and has to survive a reload unchanged.
                .thenComparingInt(pane -> pane.pos().getX())
                .thenComparingInt(pane -> pane.pos().getZ());
    }

    private static long horizontalDistanceSq(BlockPos pos, BlockPos core) {
        long dx = pos.getX() - core.getX();
        long dz = pos.getZ() - core.getZ();
        return dx * dx + dz * dz;
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
        for (Pane pane : this.pending) {
            if (level.getBlockState(pane.pos()).is(PANE)) {
                level.setBlock(pane.pos(), Blocks.AIR.defaultBlockState(), SWAP_FLAGS);
            }
        }
        this.pending.clear();
    }

    /**
     * Materialises every pane that is no longer glass (mined, blown up, or battered by an attacker)
     * and charges the warp for each. Filling the hole rather than re-glazing it is what bounds the
     * damage: a square already paid for leaves the pending list and can't be charged twice.
     */
    private int collectSmashed(ServerLevel level) {
        int penalty = 0;
        for (Iterator<Pane> panes = this.pending.iterator(); panes.hasNext(); ) {
            Pane pane = panes.next();
            if (level.getBlockState(pane.pos()).is(PANE)) {
                continue;
            }
            panes.remove();
            materialize(level, pane);
            penalty += BREAK_PENALTY_TICKS;
            level.playSound(null, pane.pos(), SoundEvents.BEACON_DEACTIVATE, SoundSource.BLOCKS, 0.6f, 1.6f);
        }
        return penalty;
    }

    private static void materialize(ServerLevel level, Pane pane) {
        level.setBlock(pane.pos(), pane.finished(), SWAP_FLAGS);
        level.sendParticles(ParticleTypes.END_ROD,
                pane.pos().getX() + 0.5, pane.pos().getY() + 0.5, pane.pos().getZ() + 0.5, 3, 0.3, 0.3, 0.3, 0.01);
    }

    public void save(ValueOutput output) {
        output.store("Scaffold", Pane.LIST_CODEC, List.copyOf(this.pending));
        output.putInt("ScaffoldNextPane", this.ticksToNextPane);
    }

    public void load(ValueInput input) {
        this.pending.clear();
        this.pending.addAll(input.read("Scaffold", Pane.LIST_CODEC).orElse(List.of()));
        this.ticksToNextPane = input.getIntOr("ScaffoldNextPane", 0);
        this.ticksToNextScan = 0;
    }
}
