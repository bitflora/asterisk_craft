package net.bitflora.asteriskcraft.client.zerg;

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
 * Ported from the 1.12 StarCraft mod's ModelHydralisk (mechanical box translation; see
 * tools/model_convert.py). Head-look + a simple limb-swing walk are re-authored here; the old
 * per-render tweaks (neck scale, blade-sheath toggle) are intentionally not reproduced.
 */
public class HydraliskModel extends EntityModel<LivingEntityRenderState> {
    private final ModelPart head;
    private final ModelPart armL;
    private final ModelPart armR;
    private final float headX0;
    private final float armLX0;
    private final float armRX0;

    public HydraliskModel(ModelPart root) {
        super(root);
        this.head = root.getChild("chest").getChild("neck").getChild("head");
        this.armL = root.getChild("chest").getChild("lShoulder").getChild("lArm1");
        this.armR = root.getChild("chest").getChild("rShoulder").getChild("rArm1");
        this.headX0 = this.head.xRot;
        this.armLX0 = this.armL.xRot;
        this.armRX0 = this.armR.xRot;
    }

    @Override
    public void setupAnim(LivingEntityRenderState state) {
        super.setupAnim(state);
        this.head.yRot = state.yRot * ((float) Math.PI / 180f);
        this.head.xRot = this.headX0 + state.xRot * ((float) Math.PI / 180f);
        float swing = Mth.cos(state.walkAnimationPos * 0.6662f) * 1.4f * state.walkAnimationSpeed;
        this.armL.xRot = this.armLX0 - swing;
        this.armR.xRot = this.armRX0 + swing;
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        PartDefinition chest = root.addOrReplaceChild("chest",
                CubeListBuilder.create().texOffs(0, 3).addBox(-5.0f, -6.7f, -6.0f, 12.0f, 11.0f, 10.0f),
                PartPose.offsetAndRotation(-1.0f, 0.5f, -16.0f, -0.18203784098300857f, 0.0f, 0.0f));
        PartDefinition spine01a = chest.addOrReplaceChild("spine01a",
                CubeListBuilder.create().texOffs(53, 61).addBox(-1.0f, -1.5f, 0.0f, 2.0f, 3.0f, 7.0f),
                PartPose.offsetAndRotation(1.0f, -3.3f, 3.2f, 0.5462880558742251f, 0.0f, 0.0f));
        spine01a.addOrReplaceChild("spine01b",
                CubeListBuilder.create().texOffs(72, 61).addBox(-0.5f, -1.0f, -0.5f, 1.0f, 2.0f, 5.0f),
                PartPose.offsetAndRotation(0.0f, 0.1f, 7.1f, 0.27314402793711257f, 0.0f, 0.0f));
        PartDefinition neck = chest.addOrReplaceChild("neck",
                CubeListBuilder.create().texOffs(135, 0).addBox(-4.5f, -3.8f, -10.9f, 9.0f, 6.0f, 12.0f),
                PartPose.offsetAndRotation(1.0f, -3.5f, 0.0f, -0.136659280431156f, 0.0f, 0.0f));
        PartDefinition head = neck.addOrReplaceChild("head",
                CubeListBuilder.create().texOffs(0, 70).addBox(-4.5f, -3.0f, -4.2f, 9.0f, 4.0f, 5.0f),
                PartPose.offsetAndRotation(0.0f, -0.9f, -10.4f, 0.5009094953223726f, 0.0f, 0.0f));
        PartDefinition crest02 = head.addOrReplaceChild("crest02",
                CubeListBuilder.create().texOffs(108, 61).addBox(-4.5f, -4.5f, 2.4f, 9.0f, 2.0f, 6.0f),
                PartPose.offset(0.0f, 0.0f, 0.0f));
        PartDefinition lCrest03a_1 = crest02.addOrReplaceChild("lCrest03a_1",
                CubeListBuilder.create().texOffs(113, 71).mirror().addBox(-2.0f, -0.1f, -2.8f, 4.0f, 2.0f, 7.0f),
                PartPose.offsetAndRotation(-3.5f, -4.2f, 7.3f, -0.045553093477052f, -0.27314402793711257f, 0.0f));
        PartDefinition lCrest03b = lCrest03a_1.addOrReplaceChild("lCrest03b",
                CubeListBuilder.create().texOffs(117, 84).mirror().addBox(-1.5f, -0.9f, 0.0f, 3.0f, 1.0f, 4.0f),
                PartPose.offsetAndRotation(-0.5f, 1.2f, 3.4f, -0.045553093477052f, 0.27314402793711257f, 0.0f));
        lCrest03b.addOrReplaceChild("lCrest03c",
                CubeListBuilder.create().texOffs(195, 92).mirror().addBox(-1.0f, -0.9f, 0.0f, 2.0f, 1.0f, 4.0f),
                PartPose.offsetAndRotation(-0.5f, 0.1f, 3.4f, -0.045553093477052f, 0.27314402793711257f, 0.0f));
        PartDefinition crest01 = head.addOrReplaceChild("crest01",
                CubeListBuilder.create().texOffs(108, 49).addBox(-4.0f, -3.8f, -4.8f, 8.0f, 2.0f, 8.0f),
                PartPose.offsetAndRotation(0.0f, 0.0f, 0.0f, 0.18203784098300857f, 0.0f, 0.0f));
        crest01.addOrReplaceChild("shape125",
                CubeListBuilder.create().texOffs(146, 47).addBox(-2.0f, -2.0f, 0.0f, 4.0f, 4.0f, 14.0f),
                PartPose.offsetAndRotation(0.0f, -1.4f, -0.2f, 0.045553093477052f, -0.045553093477052f, 0.7853981633974483f));
        PartDefinition lMandible1 = head.addOrReplaceChild("lMandible1",
                CubeListBuilder.create().texOffs(23, 83).addBox(-0.2f, -0.8f, -1.5f, 2.0f, 5.0f, 3.0f),
                PartPose.offsetAndRotation(2.5f, 2.0f, -2.8f, -0.27314402793711257f, -0.18203784098300857f, -0.18203784098300857f));
        lMandible1.addOrReplaceChild("lFangs",
                CubeListBuilder.create().texOffs(25, 100).addBox(0.9f, -1.4f, -7.2f, 0.0f, 5.0f, 6.0f),
                PartPose.offset(0.0f, 0.0f, 0.0f));
        lMandible1.addOrReplaceChild("lMandible2",
                CubeListBuilder.create().texOffs(22, 93).addBox(0.3f, 2.9f, -6.9f, 1.0f, 2.0f, 8.0f),
                PartPose.offset(0.1f, 0.0f, 0.0f));
        PartDefinition lCrest03a = head.addOrReplaceChild("lCrest03a",
                CubeListBuilder.create().texOffs(113, 71).addBox(-2.0f, -0.1f, -2.8f, 4.0f, 2.0f, 7.0f),
                PartPose.offsetAndRotation(3.5f, -4.2f, 7.3f, -0.045553093477052f, 0.27314402793711257f, 0.0f));
        PartDefinition lCrest03b_1 = lCrest03a.addOrReplaceChild("lCrest03b_1",
                CubeListBuilder.create().texOffs(117, 84).addBox(-1.5f, -0.9f, 0.0f, 3.0f, 1.0f, 4.0f),
                PartPose.offsetAndRotation(0.5f, 1.2f, 3.4f, -0.045553093477052f, -0.27314402793711257f, 0.0f));
        lCrest03b_1.addOrReplaceChild("lCrest03c_1",
                CubeListBuilder.create().texOffs(195, 92).addBox(-1.0f, -0.9f, 0.0f, 2.0f, 1.0f, 4.0f),
                PartPose.offsetAndRotation(0.5f, 0.1f, 3.4f, -0.045553093477052f, -0.27314402793711257f, 0.0f));
        PartDefinition lowerJaw1 = head.addOrReplaceChild("lowerJaw1",
                CubeListBuilder.create().texOffs(0, 107).addBox(-2.5f, -0.7f, -5.1f, 5.0f, 2.0f, 5.0f),
                PartPose.offsetAndRotation(0.0f, 1.6f, -2.9f, 0.31869712141416456f, 0.0f, 0.0f));
        lowerJaw1.addOrReplaceChild("teethLower",
                CubeListBuilder.create().texOffs(0, 100).addBox(-2.5f, -1.5f, -5.0f, 5.0f, 1.0f, 4.0f),
                PartPose.offset(0.0f, 0.0f, 0.0f));
        PartDefinition rMandible1 = head.addOrReplaceChild("rMandible1",
                CubeListBuilder.create().texOffs(23, 83).mirror().addBox(-1.7f, -0.8f, -1.5f, 2.0f, 5.0f, 3.0f),
                PartPose.offsetAndRotation(-2.5f, 2.0f, -2.8f, -0.27314402793711257f, 0.18203784098300857f, 0.18203784098300857f));
        rMandible1.addOrReplaceChild("rFangs",
                CubeListBuilder.create().texOffs(25, 100).mirror().addBox(-0.7f, -1.4f, -7.2f, 0.0f, 5.0f, 6.0f),
                PartPose.offset(0.0f, 0.0f, 0.0f));
        rMandible1.addOrReplaceChild("rMandible2",
                CubeListBuilder.create().texOffs(22, 93).mirror().addBox(-1.2f, 2.9f, -6.9f, 1.0f, 2.0f, 8.0f),
                PartPose.offset(0.0f, 0.0f, 0.0f));
        PartDefinition upperJaw = head.addOrReplaceChild("upperJaw",
                CubeListBuilder.create().texOffs(0, 84).addBox(-2.5f, -1.1f, -4.4f, 5.0f, 2.0f, 4.0f),
                PartPose.offset(0.0f, 0.0f, -3.8f));
        upperJaw.addOrReplaceChild("muzzle",
                CubeListBuilder.create().texOffs(88, 48).addBox(-2.0f, -3.0f, -3.2f, 4.0f, 2.0f, 4.0f),
                PartPose.offsetAndRotation(0.0f, 0.0f, 0.0f, 0.4553564018453205f, 0.0f, 0.0f));
        upperJaw.addOrReplaceChild("teethUpper",
                CubeListBuilder.create().texOffs(0, 92).addBox(-2.5f, 0.8f, -4.3f, 5.0f, 1.0f, 3.0f),
                PartPose.offset(0.0f, 0.0f, 0.0f));
        PartDefinition rShoulder = chest.addOrReplaceChild("rShoulder",
                CubeListBuilder.create().texOffs(0, 52).mirror().addBox(-2.0f, -1.8f, -2.5f, 4.0f, 7.0f, 5.0f),
                PartPose.offsetAndRotation(-5.3f, -2.9f, 0.0f, 0.136659280431156f, 0.0f, 0.31869712141416456f));
        PartDefinition rArm1 = rShoulder.addOrReplaceChild("rArm1",
                CubeListBuilder.create().texOffs(22, 51).addBox(-1.0f, -0.5f, -1.9f, 3.0f, 10.0f, 4.0f),
                PartPose.offsetAndRotation(-0.5f, 4.8f, 0.0f, -0.31869712141416456f, 0.0f, 0.0f));
        PartDefinition rArm2 = rArm1.addOrReplaceChild("rArm2",
                CubeListBuilder.create().texOffs(40, 45).mirror().addBox(-1.2f, -1.7f, -7.4f, 2.0f, 3.0f, 7.0f),
                PartPose.offsetAndRotation(1.0f, 8.1f, 0.0f, -0.045553093477052f, 0.0f, 0.0f));
        PartDefinition rArmSpike3a = rArm2.addOrReplaceChild("rArmSpike3a",
                CubeListBuilder.create().texOffs(185, 0).mirror().addBox(-1.0f, -0.1f, -1.0f, 2.0f, 4.0f, 2.0f),
                PartPose.offsetAndRotation(-0.3f, 1.2f, -6.3f, 0.36425021489121656f, 0.0f, 0.0f));
        rArmSpike3a.addOrReplaceChild("rArmSpike3b",
                CubeListBuilder.create().texOffs(185, 0).mirror().addBox(-0.4f, -0.5f, -0.6f, 1.0f, 4.0f, 1.0f),
                PartPose.offsetAndRotation(0.0f, 4.1f, 0.1f, 0.18203784098300857f, 0.0f, 0.0f));
        PartDefinition rArm3 = rArm2.addOrReplaceChild("rArm3",
                CubeListBuilder.create().texOffs(66, 48).mirror().addBox(-2.2f, -2.2f, -4.2f, 4.0f, 4.0f, 7.0f),
                PartPose.offset(0.0f, 0.0f, -10.0f));
        PartDefinition rArmSpike2a = rArm3.addOrReplaceChild("rArmSpike2a",
                CubeListBuilder.create().texOffs(185, 0).mirror().addBox(-1.5f, -0.1f, -1.5f, 3.0f, 4.0f, 3.0f),
                PartPose.offsetAndRotation(0.0f, 1.8f, 1.1f, 0.136659280431156f, 0.0f, 0.0f));
        PartDefinition rArmSpike2b = rArmSpike2a.addOrReplaceChild("rArmSpike2b",
                CubeListBuilder.create().texOffs(185, 0).mirror().addBox(-1.0f, -0.1f, -1.0f, 2.0f, 4.0f, 2.0f),
                PartPose.offsetAndRotation(0.0f, 3.6f, -0.1f, 0.18203784098300857f, 0.0f, 0.0f));
        rArmSpike2b.addOrReplaceChild("rArmSpike2c",
                CubeListBuilder.create().texOffs(185, 0).mirror().addBox(-0.4f, -0.5f, -0.6f, 1.0f, 4.0f, 1.0f),
                PartPose.offsetAndRotation(0.0f, 4.1f, 0.1f, 0.18203784098300857f, 0.0f, 0.0f));
        PartDefinition rArmSpike1a = rArm3.addOrReplaceChild("rArmSpike1a",
                CubeListBuilder.create().texOffs(185, 0).mirror().addBox(-1.5f, -0.1f, -1.5f, 3.0f, 6.0f, 3.0f),
                PartPose.offset(0.0f, 1.8f, -2.3f));
        PartDefinition rArmSpike1b = rArmSpike1a.addOrReplaceChild("rArmSpike1b",
                CubeListBuilder.create().texOffs(185, 0).mirror().addBox(-1.0f, -0.1f, -1.0f, 2.0f, 6.0f, 2.0f),
                PartPose.offsetAndRotation(0.0f, 5.8f, -0.1f, 0.18203784098300857f, 0.0f, 0.0f));
        rArmSpike1b.addOrReplaceChild("rArmSpike1c",
                CubeListBuilder.create().texOffs(185, 0).mirror().addBox(-0.4f, -0.5f, -0.6f, 1.0f, 6.0f, 1.0f),
                PartPose.offsetAndRotation(0.0f, 6.1f, 0.1f, 0.18203784098300857f, 0.0f, 0.0f));
        PartDefinition spine02a = chest.addOrReplaceChild("spine02a",
                CubeListBuilder.create().texOffs(53, 75).addBox(-1.0f, -1.5f, 0.0f, 2.0f, 3.0f, 6.0f),
                PartPose.offsetAndRotation(1.0f, 0.7f, 3.2f, 0.36425021489121656f, 0.0f, 0.0f));
        spine02a.addOrReplaceChild("spine02b",
                CubeListBuilder.create().texOffs(72, 77).addBox(-0.5f, -1.0f, -0.5f, 1.0f, 2.0f, 4.0f),
                PartPose.offsetAndRotation(0.0f, 0.1f, 5.8f, 0.27314402793711257f, 0.0f, 0.0f));
        PartDefinition spine03a = chest.addOrReplaceChild("spine03a",
                CubeListBuilder.create().texOffs(53, 88).addBox(-1.0f, -1.0f, 0.0f, 2.0f, 2.0f, 5.0f),
                PartPose.offsetAndRotation(1.0f, 4.0f, 2.7f, 0.18203784098300857f, 0.0f, 0.0f));
        spine03a.addOrReplaceChild("spine03b",
                CubeListBuilder.create().texOffs(69, 91).addBox(-0.5f, -0.5f, 0.0f, 1.0f, 1.0f, 3.0f),
                PartPose.offsetAndRotation(0.0f, 0.0f, 4.9f, 0.18203784098300857f, 0.0f, 0.0f));
        PartDefinition backArmour01 = chest.addOrReplaceChild("backArmour01",
                CubeListBuilder.create().texOffs(139, 72).addBox(-5.0f, -3.7f, -6.2f, 12.0f, 5.0f, 12.0f),
                PartPose.offsetAndRotation(0.0f, -6.8f, 0.0f, 0.36425021489121656f, 0.0f, 0.0f));
        PartDefinition lCrest04a = backArmour01.addOrReplaceChild("lCrest04a",
                CubeListBuilder.create().texOffs(195, 71).addBox(-2.0f, -0.5f, -2.8f, 4.0f, 3.0f, 7.0f),
                PartPose.offsetAndRotation(6.1f, -2.2f, 5.6f, -0.045553093477052f, 0.40980330836826856f, 0.0f));
        PartDefinition lCrest04b = lCrest04a.addOrReplaceChild("lCrest04b",
                CubeListBuilder.create().texOffs(195, 84).addBox(-1.5f, -1.0f, 0.0f, 3.0f, 2.0f, 4.0f),
                PartPose.offsetAndRotation(-0.5f, 1.0f, 3.4f, -0.045553093477052f, -0.27314402793711257f, 0.0f));
        lCrest04b.addOrReplaceChild("lCrest04c",
                CubeListBuilder.create().texOffs(195, 92).addBox(-1.0f, -1.0f, 0.0f, 2.0f, 1.0f, 4.0f),
                PartPose.offsetAndRotation(-0.5f, 0.4f, 3.4f, -0.045553093477052f, -0.27314402793711257f, 0.0f));
        PartDefinition rCrest04a = backArmour01.addOrReplaceChild("rCrest04a",
                CubeListBuilder.create().texOffs(195, 71).mirror().addBox(-2.0f, -0.5f, -2.8f, 4.0f, 3.0f, 7.0f),
                PartPose.offsetAndRotation(-4.1f, -2.2f, 5.6f, -0.045553093477052f, -0.40980330836826856f, 0.0f));
        PartDefinition rCrest04b = rCrest04a.addOrReplaceChild("rCrest04b",
                CubeListBuilder.create().texOffs(195, 84).mirror().addBox(-1.5f, -1.0f, 0.0f, 3.0f, 2.0f, 4.0f),
                PartPose.offsetAndRotation(0.5f, 1.0f, 3.4f, -0.045553093477052f, 0.27314402793711257f, 0.0f));
        rCrest04b.addOrReplaceChild("rCrest04c",
                CubeListBuilder.create().texOffs(195, 92).mirror().addBox(-1.0f, -1.0f, 0.0f, 2.0f, 1.0f, 4.0f),
                PartPose.offsetAndRotation(0.5f, 0.4f, 3.4f, -0.045553093477052f, 0.27314402793711257f, 0.0f));
        PartDefinition crest05a = backArmour01.addOrReplaceChild("crest05a",
                CubeListBuilder.create().texOffs(150, 93).addBox(-2.5f, -1.0f, -0.2f, 5.0f, 2.0f, 7.0f),
                PartPose.offsetAndRotation(1.0f, -2.6f, 5.6f, 0.091106186954104f, 0.0f, 0.0f));
        crest05a.addOrReplaceChild("crest05b",
                CubeListBuilder.create().texOffs(153, 108).addBox(-2.0f, -0.5f, -0.2f, 4.0f, 1.0f, 6.0f),
                PartPose.offsetAndRotation(0.0f, -0.3f, 5.6f, 0.27314402793711257f, 0.0f, 0.0f));
        PartDefinition stomach = chest.addOrReplaceChild("stomach",
                CubeListBuilder.create().texOffs(48, 4).addBox(-4.0f, 0.0f, -5.0f, 10.0f, 11.0f, 8.0f),
                PartPose.offsetAndRotation(0.0f, 3.8f, 0.0f, -0.045553093477052f, 0.0f, 0.0f));
        PartDefinition tail01 = stomach.addOrReplaceChild("tail01",
                CubeListBuilder.create().texOffs(87, 0).addBox(-4.5f, 0.0f, -5.5f, 11.0f, 11.0f, 12.0f),
                PartPose.offsetAndRotation(0.0f, 9.1f, 0.0f, 0.22759093446006054f, 0.0f, 0.0f));
        PartDefinition rTailSpike00a = tail01.addOrReplaceChild("rTailSpike00a",
                CubeListBuilder.create().texOffs(185, 2).mirror().addBox(-3.1f, -1.5f, -1.5f, 3.0f, 3.0f, 3.0f),
                PartPose.offsetAndRotation(-3.7f, 4.0f, -2.1f, 0.22759093446006054f, 0.0f, -0.18203784098300857f));
        PartDefinition rTailSpike00b = rTailSpike00a.addOrReplaceChild("rTailSpike00b",
                CubeListBuilder.create().texOffs(185, 2).mirror().addBox(-1.6f, -1.0f, -1.0f, 2.0f, 2.0f, 2.0f),
                PartPose.offsetAndRotation(-3.2f, 0.0f, 0.0f, 0.0f, 0.0f, -0.18203784098300857f));
        rTailSpike00b.addOrReplaceChild("rTailSpike00c",
                CubeListBuilder.create().texOffs(185, 2).mirror().addBox(-2.2f, -0.5f, -0.5f, 2.0f, 1.0f, 1.0f),
                PartPose.offsetAndRotation(-1.2f, 0.1f, 0.1f, 0.0f, 0.0f, -0.18203784098300857f));
        PartDefinition tail02 = tail01.addOrReplaceChild("tail02",
                CubeListBuilder.create().texOffs(0, 27).addBox(-6.0f, -5.0f, 0.0f, 12.0f, 9.0f, 6.0f),
                PartPose.offset(1.0f, 6.9f, 6.4f));
        PartDefinition lTailSpike02a = tail02.addOrReplaceChild("lTailSpike02a",
                CubeListBuilder.create().texOffs(185, 2).addBox(0.6f, -1.5f, -1.5f, 3.0f, 3.0f, 3.0f),
                PartPose.offsetAndRotation(4.5f, 1.5f, 2.2f, 0.0f, 0.0f, 0.136659280431156f));
        PartDefinition lTailSpike02b = lTailSpike02a.addOrReplaceChild("lTailSpike02b",
                CubeListBuilder.create().texOffs(185, 2).addBox(-0.1f, -1.0f, -1.0f, 2.0f, 2.0f, 2.0f),
                PartPose.offsetAndRotation(3.5f, 0.0f, 0.0f, 0.0f, -0.136659280431156f, 0.0f));
        lTailSpike02b.addOrReplaceChild("lTailSpike02c",
                CubeListBuilder.create().texOffs(185, 2).addBox(-0.1f, -0.5f, -0.5f, 2.0f, 1.0f, 1.0f),
                PartPose.offsetAndRotation(1.7f, 0.1f, 0.1f, 0.0f, -0.136659280431156f, 0.0f));
        PartDefinition rTailSpike02a = tail02.addOrReplaceChild("rTailSpike02a",
                CubeListBuilder.create().texOffs(185, 2).mirror().addBox(-3.1f, -1.5f, -1.5f, 3.0f, 3.0f, 3.0f),
                PartPose.offsetAndRotation(-5.0f, 1.5f, 2.2f, 0.0f, 0.0f, -0.136659280431156f));
        PartDefinition rTailSpike02b = rTailSpike02a.addOrReplaceChild("rTailSpike02b",
                CubeListBuilder.create().texOffs(185, 2).mirror().addBox(-1.6f, -1.0f, -1.0f, 2.0f, 2.0f, 2.0f),
                PartPose.offsetAndRotation(-3.2f, 0.0f, 0.0f, 0.0f, 0.136659280431156f, 0.0f));
        rTailSpike02b.addOrReplaceChild("rTailSpike02c",
                CubeListBuilder.create().texOffs(185, 2).mirror().addBox(-2.2f, -0.5f, -0.5f, 2.0f, 1.0f, 1.0f),
                PartPose.offsetAndRotation(-1.2f, 0.1f, 0.1f, 0.0f, 0.136659280431156f, 0.0f));
        PartDefinition tailSpike01a = tail02.addOrReplaceChild("tailSpike01a",
                CubeListBuilder.create().texOffs(185, 0).addBox(-1.5f, -2.0f, -1.5f, 3.0f, 2.0f, 3.0f),
                PartPose.offsetAndRotation(0.0f, -4.6f, 1.0f, -0.27314402793711257f, 0.0f, 0.0f));
        PartDefinition lArmSpike01b = tailSpike01a.addOrReplaceChild("lArmSpike01b",
                CubeListBuilder.create().texOffs(185, 0).addBox(-1.0f, -2.4f, -1.0f, 2.0f, 2.0f, 2.0f),
                PartPose.offsetAndRotation(0.0f, -1.0f, -0.3f, -0.136659280431156f, 0.0f, 0.0f));
        lArmSpike01b.addOrReplaceChild("lArmSpike01c",
                CubeListBuilder.create().texOffs(185, 0).addBox(-0.5f, -2.0f, -0.5f, 1.0f, 3.0f, 1.0f),
                PartPose.offsetAndRotation(0.0f, -2.0f, 0.1f, -0.22759093446006054f, 0.0f, 0.0f));
        PartDefinition tail03 = tail02.addOrReplaceChild("tail03",
                CubeListBuilder.create().texOffs(38, 28).addBox(-5.5f, -4.0f, 0.0f, 11.0f, 8.0f, 6.0f),
                PartPose.offset(0.0f, 0.0f, 5.9f));
        PartDefinition rTailSpike03a = tail03.addOrReplaceChild("rTailSpike03a",
                CubeListBuilder.create().texOffs(185, 2).mirror().addBox(-3.1f, -1.5f, -1.5f, 3.0f, 3.0f, 3.0f),
                PartPose.offsetAndRotation(-4.8f, 1.8f, 1.9f, 0.0f, 0.0f, -0.136659280431156f));
        PartDefinition rTailSpike03b = rTailSpike03a.addOrReplaceChild("rTailSpike03b",
                CubeListBuilder.create().texOffs(185, 2).mirror().addBox(-1.6f, -1.0f, -1.0f, 2.0f, 2.0f, 2.0f),
                PartPose.offsetAndRotation(-3.2f, 0.0f, 0.0f, 0.0f, 0.136659280431156f, 0.0f));
        rTailSpike03b.addOrReplaceChild("rTailSpike03c",
                CubeListBuilder.create().texOffs(185, 2).mirror().addBox(-2.2f, -0.5f, -0.5f, 2.0f, 1.0f, 1.0f),
                PartPose.offsetAndRotation(-1.2f, 0.1f, 0.1f, 0.0f, 0.136659280431156f, 0.0f));
        PartDefinition lArmSpike04a = tail03.addOrReplaceChild("lArmSpike04a",
                CubeListBuilder.create().texOffs(185, 0).addBox(-1.0f, -2.4f, -1.0f, 2.0f, 2.0f, 2.0f),
                PartPose.offsetAndRotation(0.0f, -2.1f, 6.0f, -0.136659280431156f, 0.0f, 0.0f));
        lArmSpike04a.addOrReplaceChild("lArmSpike04b",
                CubeListBuilder.create().texOffs(185, 0).addBox(-0.5f, -2.0f, -0.5f, 1.0f, 3.0f, 1.0f),
                PartPose.offsetAndRotation(0.0f, -2.0f, 0.1f, -0.22759093446006054f, 0.0f, 0.0f));
        PartDefinition tailSpike03a = tail03.addOrReplaceChild("tailSpike03a",
                CubeListBuilder.create().texOffs(185, 0).addBox(-1.5f, -2.0f, -1.5f, 3.0f, 2.0f, 3.0f),
                PartPose.offsetAndRotation(0.0f, -3.4f, 2.7f, -0.27314402793711257f, 0.0f, 0.0f));
        PartDefinition lArmSpike03b = tailSpike03a.addOrReplaceChild("lArmSpike03b",
                CubeListBuilder.create().texOffs(185, 0).addBox(-1.0f, -2.4f, -1.0f, 2.0f, 2.0f, 2.0f),
                PartPose.offsetAndRotation(0.0f, -1.0f, -0.3f, -0.136659280431156f, 0.0f, 0.0f));
        lArmSpike03b.addOrReplaceChild("lArmSpike03c",
                CubeListBuilder.create().texOffs(185, 0).addBox(-0.5f, -2.0f, -0.5f, 1.0f, 3.0f, 1.0f),
                PartPose.offsetAndRotation(0.0f, -2.0f, 0.1f, -0.22759093446006054f, 0.0f, 0.0f));
        PartDefinition tail04 = tail03.addOrReplaceChild("tail04",
                CubeListBuilder.create().texOffs(74, 29).addBox(-5.0f, -3.5f, 0.0f, 10.0f, 7.0f, 5.0f),
                PartPose.offset(0.0f, 0.5f, 6.0f));
        PartDefinition lTailSpike04a = tail04.addOrReplaceChild("lTailSpike04a",
                CubeListBuilder.create().texOffs(185, 2).addBox(0.6f, -1.5f, -1.5f, 3.0f, 3.0f, 3.0f),
                PartPose.offsetAndRotation(3.5f, 1.5f, 2.1f, 0.0f, 0.0f, 0.136659280431156f));
        PartDefinition lTailSpike04b = lTailSpike04a.addOrReplaceChild("lTailSpike04b",
                CubeListBuilder.create().texOffs(185, 2).addBox(-0.1f, -1.0f, -1.0f, 2.0f, 2.0f, 2.0f),
                PartPose.offsetAndRotation(3.5f, 0.0f, 0.0f, 0.0f, -0.136659280431156f, 0.0f));
        lTailSpike04b.addOrReplaceChild("lTailSpike04c",
                CubeListBuilder.create().texOffs(185, 2).addBox(-0.1f, -0.5f, -0.5f, 2.0f, 1.0f, 1.0f),
                PartPose.offsetAndRotation(1.7f, 0.1f, 0.1f, 0.0f, -0.136659280431156f, 0.0f));
        PartDefinition rTailSpike04a = tail04.addOrReplaceChild("rTailSpike04a",
                CubeListBuilder.create().texOffs(185, 2).mirror().addBox(-3.1f, -1.5f, -1.5f, 3.0f, 3.0f, 3.0f),
                PartPose.offsetAndRotation(-4.6f, 1.5f, 2.1f, 0.0f, 0.0f, -0.136659280431156f));
        PartDefinition rTailSpike04b = rTailSpike04a.addOrReplaceChild("rTailSpike04b",
                CubeListBuilder.create().texOffs(185, 2).mirror().addBox(-1.6f, -1.0f, -1.0f, 2.0f, 2.0f, 2.0f),
                PartPose.offsetAndRotation(-3.2f, 0.0f, 0.0f, 0.0f, 0.136659280431156f, 0.0f));
        rTailSpike04b.addOrReplaceChild("rTailSpike04c",
                CubeListBuilder.create().texOffs(185, 2).mirror().addBox(-2.2f, -0.5f, -0.5f, 2.0f, 1.0f, 1.0f),
                PartPose.offsetAndRotation(-1.2f, 0.1f, 0.1f, 0.0f, 0.136659280431156f, 0.0f));
        PartDefinition tail05 = tail04.addOrReplaceChild("tail05",
                CubeListBuilder.create().texOffs(105, 29).addBox(-4.5f, -3.0f, 0.0f, 9.0f, 6.0f, 6.0f),
                PartPose.offset(0.0f, 0.5f, 5.0f));
        PartDefinition tail06 = tail05.addOrReplaceChild("tail06",
                CubeListBuilder.create().texOffs(136, 31).addBox(-3.5f, -2.5f, 0.0f, 7.0f, 5.0f, 5.0f),
                PartPose.offset(0.0f, 0.5f, 6.0f));
        PartDefinition tail07 = tail06.addOrReplaceChild("tail07",
                CubeListBuilder.create().texOffs(161, 31).addBox(-2.5f, -2.0f, 0.0f, 5.0f, 4.0f, 6.0f),
                PartPose.offset(0.0f, 0.5f, 4.8f));
        PartDefinition lArmSpike08a = tail07.addOrReplaceChild("lArmSpike08a",
                CubeListBuilder.create().texOffs(185, 0).addBox(-1.0f, -2.4f, -1.0f, 2.0f, 2.0f, 2.0f),
                PartPose.offsetAndRotation(0.0f, -0.4f, 3.6f, -0.136659280431156f, 0.0f, 0.0f));
        lArmSpike08a.addOrReplaceChild("lArmSpike08b",
                CubeListBuilder.create().texOffs(185, 0).addBox(-0.5f, -1.6f, -0.5f, 1.0f, 2.0f, 1.0f),
                PartPose.offsetAndRotation(0.0f, -2.0f, 0.1f, -0.22759093446006054f, 0.0f, 0.0f));
        PartDefinition lArmSpike07a = tail07.addOrReplaceChild("lArmSpike07a",
                CubeListBuilder.create().texOffs(185, 0).addBox(-1.0f, -2.4f, -1.0f, 2.0f, 2.0f, 2.0f),
                PartPose.offsetAndRotation(0.0f, -1.3f, 1.0f, -0.136659280431156f, 0.0f, 0.0f));
        lArmSpike07a.addOrReplaceChild("lArmSpike07b",
                CubeListBuilder.create().texOffs(185, 0).addBox(-0.5f, -2.0f, -0.5f, 1.0f, 3.0f, 1.0f),
                PartPose.offsetAndRotation(0.0f, -2.0f, 0.1f, -0.22759093446006054f, 0.0f, 0.0f));
        PartDefinition tail08 = tail07.addOrReplaceChild("tail08",
                CubeListBuilder.create().texOffs(185, 33).addBox(-1.5f, -1.5f, 0.0f, 3.0f, 3.0f, 5.0f),
                PartPose.offset(0.0f, 0.5f, 5.1f));
        tail08.addOrReplaceChild("tail09",
                CubeListBuilder.create().texOffs(204, 33).addBox(-1.0f, -1.5f, 0.0f, 2.0f, 2.0f, 6.0f),
                PartPose.offset(0.0f, 0.9f, 4.9f));
        PartDefinition lArmSpike06a = tail06.addOrReplaceChild("lArmSpike06a",
                CubeListBuilder.create().texOffs(185, 0).addBox(-1.0f, -2.4f, -1.0f, 2.0f, 2.0f, 2.0f),
                PartPose.offsetAndRotation(0.0f, -1.7f, 2.9f, -0.136659280431156f, 0.0f, 0.0f));
        lArmSpike06a.addOrReplaceChild("lArmSpike06b",
                CubeListBuilder.create().texOffs(185, 0).addBox(-0.5f, -2.0f, -0.5f, 1.0f, 3.0f, 1.0f),
                PartPose.offsetAndRotation(0.0f, -2.0f, 0.1f, -0.22759093446006054f, 0.0f, 0.0f));
        PartDefinition lArmSpike05a = tail06.addOrReplaceChild("lArmSpike05a",
                CubeListBuilder.create().texOffs(185, 0).addBox(-1.0f, -2.4f, -1.0f, 2.0f, 2.0f, 2.0f),
                PartPose.offsetAndRotation(0.0f, -1.4f, 0.0f, -0.136659280431156f, 0.0f, 0.0f));
        lArmSpike05a.addOrReplaceChild("lArmSpike05b",
                CubeListBuilder.create().texOffs(185, 0).addBox(-0.5f, -1.6f, -0.5f, 1.0f, 2.0f, 1.0f),
                PartPose.offsetAndRotation(0.0f, -2.0f, 0.1f, -0.22759093446006054f, 0.0f, 0.0f));
        PartDefinition lTailSpike03a = tail03.addOrReplaceChild("lTailSpike03a",
                CubeListBuilder.create().texOffs(185, 2).addBox(0.6f, -1.5f, -1.5f, 3.0f, 3.0f, 3.0f),
                PartPose.offsetAndRotation(4.0f, 1.8f, 1.9f, 0.0f, 0.0f, 0.136659280431156f));
        PartDefinition lTailSpike03b = lTailSpike03a.addOrReplaceChild("lTailSpike03b",
                CubeListBuilder.create().texOffs(185, 2).addBox(-0.1f, -1.0f, -1.0f, 2.0f, 2.0f, 2.0f),
                PartPose.offsetAndRotation(3.5f, 0.0f, 0.0f, 0.0f, -0.136659280431156f, 0.0f));
        lTailSpike03b.addOrReplaceChild("lTailSpike03c",
                CubeListBuilder.create().texOffs(185, 2).addBox(-0.1f, -0.5f, -0.5f, 2.0f, 1.0f, 1.0f),
                PartPose.offsetAndRotation(1.7f, 0.1f, 0.1f, 0.0f, -0.136659280431156f, 0.0f));
        PartDefinition tailSpike02a = tail02.addOrReplaceChild("tailSpike02a",
                CubeListBuilder.create().texOffs(185, 0).mirror().addBox(-1.5f, -2.0f, -1.5f, 3.0f, 2.0f, 3.0f),
                PartPose.offsetAndRotation(0.0f, -4.6f, 5.0f, -0.27314402793711257f, 0.0f, 0.0f));
        PartDefinition lArmSpike02b = tailSpike02a.addOrReplaceChild("lArmSpike02b",
                CubeListBuilder.create().texOffs(185, 0).addBox(-1.0f, -2.4f, -1.0f, 2.0f, 2.0f, 2.0f),
                PartPose.offsetAndRotation(0.0f, -1.0f, -0.3f, -0.136659280431156f, 0.0f, 0.0f));
        lArmSpike02b.addOrReplaceChild("lArmSpike02c",
                CubeListBuilder.create().texOffs(185, 0).addBox(-0.5f, -2.0f, -0.5f, 1.0f, 3.0f, 1.0f),
                PartPose.offsetAndRotation(0.0f, -2.0f, 0.1f, -0.22759093446006054f, 0.0f, 0.0f));
        PartDefinition rTailSpike01a = tail01.addOrReplaceChild("rTailSpike01a",
                CubeListBuilder.create().texOffs(185, 2).mirror().addBox(-3.1f, -1.5f, -1.5f, 3.0f, 3.0f, 3.0f),
                PartPose.offsetAndRotation(-3.7f, 8.3f, 3.2f, 0.0f, 0.0f, -0.136659280431156f));
        PartDefinition rTailSpike01b = rTailSpike01a.addOrReplaceChild("rTailSpike01b",
                CubeListBuilder.create().texOffs(185, 2).mirror().addBox(-1.6f, -1.0f, -1.0f, 2.0f, 2.0f, 2.0f),
                PartPose.offsetAndRotation(-3.2f, 0.0f, 0.0f, 0.0f, 0.136659280431156f, 0.0f));
        rTailSpike01b.addOrReplaceChild("rTailSpike01c",
                CubeListBuilder.create().texOffs(185, 2).mirror().addBox(-2.2f, -0.5f, -0.5f, 2.0f, 1.0f, 1.0f),
                PartPose.offsetAndRotation(-1.2f, 0.1f, 0.1f, 0.0f, 0.136659280431156f, 0.0f));
        PartDefinition lTailSpike01a = tail01.addOrReplaceChild("lTailSpike01a",
                CubeListBuilder.create().texOffs(185, 2).addBox(0.6f, -1.5f, -1.5f, 3.0f, 3.0f, 3.0f),
                PartPose.offsetAndRotation(5.7f, 8.3f, 3.2f, 0.0f, 0.0f, 0.136659280431156f));
        PartDefinition lTailSpike01b = lTailSpike01a.addOrReplaceChild("lTailSpike01b",
                CubeListBuilder.create().texOffs(185, 2).addBox(-0.1f, -1.0f, -1.0f, 2.0f, 2.0f, 2.0f),
                PartPose.offsetAndRotation(3.5f, 0.0f, 0.0f, 0.0f, -0.136659280431156f, 0.0f));
        lTailSpike01b.addOrReplaceChild("lTailSpike01c",
                CubeListBuilder.create().texOffs(185, 2).addBox(-0.1f, -0.5f, -0.5f, 2.0f, 1.0f, 1.0f),
                PartPose.offsetAndRotation(1.7f, 0.1f, 0.1f, 0.0f, -0.136659280431156f, 0.0f));
        PartDefinition lTailSpike00a = tail01.addOrReplaceChild("lTailSpike00a",
                CubeListBuilder.create().texOffs(185, 2).addBox(0.6f, -1.5f, -1.5f, 3.0f, 3.0f, 3.0f),
                PartPose.offsetAndRotation(5.6f, 4.0f, -2.1f, 0.22759093446006054f, 0.0f, 0.18203784098300857f));
        PartDefinition lTailSpike00b = lTailSpike00a.addOrReplaceChild("lTailSpike00b",
                CubeListBuilder.create().texOffs(185, 2).addBox(-0.1f, -1.0f, -1.0f, 2.0f, 2.0f, 2.0f),
                PartPose.offsetAndRotation(3.5f, 0.0f, 0.0f, 0.0f, 0.0f, 0.18203784098300857f));
        lTailSpike00b.addOrReplaceChild("lTailSpike00c",
                CubeListBuilder.create().texOffs(185, 2).addBox(-0.1f, -0.5f, -0.5f, 2.0f, 1.0f, 1.0f),
                PartPose.offsetAndRotation(1.7f, 0.1f, 0.1f, 0.0f, 0.0f, 0.18203784098300857f));
        PartDefinition lShoulder = chest.addOrReplaceChild("lShoulder",
                CubeListBuilder.create().texOffs(0, 52).addBox(-1.5f, -1.8f, -2.5f, 4.0f, 7.0f, 5.0f),
                PartPose.offsetAndRotation(6.8f, -2.9f, 0.0f, 0.136659280431156f, 0.0f, -0.31869712141416456f));
        PartDefinition lArm1 = lShoulder.addOrReplaceChild("lArm1",
                CubeListBuilder.create().texOffs(22, 51).mirror().addBox(-1.0f, -0.5f, -1.9f, 3.0f, 10.0f, 4.0f),
                PartPose.offsetAndRotation(0.0f, 4.8f, 0.0f, -0.31869712141416456f, 0.0f, 0.0f));
        PartDefinition lArm2 = lArm1.addOrReplaceChild("lArm2",
                CubeListBuilder.create().texOffs(40, 45).addBox(-1.2f, -1.7f, -7.4f, 2.0f, 3.0f, 7.0f),
                PartPose.offsetAndRotation(1.0f, 8.1f, 0.0f, -0.045553093477052f, 0.0f, 0.0f));
        PartDefinition lArmSpike3a = lArm2.addOrReplaceChild("lArmSpike3a",
                CubeListBuilder.create().texOffs(185, 0).addBox(-1.0f, -0.1f, -1.0f, 2.0f, 4.0f, 2.0f),
                PartPose.offsetAndRotation(-0.3f, 1.2f, -6.3f, 0.36425021489121656f, 0.0f, 0.0f));
        lArmSpike3a.addOrReplaceChild("lArmSpike3b",
                CubeListBuilder.create().texOffs(185, 0).addBox(-0.4f, -0.5f, -0.6f, 1.0f, 4.0f, 1.0f),
                PartPose.offsetAndRotation(0.0f, 4.1f, 0.1f, 0.18203784098300857f, 0.0f, 0.0f));
        PartDefinition lArm3 = lArm2.addOrReplaceChild("lArm3",
                CubeListBuilder.create().texOffs(66, 48).addBox(-2.2f, -2.2f, -4.2f, 4.0f, 4.0f, 7.0f),
                PartPose.offset(0.0f, 0.0f, -10.0f));
        PartDefinition lArmSpike2a = lArm3.addOrReplaceChild("lArmSpike2a",
                CubeListBuilder.create().texOffs(185, 0).addBox(-1.5f, -0.1f, -1.5f, 3.0f, 4.0f, 3.0f),
                PartPose.offsetAndRotation(0.0f, 1.8f, 1.1f, 0.136659280431156f, 0.0f, 0.0f));
        PartDefinition lArmSpike2b = lArmSpike2a.addOrReplaceChild("lArmSpike2b",
                CubeListBuilder.create().texOffs(185, 0).addBox(-1.0f, -0.1f, -1.0f, 2.0f, 4.0f, 2.0f),
                PartPose.offsetAndRotation(0.0f, 3.6f, -0.1f, 0.18203784098300857f, 0.0f, 0.0f));
        lArmSpike2b.addOrReplaceChild("lArmSpike2c",
                CubeListBuilder.create().texOffs(185, 0).addBox(-0.4f, -0.5f, -0.6f, 1.0f, 4.0f, 1.0f),
                PartPose.offsetAndRotation(0.0f, 4.1f, 0.1f, 0.18203784098300857f, 0.0f, 0.0f));
        PartDefinition lArmSpike1a = lArm3.addOrReplaceChild("lArmSpike1a",
                CubeListBuilder.create().texOffs(185, 0).addBox(-1.5f, -0.1f, -1.5f, 3.0f, 6.0f, 3.0f),
                PartPose.offset(0.0f, 1.8f, -2.3f));
        PartDefinition lArmSpike1b = lArmSpike1a.addOrReplaceChild("lArmSpike1b",
                CubeListBuilder.create().texOffs(185, 0).addBox(-1.0f, -0.1f, -1.0f, 2.0f, 6.0f, 2.0f),
                PartPose.offsetAndRotation(0.0f, 5.8f, -0.1f, 0.18203784098300857f, 0.0f, 0.0f));
        lArmSpike1b.addOrReplaceChild("lArmSpike1c",
                CubeListBuilder.create().texOffs(185, 0).addBox(-0.4f, -0.5f, -0.6f, 1.0f, 6.0f, 1.0f),
                PartPose.offsetAndRotation(0.0f, 6.1f, 0.1f, 0.18203784098300857f, 0.0f, 0.0f));
        return LayerDefinition.create(mesh, 256, 128);
    }
}

