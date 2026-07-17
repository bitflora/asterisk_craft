package net.bitflora.asteriskcraft.client;

import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.util.Mth;

/**
 * Ported from the 1.12 StarCraft mod's ModelZergling (mechanical box translation; see
 * tools/model_convert.py). Head-look + a simple limb-swing walk are re-authored here; the old
 * per-render tweaks (neck scale, blade-sheath toggle) are intentionally not reproduced.
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
        this.head = root.getChild("chest").getChild("neck").getChild("head1");
        this.legL = root.getChild("chest").getChild("lowerbody").getChild("lThigh");
        this.legR = root.getChild("chest").getChild("lowerbody").getChild("rThigh");
        this.armL = root.getChild("chest").getChild("lShoulder");
        this.armR = root.getChild("chest").getChild("rShoulder");
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
        float swing = Mth.cos(state.walkAnimationPos * 0.6662f) * 1.4f * state.walkAnimationSpeed;
        this.legL.xRot = this.legLX0 + swing;
        this.legR.xRot = this.legRX0 - swing;
        this.armL.xRot = this.armLX0 - swing;
        this.armR.xRot = this.armRX0 + swing;
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        PartDefinition chest = root.addOrReplaceChild("chest",
                CubeListBuilder.create().texOffs(0, 0).addBox(-3.5f, -2.7f, -4.5f, 7.0f, 6.0f, 8.0f),
                PartPose.offset(0.0f, 16.2f, 0.0f));
        PartDefinition rHookArm1 = chest.addOrReplaceChild("rHookArm1",
                CubeListBuilder.create().texOffs(83, 10).mirror().addBox(-0.5f, -7.5f, -0.6f, 1.0f, 8.0f, 1.0f),
                PartPose.offsetAndRotation(-3.2f, -1.3f, -3.4f, -0.40980330836826856f, 0.0f, -0.22759093446006054f));
        PartDefinition rHookArm2_1 = rHookArm1.addOrReplaceChild("rHookArm2_1",
                CubeListBuilder.create().texOffs(80, 22).mirror().addBox(-1.0f, -1.0f, -3.8f, 2.0f, 2.0f, 4.0f),
                PartPose.offsetAndRotation(0.0f, -6.7f, 0.0f, -0.27314402793711257f, 0.0f, 0.0f));
        PartDefinition rHookArm3 = rHookArm2_1.addOrReplaceChild("rHookArm3",
                CubeListBuilder.create().texOffs(78, 31).mirror().addBox(-1.5f, -1.5f, -4.7f, 3.0f, 3.0f, 5.0f),
                PartPose.offsetAndRotation(0.0f, 0.0f, -3.8f, 0.27314402793711257f, 0.0f, 0.0f));
        PartDefinition rHookArmClaw3 = rHookArm3.addOrReplaceChild("rHookArmClaw3",
                CubeListBuilder.create().texOffs(87, 0).mirror().addBox(-1.0f, 0.0f, -1.0f, 2.0f, 3.0f, 2.0f),
                PartPose.offsetAndRotation(0.0f, 0.0f, -3.5f, -0.31869712141416456f, 0.7853981633974483f, -0.31869712141416456f));
        PartDefinition rHookArmClaw4a = rHookArmClaw3.addOrReplaceChild("rHookArmClaw4a",
                CubeListBuilder.create().texOffs(88, 1).addBox(-0.8f, 0.0f, -0.8f, 1.0f, 2.0f, 1.0f),
                PartPose.offsetAndRotation(0.0f, 2.5f, 0.0f, 0.136659280431156f, 0.0f, 0.136659280431156f));
        rHookArmClaw4a.addOrReplaceChild("rHookArmClaw5",
                CubeListBuilder.create().texOffs(88, 1).mirror().addBox(-0.5f, 0.0f, -0.5f, 1.0f, 3.0f, 1.0f),
                PartPose.offsetAndRotation(0.0f, 1.6f, 0.0f, 0.091106186954104f, 0.0f, 0.091106186954104f));
        rHookArmClaw4a.addOrReplaceChild("rHookArmClaw4c",
                CubeListBuilder.create().texOffs(88, 1).mirror().addBox(-0.2f, 0.0f, -0.2f, 1.0f, 2.0f, 1.0f),
                PartPose.offset(0.0f, 0.0f, 0.0f));
        rHookArmClaw4a.addOrReplaceChild("rHookArmClaw4b_1",
                CubeListBuilder.create().texOffs(88, 1).mirror().addBox(-0.8f, 0.0f, -0.2f, 1.0f, 2.0f, 1.0f),
                PartPose.offset(0.0f, 0.0f, 0.0f));
        rHookArmClaw4a.addOrReplaceChild("rHookArmClaw4b",
                CubeListBuilder.create().texOffs(89, 1).addBox(-0.2f, 0.0f, -0.8f, 1.0f, 2.0f, 1.0f),
                PartPose.offset(0.0f, 0.0f, 0.0f));
        PartDefinition rHookArmClaw1a = rHookArm3.addOrReplaceChild("rHookArmClaw1a",
                CubeListBuilder.create().texOffs(88, 0).mirror().addBox(-0.8f, 0.0f, -0.8f, 1.0f, 2.0f, 1.0f),
                PartPose.offsetAndRotation(0.0f, 1.0f, -1.5f, 0.136659280431156f, 0.7853981633974483f, 0.136659280431156f));
        rHookArmClaw1a.addOrReplaceChild("rHookArmClaw1b",
                CubeListBuilder.create().texOffs(88, 0).mirror().addBox(-0.8f, 0.0f, -0.2f, 1.0f, 2.0f, 1.0f),
                PartPose.offset(0.0f, 0.0f, 0.0f));
        rHookArmClaw1a.addOrReplaceChild("rHookArmClaw1c",
                CubeListBuilder.create().texOffs(88, 0).mirror().addBox(-0.2f, 0.0f, -0.2f, 1.0f, 2.0f, 1.0f),
                PartPose.offset(0.0f, 0.0f, 0.0f));
        rHookArmClaw1a.addOrReplaceChild("lHookArmClaw1b_2",
                CubeListBuilder.create().texOffs(88, 0).addBox(-0.2f, 0.0f, -0.8f, 1.0f, 2.0f, 1.0f),
                PartPose.offset(0.0f, 0.0f, 0.0f));
        rHookArmClaw1a.addOrReplaceChild("rHookArmClaw2",
                CubeListBuilder.create().texOffs(88, 0).mirror().addBox(-0.5f, 0.0f, -0.5f, 1.0f, 2.0f, 1.0f),
                PartPose.offsetAndRotation(0.0f, 1.6f, 0.0f, 0.091106186954104f, 0.0f, 0.091106186954104f));
        rHookArm2_1.addOrReplaceChild("rHookArmClaw0",
                CubeListBuilder.create().texOffs(88, 0).mirror().addBox(-0.5f, 0.0f, -0.5f, 1.0f, 2.0f, 1.0f),
                PartPose.offsetAndRotation(0.0f, 0.3f, -2.8f, 0.36425021489121656f, 0.7853981633974483f, 0.36425021489121656f));
        PartDefinition neck = chest.addOrReplaceChild("neck",
                CubeListBuilder.create().texOffs(21, 40).addBox(-2.5f, -2.0f, -2.6f, 5.0f, 4.0f, 3.0f),
                PartPose.offsetAndRotation(0.0f, 0.0f, -4.2f, 0.22759093446006054f, 0.0f, 0.0f));
        PartDefinition head1 = neck.addOrReplaceChild("head1",
                CubeListBuilder.create().texOffs(110, 0).addBox(-2.5f, -2.1f, -0.7f, 5.0f, 4.0f, 2.0f),
                PartPose.offset(0.0f, -0.2f, -2.4f));
        PartDefinition lowerJaw = head1.addOrReplaceChild("lowerJaw",
                CubeListBuilder.create().texOffs(110, 29).addBox(-2.0f, -0.5f, -3.0f, 4.0f, 1.0f, 3.0f),
                PartPose.offset(0.0f, 1.8f, 0.7f));
        lowerJaw.addOrReplaceChild("teethLower",
                CubeListBuilder.create().texOffs(110, 23).addBox(-2.0f, -1.3f, -2.9f, 4.0f, 1.0f, 3.0f),
                PartPose.offset(0.0f, 0.0f, 0.0f));
        PartDefinition rTusk1 = head1.addOrReplaceChild("rTusk1",
                CubeListBuilder.create().texOffs(31, 0).mirror().addBox(-1.0f, -1.0f, -1.9f, 2.0f, 2.0f, 2.0f),
                PartPose.offsetAndRotation(-1.8f, 0.7f, 1.3f, 0.31869712141416456f, 0.9105382707654417f, 0.0f));
        PartDefinition rTusk2 = rTusk1.addOrReplaceChild("rTusk2",
                CubeListBuilder.create().texOffs(31, 0).mirror().addBox(-1.0f, -1.0f, -1.8f, 2.0f, 2.0f, 2.0f),
                PartPose.offsetAndRotation(0.0f, 0.0f, -1.8f, 0.0f, -0.31869712141416456f, 0.0f));
        PartDefinition rArmClaw3a = rTusk2.addOrReplaceChild("rArmClaw3a",
                CubeListBuilder.create().texOffs(88, 0).addBox(-0.8f, -0.7f, -1.7f, 1.0f, 1.0f, 2.0f),
                PartPose.offsetAndRotation(0.0f, -0.1f, -1.1f, 0.0f, -0.22759093446006054f, 0.0f));
        rArmClaw3a.addOrReplaceChild("rArmClaw4",
                CubeListBuilder.create().texOffs(88, 0).addBox(-0.5f, -0.5f, -1.8f, 1.0f, 1.0f, 2.0f),
                PartPose.offsetAndRotation(0.0f, 0.1f, -1.6f, 0.045553093477052f, -0.22759093446006054f, -0.091106186954104f));
        rArmClaw3a.addOrReplaceChild("rArmClaw3b_1",
                CubeListBuilder.create().texOffs(88, 0).addBox(-0.8f, 0.0f, -1.7f, 1.0f, 1.0f, 2.0f),
                PartPose.offset(0.0f, 0.0f, 0.0f));
        rArmClaw3a.addOrReplaceChild("rArmClaw3c",
                CubeListBuilder.create().texOffs(88, 0).addBox(-0.2f, 0.0f, -1.7f, 1.0f, 1.0f, 1.0f),
                PartPose.offset(0.0f, 0.0f, 0.0f));
        rArmClaw3a.addOrReplaceChild("rArmClaw3b",
                CubeListBuilder.create().texOffs(88, 0).addBox(-0.2f, -0.7f, -1.7f, 1.0f, 1.0f, 1.0f),
                PartPose.offset(0.0f, 0.0f, 0.0f));
        head1.addOrReplaceChild("head2",
                CubeListBuilder.create().texOffs(112, 8).addBox(-2.0f, -2.0f, -1.1f, 4.0f, 1.0f, 1.0f),
                PartPose.offsetAndRotation(0.0f, 0.0f, 0.0f, 0.31869712141416456f, 0.0f, 0.0f));
        PartDefinition lTusk1 = head1.addOrReplaceChild("lTusk1",
                CubeListBuilder.create().texOffs(31, 0).addBox(-1.0f, -1.0f, -1.9f, 2.0f, 2.0f, 2.0f),
                PartPose.offsetAndRotation(1.8f, 0.7f, 1.3f, 0.31869712141416456f, -0.9105382707654417f, 0.0f));
        PartDefinition lTusk2 = lTusk1.addOrReplaceChild("lTusk2",
                CubeListBuilder.create().texOffs(31, 0).addBox(-1.0f, -1.0f, -1.8f, 2.0f, 2.0f, 2.0f),
                PartPose.offsetAndRotation(0.0f, 0.0f, -1.8f, 0.0f, 0.31869712141416456f, 0.0f));
        PartDefinition lArmClaw3a = lTusk2.addOrReplaceChild("lArmClaw3a",
                CubeListBuilder.create().texOffs(88, 0).addBox(-0.8f, -0.7f, -1.7f, 1.0f, 1.0f, 1.0f),
                PartPose.offsetAndRotation(0.0f, -0.1f, -1.1f, 0.0f, 0.22759093446006054f, 0.0f));
        lArmClaw3a.addOrReplaceChild("lArmClaw3b",
                CubeListBuilder.create().texOffs(88, 0).addBox(-0.2f, -0.7f, -1.7f, 1.0f, 1.0f, 2.0f),
                PartPose.offset(0.0f, 0.0f, 0.0f));
        lArmClaw3a.addOrReplaceChild("lArmClaw3b_1",
                CubeListBuilder.create().texOffs(88, 0).addBox(-0.8f, 0.0f, -1.7f, 1.0f, 1.0f, 1.0f),
                PartPose.offset(0.0f, 0.0f, 0.0f));
        lArmClaw3a.addOrReplaceChild("lArmClaw3c",
                CubeListBuilder.create().texOffs(88, 0).addBox(-0.2f, 0.0f, -1.7f, 1.0f, 1.0f, 2.0f),
                PartPose.offset(0.0f, 0.0f, 0.0f));
        lArmClaw3a.addOrReplaceChild("lArmClaw4",
                CubeListBuilder.create().texOffs(88, 0).addBox(-0.5f, -0.5f, -1.8f, 1.0f, 1.0f, 2.0f),
                PartPose.offsetAndRotation(0.0f, 0.1f, -1.6f, 0.045553093477052f, 0.22759093446006054f, 0.091106186954104f));
        head1.addOrReplaceChild("jawUpper",
                CubeListBuilder.create().texOffs(110, 15).addBox(-2.0f, -1.0f, -2.1f, 4.0f, 2.0f, 3.0f),
                PartPose.offsetAndRotation(0.0f, 0.0f, 0.0f, -0.27314402793711257f, 0.0f, 0.0f));
        PartDefinition rShoulder = chest.addOrReplaceChild("rShoulder",
                CubeListBuilder.create().texOffs(67, 0).mirror().addBox(-2.0f, -0.7f, -1.5f, 2.0f, 3.0f, 3.0f),
                PartPose.offsetAndRotation(-2.6f, -0.2f, -2.0f, 0.091106186954104f, 0.0f, 0.5918411493512771f));
        PartDefinition rArm1 = rShoulder.addOrReplaceChild("rArm1",
                CubeListBuilder.create().texOffs(70, 8).mirror().addBox(-0.5f, -0.5f, -1.0f, 1.0f, 4.0f, 2.0f),
                PartPose.offsetAndRotation(-0.8f, 1.9f, 0.0f, 0.27314402793711257f, 0.0f, -0.136659280431156f));
        PartDefinition rArm2 = rArm1.addOrReplaceChild("rArm2",
                CubeListBuilder.create().texOffs(64, 16).mirror().addBox(-1.0f, -1.0f, -4.0f, 2.0f, 2.0f, 5.0f),
                PartPose.offsetAndRotation(0.5f, 3.4f, 0.0f, -0.136659280431156f, 0.0f, -0.4553564018453205f));
        PartDefinition rArmClaw1a = rArm2.addOrReplaceChild("rArmClaw1a",
                CubeListBuilder.create().texOffs(88, 0).mirror().addBox(-0.8f, 0.0f, -0.8f, 1.0f, 2.0f, 1.0f),
                PartPose.offsetAndRotation(0.0f, 0.2f, -3.1f, 0.4553564018453205f, 0.7853981633974483f, 0.4553564018453205f));
        rArmClaw1a.addOrReplaceChild("rArmClaw2",
                CubeListBuilder.create().texOffs(88, 0).mirror().addBox(-0.5f, 0.0f, -0.5f, 1.0f, 2.0f, 1.0f),
                PartPose.offsetAndRotation(0.0f, 1.6f, 0.0f, 0.091106186954104f, 0.0f, 0.091106186954104f));
        rArmClaw1a.addOrReplaceChild("rArmClaw1c",
                CubeListBuilder.create().texOffs(88, 0).mirror().addBox(-0.2f, 0.0f, -0.2f, 1.0f, 2.0f, 1.0f),
                PartPose.offset(0.0f, 0.0f, 0.0f));
        rArmClaw1a.addOrReplaceChild("rArmClaw1b",
                CubeListBuilder.create().texOffs(88, 0).mirror().addBox(-0.2f, 0.0f, -0.8f, 1.0f, 2.0f, 1.0f),
                PartPose.offset(0.0f, 0.0f, 0.0f));
        rArmClaw1a.addOrReplaceChild("rArmClaw1b_1",
                CubeListBuilder.create().texOffs(88, 0).mirror().addBox(-0.8f, 0.0f, -0.2f, 1.0f, 2.0f, 1.0f),
                PartPose.offset(0.0f, 0.0f, 0.0f));
        rShoulder.addOrReplaceChild("rShoulderPlate",
                CubeListBuilder.create().texOffs(63, 25).mirror().addBox(-0.5f, -0.9f, 0.0f, 1.0f, 2.0f, 5.0f),
                PartPose.offsetAndRotation(-1.8f, -0.2f, -0.8f, 0.6373942428283291f, -0.045553093477052f, -0.8196066167365371f));
        PartDefinition lHookArm1 = chest.addOrReplaceChild("lHookArm1",
                CubeListBuilder.create().texOffs(83, 10).addBox(-0.5f, -7.5f, -0.6f, 1.0f, 8.0f, 1.0f),
                PartPose.offsetAndRotation(3.2f, -1.3f, -3.4f, -0.40980330836826856f, 0.0f, 0.22759093446006054f));
        PartDefinition rHookArm2 = lHookArm1.addOrReplaceChild("rHookArm2",
                CubeListBuilder.create().texOffs(80, 22).addBox(-1.0f, -1.0f, -3.8f, 2.0f, 2.0f, 4.0f),
                PartPose.offsetAndRotation(0.0f, -6.7f, 0.0f, -0.27314402793711257f, 0.0f, 0.0f));
        PartDefinition lHookArm3 = rHookArm2.addOrReplaceChild("lHookArm3",
                CubeListBuilder.create().texOffs(78, 31).addBox(-1.5f, -1.5f, -4.7f, 3.0f, 3.0f, 5.0f),
                PartPose.offsetAndRotation(0.0f, 0.0f, -3.8f, 0.27314402793711257f, 0.0f, 0.0f));
        PartDefinition lHookArmClaw3 = lHookArm3.addOrReplaceChild("lHookArmClaw3",
                CubeListBuilder.create().texOffs(87, 0).addBox(-1.0f, 0.0f, -1.0f, 2.0f, 3.0f, 2.0f),
                PartPose.offsetAndRotation(0.0f, 0.0f, -3.5f, -0.31869712141416456f, 0.7853981633974483f, -0.136659280431156f));
        PartDefinition lHookArmClaw4a = lHookArmClaw3.addOrReplaceChild("lHookArmClaw4a",
                CubeListBuilder.create().texOffs(88, 1).addBox(-0.8f, 0.0f, -0.8f, 1.0f, 2.0f, 1.0f),
                PartPose.offsetAndRotation(0.0f, 2.5f, 0.0f, 0.136659280431156f, 0.0f, 0.136659280431156f));
        lHookArmClaw4a.addOrReplaceChild("lHookArmClaw4b",
                CubeListBuilder.create().texOffs(89, 1).addBox(-0.2f, 0.0f, -0.8f, 1.0f, 2.0f, 1.0f),
                PartPose.offset(0.0f, 0.0f, 0.0f));
        lHookArmClaw4a.addOrReplaceChild("lHookArmClaw4c",
                CubeListBuilder.create().texOffs(88, 1).addBox(-0.2f, 0.0f, -0.2f, 1.0f, 2.0f, 1.0f),
                PartPose.offset(0.0f, 0.0f, 0.0f));
        lHookArmClaw4a.addOrReplaceChild("lHookArmClaw5",
                CubeListBuilder.create().texOffs(88, 1).addBox(-0.5f, 0.0f, -0.5f, 1.0f, 3.0f, 1.0f),
                PartPose.offsetAndRotation(0.0f, 1.6f, 0.0f, 0.091106186954104f, 0.0f, 0.091106186954104f));
        lHookArmClaw4a.addOrReplaceChild("lHookArmClaw4b_1",
                CubeListBuilder.create().texOffs(88, 1).addBox(-0.8f, 0.0f, -0.2f, 1.0f, 2.0f, 1.0f),
                PartPose.offset(0.0f, 0.0f, 0.0f));
        PartDefinition lHookArmClaw1a = lHookArm3.addOrReplaceChild("lHookArmClaw1a",
                CubeListBuilder.create().texOffs(88, 0).addBox(-0.8f, 0.0f, -0.8f, 1.0f, 2.0f, 1.0f),
                PartPose.offsetAndRotation(0.0f, 1.0f, -1.5f, 0.136659280431156f, 0.7853981633974483f, 0.136659280431156f));
        lHookArmClaw1a.addOrReplaceChild("lHookArmClaw1c",
                CubeListBuilder.create().texOffs(88, 0).addBox(-0.2f, 0.0f, -0.2f, 1.0f, 2.0f, 1.0f),
                PartPose.offset(0.0f, 0.0f, 0.0f));
        lHookArmClaw1a.addOrReplaceChild("lHookArmClaw1b",
                CubeListBuilder.create().texOffs(88, 0).addBox(-0.2f, 0.0f, -0.8f, 1.0f, 2.0f, 1.0f),
                PartPose.offset(0.0f, 0.0f, 0.0f));
        lHookArmClaw1a.addOrReplaceChild("lHookArmClaw1b_1",
                CubeListBuilder.create().texOffs(88, 0).addBox(-0.8f, 0.0f, -0.2f, 1.0f, 2.0f, 1.0f),
                PartPose.offset(0.0f, 0.0f, 0.0f));
        lHookArmClaw1a.addOrReplaceChild("lHookArmClaw2",
                CubeListBuilder.create().texOffs(88, 0).addBox(-0.5f, 0.0f, -0.5f, 1.0f, 2.0f, 1.0f),
                PartPose.offsetAndRotation(0.0f, 1.6f, 0.0f, 0.091106186954104f, 0.0f, 0.091106186954104f));
        rHookArm2.addOrReplaceChild("lHookArmClaw0",
                CubeListBuilder.create().texOffs(88, 0).addBox(-0.5f, 0.0f, -0.5f, 1.0f, 2.0f, 1.0f),
                PartPose.offsetAndRotation(0.0f, 0.3f, -2.8f, 0.36425021489121656f, 0.7853981633974483f, 0.36425021489121656f));
        PartDefinition lShoulder = chest.addOrReplaceChild("lShoulder",
                CubeListBuilder.create().texOffs(67, 0).addBox(0.0f, -0.7f, -1.5f, 2.0f, 3.0f, 3.0f),
                PartPose.offsetAndRotation(2.6f, -0.2f, -2.0f, 0.091106186954104f, 0.0f, -0.5918411493512771f));
        PartDefinition lArm1 = lShoulder.addOrReplaceChild("lArm1",
                CubeListBuilder.create().texOffs(70, 8).addBox(-0.5f, -0.5f, -1.0f, 1.0f, 4.0f, 2.0f),
                PartPose.offsetAndRotation(0.8f, 1.9f, 0.0f, 0.27314402793711257f, 0.0f, 0.136659280431156f));
        PartDefinition lArm2 = lArm1.addOrReplaceChild("lArm2",
                CubeListBuilder.create().texOffs(64, 16).addBox(-1.0f, -1.0f, -4.0f, 2.0f, 2.0f, 5.0f),
                PartPose.offsetAndRotation(-0.5f, 3.4f, 0.0f, -0.136659280431156f, 0.0f, 0.4553564018453205f));
        PartDefinition lArmClaw1a = lArm2.addOrReplaceChild("lArmClaw1a",
                CubeListBuilder.create().texOffs(88, 0).addBox(-0.8f, 0.0f, -0.8f, 1.0f, 2.0f, 1.0f),
                PartPose.offsetAndRotation(0.0f, 0.2f, -3.1f, 0.4553564018453205f, 0.7853981633974483f, 0.31869712141416456f));
        lArmClaw1a.addOrReplaceChild("lArmClaw1b",
                CubeListBuilder.create().texOffs(88, 0).addBox(-0.2f, 0.0f, -0.8f, 1.0f, 2.0f, 1.0f),
                PartPose.offset(0.0f, 0.0f, 0.0f));
        lArmClaw1a.addOrReplaceChild("lArmClaw2",
                CubeListBuilder.create().texOffs(88, 0).addBox(-0.5f, 0.0f, -0.5f, 1.0f, 2.0f, 1.0f),
                PartPose.offsetAndRotation(0.0f, 1.6f, 0.0f, 0.091106186954104f, 0.0f, 0.091106186954104f));
        lArmClaw1a.addOrReplaceChild("lArmClaw1b_1",
                CubeListBuilder.create().texOffs(88, 0).addBox(-0.8f, 0.0f, -0.2f, 1.0f, 2.0f, 1.0f),
                PartPose.offset(0.0f, 0.0f, 0.0f));
        lArmClaw1a.addOrReplaceChild("lArmClaw1c",
                CubeListBuilder.create().texOffs(88, 0).addBox(-0.2f, 0.0f, -0.2f, 1.0f, 2.0f, 1.0f),
                PartPose.offset(0.0f, 0.0f, 0.0f));
        lShoulder.addOrReplaceChild("lShoulderPlate",
                CubeListBuilder.create().texOffs(63, 25).addBox(-0.5f, -0.9f, 0.0f, 1.0f, 2.0f, 5.0f),
                PartPose.offsetAndRotation(1.8f, -0.2f, -0.8f, 0.6373942428283291f, 0.045553093477052f, 0.8196066167365371f));
        PartDefinition lowerbody = chest.addOrReplaceChild("lowerbody",
                CubeListBuilder.create().texOffs(0, 16).addBox(-3.0f, -2.1f, 0.0f, 6.0f, 4.0f, 6.0f),
                PartPose.offsetAndRotation(0.0f, -0.4f, 2.8f, -0.18203784098300857f, 0.0f, 0.0f));
        PartDefinition rThigh = lowerbody.addOrReplaceChild("rThigh",
                CubeListBuilder.create().texOffs(42, 0).mirror().addBox(-1.5f, -1.0f, -2.0f, 3.0f, 8.0f, 4.0f),
                PartPose.offsetAndRotation(-2.8f, -1.6f, 4.0f, -0.4553564018453205f, 0.136659280431156f, 0.40980330836826856f));
        rThigh.addOrReplaceChild("lLegPlate_1",
                CubeListBuilder.create().texOffs(57, 0).mirror().addBox(-0.5f, -4.0f, -1.0f, 1.0f, 4.0f, 2.0f),
                PartPose.offsetAndRotation(-0.9f, 0.0f, -0.5f, -0.31869712141416456f, 0.0f, -0.31869712141416456f));
        PartDefinition rLeg1 = rThigh.addOrReplaceChild("rLeg1",
                CubeListBuilder.create().texOffs(42, 15).mirror().addBox(-1.2f, -1.0f, 0.0f, 2.0f, 2.0f, 5.0f),
                PartPose.offsetAndRotation(0.0f, 6.0f, 1.0f, 0.8196066167365371f, -0.31869712141416456f, 0.0f));
        PartDefinition rLeg2 = rLeg1.addOrReplaceChild("rLeg2",
                CubeListBuilder.create().texOffs(46, 23).mirror().addBox(-0.5f, 0.0f, -1.2f, 1.0f, 5.0f, 2.0f),
                PartPose.offsetAndRotation(0.0f, 0.0f, 4.4f, -0.6829473363053812f, 0.0f, -0.40980330836826856f));
        PartDefinition rFoot = rLeg2.addOrReplaceChild("rFoot",
                CubeListBuilder.create().texOffs(44, 33).mirror().addBox(-1.0f, -0.3f, -2.3f, 2.0f, 2.0f, 3.0f),
                PartPose.offsetAndRotation(0.0f, 4.4f, -0.3f, 0.40980330836826856f, 0.0f, -0.136659280431156f));
        PartDefinition rHoof1a = rFoot.addOrReplaceChild("rHoof1a",
                CubeListBuilder.create().texOffs(88, 0).mirror().addBox(-0.6f, -0.7f, -1.7f, 1.0f, 1.0f, 2.0f),
                PartPose.offsetAndRotation(0.0f, 0.5f, -1.7f, 0.22759093446006054f, 0.0f, 0.0f));
        rHoof1a.addOrReplaceChild("rHoof1b",
                CubeListBuilder.create().texOffs(88, 0).mirror().addBox(-0.2f, -0.7f, -1.7f, 1.0f, 1.0f, 2.0f),
                PartPose.offset(0.0f, 0.0f, 0.0f));
        rHoof1a.addOrReplaceChild("rHoof1d",
                CubeListBuilder.create().texOffs(88, 0).mirror().addBox(-0.2f, -0.2f, -1.7f, 1.0f, 1.0f, 2.0f),
                PartPose.offset(0.0f, 0.0f, 0.0f));
        rHoof1a.addOrReplaceChild("rHoof1c",
                CubeListBuilder.create().texOffs(88, 0).mirror().addBox(-0.6f, -0.2f, -1.7f, 1.0f, 1.0f, 2.0f),
                PartPose.offset(0.0f, 0.0f, 0.0f));
        rHoof1a.addOrReplaceChild("rHoof2",
                CubeListBuilder.create().texOffs(88, 0).mirror().addBox(-0.5f, -0.5f, -1.1f, 1.0f, 1.0f, 1.0f),
                PartPose.offsetAndRotation(0.1f, 0.0f, -1.5f, 0.18203784098300857f, 0.0f, 0.0f));
        PartDefinition lThigh = lowerbody.addOrReplaceChild("lThigh",
                CubeListBuilder.create().texOffs(42, 0).addBox(-1.5f, -1.0f, -2.0f, 3.0f, 8.0f, 4.0f),
                PartPose.offsetAndRotation(2.8f, -1.6f, 4.0f, -0.4553564018453205f, -0.136659280431156f, -0.40980330836826856f));
        PartDefinition lLeg1 = lThigh.addOrReplaceChild("lLeg1",
                CubeListBuilder.create().texOffs(42, 15).addBox(-1.2f, -1.0f, 0.0f, 2.0f, 2.0f, 5.0f),
                PartPose.offsetAndRotation(0.0f, 6.0f, 1.0f, 0.8196066167365371f, 0.31869712141416456f, 0.0f));
        PartDefinition lLeg2 = lLeg1.addOrReplaceChild("lLeg2",
                CubeListBuilder.create().texOffs(46, 23).addBox(-0.5f, 0.0f, -1.2f, 1.0f, 5.0f, 2.0f),
                PartPose.offsetAndRotation(0.0f, 0.0f, 4.4f, -0.6829473363053812f, 0.0f, 0.40980330836826856f));
        PartDefinition lFoot = lLeg2.addOrReplaceChild("lFoot",
                CubeListBuilder.create().texOffs(44, 33).addBox(-1.0f, -0.3f, -2.3f, 2.0f, 2.0f, 3.0f),
                PartPose.offsetAndRotation(0.0f, 4.4f, -0.3f, 0.40980330836826856f, 0.0f, 0.136659280431156f));
        PartDefinition lHoof1a = lFoot.addOrReplaceChild("lHoof1a",
                CubeListBuilder.create().texOffs(88, 0).addBox(-0.6f, -0.7f, -1.7f, 1.0f, 1.0f, 2.0f),
                PartPose.offsetAndRotation(0.0f, 0.5f, -1.7f, 0.22759093446006054f, 0.0f, 0.0f));
        lHoof1a.addOrReplaceChild("lHoof1c",
                CubeListBuilder.create().texOffs(88, 0).addBox(-0.6f, -0.2f, -1.7f, 1.0f, 1.0f, 2.0f),
                PartPose.offset(0.0f, 0.0f, 0.0f));
        lHoof1a.addOrReplaceChild("lHoof1b",
                CubeListBuilder.create().texOffs(88, 0).addBox(-0.2f, -0.7f, -1.7f, 1.0f, 1.0f, 2.0f),
                PartPose.offset(0.0f, 0.0f, 0.0f));
        lHoof1a.addOrReplaceChild("lHoof2",
                CubeListBuilder.create().texOffs(88, 0).addBox(-0.5f, -0.5f, -1.1f, 1.0f, 1.0f, 1.0f),
                PartPose.offsetAndRotation(0.1f, 0.0f, -1.5f, 0.18203784098300857f, 0.0f, 0.0f));
        lHoof1a.addOrReplaceChild("lHoof1d",
                CubeListBuilder.create().texOffs(88, 0).addBox(-0.2f, -0.2f, -1.7f, 1.0f, 1.0f, 2.0f),
                PartPose.offset(0.0f, 0.0f, 0.0f));
        lThigh.addOrReplaceChild("lLegPlate",
                CubeListBuilder.create().texOffs(57, 0).addBox(-0.5f, -4.0f, -1.0f, 1.0f, 4.0f, 2.0f),
                PartPose.offsetAndRotation(0.9f, 0.0f, -0.5f, -0.31869712141416456f, 0.0f, 0.31869712141416456f));
        PartDefinition tail1 = lowerbody.addOrReplaceChild("tail1",
                CubeListBuilder.create().texOffs(0, 30).addBox(-2.5f, -2.0f, 0.0f, 5.0f, 4.0f, 6.0f),
                PartPose.offsetAndRotation(0.0f, 0.0f, 5.1f, -0.22759093446006054f, 0.0f, 0.0f));
        PartDefinition tail2 = tail1.addOrReplaceChild("tail2",
                CubeListBuilder.create().texOffs(0, 42).addBox(-2.0f, -1.5f, 0.0f, 4.0f, 3.0f, 5.0f),
                PartPose.offsetAndRotation(0.0f, -0.3f, 5.0f, -0.091106186954104f, 0.0f, 0.0f));
        PartDefinition tail3 = tail2.addOrReplaceChild("tail3",
                CubeListBuilder.create().texOffs(0, 52).addBox(-1.5f, -1.0f, 0.0f, 3.0f, 2.0f, 4.0f),
                PartPose.offsetAndRotation(0.0f, 0.2f, 4.3f, 0.136659280431156f, 0.0f, 0.0f));
        tail3.addOrReplaceChild("tail4",
                CubeListBuilder.create().texOffs(16, 52).addBox(-1.0f, -1.0f, 0.0f, 2.0f, 2.0f, 4.0f),
                PartPose.offsetAndRotation(0.0f, 0.1f, 3.8f, 0.22759093446006054f, 0.0f, 0.0f));
        return LayerDefinition.create(mesh, 128, 64);
    }
}

