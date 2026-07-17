package net.bitflora.asteriskcraft.client;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import org.junit.jupiter.api.Test;

/**
 * Structural smoke test for ported unit models. Baking a {@link LayerDefinition} builds the part
 * geometry in memory (no GL context needed), so it runs in the JUnit bootstrap and deterministically
 * catches malformed meshes (duplicate/misspelled child names, broken hierarchy) and the {@code
 * getChild(...)} animation-part lookups the renderers do at construction time. Visual correctness
 * (pivots, UVs, walk cycle) is still verified via {@code runClient}.
 */
class ModelBakeTest {

    @Test
    void zealotModelBakesAndResolvesAnimatedParts() {
        // Constructing each model runs the getChild(...) lookups for every animated part.
        assertNotNull(new ZealotModel(ZealotModel.createBodyLayer().bakeRoot()));
    }

    @Test
    void zerglingModelBakesAndResolvesAnimatedParts() {
        assertNotNull(new ZerglingModel(ZerglingModel.createBodyLayer().bakeRoot()));
    }

    @Test
    void hydraliskModelBakesAndResolvesAnimatedParts() {
        assertNotNull(new HydraliskModel(HydraliskModel.createBodyLayer().bakeRoot()));
    }

    @Test
    void probeModelBakes() {
        assertNotNull(new ProbeModel(ProbeModel.createBodyLayer().bakeRoot()));
    }

    @Test
    void droneModelBakes() {
        assertNotNull(new DroneModel(DroneModel.createBodyLayer().bakeRoot()));
    }
}
