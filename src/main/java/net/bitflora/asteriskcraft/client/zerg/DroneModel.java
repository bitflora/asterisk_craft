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
 * Ported from the 1.12 StarCraft mod's ModelLarva (mechanical box translation; see
 * tools/model_convert.py). Head-look + a simple limb-swing walk are re-authored here; the old
 * per-render tweaks (neck scale, blade-sheath toggle) are intentionally not reproduced.
 */
public class DroneModel extends EntityModel<LivingEntityRenderState> {
    private final ModelPart head;
    private final float headX0;

    public DroneModel(ModelPart root) {
        super(root);
        this.head = root.getChild("segment1").getChild("head");
        this.headX0 = this.head.xRot;
    }

    @Override
    public void setupAnim(LivingEntityRenderState state) {
        super.setupAnim(state);
        this.head.yRot = state.yRot * ((float) Math.PI / 180f);
        this.head.xRot = this.headX0 + state.xRot * ((float) Math.PI / 180f);
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        PartDefinition segment1 = root.addOrReplaceChild("segment1",
                CubeListBuilder.create().texOffs(34, 22).addBox(-3.0f, -1.5f, -2.5f, 6.0f, 3.0f, 5.0f),
                PartPose.offset(0.0f, 22.5f, -7.9f));
        PartDefinition lSpike0a = segment1.addOrReplaceChild("lSpike0a",
                CubeListBuilder.create().texOffs(37, 0).addBox(-0.1f, -0.3f, -0.7f, 2.0f, 1.0f, 1.0f),
                PartPose.offsetAndRotation(2.3f, 0.0f, -1.2f, 0.0f, -0.4553564018453205f, 0.0f));
        lSpike0a.addOrReplaceChild("lSpike0b",
                CubeListBuilder.create().texOffs(37, 0).addBox(-0.1f, -0.3f, -0.3f, 2.0f, 1.0f, 1.0f),
                PartPose.offset(0.0f, 0.0f, 0.0f));
        lSpike0a.addOrReplaceChild("lSpike0c",
                CubeListBuilder.create().texOffs(37, 0).addBox(-0.1f, 0.2f, -0.7f, 2.0f, 1.0f, 1.0f),
                PartPose.offset(0.0f, 0.0f, 0.0f));
        lSpike0a.addOrReplaceChild("lSpike0e",
                CubeListBuilder.create().texOffs(39, 0).addBox(0.0f, -0.5f, -0.5f, 1.0f, 1.0f, 1.0f),
                PartPose.offset(1.6f, 0.5f, 0.0f));
        lSpike0a.addOrReplaceChild("lSpike0d",
                CubeListBuilder.create().texOffs(37, 0).addBox(-0.1f, 0.2f, -0.3f, 2.0f, 1.0f, 1.0f),
                PartPose.offset(0.0f, 0.0f, 0.0f));
        PartDefinition lSpike1a = segment1.addOrReplaceChild("lSpike1a",
                CubeListBuilder.create().texOffs(37, 0).addBox(-0.1f, -0.5f, -1.0f, 2.0f, 2.0f, 2.0f),
                PartPose.offsetAndRotation(2.4f, 0.0f, 1.5f, 0.0f, -0.4553564018453205f, 0.0f));
        PartDefinition lSpike1b = lSpike1a.addOrReplaceChild("lSpike1b",
                CubeListBuilder.create().texOffs(39, 0).addBox(0.0f, -1.0f, -0.5f, 1.0f, 2.0f, 1.0f),
                PartPose.offset(1.6f, 0.5f, 0.0f));
        lSpike1b.addOrReplaceChild("lSpike1c",
                CubeListBuilder.create().texOffs(39, 0).addBox(0.0f, -0.7f, -0.5f, 1.0f, 1.0f, 1.0f),
                PartPose.offsetAndRotation(0.9f, 0.2f, 0.0f, 0.0f, -0.18203784098300857f, 0.0f));
        lSpike1a.addOrReplaceChild("lSpike1aa",
                CubeListBuilder.create().texOffs(37, 0).addBox(-0.1f, -0.9f, -1.0f, 2.0f, 1.0f, 2.0f),
                PartPose.offset(0.0f, 0.0f, 0.0f));
        PartDefinition head = segment1.addOrReplaceChild("head",
                CubeListBuilder.create().texOffs(0, 19).addBox(-3.5f, -1.0f, -3.0f, 7.0f, 2.0f, 3.0f),
                PartPose.offset(0.0f, 0.5f, -1.2f));
        PartDefinition lMandible = head.addOrReplaceChild("lMandible",
                CubeListBuilder.create().texOffs(23, 0).addBox(-1.0f, -0.5f, -3.8f, 2.0f, 1.0f, 4.0f),
                PartPose.offsetAndRotation(2.5f, 0.0f, -2.6f, 0.0f, -0.27314402793711257f, 0.0f));
        lMandible.addOrReplaceChild("lMandible2",
                CubeListBuilder.create().texOffs(23, 6).addBox(-0.5f, -0.5f, -3.0f, 1.0f, 1.0f, 3.0f),
                PartPose.offsetAndRotation(0.0f, 0.1f, -3.6f, 0.0f, 0.31869712141416456f, 0.0f));
        lMandible.addOrReplaceChild("lMandibleSpikes",
                CubeListBuilder.create().texOffs(19, 18).addBox(-1.5f, 0.1f, -5.7f, 4.0f, 0.0f, 5.0f),
                PartPose.offsetAndRotation(0.0f, 0.0f, 0.0f, 0.0f, 0.18203784098300857f, 0.0f));
        PartDefinition rMandible = head.addOrReplaceChild("rMandible",
                CubeListBuilder.create().texOffs(23, 0).mirror().addBox(-1.0f, -0.5f, -3.8f, 2.0f, 1.0f, 4.0f),
                PartPose.offsetAndRotation(-2.5f, 0.0f, -2.6f, 0.0f, 0.27314402793711257f, 0.0f));
        rMandible.addOrReplaceChild("rMandibleSpikes",
                CubeListBuilder.create().texOffs(19, 18).mirror().addBox(-2.5f, 0.1f, -5.7f, 4.0f, 0.0f, 5.0f),
                PartPose.offsetAndRotation(0.0f, 0.0f, 0.0f, 0.0f, -0.18203784098300857f, 0.0f));
        rMandible.addOrReplaceChild("rMandible2",
                CubeListBuilder.create().texOffs(23, 6).mirror().addBox(-0.5f, -0.5f, -3.0f, 1.0f, 1.0f, 3.0f),
                PartPose.offsetAndRotation(0.0f, 0.1f, -3.6f, 0.0f, -0.31869712141416456f, 0.0f));
        PartDefinition rSpike1a = segment1.addOrReplaceChild("rSpike1a",
                CubeListBuilder.create().texOffs(37, 0).mirror().addBox(-2.1f, -0.5f, -1.0f, 2.0f, 2.0f, 2.0f),
                PartPose.offsetAndRotation(-2.4f, 0.0f, 1.5f, 0.0f, 0.4553564018453205f, 0.0f));
        rSpike1a.addOrReplaceChild("rSpike1aa",
                CubeListBuilder.create().texOffs(37, 0).mirror().addBox(-2.1f, -0.9f, -1.0f, 2.0f, 1.0f, 2.0f),
                PartPose.offset(0.0f, 0.0f, 0.0f));
        PartDefinition rSpike1b = rSpike1a.addOrReplaceChild("rSpike1b",
                CubeListBuilder.create().texOffs(39, 0).mirror().addBox(-1.0f, -1.0f, -0.5f, 1.0f, 2.0f, 1.0f),
                PartPose.offset(-2.1f, 0.5f, 0.0f));
        rSpike1b.addOrReplaceChild("rSpike1c",
                CubeListBuilder.create().texOffs(39, 0).mirror().addBox(-1.1f, -0.7f, -0.5f, 1.0f, 1.0f, 1.0f),
                PartPose.offsetAndRotation(-0.6f, 0.2f, 0.0f, 0.0f, 0.18203784098300857f, 0.0f));
        PartDefinition rSpike0a = segment1.addOrReplaceChild("rSpike0a",
                CubeListBuilder.create().texOffs(37, 0).mirror().addBox(-2.1f, -0.3f, -0.8f, 2.0f, 1.0f, 1.0f),
                PartPose.offsetAndRotation(-2.0f, 0.0f, -1.2f, 0.0f, 0.4553564018453205f, 0.0f));
        rSpike0a.addOrReplaceChild("rSpike0e",
                CubeListBuilder.create().texOffs(39, 0).mirror().addBox(-1.0f, -0.5f, -0.5f, 1.0f, 1.0f, 1.0f),
                PartPose.offset(-2.0f, 0.5f, 0.0f));
        rSpike0a.addOrReplaceChild("rSpike0c",
                CubeListBuilder.create().texOffs(37, 0).mirror().addBox(-2.1f, 0.2f, -0.8f, 2.0f, 1.0f, 1.0f),
                PartPose.offset(0.0f, 0.0f, 0.0f));
        rSpike0a.addOrReplaceChild("rSpike0b",
                CubeListBuilder.create().texOffs(37, 0).mirror().addBox(-2.1f, -0.3f, -0.3f, 2.0f, 1.0f, 1.0f),
                PartPose.offset(0.0f, 0.0f, 0.0f));
        rSpike0a.addOrReplaceChild("rSpike0d",
                CubeListBuilder.create().texOffs(37, 0).mirror().addBox(-2.1f, 0.2f, -0.3f, 2.0f, 1.0f, 1.0f),
                PartPose.offset(0.0f, 0.0f, 0.0f));
        PartDefinition segment2 = segment1.addOrReplaceChild("segment2",
                CubeListBuilder.create().texOffs(0, 0).addBox(-3.0f, -1.5f, 0.0f, 6.0f, 3.0f, 5.0f),
                PartPose.offset(0.0f, 0.0f, 2.4f));
        PartDefinition rSpike2a = segment2.addOrReplaceChild("rSpike2a",
                CubeListBuilder.create().texOffs(37, 0).mirror().addBox(-2.1f, -0.3f, -0.8f, 2.0f, 1.0f, 1.0f),
                PartPose.offsetAndRotation(-2.4f, 0.0f, 3.5f, 0.0f, 0.4553564018453205f, 0.0f));
        rSpike2a.addOrReplaceChild("rSpike2b",
                CubeListBuilder.create().texOffs(37, 0).mirror().addBox(-2.1f, -0.3f, -0.3f, 2.0f, 1.0f, 1.0f),
                PartPose.offset(0.0f, 0.0f, 0.0f));
        rSpike2a.addOrReplaceChild("rSpike2d",
                CubeListBuilder.create().texOffs(37, 0).mirror().addBox(-2.1f, 0.2f, -0.3f, 2.0f, 1.0f, 1.0f),
                PartPose.offset(0.0f, 0.0f, 0.0f));
        rSpike2a.addOrReplaceChild("rSpike2e",
                CubeListBuilder.create().texOffs(39, 0).mirror().addBox(-1.0f, -0.5f, -0.5f, 1.0f, 1.0f, 1.0f),
                PartPose.offset(-2.0f, 0.5f, 0.0f));
        rSpike2a.addOrReplaceChild("rSpike2c",
                CubeListBuilder.create().texOffs(37, 0).mirror().addBox(-2.1f, 0.2f, -0.8f, 2.0f, 1.0f, 1.0f),
                PartPose.offset(0.0f, 0.0f, 0.0f));
        PartDefinition lSpike2a = segment2.addOrReplaceChild("lSpike2a",
                CubeListBuilder.create().texOffs(37, 0).addBox(-0.1f, -0.3f, -0.7f, 2.0f, 1.0f, 1.0f),
                PartPose.offsetAndRotation(2.5f, 0.0f, 3.9f, 0.0f, -0.4553564018453205f, 0.0f));
        lSpike2a.addOrReplaceChild("lSpike2d",
                CubeListBuilder.create().texOffs(37, 0).addBox(-0.1f, 0.2f, -0.3f, 2.0f, 1.0f, 1.0f),
                PartPose.offset(0.0f, 0.0f, 0.0f));
        lSpike2a.addOrReplaceChild("lSpike2c",
                CubeListBuilder.create().texOffs(37, 0).addBox(-0.1f, 0.2f, -0.7f, 2.0f, 1.0f, 1.0f),
                PartPose.offset(0.0f, 0.0f, 0.0f));
        lSpike2a.addOrReplaceChild("lSpike2b",
                CubeListBuilder.create().texOffs(37, 0).addBox(-0.1f, -0.3f, -0.3f, 2.0f, 1.0f, 1.0f),
                PartPose.offset(0.0f, 0.0f, 0.0f));
        lSpike2a.addOrReplaceChild("lSpike2e",
                CubeListBuilder.create().texOffs(39, 0).addBox(0.0f, -0.5f, -0.5f, 1.0f, 1.0f, 1.0f),
                PartPose.offset(1.6f, 0.5f, 0.0f));
        PartDefinition segment3 = segment2.addOrReplaceChild("segment3",
                CubeListBuilder.create().texOffs(0, 0).addBox(-3.0f, -1.5f, 0.0f, 6.0f, 3.0f, 5.0f),
                PartPose.offset(0.0f, 0.0f, 4.9f));
        PartDefinition segment4 = segment3.addOrReplaceChild("segment4",
                CubeListBuilder.create().texOffs(0, 0).addBox(-3.0f, -1.5f, 0.0f, 6.0f, 3.0f, 5.0f),
                PartPose.offset(0.0f, 0.0f, 4.9f));
        PartDefinition rSpike4a = segment4.addOrReplaceChild("rSpike4a",
                CubeListBuilder.create().texOffs(37, 0).mirror().addBox(-2.1f, -0.3f, -0.8f, 2.0f, 1.0f, 1.0f),
                PartPose.offsetAndRotation(-2.4f, 0.0f, 3.5f, 0.0f, 0.4553564018453205f, 0.0f));
        rSpike4a.addOrReplaceChild("rSpike4c",
                CubeListBuilder.create().texOffs(37, 0).mirror().addBox(-2.1f, 0.2f, -0.8f, 2.0f, 1.0f, 1.0f),
                PartPose.offset(0.0f, 0.0f, 0.0f));
        rSpike4a.addOrReplaceChild("rSpike4d",
                CubeListBuilder.create().texOffs(37, 0).mirror().addBox(-2.1f, 0.2f, -0.3f, 2.0f, 1.0f, 1.0f),
                PartPose.offset(0.0f, 0.0f, 0.0f));
        rSpike4a.addOrReplaceChild("rSpike4b",
                CubeListBuilder.create().texOffs(37, 0).mirror().addBox(-2.1f, -0.3f, -0.3f, 2.0f, 1.0f, 1.0f),
                PartPose.offset(0.0f, 0.0f, 0.0f));
        rSpike4a.addOrReplaceChild("rSpike4e",
                CubeListBuilder.create().texOffs(39, 0).mirror().addBox(-1.0f, -0.5f, -0.5f, 1.0f, 1.0f, 1.0f),
                PartPose.offset(-2.0f, 0.5f, 0.0f));
        PartDefinition lSpike4a = segment4.addOrReplaceChild("lSpike4a",
                CubeListBuilder.create().texOffs(37, 0).addBox(-0.1f, -0.3f, -0.7f, 2.0f, 1.0f, 1.0f),
                PartPose.offsetAndRotation(2.5f, 0.0f, 3.9f, 0.0f, -0.4553564018453205f, 0.0f));
        lSpike4a.addOrReplaceChild("lSpike4e",
                CubeListBuilder.create().texOffs(39, 0).addBox(0.0f, -0.5f, -0.5f, 1.0f, 1.0f, 1.0f),
                PartPose.offset(1.6f, 0.5f, 0.0f));
        lSpike4a.addOrReplaceChild("lSpike4c",
                CubeListBuilder.create().texOffs(37, 0).addBox(-0.1f, 0.2f, -0.7f, 2.0f, 1.0f, 1.0f),
                PartPose.offset(0.0f, 0.0f, 0.0f));
        lSpike4a.addOrReplaceChild("lSpike4d",
                CubeListBuilder.create().texOffs(37, 0).addBox(-0.1f, 0.2f, -0.3f, 2.0f, 1.0f, 1.0f),
                PartPose.offset(0.0f, 0.0f, 0.0f));
        lSpike4a.addOrReplaceChild("lSpike4b",
                CubeListBuilder.create().texOffs(37, 0).addBox(-0.1f, -0.3f, -0.3f, 2.0f, 1.0f, 1.0f),
                PartPose.offset(0.0f, 0.0f, 0.0f));
        PartDefinition shell4 = segment4.addOrReplaceChild("shell4",
                CubeListBuilder.create().texOffs(0, 10).addBox(-3.5f, -2.1f, -1.8f, 7.0f, 2.0f, 5.0f),
                PartPose.offsetAndRotation(0.0f, 0.0f, 2.5f, 0.18203784098300857f, 0.0f, 0.0f));
        PartDefinition bSpike6a = shell4.addOrReplaceChild("bSpike6a",
                CubeListBuilder.create().texOffs(39, 0).addBox(-0.2f, -2.0f, -0.3f, 1.0f, 2.0f, 1.0f),
                PartPose.offsetAndRotation(0.0f, -1.1f, -1.2f, -0.40980330836826856f, 0.0f, 0.0f));
        bSpike6a.addOrReplaceChild("bSpike6d",
                CubeListBuilder.create().texOffs(39, 0).addBox(-0.7f, -2.0f, -0.8f, 1.0f, 2.0f, 1.0f),
                PartPose.offset(0.0f, 0.0f, 0.0f));
        bSpike6a.addOrReplaceChild("bSpike6c",
                CubeListBuilder.create().texOffs(39, 0).addBox(-0.7f, -2.0f, -0.3f, 1.0f, 2.0f, 1.0f),
                PartPose.offset(0.0f, 0.0f, 0.0f));
        bSpike6a.addOrReplaceChild("bSpike6b",
                CubeListBuilder.create().texOffs(39, 0).addBox(-0.2f, -2.0f, -0.8f, 1.0f, 2.0f, 1.0f),
                PartPose.offset(0.0f, 0.0f, 0.0f));
        bSpike6a.addOrReplaceChild("bSpike6e",
                CubeListBuilder.create().texOffs(39, 0).addBox(-0.5f, -2.0f, -0.5f, 1.0f, 2.0f, 1.0f),
                PartPose.offsetAndRotation(0.0f, -1.6f, -0.2f, -0.18203784098300857f, 0.0f, 0.0f));
        PartDefinition bSpike7a = shell4.addOrReplaceChild("bSpike7a",
                CubeListBuilder.create().texOffs(39, 0).addBox(-0.2f, -2.0f, -0.3f, 1.0f, 2.0f, 1.0f),
                PartPose.offsetAndRotation(0.0f, -1.1f, 1.2f, -0.40980330836826856f, 0.0f, 0.0f));
        bSpike7a.addOrReplaceChild("bSpike7e",
                CubeListBuilder.create().texOffs(39, 0).addBox(-0.5f, -2.0f, -0.5f, 1.0f, 2.0f, 1.0f),
                PartPose.offsetAndRotation(0.0f, -1.2f, -0.2f, -0.18203784098300857f, 0.0f, 0.0f));
        PartDefinition bSpike7c = bSpike7a.addOrReplaceChild("bSpike7c",
                CubeListBuilder.create().texOffs(39, 0).addBox(-0.7f, -2.0f, -0.3f, 1.0f, 2.0f, 1.0f),
                PartPose.offset(0.0f, 0.0f, 0.0f));
        bSpike7c.addOrReplaceChild("bSpike7d",
                CubeListBuilder.create().texOffs(39, 0).addBox(-0.7f, -2.0f, -0.8f, 1.0f, 2.0f, 1.0f),
                PartPose.offset(0.0f, 0.0f, 0.0f));
        bSpike7a.addOrReplaceChild("bSpike7b",
                CubeListBuilder.create().texOffs(39, 0).addBox(-0.2f, -2.0f, -0.8f, 1.0f, 2.0f, 1.0f),
                PartPose.offset(0.0f, 0.0f, 0.0f));
        segment4.addOrReplaceChild("tail",
                CubeListBuilder.create().texOffs(36, 9).addBox(-2.5f, -1.0f, 0.0f, 5.0f, 2.0f, 4.0f),
                PartPose.offset(0.0f, 0.5f, 4.8f));
        PartDefinition shell3 = segment3.addOrReplaceChild("shell3",
                CubeListBuilder.create().texOffs(0, 10).addBox(-3.5f, -2.1f, -1.8f, 7.0f, 2.0f, 5.0f),
                PartPose.offsetAndRotation(0.0f, 0.0f, 2.5f, 0.18203784098300857f, 0.0f, 0.0f));
        PartDefinition bSpike4a_2 = shell3.addOrReplaceChild("bSpike4a_2",
                CubeListBuilder.create().texOffs(39, 0).addBox(-0.2f, -2.0f, -0.3f, 1.0f, 2.0f, 1.0f),
                PartPose.offsetAndRotation(0.0f, -1.1f, 1.2f, -0.40980330836826856f, 0.0f, 0.0f));
        PartDefinition bSpike5c = bSpike4a_2.addOrReplaceChild("bSpike5c",
                CubeListBuilder.create().texOffs(39, 0).addBox(-0.7f, -2.0f, -0.3f, 1.0f, 2.0f, 1.0f),
                PartPose.offset(0.0f, 0.0f, 0.0f));
        bSpike5c.addOrReplaceChild("bSpike5d",
                CubeListBuilder.create().texOffs(39, 0).addBox(-0.7f, -2.0f, -0.8f, 1.0f, 2.0f, 1.0f),
                PartPose.offset(0.0f, 0.0f, 0.0f));
        bSpike4a_2.addOrReplaceChild("bSpike4b_2",
                CubeListBuilder.create().texOffs(39, 0).addBox(-0.2f, -2.0f, -0.8f, 1.0f, 2.0f, 1.0f),
                PartPose.offset(0.0f, 0.0f, 0.0f));
        bSpike4a_2.addOrReplaceChild("bSpike5e",
                CubeListBuilder.create().texOffs(39, 0).addBox(-0.5f, -2.0f, -0.5f, 1.0f, 2.0f, 1.0f),
                PartPose.offsetAndRotation(0.0f, -1.2f, -0.2f, -0.18203784098300857f, 0.0f, 0.0f));
        PartDefinition bSpike4a_1 = shell3.addOrReplaceChild("bSpike4a_1",
                CubeListBuilder.create().texOffs(39, 0).addBox(-0.2f, -2.0f, -0.3f, 1.0f, 2.0f, 1.0f),
                PartPose.offsetAndRotation(0.0f, -1.1f, -1.2f, -0.40980330836826856f, 0.0f, 0.0f));
        bSpike4a_1.addOrReplaceChild("bSpike4d_1",
                CubeListBuilder.create().texOffs(39, 0).addBox(-0.7f, -2.0f, -0.8f, 1.0f, 2.0f, 1.0f),
                PartPose.offset(0.0f, 0.0f, 0.0f));
        bSpike4a_1.addOrReplaceChild("bSpike4c_1",
                CubeListBuilder.create().texOffs(39, 0).addBox(-0.7f, -2.0f, -0.3f, 1.0f, 2.0f, 1.0f),
                PartPose.offset(0.0f, 0.0f, 0.0f));
        bSpike4a_1.addOrReplaceChild("bSpike4b_1",
                CubeListBuilder.create().texOffs(39, 0).addBox(-0.2f, -2.0f, -0.8f, 1.0f, 2.0f, 1.0f),
                PartPose.offset(0.0f, 0.0f, 0.0f));
        bSpike4a_1.addOrReplaceChild("bSpike4e_1",
                CubeListBuilder.create().texOffs(38, 0).addBox(-0.5f, -2.0f, -0.5f, 1.0f, 2.0f, 1.0f),
                PartPose.offsetAndRotation(0.0f, -1.6f, -0.2f, -0.18203784098300857f, 0.0f, 0.0f));
        PartDefinition rSpike3a = segment3.addOrReplaceChild("rSpike3a",
                CubeListBuilder.create().texOffs(37, 0).mirror().addBox(-2.1f, -0.3f, -0.8f, 2.0f, 1.0f, 1.0f),
                PartPose.offsetAndRotation(-2.4f, 0.0f, 3.5f, 0.0f, 0.4553564018453205f, 0.0f));
        rSpike3a.addOrReplaceChild("rSpike3e",
                CubeListBuilder.create().texOffs(39, 0).mirror().addBox(-1.0f, -0.5f, -0.5f, 1.0f, 1.0f, 1.0f),
                PartPose.offset(-2.0f, 0.5f, 0.0f));
        rSpike3a.addOrReplaceChild("rSpike3d",
                CubeListBuilder.create().texOffs(37, 0).mirror().addBox(-2.1f, 0.2f, -0.3f, 2.0f, 1.0f, 1.0f),
                PartPose.offset(0.0f, 0.0f, 0.0f));
        rSpike3a.addOrReplaceChild("rSpike3b",
                CubeListBuilder.create().texOffs(37, 0).mirror().addBox(-2.1f, -0.3f, -0.3f, 2.0f, 1.0f, 1.0f),
                PartPose.offset(0.0f, 0.0f, 0.0f));
        rSpike3a.addOrReplaceChild("rSpike3c",
                CubeListBuilder.create().texOffs(37, 0).mirror().addBox(-2.1f, 0.2f, -0.8f, 2.0f, 1.0f, 1.0f),
                PartPose.offset(0.0f, 0.0f, 0.0f));
        PartDefinition lSpike3a = segment3.addOrReplaceChild("lSpike3a",
                CubeListBuilder.create().texOffs(37, 0).addBox(-0.1f, -0.3f, -0.7f, 2.0f, 1.0f, 1.0f),
                PartPose.offsetAndRotation(2.5f, 0.0f, 3.9f, 0.0f, -0.4553564018453205f, 0.0f));
        lSpike3a.addOrReplaceChild("lSpike3d",
                CubeListBuilder.create().texOffs(37, 0).addBox(-0.1f, 0.2f, -0.3f, 2.0f, 1.0f, 1.0f),
                PartPose.offset(0.0f, 0.0f, 0.0f));
        lSpike3a.addOrReplaceChild("lSpike3b",
                CubeListBuilder.create().texOffs(37, 0).addBox(-0.1f, -0.3f, -0.3f, 2.0f, 1.0f, 1.0f),
                PartPose.offset(0.0f, 0.0f, 0.0f));
        lSpike3a.addOrReplaceChild("lSpike3c",
                CubeListBuilder.create().texOffs(37, 0).addBox(-0.1f, 0.2f, -0.7f, 2.0f, 1.0f, 1.0f),
                PartPose.offset(0.0f, 0.0f, 0.0f));
        lSpike3a.addOrReplaceChild("lSpike3e",
                CubeListBuilder.create().texOffs(39, 0).addBox(0.0f, -0.5f, -0.5f, 1.0f, 1.0f, 1.0f),
                PartPose.offset(1.6f, 0.5f, 0.0f));
        PartDefinition shell2 = segment2.addOrReplaceChild("shell2",
                CubeListBuilder.create().texOffs(0, 10).addBox(-3.5f, -2.1f, -1.8f, 7.0f, 2.0f, 5.0f),
                PartPose.offsetAndRotation(0.0f, 0.0f, 2.5f, 0.18203784098300857f, 0.0f, 0.0f));
        PartDefinition bSpike4a = shell2.addOrReplaceChild("bSpike4a",
                CubeListBuilder.create().texOffs(39, 0).addBox(-0.2f, -2.0f, -0.3f, 1.0f, 2.0f, 1.0f),
                PartPose.offsetAndRotation(0.0f, -1.1f, 1.2f, -0.40980330836826856f, 0.0f, 0.0f));
        bSpike4a.addOrReplaceChild("bSpike4b",
                CubeListBuilder.create().texOffs(39, 0).addBox(-0.2f, -2.0f, -0.8f, 1.0f, 2.0f, 1.0f),
                PartPose.offset(0.0f, 0.0f, 0.0f));
        PartDefinition bSpike4c = bSpike4a.addOrReplaceChild("bSpike4c",
                CubeListBuilder.create().texOffs(39, 0).addBox(-0.7f, -2.0f, -0.3f, 1.0f, 2.0f, 1.0f),
                PartPose.offset(0.0f, 0.0f, 0.0f));
        bSpike4c.addOrReplaceChild("bSpike4d",
                CubeListBuilder.create().texOffs(39, 0).addBox(-0.7f, -2.0f, -0.8f, 1.0f, 2.0f, 1.0f),
                PartPose.offset(0.0f, 0.0f, 0.0f));
        bSpike4a.addOrReplaceChild("bSpike4e",
                CubeListBuilder.create().texOffs(39, 0).addBox(-0.5f, -2.0f, -0.5f, 1.0f, 2.0f, 1.0f),
                PartPose.offsetAndRotation(0.0f, -1.2f, -0.2f, -0.18203784098300857f, 0.0f, 0.0f));
        PartDefinition bSpike3a = shell2.addOrReplaceChild("bSpike3a",
                CubeListBuilder.create().texOffs(39, 0).addBox(-0.2f, -2.0f, -0.3f, 1.0f, 2.0f, 1.0f),
                PartPose.offsetAndRotation(0.0f, -1.1f, -1.2f, -0.40980330836826856f, 0.0f, 0.0f));
        bSpike3a.addOrReplaceChild("bSpike3c",
                CubeListBuilder.create().texOffs(39, 0).addBox(-0.7f, -2.0f, -0.3f, 1.0f, 2.0f, 1.0f),
                PartPose.offset(0.0f, 0.0f, 0.0f));
        bSpike3a.addOrReplaceChild("bSpike3d",
                CubeListBuilder.create().texOffs(39, 0).addBox(-0.7f, -2.0f, -0.8f, 1.0f, 2.0f, 1.0f),
                PartPose.offset(0.0f, 0.0f, 0.0f));
        bSpike3a.addOrReplaceChild("bSpike3e",
                CubeListBuilder.create().texOffs(39, 0).addBox(-0.5f, -2.0f, -0.5f, 1.0f, 2.0f, 1.0f),
                PartPose.offsetAndRotation(0.0f, -1.6f, -0.2f, -0.18203784098300857f, 0.0f, 0.0f));
        bSpike3a.addOrReplaceChild("bSpike3b",
                CubeListBuilder.create().texOffs(39, 0).addBox(-0.2f, -2.0f, -0.8f, 1.0f, 2.0f, 1.0f),
                PartPose.offset(0.0f, 0.0f, 0.0f));
        PartDefinition shell1 = segment1.addOrReplaceChild("shell1",
                CubeListBuilder.create().texOffs(0, 10).addBox(-3.5f, -2.1f, -1.8f, 7.0f, 2.0f, 5.0f),
                PartPose.offsetAndRotation(0.0f, 0.0f, 0.0f, 0.18203784098300857f, 0.0f, 0.0f));
        PartDefinition bSpike1a = shell1.addOrReplaceChild("bSpike1a",
                CubeListBuilder.create().texOffs(39, 0).addBox(-0.2f, -2.0f, -0.3f, 1.0f, 1.0f, 1.0f),
                PartPose.offsetAndRotation(0.0f, -0.8f, -1.2f, -0.40980330836826856f, 0.0f, 0.0f));
        bSpike1a.addOrReplaceChild("bSpike1b",
                CubeListBuilder.create().texOffs(39, 0).addBox(-0.2f, -2.0f, -0.8f, 1.0f, 1.0f, 1.0f),
                PartPose.offset(0.0f, 0.0f, 0.0f));
        bSpike1a.addOrReplaceChild("bSpike1d",
                CubeListBuilder.create().texOffs(39, 0).addBox(-0.7f, -2.0f, -0.8f, 1.0f, 1.0f, 1.0f),
                PartPose.offset(0.0f, 0.0f, 0.0f));
        bSpike1a.addOrReplaceChild("bSpike1e",
                CubeListBuilder.create().texOffs(37, 0).addBox(-0.5f, -2.0f, -0.5f, 1.0f, 1.0f, 1.0f),
                PartPose.offsetAndRotation(0.0f, -0.7f, -0.2f, -0.18203784098300857f, 0.0f, 0.0f));
        bSpike1a.addOrReplaceChild("bSpike1c",
                CubeListBuilder.create().texOffs(39, 0).addBox(-0.7f, -2.0f, -0.3f, 1.0f, 1.0f, 1.0f),
                PartPose.offset(0.0f, 0.0f, 0.0f));
        PartDefinition bSpike2a = shell1.addOrReplaceChild("bSpike2a",
                CubeListBuilder.create().texOffs(39, 0).addBox(-0.2f, -2.0f, -0.3f, 1.0f, 2.0f, 1.0f),
                PartPose.offsetAndRotation(0.0f, -0.8f, 1.2f, -0.40980330836826856f, 0.0f, 0.0f));
        bSpike2a.addOrReplaceChild("bSpike2e",
                CubeListBuilder.create().texOffs(39, 0).addBox(-0.5f, -2.0f, -0.5f, 1.0f, 2.0f, 1.0f),
                PartPose.offsetAndRotation(0.0f, -1.2f, -0.2f, -0.18203784098300857f, 0.0f, 0.0f));
        bSpike2a.addOrReplaceChild("bSpike2b",
                CubeListBuilder.create().texOffs(39, 0).addBox(-0.2f, -2.0f, -0.8f, 1.0f, 2.0f, 1.0f),
                PartPose.offset(0.0f, 0.0f, 0.0f));
        PartDefinition bSpike2c = bSpike2a.addOrReplaceChild("bSpike2c",
                CubeListBuilder.create().texOffs(39, 0).addBox(-0.7f, -2.0f, -0.3f, 1.0f, 2.0f, 1.0f),
                PartPose.offset(0.0f, 0.0f, 0.0f));
        bSpike2c.addOrReplaceChild("bSpike2d",
                CubeListBuilder.create().texOffs(39, 0).addBox(-0.7f, -2.0f, -0.8f, 1.0f, 2.0f, 1.0f),
                PartPose.offset(0.0f, 0.0f, 0.0f));
        return LayerDefinition.create(mesh, 64, 32);
    }
}

