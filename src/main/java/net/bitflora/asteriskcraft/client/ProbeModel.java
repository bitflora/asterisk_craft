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
 * Ported from the 1.12 StarCraft mod's ModelProbe (mechanical box translation; see
 * tools/model_convert.py). Head-look + a simple limb-swing walk are re-authored here; the old
 * per-render tweaks (neck scale, blade-sheath toggle) are intentionally not reproduced.
 */
public class ProbeModel extends EntityModel<LivingEntityRenderState> {



    public ProbeModel(ModelPart root) {
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
        root.addOrReplaceChild("Visor02",
                CubeListBuilder.create().texOffs(39, 25).addBox(-3.0f, -0.41f, -0.5f, 6.0f, 1.0f, 6.0f),
                PartPose.offset(0.0f, 9.9f, -4.6f));
        PartDefinition body01 = root.addOrReplaceChild("body01",
                CubeListBuilder.create().texOffs(0, 0).addBox(-2.0f, -2.5f, -6.0f, 4.0f, 5.0f, 6.0f),
                PartPose.offset(0.0f, 12.0f, 0.0f));
        body01.addOrReplaceChild("bHorn01",
                CubeListBuilder.create().texOffs(13, 37).addBox(-1.5f, 0.0f, -1.0f, 3.0f, 2.0f, 6.0f),
                PartPose.offsetAndRotation(0.0f, 2.0f, -4.8f, 0.22759093446006054f, 0.0f, 0.0f));
        PartDefinition lWing00 = body01.addOrReplaceChild("lWing00",
                CubeListBuilder.create().texOffs(34, 35).addBox(0.9f, -1.5f, -1.5f, 0.0f, 3.0f, 3.0f),
                PartPose.offset(2.0f, 0.7f, -3.2f));
        lWing00.addOrReplaceChild("lWing07",
                CubeListBuilder.create().texOffs(33, 42).addBox(0.0f, -2.2f, -1.0f, 1.0f, 1.0f, 2.0f),
                PartPose.offsetAndRotation(0.0f, 0.0f, 0.0f, -1.5707963267948966f, 0.0f, 0.0f));
        PartDefinition lWing09 = lWing00.addOrReplaceChild("lWing09",
                CubeListBuilder.create().texOffs(42, 37).addBox(-0.5f, -2.5f, -1.5f, 1.0f, 3.0f, 3.0f),
                PartPose.offsetAndRotation(0.1f, -0.9f, 1.1f, -1.1838568316277536f, 0.27314402793711257f, 0.31869712141416456f));
        lWing09.addOrReplaceChild("lWing10",
                CubeListBuilder.create().texOffs(42, 44).addBox(-0.7f, -1.0f, -1.0f, 1.0f, 1.0f, 2.0f),
                PartPose.offset(0.1f, -2.4f, -0.1f));
        PartDefinition lWing11 = lWing00.addOrReplaceChild("lWing11",
                CubeListBuilder.create().texOffs(33, 48).addBox(-0.5f, -0.3f, -1.0f, 1.0f, 5.0f, 2.0f),
                PartPose.offsetAndRotation(0.0f, 1.3f, 0.0f, -0.136659280431156f, 0.091106186954104f, -0.5918411493512771f));
        lWing11.addOrReplaceChild("lWing12",
                CubeListBuilder.create().texOffs(40, 48).addBox(-0.51f, 0.0f, -0.5f, 1.0f, 1.0f, 2.0f),
                PartPose.offsetAndRotation(0.0f, 4.1f, -0.4f, -0.40980330836826856f, 0.0f, 0.0f));
        lWing00.addOrReplaceChild("lWing06",
                CubeListBuilder.create().texOffs(33, 42).addBox(-0.01f, -2.3f, -1.0f, 1.0f, 1.0f, 2.0f),
                PartPose.offsetAndRotation(0.0f, 0.0f, 0.0f, -2.356194490192345f, 0.0f, 0.0f));
        lWing00.addOrReplaceChild("lWing05",
                CubeListBuilder.create().texOffs(33, 42).mirror().addBox(0.0f, 1.2f, -1.0f, 1.0f, 1.0f, 2.0f),
                PartPose.offset(0.0f, 0.0f, 0.0f));
        lWing00.addOrReplaceChild("lWing03",
                CubeListBuilder.create().texOffs(33, 42).addBox(0.0f, -2.2f, -1.0f, 1.0f, 1.0f, 2.0f),
                PartPose.offsetAndRotation(0.0f, 0.0f, 0.0f, 1.5707963267948966f, 0.0f, 0.0f));
        lWing00.addOrReplaceChild("lWing08",
                CubeListBuilder.create().texOffs(33, 42).addBox(-0.01f, -2.3f, -1.0f, 1.0f, 1.0f, 2.0f),
                PartPose.offsetAndRotation(0.0f, 0.0f, 0.0f, -0.7853981633974483f, 0.0f, 0.0f));
        lWing00.addOrReplaceChild("lWing01",
                CubeListBuilder.create().texOffs(33, 42).addBox(0.0f, -2.2f, -1.0f, 1.0f, 1.0f, 2.0f),
                PartPose.offset(0.0f, 0.0f, 0.0f));
        lWing00.addOrReplaceChild("lWing04",
                CubeListBuilder.create().texOffs(33, 42).addBox(-0.01f, -2.3f, -1.0f, 1.0f, 1.0f, 2.0f),
                PartPose.offsetAndRotation(0.0f, 0.0f, 0.0f, 2.356194490192345f, 0.0f, 0.0f));
        lWing00.addOrReplaceChild("lWing02",
                CubeListBuilder.create().texOffs(33, 42).addBox(-0.01f, -2.3f, -1.0f, 1.0f, 1.0f, 2.0f),
                PartPose.offsetAndRotation(0.0f, 0.0f, 0.0f, 0.7853981633974483f, 0.0f, 0.0f));
        PartDefinition tail01 = body01.addOrReplaceChild("tail01",
                CubeListBuilder.create().texOffs(0, 19).addBox(-1.0f, 0.0f, -0.6f, 2.0f, 1.0f, 5.0f),
                PartPose.offset(0.0f, -1.5f, -0.4f));
        tail01.addOrReplaceChild("tail05",
                CubeListBuilder.create().texOffs(15, 13).addBox(-0.5f, -0.6f, 0.0f, 1.0f, 1.0f, 8.0f),
                PartPose.offsetAndRotation(-0.07f, -1.0f, -1.0f, -0.18203784098300857f, 0.18203784098300857f, 0.7853981633974483f));
        PartDefinition tail02 = tail01.addOrReplaceChild("tail02",
                CubeListBuilder.create().texOffs(0, 26).addBox(-1.5f, 0.7f, 0.2f, 3.0f, 2.0f, 4.0f),
                PartPose.offsetAndRotation(0.0f, 0.3f, 0.0f, 0.091106186954104f, 0.0f, 0.0f));
        PartDefinition tail03 = tail02.addOrReplaceChild("tail03",
                CubeListBuilder.create().texOffs(0, 32).addBox(-1.0f, -1.0f, 0.0f, 2.0f, 2.0f, 2.0f),
                PartPose.offset(0.0f, 1.7f, 3.5f));
        tail03.addOrReplaceChild("tail06",
                CubeListBuilder.create().texOffs(0, 38).addBox(-1.0f, -0.5f, 0.2f, 2.0f, 1.0f, 2.0f),
                PartPose.offsetAndRotation(0.0f, 0.3f, 1.8f, 0.045553093477052f, 0.0f, 0.0f));
        tail03.addOrReplaceChild("tail04",
                CubeListBuilder.create().texOffs(9, 33).addBox(-1.0f, -0.6f, 0.0f, 2.0f, 1.0f, 2.0f),
                PartPose.offsetAndRotation(0.0f, -0.1f, 1.9f, -0.136659280431156f, 0.0f, 0.0f));
        PartDefinition eyeShield = body01.addOrReplaceChild("eyeShield",
                CubeListBuilder.create().texOffs(0, 47).addBox(-1.5f, -1.5f, -2.0f, 3.0f, 3.0f, 1.0f),
                PartPose.offset(0.0f, 0.1f, -4.4f));
        eyeShield.addOrReplaceChild("eye",
                CubeListBuilder.create().texOffs(0, 52).addBox(-1.0f, -1.0f, -1.0f, 2.0f, 2.0f, 1.0f),
                PartPose.offset(0.0f, 0.1f, -1.4f));
        PartDefinition rWing00 = body01.addOrReplaceChild("rWing00",
                CubeListBuilder.create().texOffs(34, 35).addBox(0.1f, -1.5f, -1.5f, 0.0f, 3.0f, 3.0f),
                PartPose.offset(-3.0f, 0.6f, -3.2f));
        PartDefinition rWing09 = rWing00.addOrReplaceChild("rWing09",
                CubeListBuilder.create().texOffs(42, 37).mirror().addBox(-0.5f, -2.5f, -1.5f, 1.0f, 3.0f, 3.0f),
                PartPose.offsetAndRotation(0.9f, -0.9f, 1.1f, -1.1838568316277536f, -0.27314402793711257f, -0.31869712141416456f));
        rWing09.addOrReplaceChild("rWing10",
                CubeListBuilder.create().texOffs(42, 44).mirror().addBox(-0.7f, -1.0f, -1.0f, 1.0f, 1.0f, 2.0f),
                PartPose.offset(0.3f, -2.4f, -0.1f));
        rWing00.addOrReplaceChild("rWing04",
                CubeListBuilder.create().texOffs(33, 42).mirror().addBox(0.01f, -2.3f, -1.0f, 1.0f, 1.0f, 2.0f),
                PartPose.offsetAndRotation(0.0f, 0.0f, 0.0f, 2.356194490192345f, 0.0f, 0.0f));
        rWing00.addOrReplaceChild("rWing02",
                CubeListBuilder.create().texOffs(33, 42).mirror().addBox(0.01f, -2.3f, -1.0f, 1.0f, 1.0f, 2.0f),
                PartPose.offsetAndRotation(0.0f, 0.0f, 0.0f, 0.7853981633974483f, 0.0f, 0.0f));
        rWing00.addOrReplaceChild("rWing03",
                CubeListBuilder.create().texOffs(33, 42).mirror().addBox(0.0f, -2.2f, -1.0f, 1.0f, 1.0f, 2.0f),
                PartPose.offsetAndRotation(0.0f, 0.0f, 0.0f, 1.5707963267948966f, 0.0f, 0.0f));
        rWing00.addOrReplaceChild("rWing07",
                CubeListBuilder.create().texOffs(33, 42).mirror().addBox(0.0f, -2.2f, -1.0f, 1.0f, 1.0f, 2.0f),
                PartPose.offsetAndRotation(0.0f, 0.0f, 0.0f, -1.5707963267948966f, 0.0f, 0.0f));
        rWing00.addOrReplaceChild("rWing05",
                CubeListBuilder.create().texOffs(33, 42).addBox(0.0f, 1.2f, -1.0f, 1.0f, 1.0f, 2.0f),
                PartPose.offset(0.0f, 0.0f, 0.0f));
        rWing00.addOrReplaceChild("rWing08",
                CubeListBuilder.create().texOffs(33, 42).mirror().addBox(0.01f, -2.3f, -1.0f, 1.0f, 1.0f, 2.0f),
                PartPose.offsetAndRotation(0.0f, 0.0f, 0.0f, -0.7853981633974483f, 0.0f, 0.0f));
        rWing00.addOrReplaceChild("rWing01",
                CubeListBuilder.create().texOffs(33, 42).mirror().addBox(0.0f, -2.2f, -1.0f, 1.0f, 1.0f, 2.0f),
                PartPose.offset(0.0f, 0.0f, 0.0f));
        rWing00.addOrReplaceChild("rWing06",
                CubeListBuilder.create().texOffs(33, 42).mirror().addBox(0.01f, -2.3f, -1.0f, 1.0f, 1.0f, 2.0f),
                PartPose.offsetAndRotation(0.0f, 0.0f, 0.0f, -2.356194490192345f, 0.0f, 0.0f));
        PartDefinition rWing11 = rWing00.addOrReplaceChild("rWing11",
                CubeListBuilder.create().texOffs(33, 48).mirror().addBox(-0.5f, -0.3f, -1.0f, 1.0f, 5.0f, 2.0f),
                PartPose.offsetAndRotation(1.0f, 1.3f, 0.0f, -0.136659280431156f, -0.091106186954104f, 0.5918411493512771f));
        rWing11.addOrReplaceChild("rWing12",
                CubeListBuilder.create().texOffs(40, 48).mirror().addBox(-0.51f, 0.0f, -0.5f, 1.0f, 1.0f, 2.0f),
                PartPose.offsetAndRotation(0.0f, 4.1f, -0.4f, -0.40980330836826856f, 0.0f, 0.0f));
        body01.addOrReplaceChild("body02",
                CubeListBuilder.create().texOffs(0, 12).addBox(-1.0f, -1.5f, 0.0f, 2.0f, 3.0f, 3.0f),
                PartPose.offsetAndRotation(0.0f, -1.4f, -3.6f, 0.6373942428283291f, 0.0f, 0.0f));
        root.addOrReplaceChild("rEye",
                CubeListBuilder.create().texOffs(0, 52).addBox(-1.0f, -1.0f, -1.0f, 2.0f, 2.0f, 1.0f),
                PartPose.offsetAndRotation(-1.4f, 11.0f, -5.9f, 0.0f, 0.40980330836826856f, 0.045553093477052f));
        root.addOrReplaceChild("Visor01",
                CubeListBuilder.create().texOffs(28, 23).addBox(-2.0f, -0.5f, -2.0f, 4.0f, 1.0f, 4.0f),
                PartPose.offsetAndRotation(0.0f, 9.9f, -5.1f, 0.136659280431156f, -0.7853981633974483f, -0.091106186954104f));
        PartDefinition horn01A = root.addOrReplaceChild("horn01A",
                CubeListBuilder.create().texOffs(29, 0).addBox(-1.5f, -0.5f, 0.0f, 3.0f, 2.0f, 4.0f),
                PartPose.offsetAndRotation(0.0f, 8.6f, -5.5f, 0.4553564018453205f, 0.0f, 0.0f));
        horn01A.addOrReplaceChild("horn01B",
                CubeListBuilder.create().texOffs(45, 0).addBox(-1.0f, -1.0f, -2.0f, 1.0f, 1.0f, 4.0f),
                PartPose.offsetAndRotation(0.0f, 0.3f, 1.9f, 0.0f, 0.0f, 0.7853981633974483f));
        PartDefinition horn02A = horn01A.addOrReplaceChild("horn02A",
                CubeListBuilder.create().texOffs(28, 7).addBox(-1.0f, -0.5f, -2.0f, 2.0f, 2.0f, 2.0f),
                PartPose.offsetAndRotation(0.0f, 0.0f, 0.2f, 0.40980330836826856f, 0.0f, 0.0f));
        horn02A.addOrReplaceChild("horn04",
                CubeListBuilder.create().texOffs(37, 7).addBox(-0.5f, -0.5f, -0.7f, 1.0f, 1.0f, 1.0f),
                PartPose.offsetAndRotation(0.0f, 0.1f, -1.9f, 0.6829473363053812f, 0.0f, 0.0f));
        horn02A.addOrReplaceChild("horn02B",
                CubeListBuilder.create().texOffs(47, 6).addBox(-1.5f, -1.5f, -1.0f, 1.0f, 1.0f, 2.0f),
                PartPose.offsetAndRotation(0.0f, 1.0f, -0.7f, 0.0f, 0.0f, 0.7853981633974483f));
        PartDefinition horn03A = horn01A.addOrReplaceChild("horn03A",
                CubeListBuilder.create().texOffs(29, 13).addBox(-1.0f, -0.5f, 0.0f, 2.0f, 2.0f, 3.0f),
                PartPose.offset(0.0f, 0.0f, 3.8f));
        horn03A.addOrReplaceChild("horn03B",
                CubeListBuilder.create().texOffs(45, 10).addBox(-1.0f, -1.0f, -2.2f, 1.0f, 1.0f, 4.0f),
                PartPose.offsetAndRotation(0.0f, 0.4f, 1.9f, 0.0f, 0.0f, 0.7853981633974483f));
        root.addOrReplaceChild("lEye",
                CubeListBuilder.create().texOffs(0, 52).addBox(-1.0f, -1.0f, -1.0f, 2.0f, 2.0f, 1.0f),
                PartPose.offsetAndRotation(1.4f, 11.0f, -5.9f, 0.0f, -0.40980330836826856f, -0.045553093477052f));
        return LayerDefinition.create(mesh, 64, 64);
    }
}

