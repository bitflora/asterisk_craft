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
 * <p>Colour comes from {@code textures/entity/photon_cannon.png}, in which <b>every cube owns its own
 * UV island</b> so it can be hand-painted independently — see {@code tools/blockbench_export.py},
 * which packs those islands and emits the Blockbench project. Only the lens, accent panels and eyes
 * are painted in the companion {@code photon_cannon_glow.png}, so the emissive
 * {@link UnitGlowLayer} lights just those parts.
 *
 * <p>Authored in true pixel space (16px = 1 block), feet at {@code y=24}, "up" is negative y, so the
 * renderer applies no extra scale. Static: the turret doesn't animate; directionality comes from the
 * bolt's beam, so {@link #setupAnim} is a no-op.
 */
public class PhotonCannonModel extends EntityModel<LivingEntityRenderState> {
    private static final float DEG45 = 0.7853981633974483f;
    private static final float LENS_PITCH = -0.35f; // tilt the drum back so the lens faces forward-and-up


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
                        .texOffs(0, 52).addBox(-12.0f, -6.0f, -6.0f, 24.0f, 6.0f, 12.0f)
                        .texOffs(0, 0).addBox(-6.0f, -6.0f, -12.0f, 12.0f, 6.0f, 24.0f),
                PartPose.offset(0.0f, 24.0f, 0.0f));
        root.addOrReplaceChild("hubDiag",
                CubeListBuilder.create()
                        .texOffs(73, 52).addBox(-12.0f, -6.0f, -6.0f, 24.0f, 6.0f, 12.0f)
                        .texOffs(73, 0).addBox(-6.0f, -6.0f, -12.0f, 12.0f, 6.0f, 24.0f),
                PartPose.offsetAndRotation(0.0f, 24.0f, 0.0f, 0.0f, DEG45, 0.0f));

        // Eight petals radiating from the hub — each a wide inner plate + a narrower raised outer tip.
        // Written out rather than looped: every cube needs its own editable texOffs literal so it can
        // own a UV island and be hand-painted independently (see tools/blockbench_export.py). A loop
        // would force all sixteen petal cubes through two shared offsets.
        root.addOrReplaceChild("petal0",
                CubeListBuilder.create()
                        .texOffs(0, 71).addBox(-6.0f, -4.0f, 6.0f, 12.0f, 4.0f, 12.0f)
                        .texOffs(172, 105).addBox(-4.0f, -6.0f, 16.0f, 8.0f, 4.0f, 8.0f),
                PartPose.offsetAndRotation(0.0f, 24.0f, 0.0f, 0.0f, 0.0f * DEG45, 0.0f));
        root.addOrReplaceChild("petal1",
                CubeListBuilder.create()
                        .texOffs(49, 71).addBox(-6.0f, -4.0f, 6.0f, 12.0f, 4.0f, 12.0f)
                        .texOffs(205, 105).addBox(-4.0f, -6.0f, 16.0f, 8.0f, 4.0f, 8.0f),
                PartPose.offsetAndRotation(0.0f, 24.0f, 0.0f, 0.0f, 1.0f * DEG45, 0.0f));
        root.addOrReplaceChild("petal2",
                CubeListBuilder.create()
                        .texOffs(98, 71).addBox(-6.0f, -4.0f, 6.0f, 12.0f, 4.0f, 12.0f)
                        .texOffs(0, 122).addBox(-4.0f, -6.0f, 16.0f, 8.0f, 4.0f, 8.0f),
                PartPose.offsetAndRotation(0.0f, 24.0f, 0.0f, 0.0f, 2.0f * DEG45, 0.0f));
        root.addOrReplaceChild("petal3",
                CubeListBuilder.create()
                        .texOffs(147, 71).addBox(-6.0f, -4.0f, 6.0f, 12.0f, 4.0f, 12.0f)
                        .texOffs(33, 122).addBox(-4.0f, -6.0f, 16.0f, 8.0f, 4.0f, 8.0f),
                PartPose.offsetAndRotation(0.0f, 24.0f, 0.0f, 0.0f, 3.0f * DEG45, 0.0f));
        root.addOrReplaceChild("petal4",
                CubeListBuilder.create()
                        .texOffs(196, 71).addBox(-6.0f, -4.0f, 6.0f, 12.0f, 4.0f, 12.0f)
                        .texOffs(66, 122).addBox(-4.0f, -6.0f, 16.0f, 8.0f, 4.0f, 8.0f),
                PartPose.offsetAndRotation(0.0f, 24.0f, 0.0f, 0.0f, 4.0f * DEG45, 0.0f));
        root.addOrReplaceChild("petal5",
                CubeListBuilder.create()
                        .texOffs(0, 88).addBox(-6.0f, -4.0f, 6.0f, 12.0f, 4.0f, 12.0f)
                        .texOffs(99, 122).addBox(-4.0f, -6.0f, 16.0f, 8.0f, 4.0f, 8.0f),
                PartPose.offsetAndRotation(0.0f, 24.0f, 0.0f, 0.0f, 5.0f * DEG45, 0.0f));
        root.addOrReplaceChild("petal6",
                CubeListBuilder.create()
                        .texOffs(49, 88).addBox(-6.0f, -4.0f, 6.0f, 12.0f, 4.0f, 12.0f)
                        .texOffs(132, 122).addBox(-4.0f, -6.0f, 16.0f, 8.0f, 4.0f, 8.0f),
                PartPose.offsetAndRotation(0.0f, 24.0f, 0.0f, 0.0f, 6.0f * DEG45, 0.0f));
        root.addOrReplaceChild("petal7",
                CubeListBuilder.create()
                        .texOffs(98, 88).addBox(-6.0f, -4.0f, 6.0f, 12.0f, 4.0f, 12.0f)
                        .texOffs(165, 122).addBox(-4.0f, -6.0f, 16.0f, 8.0f, 4.0f, 8.0f),
                PartPose.offsetAndRotation(0.0f, 24.0f, 0.0f, 0.0f, 7.0f * DEG45, 0.0f));

        // --- Lens drum: a gold ring holding a glowing blue lens, tilted back to face forward-and-up. ---
        PartDefinition lens = root.addOrReplaceChild("lens",
                CubeListBuilder.create(),
                PartPose.offsetAndRotation(0.0f, 10.0f, 12.0f, LENS_PITCH, 0.0f, 0.0f));
        // Square gold frame (front is +Z) + a 45° copy → an octagonal ring. Built inline rather than
        // through a shared CubeListBuilder variable so each cube's texOffs sits at its own call site.
        lens.addOrReplaceChild("ring",
                CubeListBuilder.create()
                        .texOffs(111, 147).addBox(-9.0f, -11.0f, -2.0f, 18.0f, 3.0f, 4.0f)
                        .texOffs(156, 147).addBox(-9.0f, 8.0f, -2.0f, 18.0f, 3.0f, 4.0f)
                        .texOffs(179, 0).addBox(-11.0f, -9.0f, -2.0f, 3.0f, 18.0f, 4.0f)
                        .texOffs(194, 0).addBox(8.0f, -9.0f, -2.0f, 3.0f, 18.0f, 4.0f),
                PartPose.ZERO);
        lens.addOrReplaceChild("ringDiag",
                CubeListBuilder.create()
                        .texOffs(201, 147).addBox(-9.0f, -11.0f, -2.0f, 18.0f, 3.0f, 4.0f)
                        .texOffs(0, 158).addBox(-9.0f, 8.0f, -2.0f, 18.0f, 3.0f, 4.0f)
                        .texOffs(209, 0).addBox(-11.0f, -9.0f, -2.0f, 3.0f, 18.0f, 4.0f)
                        .texOffs(224, 0).addBox(8.0f, -9.0f, -2.0f, 3.0f, 18.0f, 4.0f),
                PartPose.rotation(0.0f, 0.0f, DEG45));
        // Dark socket behind the lens, so the glow reads against black.
        lens.addOrReplaceChild("socket",
                CubeListBuilder.create()
                        .texOffs(146, 52).addBox(-8.0f, -8.0f, -3.0f, 16.0f, 16.0f, 2.0f),
                PartPose.ZERO);
        // Blue lens disc (crossed planks → round), sitting at the front of the frame. Glows.
        lens.addOrReplaceChild("disc",
                CubeListBuilder.create()
                        .texOffs(37, 147).addBox(-8.0f, -4.0f, 0.0f, 16.0f, 8.0f, 2.0f)
                        .texOffs(183, 52).addBox(-4.0f, -8.0f, 0.0f, 8.0f, 16.0f, 2.0f),
                PartPose.ZERO);
        lens.addOrReplaceChild("discDiag",
                CubeListBuilder.create()
                        .texOffs(74, 147).addBox(-8.0f, -4.0f, 0.0f, 16.0f, 8.0f, 2.0f)
                        .texOffs(204, 52).addBox(-4.0f, -8.0f, 0.0f, 8.0f, 16.0f, 2.0f),
                PartPose.rotation(0.0f, 0.0f, DEG45));

        // --- Domed head on a neck, above and behind the drum, with a dark band, purple eyes, accents. ---
        PartDefinition head = root.addOrReplaceChild("head",
                CubeListBuilder.create(),
                PartPose.offset(0.0f, -6.0f, -4.0f));
        head.addOrReplaceChild("neck",
                CubeListBuilder.create()
                        .texOffs(146, 0).addBox(-4.0f, 4.0f, -3.0f, 8.0f, 14.0f, 8.0f),
                PartPose.ZERO);
        // Dome: three sphere-profile plank-rings + a top cap, with a 45° copy of the rings.
        head.addOrReplaceChild("dome",
                CubeListBuilder.create()
                        .texOffs(74, 105).addBox(-8.0f, -2.0f, -4.0f, 16.0f, 4.0f, 8.0f)
                        .texOffs(0, 31).addBox(-4.0f, -2.0f, -8.0f, 8.0f, 4.0f, 16.0f)
                        .texOffs(120, 135).addBox(-6.0f, -6.0f, -3.0f, 12.0f, 4.0f, 6.0f)
                        .texOffs(147, 88).addBox(-3.0f, -6.0f, -6.0f, 6.0f, 4.0f, 12.0f)
                        .texOffs(157, 135).addBox(-6.0f, 2.0f, -3.0f, 12.0f, 4.0f, 6.0f)
                        .texOffs(184, 88).addBox(-3.0f, 2.0f, -6.0f, 6.0f, 4.0f, 12.0f)
                        .texOffs(53, 135).addBox(-4.0f, -9.0f, -4.0f, 8.0f, 3.0f, 8.0f),
                PartPose.ZERO);
        head.addOrReplaceChild("domeDiag",
                CubeListBuilder.create()
                        .texOffs(123, 105).addBox(-8.0f, -2.0f, -4.0f, 16.0f, 4.0f, 8.0f)
                        .texOffs(49, 31).addBox(-4.0f, -2.0f, -8.0f, 8.0f, 4.0f, 16.0f)
                        .texOffs(194, 135).addBox(-6.0f, -6.0f, -3.0f, 12.0f, 4.0f, 6.0f)
                        .texOffs(0, 105).addBox(-3.0f, -6.0f, -6.0f, 6.0f, 4.0f, 12.0f)
                        .texOffs(0, 147).addBox(-6.0f, 2.0f, -3.0f, 12.0f, 4.0f, 6.0f)
                        .texOffs(37, 105).addBox(-3.0f, 2.0f, -6.0f, 6.0f, 4.0f, 12.0f),
                PartPose.rotation(0.0f, DEG45, 0.0f));
        // Dark equator band, slightly proud of the dome.
        head.addOrReplaceChild("band",
                CubeListBuilder.create()
                        .texOffs(198, 122).addBox(-8.5f, -1.0f, -4.5f, 17.0f, 2.0f, 9.0f)
                        .texOffs(98, 31).addBox(-4.5f, -1.0f, -8.5f, 9.0f, 2.0f, 17.0f),
                PartPose.ZERO);
        head.addOrReplaceChild("bandDiag",
                CubeListBuilder.create()
                        .texOffs(0, 135).addBox(-8.5f, -1.0f, -4.5f, 17.0f, 2.0f, 9.0f)
                        .texOffs(151, 31).addBox(-4.5f, -1.0f, -8.5f, 9.0f, 2.0f, 17.0f),
                PartPose.rotation(0.0f, DEG45, 0.0f));
        // Two purple eyes on the upper front face. Glows.
        head.addOrReplaceChild("eyes",
                CubeListBuilder.create()
                        .texOffs(45, 158).addBox(-6.0f, -3.0f, 7.0f, 4.0f, 3.0f, 3.0f)
                        .texOffs(60, 158).addBox(2.0f, -3.0f, 7.0f, 4.0f, 3.0f, 3.0f),
                PartPose.ZERO);
        // Blue accent panels on the flanks. Glows.
        head.addOrReplaceChild("accents",
                CubeListBuilder.create()
                        .texOffs(86, 135).addBox(-9.0f, -2.0f, -2.0f, 2.0f, 5.0f, 6.0f)
                        .texOffs(103, 135).addBox(7.0f, -2.0f, -2.0f, 2.0f, 5.0f, 6.0f),
                PartPose.ZERO);

        return LayerDefinition.create(mesh, 256, 256);
    }

}
