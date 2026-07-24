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
 * Original blocky Mutalisk model authored for AsteriskCraft, from the reference art: a slim serpentine
 * carapace flying horizontally, a wedge head with a hanging jaw and glowing eyes (the emissive zone lit
 * by {@link net.bitflora.asteriskcraft.client.UnitGlowLayer}), a pair of broad two-segment wings swept
 * out either side, a long three-segment tail trailing behind, two claws tucked under the belly, and a
 * pair of dorsal spines. Each cube owns its own UV island; the texture is hand-painted in Blockbench via
 * tools/blockbench_export.py.
 *
 * <p>Unlike the ground units the body is centred in the air rather than standing on y=24 — the entity
 * itself is what hovers (see {@code MutaliskEntity}), so the model just floats around its own origin.
 *
 * <p>Everything animates off a single wing beat: the wings flap, the outer panels lag behind the roots
 * so each beat cracks outward instead of pivoting rigidly, and the body rides up and down on the same
 * phase (lagged) so the flap reads as what's keeping it up. The tail sways on a much slower cycle with
 * each segment lagging the one ahead of it. There is no walk cycle — {@code walkAnimationSpeed} stays
 * near zero on a flyer, which would freeze a limb-swing model solid.
 */
public class MutaliskModel extends EntityModel<LivingEntityRenderState> {
    /** Ticks per radian of wing beat — about one full flap every 12 ticks. */
    private static final float FLAP_RATE = 0.5f;
    /** How far behind the roots the wing tips trail, in radians of the beat cycle. */
    private static final float TIP_LAG = 0.7f;

    private final ModelPart body;
    private final ModelPart head;
    private final ModelPart wingRootL;
    private final ModelPart wingRootR;
    private final ModelPart wingTipL;
    private final ModelPart wingTipR;
    private final ModelPart tail1;
    private final ModelPart tail2;
    private final ModelPart tail3;

    public MutaliskModel(ModelPart root) {
        super(root);
        this.body = root.getChild("body");
        this.head = this.body.getChild("head");
        this.wingRootL = this.body.getChild("wingRootL");
        this.wingRootR = this.body.getChild("wingRootR");
        this.wingTipL = this.wingRootL.getChild("wingTipL");
        this.wingTipR = this.wingRootR.getChild("wingTipR");
        this.tail1 = this.body.getChild("tail1");
        this.tail2 = this.tail1.getChild("tail2");
        this.tail3 = this.tail2.getChild("tail3");
    }

    @Override
    public void setupAnim(LivingEntityRenderState state) {
        // super restores every part to its baked PartPose, so everything below adds a delta onto it.
        super.setupAnim(state);

        float t = state.ageInTicks;
        float beat = Mth.sin(t * FLAP_RATE);
        float trail = Mth.sin(t * FLAP_RATE - TIP_LAG);

        this.head.yRot += state.yRot * ((float) Math.PI / 180f);
        this.head.xRot += state.xRot * ((float) Math.PI / 180f);

        // The wings mirror each other: on the left wing (extending along +x) a positive zRot swings
        // the far end downward, so the right wing takes the negated angle to beat in sync.
        this.wingRootL.zRot += beat * 0.7f;
        this.wingRootR.zRot -= beat * 0.7f;
        this.wingTipL.zRot += trail * 0.9f;
        this.wingTipR.zRot -= trail * 0.9f;

        // Lift on the downbeat: +y is down in model space, so the trailing term is subtracted.
        this.body.y -= trail * 0.6f;

        this.tail1.yRot += Mth.cos(t * 0.09f) * 0.18f;
        this.tail2.yRot += Mth.cos(t * 0.09f - 0.5f) * 0.22f;
        this.tail3.yRot += Mth.cos(t * 0.09f - 1.0f) * 0.26f;
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();

        // Each cube's texOffs points at its own packed UV island — do not hand-edit these; they are
        // assigned by tools/blockbench_export.py and guarded by ModelUvLayoutTest.
        // Everything hangs off "body", which floats mid-height rather than standing on the ground.
        PartDefinition body = root.addOrReplaceChild("body",
                CubeListBuilder.create().texOffs(0, 0).addBox(-3.0f, -3.5f, -6.0f, 6.0f, 7.0f, 12.0f),
                PartPose.offset(0.0f, 17.0f, 0.0f));

        // --- Head: wedge snout at the front, hanging jaw, glowing eyes ------------------------
        PartDefinition head = body.addOrReplaceChild("head",
                CubeListBuilder.create().texOffs(0, 20).addBox(-2.5f, -2.5f, -6.0f, 5.0f, 5.0f, 6.0f),
                PartPose.offset(0.0f, 0.0f, -6.0f));
        head.addOrReplaceChild("jaw",
                CubeListBuilder.create().texOffs(70, 32).addBox(-2.0f, -1.0f, -4.0f, 4.0f, 2.0f, 4.0f),
                PartPose.offsetAndRotation(0.0f, 2.0f, -5.0f, 0.15f, 0.0f, 0.0f));
        head.addOrReplaceChild("eyeL",
                CubeListBuilder.create().texOffs(87, 32).addBox(-0.75f, -0.75f, -0.5f, 1.5f, 1.5f, 1.0f),
                PartPose.offset(1.7f, -1.2f, -5.6f));
        head.addOrReplaceChild("eyeR",
                CubeListBuilder.create().texOffs(93, 32).addBox(-0.75f, -0.75f, -0.5f, 1.5f, 1.5f, 1.0f),
                PartPose.offset(-1.7f, -1.2f, -5.6f));

        // --- Wings: broad inner panel hinged at the shoulder, outer panel hinged off its tip ---
        // Written out per side rather than through a helper: the texture tooling needs one editable
        // texOffs literal per cube. See docs/texturing.md.
        PartDefinition wingRootL = body.addOrReplaceChild("wingRootL",
                CubeListBuilder.create().texOffs(37, 0).addBox(0.0f, -1.0f, -5.0f, 9.0f, 2.0f, 10.0f),
                PartPose.offsetAndRotation(3.0f, -2.0f, -1.0f, 0.0f, 0.0f, -0.25f));
        wingRootL.addOrReplaceChild("wingTipL",
                CubeListBuilder.create().texOffs(46, 20).addBox(0.0f, -0.75f, -4.0f, 8.0f, 1.5f, 8.0f),
                PartPose.offsetAndRotation(9.0f, 0.0f, 0.0f, 0.0f, 0.0f, -0.2f));

        PartDefinition wingRootR = body.addOrReplaceChild("wingRootR",
                CubeListBuilder.create().texOffs(76, 0).addBox(-9.0f, -1.0f, -5.0f, 9.0f, 2.0f, 10.0f),
                PartPose.offsetAndRotation(-3.0f, -2.0f, -1.0f, 0.0f, 0.0f, 0.25f));
        wingRootR.addOrReplaceChild("wingTipR",
                CubeListBuilder.create().texOffs(79, 20).addBox(-8.0f, -0.75f, -4.0f, 8.0f, 1.5f, 8.0f),
                PartPose.offsetAndRotation(-9.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.2f));

        // --- Tail: three tapering segments trailing behind ------------------------------------
        PartDefinition tail1 = body.addOrReplaceChild("tail1",
                CubeListBuilder.create().texOffs(23, 20).addBox(-2.0f, -2.0f, 0.0f, 4.0f, 4.0f, 7.0f),
                PartPose.offsetAndRotation(0.0f, -0.5f, 6.0f, 0.15f, 0.0f, 0.0f));
        PartDefinition tail2 = tail1.addOrReplaceChild("tail2",
                CubeListBuilder.create().texOffs(0, 32).addBox(-1.5f, -1.5f, 0.0f, 3.0f, 3.0f, 6.0f),
                PartPose.offsetAndRotation(0.0f, 0.0f, 7.0f, 0.2f, 0.0f, 0.0f));
        tail2.addOrReplaceChild("tail3",
                CubeListBuilder.create().texOffs(19, 32).addBox(-1.0f, -1.0f, 0.0f, 2.0f, 2.0f, 6.0f),
                PartPose.offsetAndRotation(0.0f, 0.0f, 6.0f, 0.25f, 0.0f, 0.0f));

        // --- Claws tucked under the belly ------------------------------------------------------
        body.addOrReplaceChild("clawL",
                CubeListBuilder.create().texOffs(36, 32).addBox(-1.0f, 0.0f, -1.0f, 2.0f, 6.0f, 2.0f),
                PartPose.offsetAndRotation(2.5f, 3.0f, -3.0f, 0.4f, 0.0f, -0.25f));
        body.addOrReplaceChild("clawR",
                CubeListBuilder.create().texOffs(45, 32).addBox(-1.0f, 0.0f, -1.0f, 2.0f, 6.0f, 2.0f),
                PartPose.offsetAndRotation(-2.5f, 3.0f, -3.0f, 0.4f, 0.0f, 0.25f));

        // --- Dorsal spines (silhouette only) ---------------------------------------------------
        body.addOrReplaceChild("spineL",
                CubeListBuilder.create().texOffs(54, 32).addBox(-0.75f, -6.0f, -1.0f, 1.5f, 6.0f, 2.0f),
                PartPose.offsetAndRotation(2.0f, -3.5f, 1.0f, -0.3f, 0.0f, -0.4f));
        body.addOrReplaceChild("spineR",
                CubeListBuilder.create().texOffs(62, 32).addBox(-0.75f, -6.0f, -1.0f, 1.5f, 6.0f, 2.0f),
                PartPose.offsetAndRotation(-2.0f, -3.5f, 1.0f, -0.3f, 0.0f, 0.4f));

        return LayerDefinition.create(mesh, 128, 128);
    }
}
