package net.bitflora.asteriskcraft.tools;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import net.bitflora.asteriskcraft.client.protoss.DragoonModel;
import net.bitflora.asteriskcraft.client.protoss.PhotonCannonModel;
import net.bitflora.asteriskcraft.client.protoss.ProbeModel;
import net.bitflora.asteriskcraft.client.protoss.DarkTemplarModel;
import net.bitflora.asteriskcraft.client.protoss.ObserverModel;
import net.bitflora.asteriskcraft.client.protoss.ScoutModel;
import net.bitflora.asteriskcraft.client.protoss.ZealotModel;
import net.bitflora.asteriskcraft.client.zerg.DroneModel;
import net.bitflora.asteriskcraft.client.zerg.HydraliskModel;
import net.bitflora.asteriskcraft.client.zerg.LurkerModel;
import net.bitflora.asteriskcraft.client.zerg.MutaliskModel;
import net.bitflora.asteriskcraft.client.zerg.OverlordModel;
import net.bitflora.asteriskcraft.client.zerg.SunkenColonyModel;
import net.bitflora.asteriskcraft.client.zerg.SporeColonyModel;
import net.bitflora.asteriskcraft.client.zerg.InfestedVillagerModel;
import net.bitflora.asteriskcraft.client.terran.BunkerModel;
import net.bitflora.asteriskcraft.client.terran.FirebatModel;
import net.bitflora.asteriskcraft.client.terran.GhostModel;
import net.bitflora.asteriskcraft.client.terran.WraithModel;
import net.bitflora.asteriskcraft.client.terran.MarineModel;
import net.bitflora.asteriskcraft.client.terran.MissileTurretModel;
import net.bitflora.asteriskcraft.client.terran.ScvModel;
import net.bitflora.asteriskcraft.client.zerg.ZerglingModel;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import org.joml.Vector3fc;

/**
 * Reads a unit model's {@code createBodyLayer()} geometry back out into plain records, for the two
 * tests that need to inspect rather than render it: {@link ModelGeometryDumpTest} (writes it out for
 * {@code tools/blockbench_export.py}) and {@link ModelUvLayoutTest} (asserts the UV islands don't
 * overlap).
 *
 * <p><b>Why reflection:</b> the builder API is deliberately write-only. Verified against the decompiled
 * 26.1.2 source: {@link PartDefinition#getChildren()} is public and {@link PartPose} is a public record,
 * but {@code PartDefinition.cubes}/{@code .partPose}, every {@code CubeDefinition} field, and
 * {@code MaterialDefinition.xTexSize}/{@code yTexSize} are private or package-private with no accessors.
 * No {@code --add-opens} is required in the NeoForge JUnit bootstrap.
 *
 * <p><b>Why paths, not tree order:</b> {@code PartDefinition.children} is a {@link java.util.HashMap},
 * so iteration order is not source order. Children are sorted by name here and every part carries its
 * slash-joined path, giving a stable {@code (partPath, cubeIndex)} key that the Java-rewriting half of
 * the tooling can match back to the originating {@code texOffs(...)} call site.
 */
final class ModelGeometry {

    private ModelGeometry() {}

    /** Every model that renders a unit, keyed by the texture/asset name it uses. */
    static final Map<String, Supplier<LayerDefinition>> MODELS = models();

    record Cube(float[] origin, float[] dimensions, float[] grow, boolean mirror, float u, float v) {
        /**
         * Width of this cube's box-UV footprint on the atlas: {@code 2 * (width + depth)}. Verified
         * against {@code ModelPart.Cube}'s constructor, where the rightmost column is
         * {@code xTexOffs + depth + width + depth + width}. {@code grow} does not affect UVs.
         */
        float footprintWidth() {
            return 2.0f * (dimensions[0] + dimensions[2]);
        }

        /** Height of the footprint: {@code depth + height} ({@code v2} in {@code ModelPart.Cube}). */
        float footprintHeight() {
            return dimensions[1] + dimensions[2];
        }
    }

    record Part(String path, PartPose pose, List<Cube> cubes) {}

    record Model(String unit, int texWidth, int texHeight, List<Part> parts) {}

    static Model read(String unit, LayerDefinition layer) {
        try {
            MeshDefinition mesh = (MeshDefinition) field(LayerDefinition.class, "mesh").get(layer);
            Object material = field(LayerDefinition.class, "material").get(layer);
            List<Part> parts = new ArrayList<>();
            collect(mesh.getRoot(), "", parts);
            return new Model(
                    unit,
                    (int) field(material.getClass(), "xTexSize").get(material),
                    (int) field(material.getClass(), "yTexSize").get(material),
                    parts);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Could not read geometry for model '" + unit + "'", e);
        }
    }

    /** Pre-order walk, children sorted by name so the result is stable across runs. */
    private static void collect(PartDefinition part, String path, List<Part> out)
            throws ReflectiveOperationException {
        // The synthetic root carries no cubes and no meaningful pose; skip it but still walk into it.
        if (!path.isEmpty()) {
            PartPose pose = (PartPose) field(PartDefinition.class, "partPose").get(part);
            out.add(new Part(path, pose, cubesOf(part)));
        }

        List<Map.Entry<String, PartDefinition>> children = new ArrayList<>(part.getChildren());
        children.sort(Comparator.comparing(Map.Entry::getKey));
        for (Map.Entry<String, PartDefinition> child : children) {
            collect(child.getValue(), path.isEmpty() ? child.getKey() : path + "/" + child.getKey(), out);
        }
    }

    private static List<Cube> cubesOf(PartDefinition part) throws ReflectiveOperationException {
        @SuppressWarnings("unchecked")
        List<Object> defs = (List<Object>) field(PartDefinition.class, "cubes").get(part);
        List<Cube> out = new ArrayList<>(defs.size());
        for (Object cube : defs) {
            Class<?> type = cube.getClass();
            Vector3fc origin = (Vector3fc) field(type, "origin").get(cube);
            Vector3fc dims = (Vector3fc) field(type, "dimensions").get(cube);
            Object grow = field(type, "grow").get(cube);
            Object texCoord = field(type, "texCoord").get(cube);
            out.add(new Cube(
                    new float[] {origin.x(), origin.y(), origin.z()},
                    new float[] {dims.x(), dims.y(), dims.z()},
                    new float[] {
                        (float) field(grow.getClass(), "growX").get(grow),
                        (float) field(grow.getClass(), "growY").get(grow),
                        (float) field(grow.getClass(), "growZ").get(grow)
                    },
                    (boolean) field(type, "mirror").get(cube),
                    // UVPair is a public record; texCoord holds the raw texOffs(u, v) the model declared.
                    (float) texCoord.getClass().getMethod("u").invoke(texCoord),
                    (float) texCoord.getClass().getMethod("v").invoke(texCoord)));
        }
        return out;
    }

    private static Field field(Class<?> owner, String name) throws NoSuchFieldException {
        Field f = owner.getDeclaredField(name);
        f.setAccessible(true);
        return f;
    }

    /** Insertion-ordered so both tests and the dumped files iterate units predictably. */
    private static Map<String, Supplier<LayerDefinition>> models() {
        Map<String, Supplier<LayerDefinition>> map = new LinkedHashMap<>();
        map.put("probe", ProbeModel::createBodyLayer);
        map.put("zealot", ZealotModel::createBodyLayer);
        map.put("dragoon", DragoonModel::createBodyLayer);
        map.put("scout", ScoutModel::createBodyLayer);
        map.put("observer", ObserverModel::createBodyLayer);
        map.put("dark_templar", DarkTemplarModel::createBodyLayer);
        map.put("photon_cannon", PhotonCannonModel::createBodyLayer);
        map.put("zergling", ZerglingModel::createBodyLayer);
        map.put("infested_villager", InfestedVillagerModel::createBodyLayer);
        map.put("hydralisk", HydraliskModel::createBodyLayer);
        map.put("lurker", LurkerModel::createBodyLayer);
        map.put("mutalisk", MutaliskModel::createBodyLayer);
        map.put("overlord", OverlordModel::createBodyLayer);
        map.put("drone", DroneModel::createBodyLayer);
        map.put("sunken_colony", SunkenColonyModel::createBodyLayer);
        map.put("spore_colony", SporeColonyModel::createBodyLayer);
        map.put("scv", ScvModel::createBodyLayer);
        map.put("marine", MarineModel::createBodyLayer);
        map.put("firebat", FirebatModel::createBodyLayer);
        map.put("ghost", GhostModel::createBodyLayer);
        map.put("wraith", WraithModel::createBodyLayer);
        map.put("bunker", BunkerModel::createBodyLayer);
        map.put("missile_turret", MissileTurretModel::createBodyLayer);
        // Collections.unmodifiableMap, not Map.copyOf — the latter does not preserve insertion order.
        return Collections.unmodifiableMap(map);
    }
}
