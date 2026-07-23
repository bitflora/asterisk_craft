package net.bitflora.asteriskcraft.client.zerg;

import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.util.Mth;

/**
 * Original blocky Zergling model authored for AsteriskCraft — a low, crouched, vanilla-style bug built
 * from a handful of boxes (no ported geometry). Silhouette cues: a hunched carapace body, a forward head
 * with bone mandibles and glowing eyes (the emissive zone lit by
 * {@link net.bitflora.asteriskcraft.client.UnitGlowLayer}), clawed front legs, hind legs, a pair of
 * raised back scythes, and a stub tail. Each cube owns its own UV island; the texture is hand-painted in Blockbench via tools/blockbench_export.py.
 * Head-look plus a simple limb-swing walk are driven in setupAnim; armL/armR are the front legs.
 */
public class ZerglingModel extends EntityModel<LivingEntityRenderState> {
    private final ModelPart head;
    private final ModelPart legL;
    private final ModelPart legR;
    private final ModelPart armL;
    private final ModelPart armR;
    private final float headX0;
    private final float legLX0;
    private final float legRX0;
    private final float armLX0;
    private final float armRX0;

    public ZerglingModel(ModelPart root) {
        super(root);
        this.head = root.getChild("body").getChild("head");
        this.legL = root.getChild("body").getChild("legL");
        this.legR = root.getChild("body").getChild("legR");
        this.armL = root.getChild("body").getChild("armL");
        this.armR = root.getChild("body").getChild("armR");
        this.headX0 = this.head.xRot;
        this.legLX0 = this.legL.xRot;
        this.legRX0 = this.legR.xRot;
        this.armLX0 = this.armL.xRot;
        this.armRX0 = this.armR.xRot;
    }

    @Override
    public void setupAnim(LivingEntityRenderState state) {
        super.setupAnim(state);
        this.head.yRot = state.yRot * ((float) Math.PI / 180f);
        this.head.xRot = this.headX0 + state.xRot * ((float) Math.PI / 180f);
        float swing = Mth.cos(state.walkAnimationPos * 0.6662f) * 1.2f * state.walkAnimationSpeed;
        this.legL.xRot = this.legLX0 + swing;
        this.legR.xRot = this.legRX0 - swing;
        this.armL.xRot = this.armLX0 - swing;
        this.armR.xRot = this.armRX0 + swing;
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();

        // Each cube's texOffs points at its own packed UV island — do not hand-edit these; they are
        // assigned by tools/blockbench_export.py and guarded by ModelUvLayoutTest.
        // Body sits low; the whole creature hangs off "body" so it reads as one crouched mass.
        PartDefinition body = root.addOrReplaceChild("body",
                CubeListBuilder.create().texOffs(0, 0).addBox(-4.0f, -3.0f, -5.0f, 8.0f, 6.0f, 10.0f),
                PartPose.offsetAndRotation(0.0f, 18.0f, 0.0f, 0.05f, 0.0f, 0.0f));

        // --- Head (pivot at the front of the body) ----------------------------------------
        PartDefinition head = body.addOrReplaceChild("head",
                CubeListBuilder.create().texOffs(63, 0).addBox(-3.5f, -2.5f, -6.0f, 7.0f, 5.0f, 6.0f),
                PartPose.offset(0.0f, 0.5f, -5.0f));
        head.addOrReplaceChild("mandibleL",
                CubeListBuilder.create().texOffs(29, 17).addBox(-0.5f, -0.5f, -4.0f, 1.0f, 1.0f, 4.0f),
                PartPose.offsetAndRotation(2.2f, 2.0f, -5.5f, 0.0f, -0.25f, 0.0f));
        head.addOrReplaceChild("mandibleR",
                CubeListBuilder.create().texOffs(40, 17).addBox(-0.5f, -0.5f, -4.0f, 1.0f, 1.0f, 4.0f),
                PartPose.offsetAndRotation(-2.2f, 2.0f, -5.5f, 0.0f, 0.25f, 0.0f));
        head.addOrReplaceChild("eyeL",
                CubeListBuilder.create().texOffs(51, 17).addBox(-0.75f, -0.75f, -0.5f, 1.5f, 1.5f, 1.0f),
                PartPose.offset(2.0f, -1.0f, -6.1f));
        head.addOrReplaceChild("eyeR",
                CubeListBuilder.create().texOffs(57, 17).addBox(-0.75f, -0.75f, -0.5f, 1.5f, 1.5f, 1.0f),
                PartPose.offset(-2.0f, -1.0f, -6.1f));

        // --- Front legs (clawed, pivot at the shoulders; reach forward-down to the ground) --
        body.addOrReplaceChild("armL",
                CubeListBuilder.create().texOffs(37, 0).addBox(-1.5f, 0.0f, -1.5f, 3.0f, 9.0f, 3.0f),
                PartPose.offsetAndRotation(4.0f, 2.0f, -3.5f, 0.45f, 0.0f, -0.2f));
        body.addOrReplaceChild("armR",
                CubeListBuilder.create().texOffs(50, 0).addBox(-1.5f, 0.0f, -1.5f, 3.0f, 9.0f, 3.0f),
                PartPose.offsetAndRotation(-4.0f, 2.0f, -3.5f, 0.45f, 0.0f, 0.2f));

        // --- Hind legs (pivot at the hips) ------------------------------------------------
        body.addOrReplaceChild("legL",
                CubeListBuilder.create().texOffs(90, 0).addBox(-1.5f, 0.0f, -1.5f, 3.0f, 8.0f, 3.0f),
                PartPose.offsetAndRotation(4.0f, 2.5f, 3.5f, -0.1f, 0.0f, -0.35f));
        body.addOrReplaceChild("legR",
                CubeListBuilder.create().texOffs(103, 0).addBox(-1.5f, 0.0f, -1.5f, 3.0f, 8.0f, 3.0f),
                PartPose.offsetAndRotation(-4.0f, 2.5f, 3.5f, -0.1f, 0.0f, 0.35f));

        // --- Raised back scythes (decorative silhouette) ----------------------------------
        body.addOrReplaceChild("scytheL",
                CubeListBuilder.create().texOffs(116, 0).addBox(-0.75f, -9.0f, -1.0f, 1.5f, 9.0f, 2.0f),
                PartPose.offsetAndRotation(2.5f, -2.5f, 2.5f, -0.5f, 0.0f, -0.35f));
        body.addOrReplaceChild("scytheR",
                CubeListBuilder.create().texOffs(0, 17).addBox(-0.75f, -9.0f, -1.0f, 1.5f, 9.0f, 2.0f),
                PartPose.offsetAndRotation(-2.5f, -2.5f, 2.5f, -0.5f, 0.0f, 0.35f));

        // --- Tail --------------------------------------------------------------------------
        body.addOrReplaceChild("tail",
                CubeListBuilder.create().texOffs(8, 17).addBox(-1.5f, -1.5f, 0.0f, 3.0f, 3.0f, 7.0f),
                PartPose.offsetAndRotation(0.0f, 1.5f, 4.5f, 0.35f, 0.0f, 0.0f));

        return LayerDefinition.create(mesh, 128, 64);
    }
}
