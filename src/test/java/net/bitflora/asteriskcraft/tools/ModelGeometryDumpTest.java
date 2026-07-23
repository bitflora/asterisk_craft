package net.bitflora.asteriskcraft.tools;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.function.Supplier;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import org.junit.jupiter.api.Test;

/**
 * Dumps every unit model's {@code createBodyLayer()} geometry to {@code build/model-export/<unit>.json}
 * so {@code tools/blockbench_export.py} can pack UVs, remap the textures and emit a {@code .bbmodel}
 * for hand-painting in Blockbench.
 *
 * <p>Runs as a plain test because building a {@link LayerDefinition} touches no registries — it is pure
 * geometry math — and the JUnit bootstrap is already proven to load these client-dist classes (see
 * {@code client/protoss/ModelBakeTest}, which bakes all eight). It writes only under {@code build/}, so
 * running on every {@code ./gradlew test} is harmless and keeps the dump in sync with the Java.
 *
 * <p>See {@link ModelGeometry} for why this needs reflection and why cubes are keyed by part path.
 */
class ModelGeometryDumpTest {

    private static final Path OUT_DIR = projectRoot().resolve("build").resolve("model-export");

    @Test
    void dumpsEveryModelGeometry() throws Exception {
        Files.createDirectories(OUT_DIR);
        for (Map.Entry<String, Supplier<LayerDefinition>> entry : ModelGeometry.MODELS.entrySet()) {
            String unit = entry.getKey();
            write(unit, toJson(ModelGeometry.read(unit, entry.getValue().get())));
        }
    }

    private static JsonObject toJson(ModelGeometry.Model model) {
        JsonObject json = new JsonObject();
        json.addProperty("unit", model.unit());
        json.addProperty("texWidth", model.texWidth());
        json.addProperty("texHeight", model.texHeight());

        JsonArray parts = new JsonArray();
        for (ModelGeometry.Part part : model.parts()) {
            JsonObject partJson = new JsonObject();
            partJson.addProperty("path", part.path());
            partJson.add("pose", poseOf(part.pose()));

            JsonArray cubes = new JsonArray();
            for (int i = 0; i < part.cubes().size(); i++) {
                ModelGeometry.Cube cube = part.cubes().get(i);
                JsonObject cubeJson = new JsonObject();
                cubeJson.addProperty("index", i);
                cubeJson.add("origin", vec(cube.origin()));
                cubeJson.add("dimensions", vec(cube.dimensions()));
                cubeJson.add("grow", vec(cube.grow()));
                cubeJson.addProperty("mirror", cube.mirror());
                cubeJson.add("texOffs", vec(new float[] {cube.u(), cube.v()}));
                cubes.add(cubeJson);
            }
            partJson.add("cubes", cubes);
            parts.add(partJson);
        }
        json.add("parts", parts);
        return json;
    }

    private static JsonObject poseOf(PartPose pose) {
        JsonObject json = new JsonObject();
        json.addProperty("x", pose.x());
        json.addProperty("y", pose.y());
        json.addProperty("z", pose.z());
        json.addProperty("xRot", pose.xRot());
        json.addProperty("yRot", pose.yRot());
        json.addProperty("zRot", pose.zRot());
        return json;
    }

    private static JsonArray vec(float[] values) {
        JsonArray out = new JsonArray();
        for (float v : values) {
            out.add(v);
        }
        return out;
    }

    private static void write(String unit, JsonObject json) throws IOException {
        String text = new GsonBuilder().setPrettyPrinting().create().toJson(json);
        Files.writeString(OUT_DIR.resolve(unit + ".json"), text, StandardCharsets.UTF_8);
    }

    /**
     * The NeoForge JUnit bootstrap runs tests with the working directory set to
     * {@code build/minecraft-junit}, not the project directory, so a bare relative path would bury the
     * dump where the tooling can't find it. Walk up to the directory holding {@code gradle.properties}.
     */
    private static Path projectRoot() {
        Path dir = Path.of("").toAbsolutePath();
        while (dir != null && !Files.isRegularFile(dir.resolve("gradle.properties"))) {
            dir = dir.getParent();
        }
        if (dir == null) {
            throw new IllegalStateException("Could not locate the project root (no gradle.properties above "
                    + Path.of("").toAbsolutePath() + ")");
        }
        return dir;
    }
}
