package net.bitflora.asteriskcraft.game;

import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;

/**
 * The mineral field laid around a base at world generation: a third of a circle, filled with short
 * columns of wood and stone that a worker line parks against.
 *
 * <p>Replaces the old unbroken stone ring (and the handful of scattered garden nodes beside it),
 * which read as a wall rather than a patch. The field keeps the ring's line — its inner edge is the
 * radius the ring sat at — and reaches one block further out, so the two bands together are solid.
 * A third of the circumference is filled rather than all of it, which is what makes it a mineral
 * <i>line</i> with a facing instead of a fence: the other 240° stay open for units to walk through.
 *
 * <p>Pure geometry, no level: {@link #plan} takes a facing and an RNG and hands back the columns as
 * offsets, and {@code GameBootstrap} is the only thing that turns them into blocks. Same split as
 * {@link net.bitflora.asteriskcraft.building.BuildingTemplates#planSitePrep}, and for the same
 * reason — the shape is unit-testable without a running server.
 */
public final class MineralField {

    /**
     * Inner edge, the radius the old stone ring sat at: outside the base's own footprint and inside
     * the ground-cover disc, so the field lands on ground the base has already cleared.
     */
    static final int INNER_RADIUS = 8;
    /** Outer edge — one block further out than the ring reached. */
    static final int OUTER_RADIUS = 9;
    /** Half of the 120° arc the field occupies, i.e. a third of the circle. */
    static final float HALF_SPAN = 60.0f;
    static final int MIN_HEIGHT = 1;
    static final int MAX_HEIGHT = 3;
    /** The AI's one iron column is always full height, so it reads as the prize of the patch. */
    static final int IRON_HEIGHT = 3;

    private static final BlockState WOOD = Blocks.OAK_LOG.defaultBlockState();
    // minecraft:stone specifically, not any stone-ish block: data/asteriskcraft/tags/block/harvestable.json
    // lists the literal block and no cobblestone or deepslate, so a variant would be inert to a worker.
    private static final BlockState STONE = Blocks.STONE.defaultBlockState();
    private static final BlockState IRON = Blocks.IRON_ORE.defaultBlockState();

    private MineralField() {
    }

    /**
     * One column of the field: where it stands relative to the base's centre, what it is made of,
     * and how many blocks of it stack up from the ground.
     */
    public record Column(int dx, int dz, BlockState block, int height) {
    }

    /**
     * The columns of one field, in a deterministic order.
     *
     * <p>Every column is one material through its whole height, so the field reads as adjacent wood
     * and mineral patches rather than speckled noise. Passing {@code includeIron} swaps a single
     * column for iron ore — the computer player's one edge, and what the removed garden's iron nodes
     * became.
     *
     * @param facingDegrees which way the arc points, as an angle in the XZ plane
     * @param random        drawn from in a fixed order, so one (facing, seed) reproduces one field
     * @param includeIron   whether to plant the single iron column
     */
    public static List<Column> plan(float facingDegrees, RandomSource random, boolean includeIron) {
        List<Column> columns = new ArrayList<>();
        for (int dx = -OUTER_RADIUS; dx <= OUTER_RADIUS; dx++) {
            for (int dz = -OUTER_RADIUS; dz <= OUTER_RADIUS; dz++) {
                if (!inBand(dx, dz) || !inArc(facingDegrees, dx, dz)) {
                    continue;
                }
                BlockState block = random.nextBoolean() ? WOOD : STONE;
                int height = Mth.nextInt(random, MIN_HEIGHT, MAX_HEIGHT);
                columns.add(new Column(dx, dz, block, height));
            }
        }
        if (includeIron) {
            plantIron(columns, facingDegrees);
        }
        return columns;
    }

    /**
     * True if this offset belongs to the two-block band the field fills. The distance is
     * <i>rounded</i> rather than compared exactly, which is what makes the band solid: a strict
     * {@code dist == radius} test leaves gaps wherever the circle passes between block centres.
     */
    static boolean inBand(int dx, int dz) {
        long ring = Math.round(Math.sqrt(dx * dx + dz * dz));
        return ring >= INNER_RADIUS && ring <= OUTER_RADIUS;
    }

    /** True if this offset falls inside the third of the circle the field faces. */
    static boolean inArc(float facingDegrees, int dx, int dz) {
        return angularDistance(facingDegrees, angleOf(dx, dz)) <= HALF_SPAN;
    }

    /** This offset's direction from the base's centre, in degrees. */
    static float angleOf(int dx, int dz) {
        return (float) Math.toDegrees(Math.atan2(dz, dx));
    }

    /**
     * The separation between two directions, wrapped into {@code [0, 180]} so that 350° and 10° come
     * out 20° apart rather than 340°. Written out rather than reaching for a vanilla helper, so the
     * field's geometry carries no API dependency and the test can exercise the wrap directly.
     */
    static float angularDistance(float a, float b) {
        float diff = Math.abs(a - b) % 360.0f;
        return diff > 180.0f ? 360.0f - diff : diff;
    }

    /**
     * Swaps the column closest to the middle of the arc for the iron one, so it sits where a worker
     * will reach it rather than out at a ragged edge. Ties break toward the inner band, again for
     * reach. Deliberately not a fresh random draw: picking by position keeps a plan reproducible
     * without spending another roll and shifting every column after it.
     */
    private static void plantIron(List<Column> columns, float facingDegrees) {
        int best = -1;
        float bestOffAxis = Float.MAX_VALUE;
        long bestRing = Long.MAX_VALUE;
        for (int i = 0; i < columns.size(); i++) {
            Column column = columns.get(i);
            float offAxis = angularDistance(facingDegrees, angleOf(column.dx(), column.dz()));
            long ring = Math.round(Math.sqrt(column.dx() * column.dx() + column.dz() * column.dz()));
            if (offAxis < bestOffAxis || (offAxis == bestOffAxis && ring < bestRing)) {
                best = i;
                bestOffAxis = offAxis;
                bestRing = ring;
            }
        }
        if (best >= 0) {
            Column column = columns.get(best);
            columns.set(best, new Column(column.dx(), column.dz(), IRON, IRON_HEIGHT));
        }
    }
}
