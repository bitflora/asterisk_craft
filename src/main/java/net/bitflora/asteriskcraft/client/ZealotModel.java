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
 * Ported from the 1.12 StarCraft mod's ModelZealot (mechanical box translation; see
 * tools/model_convert.py). Head-look + a simple limb-swing walk are re-authored here; the old
 * per-render tweaks (neck scale, blade-sheath toggle) are intentionally not reproduced.
 */
public class ZealotModel extends EntityModel<LivingEntityRenderState> {
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

    public ZealotModel(ModelPart root) {
        super(root);
        this.head = root.getChild("neck").getChild("head");
        this.legL = root.getChild("chestUpper").getChild("abdomen").getChild("waist").getChild("legLeft1");
        this.legR = root.getChild("chestUpper").getChild("abdomen").getChild("waist").getChild("legRight1");
        this.armL = root.getChild("chestUpper").getChild("shoulders").getChild("armLeft1");
        this.armR = root.getChild("chestUpper").getChild("shoulders").getChild("armRight1");
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
        PartDefinition arcRight1 = root.addOrReplaceChild("arcRight1",
                CubeListBuilder.create().texOffs(50, 60).addBox(0.0f, 0.0f, 0.0f, 3.0f, 13.0f, 1.0f),
                PartPose.offsetAndRotation(-12.0f, -29.0f, -6.0f, 0.5235987901687622f, 0.0f, -0.296705961227417f));
        PartDefinition arcRight2 = arcRight1.addOrReplaceChild("arcRight2",
                CubeListBuilder.create().texOffs(50, 76).addBox(0.0f, 0.0f, 0.0f, 3.0f, 12.0f, 1.0f),
                PartPose.offsetAndRotation(0.0f, 0.6f, 0.2f, 1.8151424220741026f, 0.0f, 0.0f));
        PartDefinition arcRight3 = arcRight2.addOrReplaceChild("arcRight3",
                CubeListBuilder.create().texOffs(50, 91).addBox(0.0f, 0.0f, 0.0f, 3.0f, 14.0f, 1.0f),
                PartPose.offsetAndRotation(0.0f, 11.4f, 0.7f, -1.3203415791337103f, 0.0f, 0.0f));
        PartDefinition arcRight4 = arcRight3.addOrReplaceChild("arcRight4",
                CubeListBuilder.create().texOffs(50, 108).addBox(0.0f, 0.0f, 0.0f, 3.0f, 12.0f, 1.0f),
                PartPose.offsetAndRotation(0.1f, 12.8f, 0.3f, -1.1838568316277536f, 0.0f, 0.045553093477052f));
        arcRight4.addOrReplaceChild("arcRight5",
                CubeListBuilder.create().texOffs(50, 122).addBox(-1.5f, 0.0f, 0.0f, 4.0f, 3.0f, 3.0f),
                PartPose.offsetAndRotation(0.9f, 10.9f, -1.2f, 0.20943951023931953f, 0.0f, 0.27314402793711257f));
        PartDefinition neck = root.addOrReplaceChild("neck",
                CubeListBuilder.create().texOffs(189, 21).addBox(-2.5f, -3.2f, -1.4f, 5.0f, 5.0f, 4.0f),
                PartPose.offsetAndRotation(0.0f, -27.9f, 3.2f, 0.6829473363053812f, 0.0f, 0.0f));
        PartDefinition head = neck.addOrReplaceChild("head",
                CubeListBuilder.create().texOffs(166, 0).addBox(-3.0f, -2.0f, -2.0f, 6.0f, 4.0f, 3.0f),
                PartPose.offsetAndRotation(0.0f, -4.0f, 0.7f, -0.8196066167365371f, 0.0f, 0.0f));
        head.addOrReplaceChild("headBack",
                CubeListBuilder.create().texOffs(166, 18).addBox(-2.5f, -5.4f, -0.05f, 5.0f, 6.0f, 2.0f),
                PartPose.offsetAndRotation(0.0f, -1.8f, -2.1f, -0.6829473363053812f, 0.0f, 0.0f));
        PartDefinition mouthIGuess = head.addOrReplaceChild("mouthIGuess",
                CubeListBuilder.create().texOffs(166, 9).addBox(-1.5f, -0.4f, -1.0f, 3.0f, 3.0f, 2.0f),
                PartPose.offsetAndRotation(0.0f, 1.6f, -1.2f, -0.27314402793711257f, 0.0f, 0.0f));
        mouthIGuess.addOrReplaceChild("chin",
                CubeListBuilder.create().texOffs(178, 11).addBox(-1.0f, 0.0f, -0.5f, 2.0f, 2.0f, 1.0f),
                PartPose.offsetAndRotation(0.0f, 1.8f, -0.3f, -0.22759093446006054f, 0.0f, 0.0f));
        head.addOrReplaceChild("lSkullSlant2",
                CubeListBuilder.create().texOffs(200, 5).addBox(1.4f, -3.3f, 0.2f, 2.0f, 4.0f, 1.0f),
                PartPose.offsetAndRotation(0.0f, 0.0f, 0.0f, -0.7740535232594852f, 0.0f, 0.0f));
        head.addOrReplaceChild("headBack2",
                CubeListBuilder.create().texOffs(166, 27).addBox(-3.0f, -7.0f, -0.8f, 6.0f, 8.0f, 2.0f),
                PartPose.offsetAndRotation(0.0f, -1.0f, -0.8f, -0.7740535232594852f, 0.0f, 0.0f));
        head.addOrReplaceChild("eyes",
                CubeListBuilder.create().texOffs(192, 0).addBox(-2.5f, 0.1f, -1.9f, 5.0f, 2.0f, 2.0f),
                PartPose.offsetAndRotation(0.0f, 0.0f, 0.0f, -1.2747884856566583f, 0.0f, 0.0f));
        head.addOrReplaceChild("rSkullSlant2",
                CubeListBuilder.create().texOffs(200, 5).addBox(-3.4f, -3.3f, 0.2f, 2.0f, 4.0f, 1.0f),
                PartPose.offsetAndRotation(0.0f, 0.0f, 0.0f, -0.7740535232594852f, 0.0f, 0.0f));
        head.addOrReplaceChild("rSkullSlant",
                CubeListBuilder.create().texOffs(191, 6).mirror().addBox(-3.5f, -0.5f, -0.5f, 2.0f, 4.0f, 1.0f),
                PartPose.offsetAndRotation(0.0f, 0.0f, 0.0f, -0.36425021489121656f, 0.0f, -0.40980330836826856f));
        PartDefinition headTendrilBundle = head.addOrReplaceChild("headTendrilBundle",
                CubeListBuilder.create().texOffs(210, 2).addBox(-1.5f, -0.9f, -0.7f, 3.0f, 3.0f, 5.0f),
                PartPose.offsetAndRotation(0.0f, -1.6f, 1.8f, 0.091106186954104f, 0.0f, 0.0f));
        PartDefinition headTendrilBundle2 = headTendrilBundle.addOrReplaceChild("headTendrilBundle2",
                CubeListBuilder.create().texOffs(210, 13).addBox(-1.5f, -1.5f, 0.0f, 3.0f, 3.0f, 4.0f),
                PartPose.offsetAndRotation(0.0f, 0.3f, 3.3f, -0.5009094953223726f, 0.0f, 0.0f));
        PartDefinition headTendrilBundle3 = headTendrilBundle2.addOrReplaceChild("headTendrilBundle3",
                CubeListBuilder.create().texOffs(212, 22).addBox(-1.5f, -1.5f, 0.0f, 3.0f, 3.0f, 6.0f),
                PartPose.offsetAndRotation(0.0f, -0.3f, 3.1f, -0.6373942428283291f, 0.0f, 0.0f));
        PartDefinition headTendrilBundle4 = headTendrilBundle3.addOrReplaceChild("headTendrilBundle4",
                CubeListBuilder.create().texOffs(213, 33).addBox(-1.0f, -1.0f, 0.0f, 2.0f, 2.0f, 5.0f),
                PartPose.offsetAndRotation(0.0f, -0.2f, 5.7f, -0.36425021489121656f, 0.0f, 0.0f));
        PartDefinition headTendrilBundle5 = headTendrilBundle4.addOrReplaceChild("headTendrilBundle5",
                CubeListBuilder.create().texOffs(213, 44).addBox(-1.0f, -1.0f, 0.0f, 2.0f, 2.0f, 8.0f),
                PartPose.offset(0.0f, 0.0f, 4.7f));
        headTendrilBundle5.addOrReplaceChild("headTendrilBundle6",
                CubeListBuilder.create().texOffs(213, 55).addBox(-0.5f, -0.5f, 0.0f, 1.0f, 1.0f, 6.0f),
                PartPose.offset(0.0f, 0.0f, 7.7f));
        head.addOrReplaceChild("lSkullSlant",
                CubeListBuilder.create().texOffs(191, 6).addBox(1.5f, -0.5f, -0.5f, 2.0f, 4.0f, 1.0f),
                PartPose.offsetAndRotation(0.0f, 0.0f, 0.0f, -0.36425021489121656f, 0.0f, 0.40980330836826856f));
        PartDefinition chestUpper = root.addOrReplaceChild("chestUpper",
                CubeListBuilder.create().texOffs(0, 18).addBox(-12.0f, 0.0f, -3.0f, 24.0f, 9.0f, 14.0f),
                PartPose.offset(0.0f, -23.3f, 0.0f));
        PartDefinition abdomen = chestUpper.addOrReplaceChild("abdomen",
                CubeListBuilder.create().texOffs(0, 59).addBox(-5.0f, 12.0f, -2.0f, 10.0f, 7.0f, 5.0f),
                PartPose.offset(0.0f, 0.0f, 4.0f));
        PartDefinition waist = abdomen.addOrReplaceChild("waist",
                CubeListBuilder.create().texOffs(0, 73).addBox(-7.0f, 17.5f, -4.0f, 14.0f, 5.0f, 9.0f),
                PartPose.offset(0.0f, 0.0f, 0.0f));
        PartDefinition legRight1 = waist.addOrReplaceChild("legRight1",
                CubeListBuilder.create().texOffs(92, 0).addBox(-3.0f, -2.0f, -3.0f, 6.0f, 15.0f, 6.0f),
                PartPose.offsetAndRotation(-6.0f, 21.0f, 0.0f, -0.5235987755982988f, 0.136659280431156f, 0.0f));
        PartDefinition legRight2 = legRight1.addOrReplaceChild("legRight2",
                CubeListBuilder.create().texOffs(92, 23).addBox(-3.5f, -3.0f, -3.0f, 7.0f, 6.0f, 6.0f),
                PartPose.offsetAndRotation(0.0f, 14.7f, 0.0f, 0.5235987755982988f, -0.136659280431156f, -0.045553093477052f));
        PartDefinition legRight3 = legRight2.addOrReplaceChild("legRight3",
                CubeListBuilder.create().texOffs(92, 36).addBox(-2.5f, -2.5f, 0.0f, 5.0f, 5.0f, 10.0f),
                PartPose.offsetAndRotation(-0.7f, 0.2f, 1.0f, 0.18203784098300857f, 0.0f, 0.0f));
        PartDefinition legRight4 = legRight3.addOrReplaceChild("legRight4",
                CubeListBuilder.create().texOffs(92, 53).addBox(-3.0f, -2.5f, 0.0f, 6.0f, 5.0f, 5.0f),
                PartPose.offsetAndRotation(0.0f, -0.9f, 7.0f, -0.27314402793711257f, 0.0f, 0.0f));
        PartDefinition legRight5 = legRight4.addOrReplaceChild("legRight5",
                CubeListBuilder.create().texOffs(92, 65).addBox(-2.48f, 0.0f, -2.5f, 5.0f, 14.0f, 5.0f),
                PartPose.offsetAndRotation(0.0f, 0.6f, 2.8f, -0.136659280431156f, 0.091106186954104f, 0.0f));
        PartDefinition footRight = legRight5.addOrReplaceChild("footRight",
                CubeListBuilder.create().texOffs(92, 86).addBox(-3.0f, 0.0f, -7.0f, 6.0f, 3.0f, 8.0f),
                PartPose.offsetAndRotation(0.0f, 12.1f, 0.2f, 0.22759093446006054f, 0.091106186954104f, 0.0f));
        footRight.addOrReplaceChild("toeRightLeft",
                CubeListBuilder.create().texOffs(92, 106).addBox(0.5f, 0.9f, -9.6f, 2.0f, 2.0f, 4.0f),
                PartPose.offset(0.0f, 0.0f, 0.0f));
        footRight.addOrReplaceChild("toeRightRight",
                CubeListBuilder.create().texOffs(92, 106).addBox(-2.5f, 0.9f, -9.5f, 2.0f, 2.0f, 4.0f),
                PartPose.offset(0.0f, 0.0f, 0.0f));
        footRight.addOrReplaceChild("footRightUpper",
                CubeListBuilder.create().texOffs(92, 99).addBox(-2.0f, -0.9f, -5.0f, 4.0f, 2.0f, 4.0f),
                PartPose.offset(0.0f, 0.0f, 0.0f));
        PartDefinition waistPlate1 = waist.addOrReplaceChild("waistPlate1",
                CubeListBuilder.create().texOffs(48, 44).addBox(-2.5f, -2.5f, 0.0f, 5.0f, 5.0f, 1.0f),
                PartPose.offsetAndRotation(0.0f, 19.0f, -5.1f, 0.0f, 0.0f, 0.7853981633974483f));
        waistPlate1.addOrReplaceChild("waistPlate2",
                CubeListBuilder.create().texOffs(62, 44).addBox(-1.0f, 0.0f, 0.0f, 2.0f, 6.0f, 1.0f),
                PartPose.offsetAndRotation(0.0f, 0.0f, 0.1f, 0.0f, 0.0f, -0.7853981633974483f));
        PartDefinition legLeft1 = waist.addOrReplaceChild("legLeft1",
                CubeListBuilder.create().texOffs(92, 0).mirror().addBox(-3.0f, -2.0f, -3.0f, 6.0f, 15.0f, 6.0f),
                PartPose.offsetAndRotation(6.0f, 21.0f, 0.0f, -0.5235987755982988f, -0.136659280431156f, 0.0f));
        PartDefinition legLeft2 = legLeft1.addOrReplaceChild("legLeft2",
                CubeListBuilder.create().texOffs(92, 23).mirror().addBox(-3.5f, -3.0f, -3.2f, 7.0f, 6.0f, 6.0f),
                PartPose.offsetAndRotation(0.0f, 14.7f, 0.0f, 0.5235987755982988f, 0.136659280431156f, 0.045553093477052f));
        PartDefinition legLeft3 = legLeft2.addOrReplaceChild("legLeft3",
                CubeListBuilder.create().texOffs(92, 36).mirror().addBox(-2.5f, -2.5f, 0.0f, 5.0f, 5.0f, 10.0f),
                PartPose.offsetAndRotation(0.7f, 0.2f, 1.0f, 0.18203784098300857f, 0.0f, 0.0f));
        PartDefinition legLeft4 = legLeft3.addOrReplaceChild("legLeft4",
                CubeListBuilder.create().texOffs(92, 53).mirror().addBox(-3.0f, -2.5f, 0.0f, 6.0f, 5.0f, 5.0f),
                PartPose.offsetAndRotation(0.0f, -0.9f, 7.0f, -0.27314402793711257f, 0.0f, 0.0f));
        PartDefinition legLeft5 = legLeft4.addOrReplaceChild("legLeft5",
                CubeListBuilder.create().texOffs(92, 65).mirror().addBox(-2.52f, 0.0f, -2.5f, 5.0f, 14.0f, 5.0f),
                PartPose.offsetAndRotation(0.0f, 0.6f, 2.8f, -0.136659280431156f, -0.091106186954104f, 0.0f));
        PartDefinition footLeft = legLeft5.addOrReplaceChild("footLeft",
                CubeListBuilder.create().texOffs(92, 86).mirror().addBox(-3.0f, 0.0f, -7.0f, 6.0f, 3.0f, 8.0f),
                PartPose.offsetAndRotation(0.0f, 12.1f, 0.2f, 0.22759093446006054f, -0.091106186954104f, 0.0f));
        footLeft.addOrReplaceChild("toeLeftRight",
                CubeListBuilder.create().texOffs(92, 106).mirror().addBox(-2.5f, 0.9f, -9.6f, 2.0f, 2.0f, 4.0f),
                PartPose.offset(0.0f, 0.0f, 0.0f));
        footLeft.addOrReplaceChild("toeLeftLeft",
                CubeListBuilder.create().texOffs(92, 106).mirror().addBox(0.5f, 0.9f, -9.5f, 2.0f, 2.0f, 4.0f),
                PartPose.offset(0.0f, 0.0f, 0.0f));
        footLeft.addOrReplaceChild("footLeftUpper",
                CubeListBuilder.create().texOffs(92, 99).mirror().addBox(-2.0f, -0.9f, -5.0f, 4.0f, 2.0f, 4.0f),
                PartPose.offset(0.0f, 0.0f, 0.0f));
        PartDefinition shoulders = chestUpper.addOrReplaceChild("shoulders",
                CubeListBuilder.create().texOffs(0, 0).addBox(-18.0f, -5.0f, 1.0f, 36.0f, 8.0f, 9.0f),
                PartPose.offset(0.0f, 0.0f, 0.0f));
        PartDefinition armRight1 = shoulders.addOrReplaceChild("armRight1",
                CubeListBuilder.create().texOffs(126, 1).addBox(-4.0f, 0.0f, -3.0f, 5.0f, 13.0f, 5.0f),
                PartPose.offsetAndRotation(-13.0f, 0.0f, 5.1f, 0.18203784098300857f, 0.0f, 0.17453292519943295f));
        PartDefinition rForearm = armRight1.addOrReplaceChild("rForearm",
                CubeListBuilder.create().texOffs(129, 90).addBox(-2.0f, -2.0f, -12.0f, 4.0f, 4.0f, 12.0f),
                PartPose.offsetAndRotation(-1.5f, 11.3f, 0.0f, 0.136659280431156f, 0.0f, 0.0f));
        PartDefinition armRight2 = rForearm.addOrReplaceChild("armRight2",
                CubeListBuilder.create().texOffs(126, 21).addBox(-3.0f, -3.0f, -11.0f, 6.0f, 6.0f, 11.0f),
                PartPose.offset(0.0f, 0.0f, 1.4f));
        PartDefinition bladeRight1 = armRight2.addOrReplaceChild("bladeRight1",
                CubeListBuilder.create().texOffs(126, 40).addBox(-2.0f, -2.0f, -3.5f, 2.0f, 4.0f, 7.0f),
                PartPose.offset(-3.0f, 0.0f, -8.1f));
        bladeRight1.addOrReplaceChild("bladeRight2",
                CubeListBuilder.create().texOffs(126, 53).addBox(-2.7f, -3.0f, -5.5f, 1.0f, 6.0f, 11.0f),
                PartPose.offset(0.0f, 0.0f, 0.0f));
        bladeRight1.addOrReplaceChild("bladeRight3",
                CubeListBuilder.create().texOffs(126, 72).addBox(-0.5f, -1.5f, -12.0f, 1.0f, 3.0f, 12.0f),
                PartPose.offset(-0.9f, 0.0f, -3.4f));
        rForearm.addOrReplaceChild("rFist",
                CubeListBuilder.create().texOffs(134, 110).addBox(-2.1f, -2.5f, -4.1f, 5.0f, 5.0f, 5.0f),
                PartPose.offsetAndRotation(0.0f, 0.0f, -12.0f, 0.0f, -0.40980330836826856f, 0.0f));
        PartDefinition armLeft1 = shoulders.addOrReplaceChild("armLeft1",
                CubeListBuilder.create().texOffs(126, 0).mirror().addBox(-2.0f, 0.0f, -2.0f, 5.0f, 13.0f, 5.0f),
                PartPose.offsetAndRotation(14.0f, 0.0f, 5.1f, 0.18203784098300857f, 0.0f, -0.17453292519943295f));
        PartDefinition lForearm = armLeft1.addOrReplaceChild("lForearm",
                CubeListBuilder.create().texOffs(129, 90).mirror().addBox(-2.0f, -2.0f, -12.0f, 4.0f, 4.0f, 12.0f),
                PartPose.offsetAndRotation(0.5f, 11.3f, 0.0f, 0.136659280431156f, 0.0f, 0.0f));
        PartDefinition armLeft2 = lForearm.addOrReplaceChild("armLeft2",
                CubeListBuilder.create().texOffs(126, 21).mirror().addBox(-3.0f, -3.0f, -11.0f, 6.0f, 6.0f, 11.0f),
                PartPose.offset(0.0f, 0.0f, 1.4f));
        PartDefinition bladeLeft1 = armLeft2.addOrReplaceChild("bladeLeft1",
                CubeListBuilder.create().texOffs(126, 40).mirror().addBox(0.0f, -2.0f, -3.5f, 2.0f, 4.0f, 7.0f),
                PartPose.offset(3.0f, 0.0f, -8.1f));
        bladeLeft1.addOrReplaceChild("bladeLeft3",
                CubeListBuilder.create().texOffs(126, 72).mirror().addBox(-0.5f, -1.5f, -12.0f, 1.0f, 3.0f, 12.0f),
                PartPose.offset(0.9f, 0.0f, -3.4f));
        bladeLeft1.addOrReplaceChild("bladeLeft2",
                CubeListBuilder.create().texOffs(126, 53).mirror().addBox(1.7f, -3.0f, -5.5f, 1.0f, 6.0f, 11.0f),
                PartPose.offset(0.0f, 0.0f, 0.0f));
        lForearm.addOrReplaceChild("lFist",
                CubeListBuilder.create().texOffs(134, 110).mirror().addBox(-3.1f, -2.5f, -4.1f, 5.0f, 5.0f, 5.0f),
                PartPose.offsetAndRotation(0.0f, 0.0f, -12.0f, 0.0f, 0.40980330836826856f, 0.0f));
        chestUpper.addOrReplaceChild("chestLower",
                CubeListBuilder.create().texOffs(0, 43).addBox(-7.0f, 8.2f, 0.0f, 14.0f, 5.0f, 9.0f),
                PartPose.offset(0.0f, 0.0f, 0.0f));
        chestUpper.addOrReplaceChild("chestPlate",
                CubeListBuilder.create().texOffs(48, 52).addBox(-2.5f, -2.5f, 0.0f, 5.0f, 5.0f, 1.0f),
                PartPose.offsetAndRotation(0.0f, 9.8f, -3.0f, 0.38781215979314f, -0.3605850234620285f, 0.737750674818003f));
        PartDefinition arcRight1_1 = root.addOrReplaceChild("arcRight1_1",
                CubeListBuilder.create().texOffs(50, 60).mirror().addBox(-3.0f, 0.0f, 0.0f, 3.0f, 13.0f, 1.0f),
                PartPose.offsetAndRotation(12.0f, -29.0f, -6.0f, 0.5235987755982988f, 0.0f, 0.296705972839036f));
        PartDefinition arcRight2_1 = arcRight1_1.addOrReplaceChild("arcRight2_1",
                CubeListBuilder.create().texOffs(50, 76).mirror().addBox(-3.0f, 0.0f, 0.0f, 3.0f, 12.0f, 1.0f),
                PartPose.offsetAndRotation(0.0f, 0.6f, 0.0f, 1.8151424220741026f, 0.0f, 0.0f));
        PartDefinition arcRight3_1 = arcRight2_1.addOrReplaceChild("arcRight3_1",
                CubeListBuilder.create().texOffs(50, 91).mirror().addBox(-3.0f, 0.0f, 0.0f, 3.0f, 14.0f, 1.0f),
                PartPose.offsetAndRotation(0.0f, 11.4f, 0.7f, -1.3203415791337103f, 0.0f, 0.0f));
        PartDefinition arcRight4_1 = arcRight3_1.addOrReplaceChild("arcRight4_1",
                CubeListBuilder.create().texOffs(50, 108).mirror().addBox(-3.0f, 0.0f, 0.0f, 3.0f, 12.0f, 1.0f),
                PartPose.offsetAndRotation(0.1f, 12.8f, 0.3f, -1.1838568316277536f, 0.0f, 0.045553093477052f));
        arcRight4_1.addOrReplaceChild("arcRight5_1",
                CubeListBuilder.create().texOffs(50, 122).mirror().addBox(-2.5f, 0.0f, 0.0f, 4.0f, 3.0f, 3.0f),
                PartPose.offsetAndRotation(-0.9f, 10.9f, -1.2f, 0.20943951023931953f, 0.0f, -0.31869712141416456f));
        return LayerDefinition.create(mesh, 256, 128);
    }
}

