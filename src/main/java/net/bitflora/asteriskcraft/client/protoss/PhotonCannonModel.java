package net.bitflora.asteriskcraft.client.protoss;

import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;

/**
 * Hand-authored geometry for the Photon Cannon, shaped after the StarCraft II turret: a golden
 * <b>star base</b> of raised armour petals, a forward-tilted <b>drum</b> holding a glowing blue lens
 * "eye", and a domed gold <b>head</b> with two glowing purple eyes and blue accent panels.
 *
 * <p>Cuboids can't be curved, so every round shape is a bundle of <b>crossed planks</b> — an axis
 * "plus" (a wide plank + a deep plank) plus a 45°-rotated copy of the same, giving four planks that
 * reach the same radius in eight directions, so the union reads as an inscribed circle. The dome is a
 * vertical stack of such plank-rings following a sphere profile; the base hub and the lens/drum ring
 * are single plank-rings.
 *
 * <p>Colour comes from flat single-colour zones of {@code textures/entity/photon_cannon.png} (see
 * {@code tools/gen_photon_cannon_texture.py}); every cube just points its {@code texOffs} at the
 * top-left of its material's zone (GOLD 0,0 · DARK 170,0 · BLUE 0,120 · PURPLE 110,120), so cubes of
 * the same material freely share offsets. The BLUE (lens + accents) and PURPLE (eyes) zones are the
 * only ones painted in the companion {@code photon_cannon_glow.png}, so the emissive
 * {@link UnitGlowLayer} lights just those parts.
 *
 * <p>Authored in true pixel space (16px = 1 block), feet at {@code y=24}, "up" is negative y, so the
 * renderer applies no extra scale. Static: the turret doesn't animate; directionality comes from the
 * bolt's beam, so {@link #setupAnim} is a no-op.
 */
public class PhotonCannonModel extends EntityModel<LivingEntityRenderState> {
    private static final float DEG45 = 0.7853981633974483f;
    private static final float LENS_PITCH = -0.35f; // tilt the drum back so the lens faces forward-and-up

    // Material zone offsets — match the zones painted by gen_photon_cannon_texture.py.
    private static final int GOLD_U = 0, GOLD_V = 0;
    private static final int DARK_U = 170, DARK_V = 0;
    private static final int BLUE_U = 0, BLUE_V = 120;
    private static final int PURPLE_U = 110, PURPLE_V = 120;

    public PhotonCannonModel(ModelPart root) {
        super(root);
    }

    @Override
    public void setupAnim(LivingEntityRenderState state) {
        super.setupAnim(state);
        // no animated parts
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();

        // --- Star base: a central hub ring plus eight raised armour petals (radius 24 = 3 blocks). ---
        root.addOrReplaceChild("hub",
                CubeListBuilder.create()
                        .texOffs(GOLD_U, GOLD_V).addBox(-12.0f, -6.0f, -6.0f, 24.0f, 6.0f, 12.0f)
                        .texOffs(GOLD_U, GOLD_V).addBox(-6.0f, -6.0f, -12.0f, 12.0f, 6.0f, 24.0f),
                PartPose.offset(0.0f, 24.0f, 0.0f));
        root.addOrReplaceChild("hubDiag",
                CubeListBuilder.create()
                        .texOffs(GOLD_U, GOLD_V).addBox(-12.0f, -6.0f, -6.0f, 24.0f, 6.0f, 12.0f)
                        .texOffs(GOLD_U, GOLD_V).addBox(-6.0f, -6.0f, -12.0f, 12.0f, 6.0f, 24.0f),
                PartPose.offsetAndRotation(0.0f, 24.0f, 0.0f, 0.0f, DEG45, 0.0f));

        // Eight petals radiating from the hub — each a wide inner plate + a narrower raised outer tip.
        for (int i = 0; i < 8; i++) {
            addPetal(root, "petal" + i, i * DEG45);
        }

        // --- Lens drum: a gold ring holding a glowing blue lens, tilted back to face forward-and-up. ---
        PartDefinition lens = root.addOrReplaceChild("lens",
                CubeListBuilder.create(),
                PartPose.offsetAndRotation(0.0f, 10.0f, 12.0f, LENS_PITCH, 0.0f, 0.0f));
        // Square gold frame (front is +Z) + a 45° copy → an octagonal ring.
        CubeListBuilder ring = CubeListBuilder.create()
                .texOffs(GOLD_U, GOLD_V).addBox(-9.0f, -11.0f, -2.0f, 18.0f, 3.0f, 4.0f)
                .texOffs(GOLD_U, GOLD_V).addBox(-9.0f, 8.0f, -2.0f, 18.0f, 3.0f, 4.0f)
                .texOffs(GOLD_U, GOLD_V).addBox(-11.0f, -9.0f, -2.0f, 3.0f, 18.0f, 4.0f)
                .texOffs(GOLD_U, GOLD_V).addBox(8.0f, -9.0f, -2.0f, 3.0f, 18.0f, 4.0f);
        lens.addOrReplaceChild("ring", ring, PartPose.ZERO);
        lens.addOrReplaceChild("ringDiag",
                CubeListBuilder.create()
                        .texOffs(GOLD_U, GOLD_V).addBox(-9.0f, -11.0f, -2.0f, 18.0f, 3.0f, 4.0f)
                        .texOffs(GOLD_U, GOLD_V).addBox(-9.0f, 8.0f, -2.0f, 18.0f, 3.0f, 4.0f)
                        .texOffs(GOLD_U, GOLD_V).addBox(-11.0f, -9.0f, -2.0f, 3.0f, 18.0f, 4.0f)
                        .texOffs(GOLD_U, GOLD_V).addBox(8.0f, -9.0f, -2.0f, 3.0f, 18.0f, 4.0f),
                PartPose.rotation(0.0f, 0.0f, DEG45));
        // Dark socket behind the lens, so the glow reads against black.
        lens.addOrReplaceChild("socket",
                CubeListBuilder.create()
                        .texOffs(DARK_U, DARK_V).addBox(-8.0f, -8.0f, -3.0f, 16.0f, 16.0f, 2.0f),
                PartPose.ZERO);
        // Blue lens disc (crossed planks → round), sitting at the front of the frame. Glows.
        lens.addOrReplaceChild("disc",
                CubeListBuilder.create()
                        .texOffs(BLUE_U, BLUE_V).addBox(-8.0f, -4.0f, 0.0f, 16.0f, 8.0f, 2.0f)
                        .texOffs(BLUE_U, BLUE_V).addBox(-4.0f, -8.0f, 0.0f, 8.0f, 16.0f, 2.0f),
                PartPose.ZERO);
        lens.addOrReplaceChild("discDiag",
                CubeListBuilder.create()
                        .texOffs(BLUE_U, BLUE_V).addBox(-8.0f, -4.0f, 0.0f, 16.0f, 8.0f, 2.0f)
                        .texOffs(BLUE_U, BLUE_V).addBox(-4.0f, -8.0f, 0.0f, 8.0f, 16.0f, 2.0f),
                PartPose.rotation(0.0f, 0.0f, DEG45));

        // --- Domed head on a neck, above and behind the drum, with a dark band, purple eyes, accents. ---
        PartDefinition head = root.addOrReplaceChild("head",
                CubeListBuilder.create(),
                PartPose.offset(0.0f, -6.0f, -4.0f));
        head.addOrReplaceChild("neck",
                CubeListBuilder.create()
                        .texOffs(GOLD_U, GOLD_V).addBox(-4.0f, 4.0f, -3.0f, 8.0f, 14.0f, 8.0f),
                PartPose.ZERO);
        // Dome: three sphere-profile plank-rings + a top cap, with a 45° copy of the rings.
        head.addOrReplaceChild("dome",
                CubeListBuilder.create()
                        .texOffs(GOLD_U, GOLD_V).addBox(-8.0f, -2.0f, -4.0f, 16.0f, 4.0f, 8.0f)
                        .texOffs(GOLD_U, GOLD_V).addBox(-4.0f, -2.0f, -8.0f, 8.0f, 4.0f, 16.0f)
                        .texOffs(GOLD_U, GOLD_V).addBox(-6.0f, -6.0f, -3.0f, 12.0f, 4.0f, 6.0f)
                        .texOffs(GOLD_U, GOLD_V).addBox(-3.0f, -6.0f, -6.0f, 6.0f, 4.0f, 12.0f)
                        .texOffs(GOLD_U, GOLD_V).addBox(-6.0f, 2.0f, -3.0f, 12.0f, 4.0f, 6.0f)
                        .texOffs(GOLD_U, GOLD_V).addBox(-3.0f, 2.0f, -6.0f, 6.0f, 4.0f, 12.0f)
                        .texOffs(GOLD_U, GOLD_V).addBox(-4.0f, -9.0f, -4.0f, 8.0f, 3.0f, 8.0f),
                PartPose.ZERO);
        head.addOrReplaceChild("domeDiag",
                CubeListBuilder.create()
                        .texOffs(GOLD_U, GOLD_V).addBox(-8.0f, -2.0f, -4.0f, 16.0f, 4.0f, 8.0f)
                        .texOffs(GOLD_U, GOLD_V).addBox(-4.0f, -2.0f, -8.0f, 8.0f, 4.0f, 16.0f)
                        .texOffs(GOLD_U, GOLD_V).addBox(-6.0f, -6.0f, -3.0f, 12.0f, 4.0f, 6.0f)
                        .texOffs(GOLD_U, GOLD_V).addBox(-3.0f, -6.0f, -6.0f, 6.0f, 4.0f, 12.0f)
                        .texOffs(GOLD_U, GOLD_V).addBox(-6.0f, 2.0f, -3.0f, 12.0f, 4.0f, 6.0f)
                        .texOffs(GOLD_U, GOLD_V).addBox(-3.0f, 2.0f, -6.0f, 6.0f, 4.0f, 12.0f),
                PartPose.rotation(0.0f, DEG45, 0.0f));
        // Dark equator band, slightly proud of the dome.
        head.addOrReplaceChild("band",
                CubeListBuilder.create()
                        .texOffs(DARK_U, DARK_V).addBox(-8.5f, -1.0f, -4.5f, 17.0f, 2.0f, 9.0f)
                        .texOffs(DARK_U, DARK_V).addBox(-4.5f, -1.0f, -8.5f, 9.0f, 2.0f, 17.0f),
                PartPose.ZERO);
        head.addOrReplaceChild("bandDiag",
                CubeListBuilder.create()
                        .texOffs(DARK_U, DARK_V).addBox(-8.5f, -1.0f, -4.5f, 17.0f, 2.0f, 9.0f)
                        .texOffs(DARK_U, DARK_V).addBox(-4.5f, -1.0f, -8.5f, 9.0f, 2.0f, 17.0f),
                PartPose.rotation(0.0f, DEG45, 0.0f));
        // Two purple eyes on the upper front face. Glows.
        head.addOrReplaceChild("eyes",
                CubeListBuilder.create()
                        .texOffs(PURPLE_U, PURPLE_V).addBox(-6.0f, -3.0f, 7.0f, 4.0f, 3.0f, 3.0f)
                        .texOffs(PURPLE_U, PURPLE_V).addBox(2.0f, -3.0f, 7.0f, 4.0f, 3.0f, 3.0f),
                PartPose.ZERO);
        // Blue accent panels on the flanks. Glows.
        head.addOrReplaceChild("accents",
                CubeListBuilder.create()
                        .texOffs(BLUE_U, BLUE_V).addBox(-9.0f, -2.0f, -2.0f, 2.0f, 5.0f, 6.0f)
                        .texOffs(BLUE_U, BLUE_V).addBox(7.0f, -2.0f, -2.0f, 2.0f, 5.0f, 6.0f),
                PartPose.ZERO);

        return LayerDefinition.create(mesh, 256, 256);
    }

    /**
     * Adds one raised armour petal pointing outward along {@code yaw} (0 = +Z / north): a wide inner
     * plate flush with the hub plus a narrower, raised outer tip that reaches the base radius (24),
     * faking a pointed, lifted plate.
     */
    private static void addPetal(PartDefinition root, String name, float yaw) {
        root.addOrReplaceChild(name,
                CubeListBuilder.create()
                        .texOffs(GOLD_U, GOLD_V).addBox(-6.0f, -4.0f, 6.0f, 12.0f, 4.0f, 12.0f)
                        .texOffs(GOLD_U, GOLD_V).addBox(-4.0f, -6.0f, 16.0f, 8.0f, 4.0f, 8.0f),
                PartPose.offsetAndRotation(0.0f, 24.0f, 0.0f, 0.0f, yaw, 0.0f));
    }
}
