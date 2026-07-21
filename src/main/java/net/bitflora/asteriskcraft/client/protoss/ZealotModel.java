package net.bitflora.asteriskcraft.client.protoss;

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
 * Original blocky Zealot model authored for AsteriskCraft — a chunky, vanilla-style biped built from a
 * handful of boxes (no ported geometry). Silhouette cues: broad gold chest, an elongated down-tilted
 * helmet snout, a waist tasset, and the signature twin forearm psi-blades (the emissive
 * zone lit by {@link net.bitflora.asteriskcraft.client.UnitGlowLayer}). Textured via flat colour zones
 * (see tools/gen_zealot_texture.py). Head-look plus a simple limb-swing walk are driven in setupAnim.
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
        this.head = root.getChild("head");
        this.armL = root.getChild("body").getChild("armL");
        this.armR = root.getChild("body").getChild("armR");
        this.legL = root.getChild("legL");
        this.legR = root.getChild("legR");
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

        // --- Head (pivot at the neck, y=0) -------------------------------------------------
        // Flat colour zones on the atlas: GOLD = texOffs(0,0), BLADE/emissive = texOffs(70,0),
        // DARK = texOffs(0,50). Every cube of a material shares that material's corner.
        // An elongated helmet: deeper (front-to-back) than it is tall, jutting forward (-z) and given a
        // baseline downward tilt so the snout points at the ground — the Zealot's forward-leaning skull.
        // headX0 in the ctor captures this base xRot, so setupAnim's head-look pitch stacks on top of it.
        // Shifted rearward so ~1/3 of the 11-deep helmet hangs behind the neck pivot (local z 0): the box
        // spans local z -7.5..+3.5, ~7.5 forward of the pivot and ~3.5 overhanging the back.
        PartDefinition head = root.addOrReplaceChild("head",
                CubeListBuilder.create().texOffs(0, 0).addBox(-3.5f, -5.0f, -7.5f, 7.0f, 5.0f, 11.0f),
                PartPose.offsetAndRotation(0.0f, -1.0f, -1.0f, 0.45f, 0.0f, 0.0f));
        // Glowing eye visor across the front snout face.
        head.addOrReplaceChild("visor",
                CubeListBuilder.create().texOffs(70, 0).addBox(-3.0f, -3.5f, -8.1f, 6.0f, 2.0f, 1.0f),
                PartPose.ZERO);

        // --- Body / torso (y 0..12) --------------------------------------------------------
        PartDefinition body = root.addOrReplaceChild("body",
                CubeListBuilder.create().texOffs(0, 0).addBox(-5.0f, 0.0f, -3.0f, 10.0f, 12.0f, 6.0f),
                PartPose.offset(0.0f, 0.0f, 0.0f));
        // Waist tasset.
        body.addOrReplaceChild("skirt",
                CubeListBuilder.create().texOffs(0, 50).addBox(-5.5f, 10.0f, -3.5f, 11.0f, 4.0f, 7.0f),
                PartPose.offset(0.0f, 0.0f, 0.0f));

        // --- Arms (pivot at the shoulders, y=2) -------------------------------------------
        PartDefinition armL = body.addOrReplaceChild("armL",
                CubeListBuilder.create().texOffs(0, 0).addBox(-2.0f, -1.0f, -2.0f, 4.0f, 10.0f, 4.0f),
                PartPose.offset(6.5f, 2.0f, 0.0f));
        // Left psi-blade: a straight continuation of the forearm. It overlaps the arm's lower end (no
        // gap, so it reads as fused, not held) and extends straight down in line with the arm.
        armL.addOrReplaceChild("bladeL",
                CubeListBuilder.create().texOffs(70, 0).addBox(-1.0f, 0.0f, -1.0f, 2.0f, 13.0f, 2.0f),
                PartPose.offset(0.0f, 7.0f, 0.0f));
        PartDefinition armR = body.addOrReplaceChild("armR",
                CubeListBuilder.create().texOffs(0, 0).addBox(-2.0f, -1.0f, -2.0f, 4.0f, 10.0f, 4.0f),
                PartPose.offset(-6.5f, 2.0f, 0.0f));
        armR.addOrReplaceChild("bladeR",
                CubeListBuilder.create().texOffs(70, 0).addBox(-1.0f, 0.0f, -1.0f, 2.0f, 13.0f, 2.0f),
                PartPose.offset(0.0f, 7.0f, 0.0f));

        // --- Legs (pivot at the hips, y=12) -----------------------------------------------
        root.addOrReplaceChild("legL",
                CubeListBuilder.create().texOffs(0, 50).addBox(-2.0f, 0.0f, -2.0f, 4.0f, 12.0f, 4.0f),
                PartPose.offset(2.5f, 12.0f, 0.0f));
        root.addOrReplaceChild("legR",
                CubeListBuilder.create().texOffs(0, 50).addBox(-2.0f, 0.0f, -2.0f, 4.0f, 12.0f, 4.0f),
                PartPose.offset(-2.5f, 12.0f, 0.0f));

        return LayerDefinition.create(mesh, 128, 128);
    }
}
