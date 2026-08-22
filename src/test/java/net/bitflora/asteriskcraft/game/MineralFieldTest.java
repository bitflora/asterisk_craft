package net.bitflora.asteriskcraft.game;

import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Guards for the shape of a base's mineral field. All pure — {@code MineralField.plan} takes a
 * facing and an RNG and never touches a level — so unlike the rest of world generation this really
 * is unit-testable end to end. Block <i>states</i> resolve in the NeoForge JUnit bootstrap; only
 * tags don't, and nothing here needs one.
 *
 * <p>Each geometric property is checked at several facings rather than one, because the arc filter
 * is the piece most likely to be right in one quadrant and wrong where the angle wraps.
 */
class MineralFieldTest {

    private static final float[] FACINGS = {0.0f, 37.5f, 90.0f, 180.0f, 260.0f, 359.0f};
    private static final BlockState WOOD = Blocks.OAK_LOG.defaultBlockState();
    private static final BlockState STONE = Blocks.STONE.defaultBlockState();
    private static final BlockState IRON = Blocks.IRON_ORE.defaultBlockState();

    private static List<MineralField.Column> plan(float facing, long seed, boolean includeIron) {
        return MineralField.plan(facing, RandomSource.create(seed), includeIron);
    }

    /** Every column of the full ring the field is carved out of, regardless of facing. */
    private static int fullBandSize() {
        int count = 0;
        for (int dx = -MineralField.OUTER_RADIUS; dx <= MineralField.OUTER_RADIUS; dx++) {
            for (int dz = -MineralField.OUTER_RADIUS; dz <= MineralField.OUTER_RADIUS; dz++) {
                if (MineralField.inBand(dx, dz)) {
                    count++;
                }
            }
        }
        return count;
    }

    /**
     * The field must clear the base's own footprint on the inside and stop at its declared edge on
     * the outside — a column stamped inward would land on the building's platform.
     */
    @Test
    void everyColumnSitsInTheTwoBlockBand() {
        for (float facing : FACINGS) {
            for (MineralField.Column column : plan(facing, 1234L, false)) {
                double distance = Math.sqrt(column.dx() * column.dx() + column.dz() * column.dz());
                assertTrue(distance >= MineralField.INNER_RADIUS - 0.5
                                && distance < MineralField.OUTER_RADIUS + 0.5,
                        "column " + column.dx() + "," + column.dz() + " is " + distance + " out");
            }
        }
    }

    /**
     * The field points where it was told to. Stated as which side of the base its columns land on,
     * rather than by re-running the arc filter the planner itself used — an inverted or dropped
     * filter would sail through the latter.
     */
    @Test
    void theFieldFacesTheDirectionItWasGiven() {
        for (MineralField.Column column : plan(0.0f, 99L, false)) {
            assertTrue(column.dx() > 0, "a field facing +X put a column at dx=" + column.dx());
        }
        for (MineralField.Column column : plan(180.0f, 99L, false)) {
            assertTrue(column.dx() < 0, "a field facing -X put a column at dx=" + column.dx());
        }
        for (MineralField.Column column : plan(90.0f, 99L, false)) {
            assertTrue(column.dz() > 0, "a field facing +Z put a column at dz=" + column.dz());
        }
        for (MineralField.Column column : plan(270.0f, 99L, false)) {
            assertTrue(column.dz() < 0, "a field facing -Z put a column at dz=" + column.dz());
        }
    }

    /**
     * A third of the circle, not the whole of it: the open 240° is what keeps the field a mineral
     * line rather than a fence around the base.
     */
    @Test
    void theFieldIsAThirdOfTheBandNotAllOfIt() {
        int band = fullBandSize();
        for (float facing : FACINGS) {
            int size = plan(facing, 7L, false).size();
            assertTrue(size > band / 4 && size < band / 2,
                    "facing " + facing + " filled " + size + " of the band's " + band + " columns");
        }
    }

    /** Short columns, and actually varied — a field pinned to one height reads as a wall. */
    @Test
    void columnsAreShortAndVaried() {
        Set<Integer> heights = new HashSet<>();
        for (MineralField.Column column : plan(45.0f, 2024L, false)) {
            assertTrue(column.height() >= 1 && column.height() <= 3,
                    "column height " + column.height() + " is out of range");
            heights.add(column.height());
        }
        assertTrue(heights.size() > 1, "every column came out the same height");
    }

    /** The player's field is wood and stone, both present, and carries none of the AI's iron. */
    @Test
    void aPlayerFieldIsWoodAndStoneOnly() {
        Set<BlockState> materials = new HashSet<>();
        for (MineralField.Column column : plan(120.0f, 55L, false)) {
            materials.add(column.block());
        }
        assertEquals(Set.of(WOOD, STONE), materials);
    }

    /**
     * The computer player's one edge: a single iron column, full height, and nothing else about the
     * field changed — the same seed and facing must lay out the same patch either way.
     */
    @Test
    void theAiFieldAddsExactlyOneTallIronColumn() {
        for (float facing : FACINGS) {
            List<MineralField.Column> plain = plan(facing, 88L, false);
            List<MineralField.Column> withIron = plan(facing, 88L, true);
            assertEquals(plain.size(), withIron.size(), "the iron column must replace one, not add one");

            List<MineralField.Column> iron = new ArrayList<>();
            for (int i = 0; i < withIron.size(); i++) {
                MineralField.Column column = withIron.get(i);
                assertEquals(plain.get(i).dx(), column.dx());
                assertEquals(plain.get(i).dz(), column.dz());
                if (column.block().equals(IRON)) {
                    iron.add(column);
                }
            }
            assertEquals(1, iron.size(), "facing " + facing + " planted " + iron.size() + " iron columns");
            assertEquals(3, iron.getFirst().height(), "the iron column must stand full height");
        }
    }

    /** The iron sits in the middle of the patch, where a worker reaches it, not out at a ragged edge. */
    @Test
    void theIronColumnSitsInTheMiddleOfThePatch() {
        float facing = 200.0f;
        List<MineralField.Column> columns = plan(facing, 4L, true);
        MineralField.Column iron = null;
        for (MineralField.Column column : columns) {
            if (column.block().equals(IRON)) {
                iron = column;
            }
        }
        assertNotNull(iron, "the field carries no iron column at all");
        float ironOffAxis = MineralField.angularDistance(facing, MineralField.angleOf(iron.dx(), iron.dz()));
        for (MineralField.Column column : columns) {
            float offAxis = MineralField.angularDistance(facing, MineralField.angleOf(column.dx(), column.dz()));
            assertTrue(ironOffAxis <= offAxis,
                    "a column at " + column.dx() + "," + column.dz() + " is nearer the arc's middle than the iron");
        }
    }

    /**
     * World generation is seeded off the world seed so one seed reproduces one map. That only holds
     * if a plan is a pure function of its facing and its RNG.
     */
    @Test
    void oneSeedAndFacingReproduceOneField() {
        assertEquals(plan(77.0f, 31337L, true), plan(77.0f, 31337L, true));
        assertNotEquals(plan(77.0f, 31337L, true), plan(77.0f, 31338L, true),
                "two different seeds should not lay the same field");
    }

    @Test
    void noTwoColumnsShareASpot() {
        Set<Long> spots = new HashSet<>();
        for (MineralField.Column column : plan(15.0f, 6L, true)) {
            assertTrue(spots.add((long) column.dx() << 32 | (column.dz() & 0xFFFFFFFFL)),
                    "two columns claim " + column.dx() + "," + column.dz());
        }
    }

    /** 350° and 10° are 20° apart, not 340° — the wrap is what keeps a field whole across due east. */
    @Test
    void angularDistanceWrapsAroundTheCircle() {
        assertEquals(20.0f, MineralField.angularDistance(350.0f, 10.0f), 0.001f);
        assertEquals(20.0f, MineralField.angularDistance(10.0f, 350.0f), 0.001f);
        assertEquals(180.0f, MineralField.angularDistance(90.0f, 270.0f), 0.001f);
        assertEquals(0.0f, MineralField.angularDistance(-45.0f, 315.0f), 0.001f);
    }
}
