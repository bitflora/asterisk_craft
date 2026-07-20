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
 * Original blocky Hydralisk model authored for AsteriskCraft — an upright, vanilla-style serpent built
 * from a handful of boxes (no ported geometry). Silhouette cues: a coiled carapace base, a torso rising
 * from it, a hooded head with a crest, twin bone scythe-arms, and orange-glowing back and crest spines
 * (the emissive zone lit by {@link net.bitflora.asteriskcraft.client.UnitGlowLayer}). Textured via flat
 * colour zones (see tools/gen_hydralisk_texture.py). It has no legs; head-look plus an arm-swing are
 * driven in setupAnim.
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
        this.head = root.getChild("base").getChild("torso").getChild("head");
        this.armL = root.getChild("base").getChild("torso").getChild("armL");
        this.armR = root.getChild("base").getChild("torso").getChild("armR");
        this.headX0 = this.head.xRot;
        this.armLX0 = this.armL.xRot;
        this.armRX0 = this.armR.xRot;
    }

    @Override
    public void setupAnim(LivingEntityRenderState state) {
        super.setupAnim(state);
        this.head.yRot = state.yRot * ((float) Math.PI / 180f);
        this.head.xRot = this.headX0 + state.xRot * ((float) Math.PI / 180f);
        float swing = Mth.cos(state.walkAnimationPos * 0.6662f) * 1.0f * state.walkAnimationSpeed;
        this.armL.xRot = this.armLX0 - swing;
        this.armR.xRot = this.armRX0 + swing;
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();

        // Flat colour zones on the atlas: CARAPACE = texOffs(0,0), CLAW/bone = texOffs(48,0),
        // SPINE/emissive = texOffs(64,0).
        // --- Coiled base sitting on the ground (y 16..24) ---------------------------------
        PartDefinition base = root.addOrReplaceChild("base",
                CubeListBuilder.create().texOffs(0, 0).addBox(-5.0f, -8.0f, -5.0f, 10.0f, 8.0f, 11.0f),
                PartPose.offset(0.0f, 24.0f, 0.0f));

        // --- Torso rising from the base (leans forward slightly) --------------------------
        PartDefinition torso = base.addOrReplaceChild("torso",
                CubeListBuilder.create().texOffs(0, 0).addBox(-4.0f, -12.0f, -3.5f, 8.0f, 12.0f, 7.0f),
                PartPose.offsetAndRotation(0.0f, -8.0f, -1.0f, -0.15f, 0.0f, 0.0f));

        // --- Hooded head (pivot at the top of the torso) ----------------------------------
        PartDefinition head = torso.addOrReplaceChild("head",
                CubeListBuilder.create().texOffs(0, 0).addBox(-3.0f, -4.0f, -5.0f, 6.0f, 5.0f, 5.0f),
                PartPose.offsetAndRotation(0.0f, -12.0f, -1.0f, 0.2f, 0.0f, 0.0f));
        // Back hood/crest fanning up behind the head.
        head.addOrReplaceChild("hood",
                CubeListBuilder.create().texOffs(0, 0).addBox(-4.0f, -3.0f, 0.0f, 8.0f, 7.0f, 2.0f),
                PartPose.offsetAndRotation(0.0f, -3.0f, 0.5f, -0.5f, 0.0f, 0.0f));
        // Two crest spines (emissive orange tips).
        head.addOrReplaceChild("crestSpineL",
                CubeListBuilder.create().texOffs(64, 0).addBox(-0.75f, -7.0f, -0.75f, 1.5f, 7.0f, 1.5f),
                PartPose.offsetAndRotation(2.0f, -4.0f, 1.5f, -0.7f, 0.0f, -0.25f));
        head.addOrReplaceChild("crestSpineR",
                CubeListBuilder.create().texOffs(64, 0).addBox(-0.75f, -7.0f, -0.75f, 1.5f, 7.0f, 1.5f),
                PartPose.offsetAndRotation(-2.0f, -4.0f, 1.5f, -0.7f, 0.0f, 0.25f));
        // Glowing eyes.
        head.addOrReplaceChild("eyeL",
                CubeListBuilder.create().texOffs(64, 0).addBox(-0.75f, -0.75f, -0.5f, 1.5f, 1.5f, 1.0f),
                PartPose.offset(1.6f, -1.5f, -5.1f));
        head.addOrReplaceChild("eyeR",
                CubeListBuilder.create().texOffs(64, 0).addBox(-0.75f, -0.75f, -0.5f, 1.5f, 1.5f, 1.0f),
                PartPose.offset(-1.6f, -1.5f, -5.1f));

        // --- Scythe arms: three segments (upper arm -> forearm -> bone blade) --------------
        // The arms are held out in front: the upper arm swings forward to roughly horizontal, the
        // forearm continues forward, and the bone blade (third segment) bends back down at the wrist.
        // armL/armR are swung by setupAnim.
        PartDefinition armL = torso.addOrReplaceChild("armL",
                CubeListBuilder.create().texOffs(0, 0).addBox(-1.5f, -1.0f, -1.5f, 3.0f, 6.0f, 3.0f),
                PartPose.offsetAndRotation(4.5f, -10.0f, -1.0f, -1.4f, 0.0f, -0.15f));
        PartDefinition foreArmL = armL.addOrReplaceChild("foreArmL",
                CubeListBuilder.create().texOffs(0, 0).addBox(-1.25f, 0.0f, -1.25f, 2.5f, 6.0f, 2.5f),
                PartPose.offsetAndRotation(0.0f, 5.0f, 0.0f, 0.15f, 0.0f, 0.0f));
        foreArmL.addOrReplaceChild("bladeL",
                CubeListBuilder.create().texOffs(48, 0).addBox(-1.0f, 0.0f, -1.5f, 2.0f, 12.0f, 3.0f),
                PartPose.offsetAndRotation(0.0f, 5.5f, -0.5f, 1.4f, 0.0f, 0.0f));
        PartDefinition armR = torso.addOrReplaceChild("armR",
                CubeListBuilder.create().texOffs(0, 0).addBox(-1.5f, -1.0f, -1.5f, 3.0f, 6.0f, 3.0f),
                PartPose.offsetAndRotation(-4.5f, -10.0f, -1.0f, -1.4f, 0.0f, 0.15f));
        PartDefinition foreArmR = armR.addOrReplaceChild("foreArmR",
                CubeListBuilder.create().texOffs(0, 0).addBox(-1.25f, 0.0f, -1.25f, 2.5f, 6.0f, 2.5f),
                PartPose.offsetAndRotation(0.0f, 5.0f, 0.0f, 0.15f, 0.0f, 0.0f));
        foreArmR.addOrReplaceChild("bladeR",
                CubeListBuilder.create().texOffs(48, 0).addBox(-1.0f, 0.0f, -1.5f, 2.0f, 12.0f, 3.0f),
                PartPose.offsetAndRotation(0.0f, 5.5f, -0.5f, 1.4f, 0.0f, 0.0f));

        // --- Back spines (emissive orange, up the spine) ----------------------------------
        torso.addOrReplaceChild("backSpine1",
                CubeListBuilder.create().texOffs(64, 0).addBox(-0.75f, -8.0f, -0.75f, 1.5f, 8.0f, 1.5f),
                PartPose.offsetAndRotation(0.0f, -3.0f, 3.0f, -0.9f, 0.0f, 0.0f));
        torso.addOrReplaceChild("backSpine2",
                CubeListBuilder.create().texOffs(64, 0).addBox(-0.75f, -6.0f, -0.75f, 1.5f, 6.0f, 1.5f),
                PartPose.offsetAndRotation(0.0f, -8.0f, 3.0f, -0.9f, 0.0f, 0.0f));

        // --- Long trailing tail (three tapering segments off the back of the base) ---------
        PartDefinition tail1 = base.addOrReplaceChild("tail1",
                CubeListBuilder.create().texOffs(0, 0).addBox(-2.5f, -2.5f, 0.0f, 5.0f, 5.0f, 8.0f),
                PartPose.offsetAndRotation(0.0f, -2.5f, 6.0f, 0.25f, 0.0f, 0.0f));
        PartDefinition tail2 = tail1.addOrReplaceChild("tail2",
                CubeListBuilder.create().texOffs(0, 0).addBox(-1.5f, -1.5f, 0.0f, 3.0f, 3.0f, 8.0f),
                PartPose.offsetAndRotation(0.0f, 0.0f, 8.0f, 0.25f, 0.0f, 0.0f));
        tail2.addOrReplaceChild("tail3",
                CubeListBuilder.create().texOffs(0, 0).addBox(-1.0f, -1.0f, 0.0f, 2.0f, 2.0f, 8.0f),
                PartPose.offsetAndRotation(0.0f, 0.0f, 8.0f, 0.3f, 0.0f, 0.0f));

        return LayerDefinition.create(mesh, 128, 128);
    }
}
