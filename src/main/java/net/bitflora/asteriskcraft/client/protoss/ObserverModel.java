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
 * Original blocky Observer model authored for AsteriskCraft, from the reference art: a caged eye.
 * A small faceted pod — the reference's sphere, cubified into a 6-unit core with a stepped cap above
 * and below — carries a single large lens on its nose, and rides inside a cage of three arcs that
 * run front-to-back over it, one over the crown and two under the flanks at 120 degrees apart. A
 * blister on the crown and the lens itself are the only lit parts (see
 * {@link net.bitflora.asteriskcraft.client.UnitGlowLayer}); a short blade at the rear closes the
 * silhouette and tells the eye which way the pod is pointing.
 *
 * <p>Like {@code ScoutModel} the body is centred in the air rather than standing on y=24 — the
 * entity itself is what hovers (see {@code ObserverEntity}), so the model just floats around its own
 * origin.
 *
 * <p>The three arcs are built the same way: an empty container rolled about the front-back axis, so
 * one arc's geometry is written once per arc and the 120-degree spacing is a single {@code zRot} on
 * its parent. Each arc's three segments are chords of a circle of radius {@link #CAGE_RADIUS} at
 * {@link #ARC_STEP} apart, so a segment's pivot sits on that circle and its {@code xRot} is the
 * negated circle angle — that is what lays it along the tangent instead of across it.
 *
 * <p>Everything animates off {@code ageInTicks} alone: the cage turns steadily about the vertical
 * axis while the pod inside it holds still, and the whole thing bobs on its own phase. There is
 * deliberately no walk cycle — {@code walkAnimationSpeed} stays near zero on a flyer, which would
 * freeze a limb-swing model solid.
 */
public class ObserverModel extends EntityModel<LivingEntityRenderState> {
    /** Radians per tick of the cage's turn — one full revolution roughly every ten seconds. */
    private static final float SPIN_RATE = 0.03f;
    /** Radians per tick of the vertical bob, deliberately off the spin's phase. */
    private static final float BOB_RATE = 0.09f;

    /** Model units from the pod's centre to the middle of an arc segment. */
    private static final float CAGE_RADIUS = 6.0f;
    /** Radians of arc between one segment's pivot and the next. */
    private static final float ARC_STEP = 0.698f;

    private final ModelPart body;
    private final ModelPart cage;

    public ObserverModel(ModelPart root) {
        super(root);
        this.body = root.getChild("body");
        this.cage = this.body.getChild("cage");
    }

    @Override
    public void setupAnim(LivingEntityRenderState state) {
        // super restores every part to its baked PartPose, so everything below adds a delta onto it.
        super.setupAnim(state);

        float t = state.ageInTicks;

        // The cage turns and the pod does not: the eye stays pointed where the unit is facing while
        // the arcs sweep around it, which is what reads as "watching" rather than "tumbling".
        this.cage.yRot += t * SPIN_RATE;
        // +y is down in model space, so subtracting lifts the pod on the upbeat.
        this.body.y -= Mth.sin(t * BOB_RATE) * 0.35f;
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();

        // Each cube's texOffs points at its own packed UV island — do not hand-edit these; they are
        // assigned by tools/blockbench_export.py and guarded by ModelUvLayoutTest.
        // "body" is the pod's core; everything else hangs off it. y=16 puts the pod's centre half a
        // block above the entity position, i.e. the middle of its one-block hitbox.
        PartDefinition body = root.addOrReplaceChild("body",
                CubeListBuilder.create().texOffs(0, 0).addBox(-3.0f, -3.0f, -3.0f, 6.0f, 6.0f, 6.0f),
                PartPose.offset(0.0f, 16.0f, 0.0f));

        // --- The sphere, cubified: one step narrower above and below the core -------------------
        body.addOrReplaceChild("cap_top",
                CubeListBuilder.create().texOffs(26, 0).addBox(-2.0f, -2.0f, -2.0f, 4.0f, 4.0f, 4.0f),
                PartPose.offset(0.0f, -5.0f, 0.0f));
        body.addOrReplaceChild("cap_bottom",
                CubeListBuilder.create().texOffs(44, 0).addBox(-2.0f, -2.0f, -2.0f, 4.0f, 4.0f, 4.0f),
                PartPose.offset(0.0f, 5.0f, 0.0f));

        // --- The eye: a bezel on the nose with the lens standing proud of it ---------------------
        // The lens is a sibling rather than a child of the bezel: nothing moves either of them, and
        // the glow pass wants it as one flat face at a known depth.
        body.addOrReplaceChild("bezel",
                CubeListBuilder.create().texOffs(62, 0).addBox(-2.5f, -2.5f, -1.0f, 5.0f, 5.0f, 1.0f),
                PartPose.offset(0.0f, 0.0f, -3.0f));
        body.addOrReplaceChild("lens",
                CubeListBuilder.create().texOffs(76, 0).addBox(-1.5f, -1.5f, -1.0f, 3.0f, 3.0f, 1.0f),
                PartPose.offset(0.0f, 0.0f, -4.0f));

        // --- Crown emitter and tail blade --------------------------------------------------------
        body.addOrReplaceChild("emitter",
                CubeListBuilder.create().texOffs(86, 0).addBox(-1.0f, -1.5f, -1.0f, 2.0f, 1.5f, 2.0f),
                PartPose.offset(0.0f, -7.0f, 0.0f));
        body.addOrReplaceChild("fin",
                CubeListBuilder.create().texOffs(96, 0).addBox(-0.5f, -4.0f, 0.0f, 1.0f, 4.0f, 2.5f),
                PartPose.offset(0.0f, -3.0f, 3.0f));

        // --- The cage: three arcs, 120 degrees apart, spun as one --------------------------------
        // Empty containers all the way down. "cage" is what setupAnim turns; each arc is the same
        // three-segment geometry rolled about z, so the spacing is one number per arc rather than
        // three sets of hand-solved pivots.
        PartDefinition cage = body.addOrReplaceChild("cage",
                CubeListBuilder.create(),
                PartPose.ZERO);

        // Segment pivots sit on a circle of CAGE_RADIUS at -ARC_STEP, 0 and +ARC_STEP; each xRot is
        // the negated angle, which lays the box along the tangent. Written out per segment rather
        // than through a helper or a loop: the texture tooling needs one editable texOffs literal
        // per cube. See docs/texturing.md.
        PartDefinition armTop = cage.addOrReplaceChild("arm_top",
                CubeListBuilder.create(),
                PartPose.ZERO);
        armTop.addOrReplaceChild("arm_top_1",
                CubeListBuilder.create().texOffs(0, 16).addBox(-0.75f, -0.75f, -2.25f, 1.5f, 1.5f, 4.5f),
                PartPose.offsetAndRotation(0.0f, -4.6f, -3.86f, ARC_STEP, 0.0f, 0.0f));
        armTop.addOrReplaceChild("arm_top_2",
                CubeListBuilder.create().texOffs(14, 16).addBox(-0.75f, -0.75f, -2.25f, 1.5f, 1.5f, 4.5f),
                PartPose.offset(0.0f, -CAGE_RADIUS, 0.0f));
        armTop.addOrReplaceChild("arm_top_3",
                CubeListBuilder.create().texOffs(28, 16).addBox(-0.75f, -0.75f, -2.25f, 1.5f, 1.5f, 4.5f),
                PartPose.offsetAndRotation(0.0f, -4.6f, 3.86f, -ARC_STEP, 0.0f, 0.0f));

        PartDefinition armLeft = cage.addOrReplaceChild("arm_left",
                CubeListBuilder.create(),
                PartPose.offsetAndRotation(0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 2.094f));
        armLeft.addOrReplaceChild("arm_left_1",
                CubeListBuilder.create().texOffs(42, 16).addBox(-0.75f, -0.75f, -2.25f, 1.5f, 1.5f, 4.5f),
                PartPose.offsetAndRotation(0.0f, -4.6f, -3.86f, ARC_STEP, 0.0f, 0.0f));
        armLeft.addOrReplaceChild("arm_left_2",
                CubeListBuilder.create().texOffs(56, 16).addBox(-0.75f, -0.75f, -2.25f, 1.5f, 1.5f, 4.5f),
                PartPose.offset(0.0f, -CAGE_RADIUS, 0.0f));
        armLeft.addOrReplaceChild("arm_left_3",
                CubeListBuilder.create().texOffs(70, 16).addBox(-0.75f, -0.75f, -2.25f, 1.5f, 1.5f, 4.5f),
                PartPose.offsetAndRotation(0.0f, -4.6f, 3.86f, -ARC_STEP, 0.0f, 0.0f));

        PartDefinition armRight = cage.addOrReplaceChild("arm_right",
                CubeListBuilder.create(),
                PartPose.offsetAndRotation(0.0f, 0.0f, 0.0f, 0.0f, 0.0f, -2.094f));
        armRight.addOrReplaceChild("arm_right_1",
                CubeListBuilder.create().texOffs(84, 16).addBox(-0.75f, -0.75f, -2.25f, 1.5f, 1.5f, 4.5f),
                PartPose.offsetAndRotation(0.0f, -4.6f, -3.86f, ARC_STEP, 0.0f, 0.0f));
        armRight.addOrReplaceChild("arm_right_2",
                CubeListBuilder.create().texOffs(98, 16).addBox(-0.75f, -0.75f, -2.25f, 1.5f, 1.5f, 4.5f),
                PartPose.offset(0.0f, -CAGE_RADIUS, 0.0f));
        armRight.addOrReplaceChild("arm_right_3",
                CubeListBuilder.create().texOffs(112, 16).addBox(-0.75f, -0.75f, -2.25f, 1.5f, 1.5f, 4.5f),
                PartPose.offsetAndRotation(0.0f, -4.6f, 3.86f, -ARC_STEP, 0.0f, 0.0f));

        return LayerDefinition.create(mesh, 128, 128);
    }
}
