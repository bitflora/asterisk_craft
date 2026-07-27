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
 * Hand-authored geometry for the Dragoon, shaped after the reference art: an armoured walker whose
 * <b>four legs are the silhouette</b>. Heavy thigh shells rake up and outward from the pod's hip
 * mounts so the knee joints stand <i>above</i> the hull, then a long tapered shin drops through a
 * thinner pastern to a claw planted on the ground. Slung between them is a rounded gold body pod —
 * a prow tapering down at the front, a cowl over the back — carrying one large <b>blue energy
 * lens</b> that dominates its face, and a short phase-disruptor barrel underneath.
 *
 * <p>The Dragoon has no shoulder-mounted weapons; the raised thigh shells are what read as such at a
 * glance. Its only emitter is the underslung barrel.
 *
 * <p>Authored in true pixel space (16px = 1 block), "up" is negative y, and colour comes from
 * {@code textures/entity/dragoon.png}, in which <b>every cube owns its own UV island</b> so it can be
 * hand-painted independently — see {@code tools/blockbench_export.py}, which packs those islands and
 * emits the Blockbench project. Only the lens, the pupil and the barrel tip are painted into
 * {@code dragoon_glow.png}, so the emissive {@link UnitGlowLayer} lights just those; everything else
 * must stay transparent there, since that layer re-submits the whole model.
 *
 * <p>{@link #setupAnim} drives head-look, an idle bob and a diagonal-gait walk: the two diagonal pairs
 * (front-right + back-left, front-left + back-right) swing half a cycle apart, and each leg's knee
 * folds against its thigh's lift so the leg tucks rather than pivoting rigidly about the hip.
 * Everything stacks as a delta onto the baked pose, since {@code super.setupAnim} restores every part
 * first.
 */
public class DragoonModel extends EntityModel<LivingEntityRenderState> {
    private static final float DEG = (float) (Math.PI / 180.0);

    /** Radians per tick of the idle bob. Slower and shallower than the Scout's — this one is walking,
     * not flying, so the pod only settles on its legs rather than riding a thermal. */
    private static final float BOB_RATE = 0.07f;

    // --- Baked leg pose ------------------------------------------------------------------------
    // A thigh is yawed outward by SPLAY (toward -z at the front, +z at the back) and rolled by LIFT
    // so its far end rises. LIFT is not a round number because it is *solved*, not chosen: it is the
    // angle that puts the knee joint exactly on y = 1 given a 7.5px thigh off a hip at y = 5.5.
    /** Outward yaw of each thigh, 30°. */
    private static final float SPLAY = 0.5236f;
    /** Roll that rakes each thigh up to the knee, ~43.9°. */
    private static final float LIFT = 0.7654f;
    // Each knee carries the exact inverse of its thigh's rotation, so the shin below it hangs
    // world-vertical and the claw lands flat on the ground line whatever the splay. A rotation is
    // applied as Rz*Ry*Rx, so inverting one is not a matter of negating the three angles — these are
    // the ZYX decomposition of (Rz(LIFT)*Ry(SPLAY))^-1, and the signs below are per corner.
    private static final float KNEE_X = 0.3805f;
    private static final float KNEE_Y = 0.3689f;
    private static final float KNEE_Z = 0.8372f;

    private final ModelPart body;
    private final ModelPart head;
    private final ModelPart legFrontRight;
    private final ModelPart legFrontLeft;
    private final ModelPart legBackRight;
    private final ModelPart legBackLeft;
    private final ModelPart kneeFrontRight;
    private final ModelPart kneeFrontLeft;
    private final ModelPart kneeBackRight;
    private final ModelPart kneeBackLeft;

    public DragoonModel(ModelPart root) {
        super(root);
        this.body = root.getChild("body");
        this.head = this.body.getChild("head");
        this.legFrontRight = root.getChild("leg_front_right");
        this.legFrontLeft = root.getChild("leg_front_left");
        this.legBackRight = root.getChild("leg_back_right");
        this.legBackLeft = root.getChild("leg_back_left");
        this.kneeFrontRight = this.legFrontRight.getChild("leg_front_right_knee");
        this.kneeFrontLeft = this.legFrontLeft.getChild("leg_front_left_knee");
        this.kneeBackRight = this.legBackRight.getChild("leg_back_right_knee");
        this.kneeBackLeft = this.legBackLeft.getChild("leg_back_left_knee");
    }

    @Override
    public void setupAnim(LivingEntityRenderState state) {
        // super restores every part to its baked PartPose, so everything below adds a delta onto it.
        super.setupAnim(state);

        // The pod's face tracks the look direction; the legs stay where they are planted.
        this.head.yRot += state.yRot * DEG;
        this.head.xRot += state.xRot * DEG;

        // Idle bob, so a standing Dragoon rides on its suspension instead of reading as inert. +y is
        // down, so subtracting lifts the pod. This moves the pod alone — the legs hang off the root,
        // not off the body, so the claws stay planted while the chassis rocks over them.
        this.body.y -= Mth.sin(state.ageInTicks * BOB_RATE) * 0.3f;

        // Diagonal gait. pos advances the cycle; speed scales it to zero when standing still.
        float pos = state.walkAnimationPos * 0.6662f;
        float speed = state.walkAnimationSpeed;

        // Two diagonal pairs, half a cycle apart: (FR, BL) at phase 0, (FL, BR) at phase PI.
        animateLeg(this.legFrontRight, this.kneeFrontRight, pos, speed, false, 0.0f);
        animateLeg(this.legBackLeft, this.kneeBackLeft, pos, speed, true, 0.0f);
        animateLeg(this.legFrontLeft, this.kneeFrontLeft, pos, speed, true, Mth.PI);
        animateLeg(this.legBackRight, this.kneeBackRight, pos, speed, false, Mth.PI);
    }

    /**
     * Applies one leg's walk delta on top of its baked splay: a fore/aft swing about the hip
     * ({@code yRot}), a lift through the forward half of the cycle ({@code zRot}), and a knee fold
     * on top of it.
     *
     * <p>The fold is much the larger of the two, which is not a stylistic choice. The thigh cannot
     * carry the step on its own — the knees already sit near the top of the 2.0-block hitbox, so
     * there is barely a pixel of lift available before the model pokes out of it. And a fold that
     * merely counter-rotates the thigh buys nothing either: raising the claw by tilting a straight
     * shin through angle <i>a</i> gains only {@code L * (1 - cos a)}, which is second order and
     * near zero for small angles. Only a deep fold actually picks the foot up. Measured over the
     * baked pose, these two land ~4.8px of ground clearance at the peak of a step while the model
     * still fits inside its hitbox. {@code left} legs mirror the right side's signs.
     */
    private static void animateLeg(ModelPart thigh, ModelPart knee, float pos, float speed, boolean left, float phase) {
        float swing = Mth.cos(pos + phase) * 0.45f * speed;
        // Only the forward half lifts; through the back half the foot is planted and bears weight.
        float step = Math.max(0.0f, Mth.sin(pos + phase)) * speed;
        float lift = step * 0.3f;
        float fold = step * 1.1f;

        thigh.yRot += left ? -swing : swing;
        thigh.zRot += left ? -lift : lift;
        knee.yRot += left ? swing * 0.3f : -swing * 0.3f;
        knee.zRot += left ? fold : -fold;
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();

        // Each cube's texOffs points at its own packed UV island — do not hand-edit these; they are
        // assigned by tools/blockbench_export.py and guarded by ModelUvLayoutTest.
        //
        // Vertical layout, y+ pointing down, -z forward: the knee joints sit on y=1 and their shells
        // top out at y=-2.2, the tallest thing on the model — above the pod, which is slung between
        // them spanning y 2.5..13.5, with the barrel reaching y=14. The claws plant on y=24, the
        // ground line. That apex is what the entity's height is sized against: (24 - -2.2) * 1.15
        // render scale / 16 = 1.88 blocks at rest, inside the 2.0 hitbox with just enough headroom
        // left for setupAnim's step to lift a knee without pushing the model out of it.
        //
        // Legs written out longhand rather than built by a shared helper: every cube needs its own
        // editable texOffs literal so it can own a UV island and be hand-painted independently, and
        // the .bbmodel round-trip verifier keys parts by leaf name so no two may share one. See
        // docs/texturing.md — a helper called four times is exactly what that document forbids.

        // --- Body pod ------------------------------------------------------------------------
        // The hull proper, with a wider but shallower shell crossed over it so the pod reads as a
        // rounded armoured mass rather than one slab. (The Zealot's chestPlate borrows this trick.)
        PartDefinition body = root.addOrReplaceChild("body",
                CubeListBuilder.create().texOffs(0, 0).addBox(-5.5f, -6.0f, -6.5f, 11.0f, 11.0f, 13.0f),
                PartPose.offset(0.0f, 8.5f, 0.0f));
        body.addOrReplaceChild("shell",
                CubeListBuilder.create().texOffs(49, 0).addBox(-7.0f, -4.0f, -5.0f, 14.0f, 8.0f, 10.0f),
                PartPose.ZERO);
        // Prow: the pod tapers down and forward to a chin rather than ending in a flat face.
        body.addOrReplaceChild("prow",
                CubeListBuilder.create().texOffs(21, 67).addBox(-4.0f, 1.5f, -8.0f, 8.0f, 5.0f, 4.0f),
                PartPose.ZERO);
        // Cowl over the back, and the vent let into the end of it.
        body.addOrReplaceChild("cowl",
                CubeListBuilder.create().texOffs(80, 25).addBox(-5.0f, -7.0f, 1.0f, 10.0f, 5.0f, 7.0f),
                PartPose.ZERO);
        body.addOrReplaceChild("vent",
                CubeListBuilder.create().texOffs(0, 87).addBox(-3.5f, -6.0f, 7.5f, 7.0f, 4.0f, 1.0f),
                PartPose.ZERO);
        // Low ridge along the spine, between the front knees and the cowl.
        body.addOrReplaceChild("spine",
                CubeListBuilder.create().texOffs(39, 55).addBox(-2.0f, -8.0f, -3.0f, 4.0f, 3.0f, 7.0f),
                PartPose.ZERO);

        // Hip mounts: the ball joints the legs socket into, standing proud of the shell so the legs
        // look mounted rather than glued on. One per corner, sharing the leg pivots below.
        body.addOrReplaceChild("hip_front_right",
                CubeListBuilder.create().texOffs(0, 67).addBox(-2.5f, -2.5f, -2.5f, 5.0f, 5.0f, 5.0f),
                PartPose.offset(-6.0f, -3.0f, -5.0f));
        body.addOrReplaceChild("hip_front_left",
                CubeListBuilder.create().texOffs(104, 55).addBox(-2.5f, -2.5f, -2.5f, 5.0f, 5.0f, 5.0f),
                PartPose.offset(6.0f, -3.0f, -5.0f));
        body.addOrReplaceChild("hip_back_right",
                CubeListBuilder.create().texOffs(83, 55).addBox(-2.5f, -2.5f, -2.5f, 5.0f, 5.0f, 5.0f),
                PartPose.offset(-6.0f, -3.0f, 5.0f));
        body.addOrReplaceChild("hip_back_left",
                CubeListBuilder.create().texOffs(62, 55).addBox(-2.5f, -2.5f, -2.5f, 5.0f, 5.0f, 5.0f),
                PartPose.offset(6.0f, -3.0f, 5.0f));

        // --- Phase disruptor: a short barrel slung under the prow, its tip the only lit emitter ---
        PartDefinition barrel = body.addOrReplaceChild("barrel",
                CubeListBuilder.create().texOffs(100, 78).addBox(-1.5f, -1.5f, -3.0f, 3.0f, 3.0f, 3.0f),
                PartPose.offset(0.0f, 4.0f, -6.0f));
        barrel.addOrReplaceChild("barrel_tip",
                CubeListBuilder.create().texOffs(26, 87).addBox(-1.0f, -1.0f, -3.9f, 2.0f, 2.0f, 1.0f),
                PartPose.ZERO);

        // --- Face: a cowl ring around a recessed socket holding the blue lens ---------------------
        // Six wide against the pod's fourteen, so the lens dominates the front the way the art does.
        // Each layer sits proud of the one behind it by a fraction of a texel so they never z-fight.
        PartDefinition head = body.addOrReplaceChild("head",
                CubeListBuilder.create().texOffs(51, 25).addBox(-5.0f, -4.5f, -2.5f, 10.0f, 9.0f, 4.0f),
                PartPose.offset(0.0f, -1.5f, -6.5f));
        head.addOrReplaceChild("socket",
                CubeListBuilder.create().texOffs(0, 78).addBox(-3.5f, -3.5f, -3.2f, 7.0f, 7.0f, 1.0f),
                PartPose.ZERO);
        head.addOrReplaceChild("lens",
                CubeListBuilder.create().texOffs(85, 78).addBox(-3.0f, -3.0f, -3.6f, 6.0f, 6.0f, 1.0f),
                PartPose.ZERO);
        head.addOrReplaceChild("pupil",
                CubeListBuilder.create().texOffs(17, 87).addBox(-1.5f, -1.5f, -3.8f, 3.0f, 3.0f, 1.0f),
                PartPose.ZERO);

        // --- Legs ---------------------------------------------------------------------------------
        // Per leg: a 6x6 armoured thigh shell raking up and out to the knee, then shin, pastern and
        // claw hanging world-vertical beneath it (see KNEE_X/Y/Z). Right legs build their thigh along
        // local -x and lift on a positive roll; left legs mirror both.
        //
        // Hung off the root rather than off the pod, so setupAnim's idle bob rocks the chassis
        // without lifting the planted claws with it. (The Zealot's legs hang off root for the same
        // reason.) Their pivots are therefore absolute, not relative to the body's y=8.5.
        PartDefinition legFR = root.addOrReplaceChild("leg_front_right",
                CubeListBuilder.create().texOffs(84, 42).addBox(-7.5f, -3.0f, -3.0f, 7.5f, 6.0f, 6.0f),
                PartPose.offsetAndRotation(-6.0f, 5.5f, -5.0f, 0.0f, -SPLAY, LIFT));
        PartDefinition kneeFR = legFR.addOrReplaceChild("leg_front_right_knee",
                CubeListBuilder.create().texOffs(68, 78).addBox(-2.0f, -2.0f, -2.0f, 4.0f, 4.0f, 4.0f),
                PartPose.offsetAndRotation(-7.5f, 0.0f, 0.0f, -KNEE_X, KNEE_Y, -KNEE_Z));
        PartDefinition shinFR = kneeFR.addOrReplaceChild("leg_front_right_shin",
                CubeListBuilder.create().texOffs(34, 25).addBox(-2.0f, 0.0f, -2.0f, 4.0f, 12.0f, 4.0f),
                PartPose.ZERO);
        PartDefinition pasternFR = shinFR.addOrReplaceChild("leg_front_right_pastern",
                CubeListBuilder.create().texOffs(26, 55).addBox(-1.5f, 0.0f, -1.5f, 3.0f, 8.0f, 3.0f),
                PartPose.offset(0.0f, 12.0f, 0.0f));
        pasternFR.addOrReplaceChild("leg_front_right_claw",
                CubeListBuilder.create().texOffs(103, 67).addBox(-2.0f, 0.0f, -3.0f, 4.0f, 3.0f, 5.0f),
                PartPose.offset(0.0f, 8.0f, 0.0f));

        PartDefinition legFL = root.addOrReplaceChild("leg_front_left",
                CubeListBuilder.create().texOffs(56, 42).addBox(0.0f, -3.0f, -3.0f, 7.5f, 6.0f, 6.0f),
                PartPose.offsetAndRotation(6.0f, 5.5f, -5.0f, 0.0f, SPLAY, -LIFT));
        PartDefinition kneeFL = legFL.addOrReplaceChild("leg_front_left_knee",
                CubeListBuilder.create().texOffs(51, 78).addBox(-2.0f, -2.0f, -2.0f, 4.0f, 4.0f, 4.0f),
                PartPose.offsetAndRotation(7.5f, 0.0f, 0.0f, -KNEE_X, -KNEE_Y, KNEE_Z));
        PartDefinition shinFL = kneeFL.addOrReplaceChild("leg_front_left_shin",
                CubeListBuilder.create().texOffs(17, 25).addBox(-2.0f, 0.0f, -2.0f, 4.0f, 12.0f, 4.0f),
                PartPose.ZERO);
        PartDefinition pasternFL = shinFL.addOrReplaceChild("leg_front_left_pastern",
                CubeListBuilder.create().texOffs(13, 55).addBox(-1.5f, 0.0f, -1.5f, 3.0f, 8.0f, 3.0f),
                PartPose.offset(0.0f, 12.0f, 0.0f));
        pasternFL.addOrReplaceChild("leg_front_left_claw",
                CubeListBuilder.create().texOffs(84, 67).addBox(-2.0f, 0.0f, -3.0f, 4.0f, 3.0f, 5.0f),
                PartPose.offset(0.0f, 8.0f, 0.0f));

        PartDefinition legBR = root.addOrReplaceChild("leg_back_right",
                CubeListBuilder.create().texOffs(28, 42).addBox(-7.5f, -3.0f, -3.0f, 7.5f, 6.0f, 6.0f),
                PartPose.offsetAndRotation(-6.0f, 5.5f, 5.0f, 0.0f, SPLAY, LIFT));
        PartDefinition kneeBR = legBR.addOrReplaceChild("leg_back_right_knee",
                CubeListBuilder.create().texOffs(34, 78).addBox(-2.0f, -2.0f, -2.0f, 4.0f, 4.0f, 4.0f),
                PartPose.offsetAndRotation(-7.5f, 0.0f, 0.0f, KNEE_X, -KNEE_Y, -KNEE_Z));
        PartDefinition shinBR = kneeBR.addOrReplaceChild("leg_back_right_shin",
                CubeListBuilder.create().texOffs(0, 25).addBox(-2.0f, 0.0f, -2.0f, 4.0f, 12.0f, 4.0f),
                PartPose.ZERO);
        PartDefinition pasternBR = shinBR.addOrReplaceChild("leg_back_right_pastern",
                CubeListBuilder.create().texOffs(0, 55).addBox(-1.5f, 0.0f, -1.5f, 3.0f, 8.0f, 3.0f),
                PartPose.offset(0.0f, 12.0f, 0.0f));
        pasternBR.addOrReplaceChild("leg_back_right_claw",
                CubeListBuilder.create().texOffs(65, 67).addBox(-2.0f, 0.0f, -3.0f, 4.0f, 3.0f, 5.0f),
                PartPose.offset(0.0f, 8.0f, 0.0f));

        PartDefinition legBL = root.addOrReplaceChild("leg_back_left",
                CubeListBuilder.create().texOffs(0, 42).addBox(0.0f, -3.0f, -3.0f, 7.5f, 6.0f, 6.0f),
                PartPose.offsetAndRotation(6.0f, 5.5f, 5.0f, 0.0f, -SPLAY, -LIFT));
        PartDefinition kneeBL = legBL.addOrReplaceChild("leg_back_left_knee",
                CubeListBuilder.create().texOffs(17, 78).addBox(-2.0f, -2.0f, -2.0f, 4.0f, 4.0f, 4.0f),
                PartPose.offsetAndRotation(7.5f, 0.0f, 0.0f, KNEE_X, KNEE_Y, KNEE_Z));
        PartDefinition shinBL = kneeBL.addOrReplaceChild("leg_back_left_shin",
                CubeListBuilder.create().texOffs(98, 0).addBox(-2.0f, 0.0f, -2.0f, 4.0f, 12.0f, 4.0f),
                PartPose.ZERO);
        PartDefinition pasternBL = shinBL.addOrReplaceChild("leg_back_left_pastern",
                CubeListBuilder.create().texOffs(112, 42).addBox(-1.5f, 0.0f, -1.5f, 3.0f, 8.0f, 3.0f),
                PartPose.offset(0.0f, 12.0f, 0.0f));
        pasternBL.addOrReplaceChild("leg_back_left_claw",
                CubeListBuilder.create().texOffs(46, 67).addBox(-2.0f, 0.0f, -3.0f, 4.0f, 3.0f, 5.0f),
                PartPose.offset(0.0f, 8.0f, 0.0f));

        return LayerDefinition.create(mesh, 128, 128);
    }

}
