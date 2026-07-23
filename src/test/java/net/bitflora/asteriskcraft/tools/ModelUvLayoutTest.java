package net.bitflora.asteriskcraft.tools;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import org.junit.jupiter.api.Test;

/**
 * Asserts that every cube in every unit model owns a private, in-bounds region of its texture.
 *
 * <p>This is the guard that makes hand-painting safe. The models were originally textured with flat
 * colour zones — a dozen differently-sized cubes all declaring the same {@code texOffs(0, 0)} and
 * sampling one solid-colour rectangle. That is invisible while the rectangle stays a single colour, but
 * the moment someone paints an eye or a shading gradient in Blockbench it smears onto every cube
 * sharing that corner. After the UV migration (see {@code tools/blockbench_export.py}) each cube has its
 * own island, and this test fails the build if an edit ever reintroduces a shared or out-of-bounds
 * {@code texOffs}.
 *
 * <p>Footprints are {@code 2 * (width + depth)} by {@code depth + height}, verified against
 * {@code ModelPart.Cube}'s constructor. Several models use fractional cube sizes (1.5, 0.75), so
 * footprints are compared on the integer texel grid they actually occupy: floor of the low edge, ceil of
 * the high edge. {@code grow}/inflate does not affect UVs and is correctly ignored.
 */
class ModelUvLayoutTest {

    /** A cube's occupied texel rectangle, plus enough context to name it in a failure message. */
    private record Island(String partPath, int cubeIndex, int x0, int y0, int x1, int y1) {
        boolean overlaps(Island other) {
            return x0 < other.x1 && other.x0 < x1 && y0 < other.y1 && other.y0 < y1;
        }

        String describe() {
            return partPath + "#" + cubeIndex + " [" + x0 + "," + y0 + " -> " + x1 + "," + y1 + "]";
        }
    }

    @Test
    void everyCubeOwnsAPrivateUvIsland() {
        List<String> failures = new ArrayList<>();
        for (Map.Entry<String, Supplier<LayerDefinition>> entry : ModelGeometry.MODELS.entrySet()) {
            String unit = entry.getKey();
            ModelGeometry.Model model = ModelGeometry.read(unit, entry.getValue().get());
            List<Island> islands = islandsOf(model);

            for (Island island : islands) {
                if (island.x0() < 0 || island.y0() < 0
                        || island.x1() > model.texWidth() || island.y1() > model.texHeight()) {
                    failures.add(unit + ": " + island.describe() + " falls outside the "
                            + model.texWidth() + "x" + model.texHeight() + " texture");
                }
            }
            for (int i = 0; i < islands.size(); i++) {
                for (int j = i + 1; j < islands.size(); j++) {
                    if (islands.get(i).overlaps(islands.get(j))) {
                        failures.add(unit + ": " + islands.get(i).describe() + " overlaps "
                                + islands.get(j).describe());
                    }
                }
            }
        }

        if (!failures.isEmpty()) {
            fail("Overlapping or out-of-bounds UV islands (" + failures.size() + "):\n  "
                    + String.join("\n  ", failures));
        }
    }

    /** Sanity check that the walker sees real geometry, so an empty model list can't pass vacuously. */
    @Test
    void everyModelReportsCubes() {
        for (Map.Entry<String, Supplier<LayerDefinition>> entry : ModelGeometry.MODELS.entrySet()) {
            ModelGeometry.Model model = ModelGeometry.read(entry.getKey(), entry.getValue().get());
            assertTrue(islandsOf(model).size() > 1, entry.getKey() + " reported no cubes");
        }
    }

    private static List<Island> islandsOf(ModelGeometry.Model model) {
        List<Island> islands = new ArrayList<>();
        for (ModelGeometry.Part part : model.parts()) {
            for (int i = 0; i < part.cubes().size(); i++) {
                ModelGeometry.Cube cube = part.cubes().get(i);
                // A zero-volume cube covers no texels; it can neither overlap nor go out of bounds.
                if (cube.footprintWidth() <= 0.0f || cube.footprintHeight() <= 0.0f) {
                    continue;
                }
                islands.add(new Island(
                        part.path(),
                        i,
                        (int) Math.floor(cube.u()),
                        (int) Math.floor(cube.v()),
                        (int) Math.ceil(cube.u() + cube.footprintWidth()),
                        (int) Math.ceil(cube.v() + cube.footprintHeight())));
            }
        }
        return islands;
    }
}
