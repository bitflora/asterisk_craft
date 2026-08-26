package net.bitflora.asteriskcraft.client.protoss;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import net.bitflora.asteriskcraft.client.terran.BunkerModel;
import net.bitflora.asteriskcraft.client.terran.MissileTurretModel;
import net.bitflora.asteriskcraft.client.terran.FirebatModel;
import net.bitflora.asteriskcraft.client.terran.GhostModel;
import net.bitflora.asteriskcraft.client.terran.MarineModel;
import net.bitflora.asteriskcraft.client.terran.ScvModel;
import net.bitflora.asteriskcraft.client.zerg.DroneModel;
import net.bitflora.asteriskcraft.client.zerg.HydraliskModel;
import net.bitflora.asteriskcraft.client.zerg.LurkerModel;
import net.bitflora.asteriskcraft.client.zerg.MutaliskModel;
import net.bitflora.asteriskcraft.client.zerg.SunkenColonyModel;
import net.bitflora.asteriskcraft.client.zerg.SporeColonyModel;
import net.bitflora.asteriskcraft.client.zerg.ZerglingModel;
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
    void scvModelBakesAndResolvesAnimatedParts() {
        assertNotNull(new ScvModel(ScvModel.createBodyLayer().bakeRoot()));
    }

    @Test
    void marineModelBakesAndResolvesAnimatedParts() {
        assertNotNull(new MarineModel(MarineModel.createBodyLayer().bakeRoot()));
    }

    @Test
    void firebatModelBakesAndResolvesAnimatedParts() {
        assertNotNull(new FirebatModel(FirebatModel.createBodyLayer().bakeRoot()));
    }

    @Test
    void ghostModelBakesAndResolvesAnimatedParts() {
        assertNotNull(new GhostModel(GhostModel.createBodyLayer().bakeRoot()));
    }

    @Test
    void bunkerModelBakesAndResolvesAnimatedParts() {
        assertNotNull(new BunkerModel(BunkerModel.createBodyLayer().bakeRoot()));
    }

    @Test
    void missileTurretModelBakesAndResolvesAnimatedParts() {
        assertNotNull(new MissileTurretModel(MissileTurretModel.createBodyLayer().bakeRoot()));
    }

    @Test
    void dragoonModelBakesAndResolvesAnimatedParts() {
        assertNotNull(new DragoonModel(DragoonModel.createBodyLayer().bakeRoot()));
    }

    @Test
    void scoutModelBakesAndResolvesAnimatedParts() {
        assertNotNull(new ScoutModel(ScoutModel.createBodyLayer().bakeRoot()));
    }

    @Test
    void darkTemplarModelBakesAndResolvesAnimatedParts() {
        assertNotNull(new DarkTemplarModel(DarkTemplarModel.createBodyLayer().bakeRoot()));
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
    void lurkerModelBakesAndResolvesAnimatedParts() {
        assertNotNull(new LurkerModel(LurkerModel.createBodyLayer().bakeRoot()));
    }

    @Test
    void mutaliskModelBakesAndResolvesAnimatedParts() {
        assertNotNull(new MutaliskModel(MutaliskModel.createBodyLayer().bakeRoot()));
    }

    @Test
    void probeModelBakes() {
        assertNotNull(new ProbeModel(ProbeModel.createBodyLayer().bakeRoot()));
    }

    @Test
    void droneModelBakes() {
        assertNotNull(new DroneModel(DroneModel.createBodyLayer().bakeRoot()));
    }

    @Test
    void sunkenColonyModelBakesAndResolvesAnimatedParts() {
        assertNotNull(new SunkenColonyModel(SunkenColonyModel.createBodyLayer().bakeRoot()));
    }

    @Test
    void sporeColonyModelBakesAndResolvesAnimatedParts() {
        assertNotNull(new SporeColonyModel(SporeColonyModel.createBodyLayer().bakeRoot()));
    }
}
