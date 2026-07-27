package net.bitflora.asteriskcraft.client.zerg;

import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.util.Mth;

/**
 * Hand-authored geometry for the Drone, shaped after the reference art: a squat crab-like digger whose
 * <b>silhouette is one dominant carapace</b>. A wide amber shell — a broad plate with a narrower crown
 * laid over it so the mass reads domed rather than slabbed — sits over a low thorax, flares into a
 * rear plate above a two-segment abdomen, and throws three backswept spines off its tail end. The head
 * is small and tucked under the shell's front lip, barely protruding, carrying bone pincers and a pair
 * of glowing eyes. Slung off the right shoulder is the unit's one real tool: a long <b>bone mining
 * sickle</b> on a two-segment arm, its blade two cubes angled against each other so it reads as a
 * curve. A smaller manipulator balances it on the left. Six short legs carry the whole thing.
 *
 * <p>The Drone does <i>not</i> hover, and previous versions of this model wrongly implied it did:
 * {@link net.bitflora.asteriskcraft.entity.zerg.DroneEntity} is a {@code PathfinderMob}, so it walks
 * everywhere it goes.
 *
 * <p>Authored in true pixel space (16px = 1 block), "up" is negative y, and colour comes from
 * {@code textures/entity/drone.png}, in which <b>every cube owns its own UV island</b> so it can be
 * hand-painted independently — see {@code tools/blockbench_export.py}, which packs those islands and
 * emits the Blockbench project. Only the eyes and a few carapace ridge cracks are painted into
 * {@code drone_glow.png}, so the emissive {@link net.bitflora.asteriskcraft.client.UnitGlowLayer}
 * lights just those; everything else must stay transparent there, since that layer re-submits the
 * whole model.
 *
 * <p>{@link #setupAnim} drives head-look, an idle bob, an alternating-tripod walk and a mining chop.
 * Everything stacks as a delta onto the baked pose, since {@code super.setupAnim} restores every part
 * first.
 */
public class DroneModel extends EntityModel<DroneRenderState> {
    private static final float DEG = (float) (Math.PI / 180.0);

    /** Radians per tick of the idle bob — the chassis settling on its legs, not a hover. */
    private static final float BOB_RATE = 0.09f;

    // --- Baked leg pose ------------------------------------------------------------------------
    // Each thigh is rolled outward-and-down from its hip; the shin below it carries the exact inverse
    // roll so it hangs world-vertical and the foot lands flat on the ground line. With only a Z
    // rotation in play, that inverse is simply the negated angle — no ZYX decomposition needed here,
    // unlike DragoonModel's splayed legs.
    /** Roll of each thigh away from its hip, ~34.4°. */
    private static final float HIP_ROLL = 0.6f;
    /** Thigh length. Solved together with the hip height: a hip on y=18 puts the knee on
     * y = 18 + THIGH*sin(HIP_ROLL) = 20.26 and x = 3 + THIGH*cos(HIP_ROLL) = 6.30, so the shin box
     * around it reaches x = 7.30 — just inside the 8px half-width the 0.9-wide hitbox allows at the
     * renderer's 0.9 scale. Hips one pixel wider than this and the feet poke out of the hitbox. */
    private static final float THIGH = 4.0f;
    /** Shin length, = 24 - 20.26, so the foot plants exactly on the y=24 ground line. */
    private static final float SHIN = 3.75f;

    private final ModelPart body;
    private final ModelPart head;
    private final ModelPart clawArm;
    private final ModelPart clawFore;
    private final ModelPart legFrontRight;
    private final ModelPart legMidRight;
    private final ModelPart legBackRight;
    private final ModelPart legFrontLeft;
    private final ModelPart legMidLeft;
    private final ModelPart legBackLeft;
    private final ModelPart shinFrontRight;
    private final ModelPart shinMidRight;
    private final ModelPart shinBackRight;
    private final ModelPart shinFrontLeft;
    private final ModelPart shinMidLeft;
    private final ModelPart shinBackLeft;

    public DroneModel(ModelPart root) {
        super(root);
        this.body = root.getChild("body");
        this.head = this.body.getChild("head");
        this.clawArm = this.body.getChild("claw_arm");
        this.clawFore = this.clawArm.getChild("claw_fore");
        this.legFrontRight = root.getChild("leg_front_right");
        this.legMidRight = root.getChild("leg_mid_right");
        this.legBackRight = root.getChild("leg_back_right");
        this.legFrontLeft = root.getChild("leg_front_left");
        this.legMidLeft = root.getChild("leg_mid_left");
        this.legBackLeft = root.getChild("leg_back_left");
        this.shinFrontRight = this.legFrontRight.getChild("leg_front_right_shin");
        this.shinMidRight = this.legMidRight.getChild("leg_mid_right_shin");
        this.shinBackRight = this.legBackRight.getChild("leg_back_right_shin");
        this.shinFrontLeft = this.legFrontLeft.getChild("leg_front_left_shin");
        this.shinMidLeft = this.legMidLeft.getChild("leg_mid_left_shin");
        this.shinBackLeft = this.legBackLeft.getChild("leg_back_left_shin");
    }

    @Override
    public void setupAnim(DroneRenderState state) {
        // super restores every part to its baked PartPose, so everything below adds a delta onto it.
        super.setupAnim(state);

        this.head.yRot += state.yRot * DEG;
        this.head.xRot += state.xRot * DEG;

        // Idle bob: the shell rocks on its suspension so a standing Drone doesn't read as inert. +y is
        // down, so subtracting lifts it. This moves the body alone — the legs hang off the root, not
        // off the body, so the feet stay planted while the shell settles over them.
        this.body.y -= Mth.sin(state.ageInTicks * BOB_RATE) * 0.35f;

        // Alternating tripod gait, the real insect one: front-left + mid-right + back-left step
        // together, and the opposite three half a cycle later, so three feet are always planted.
        float pos = state.walkAnimationPos * 0.6662f;
        float speed = state.walkAnimationSpeed;
        animateLeg(this.legFrontLeft, this.shinFrontLeft, pos, speed, true, 0.0f);
        animateLeg(this.legMidRight, this.shinMidRight, pos, speed, false, 0.0f);
        animateLeg(this.legBackLeft, this.shinBackLeft, pos, speed, true, 0.0f);
        animateLeg(this.legFrontRight, this.shinFrontRight, pos, speed, false, Mth.PI);
        animateLeg(this.legMidLeft, this.shinMidLeft, pos, speed, true, Mth.PI);
        animateLeg(this.legBackRight, this.shinBackRight, pos, speed, false, Mth.PI);

        // Mining stroke: the sickle rakes down and outward, and recovers, across one swing.
        // ProbeEntity.HarvestGoal swings every tick it is in range of a node, and LivingEntity.swing
        // restarts from the half-way mark, so this cycles continuously for as long as the Drone works.
        //
        // The amplitudes are solved against the floor, not chosen. The blade already hangs at y=20.9
        // at rest, leaving only 3.1px above the y=24 ground line, so a pitch of more than ~0.25rad at
        // the shoulder buries the sickle in the terrain. Most of the visible travel therefore comes
        // from the yaw, which has no such ceiling: together they take the blade tip through 3.7px of
        // drop and 2.9px of sideways rake, bottoming out at y=23.7.
        if (state.swingProgress > 0.0f) {
            float chop = Mth.sin(state.swingProgress * Mth.PI);
            this.clawArm.xRot += chop * 0.24f;
            this.clawArm.yRot += chop * 0.35f;
            this.clawFore.xRot += chop * 0.13f;
        }
    }

    /**
     * Applies one leg's walk delta on top of its baked roll: a fore/aft swing about the hip
     * ({@code yRot}) and, through the forward half of the cycle only, a lift ({@code zRot}) that rolls
     * the thigh back toward horizontal to pick the knee up.
     *
     * <p>The shin takes the exact negation of the lift, so it stays world-vertical throughout and the
     * whole rise at the knee becomes ground clearance at the foot — about 1.9px at the peak of a step.
     * Short legs on a squat body cannot stride far, and faking a longer step would only make the feet
     * skate. Through the back half of the cycle the lift is zero and the foot is planted, bearing
     * weight. {@code left} legs mirror the right side's signs so a tripod's three feet all travel the
     * same way in world space.
     */
    private static void animateLeg(ModelPart thigh, ModelPart shin, float pos, float speed, boolean left, float phase) {
        float swing = Mth.cos(pos + phase) * 0.5f * speed;
        float lift = Math.max(0.0f, Mth.sin(pos + phase)) * speed * 0.5f;

        thigh.yRot += left ? -swing : swing;
        thigh.zRot += left ? -lift : lift;
        shin.zRot += left ? lift : -lift;
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();

        // Each cube's texOffs points at its own packed UV island — do not hand-edit these; they are
        // assigned by tools/blockbench_export.py and guarded by ModelUvLayoutTest.
        //
        // Vertical layout, y+ pointing down, -z forward: the crown plate tops out at y=10.5, standing
        // 2px proud of a carapace spanning y 12.5..17.5, over a thorax at y 14..20. The spine tips rake
        // back to y=10.6, just under the crown by design — they are the only other thing that comes
        // near it. The feet plant on y=24, the ground line. That apex is what the entity's height is
        // sized against: (24 - 10.5) * 0.9 render scale / 16 = 0.76 blocks at rest, inside the 0.8
        // hitbox with headroom for the gait to lift a knee without pushing the model out of it.
        // Half-width stays inside 8px (the shins, at x = +/-7.3, are the widest points) against the
        // 0.9 hitbox.
        //
        // Legs written out longhand rather than built by a shared helper: every cube needs its own
        // editable texOffs literal so it can own a UV island and be hand-painted independently, and
        // the .bbmodel round-trip verifier keys parts by leaf name so no two may share one. See
        // docs/texturing.md — a helper called six times is exactly what that document forbids.

        // --- Thorax, and the carapace that is the whole silhouette -----------------------------
        // The thorax deliberately hangs 2.5px below the shell's rim: that strip of violet flesh between
        // amber plate and legs is the whole reason this cube is visible at all.
        PartDefinition body = root.addOrReplaceChild("body",
                CubeListBuilder.create().texOffs(57, 0).addBox(-4.5f, -2.0f, -6.5f, 9.0f, 6.0f, 13.0f),
                PartPose.offset(0.0f, 16.0f, 0.0f));
        // The broad shell, and a narrower crown laid over it so the mass reads domed rather than as
        // one slab. (DragoonModel's body/shell pair uses the same trick.)
        body.addOrReplaceChild("carapace",
                CubeListBuilder.create().texOffs(0, 0).addBox(-7.0f, -3.5f, -7.0f, 14.0f, 5.0f, 14.0f),
                PartPose.ZERO);
        body.addOrReplaceChild("crown",
                CubeListBuilder.create().texOffs(23, 20).addBox(-4.0f, -5.5f, -3.5f, 8.0f, 3.0f, 9.0f),
                PartPose.ZERO);
        // Brow: the shell's front lip rakes down over the head rather than ending in a flat wall.
        body.addOrReplaceChild("brow",
                CubeListBuilder.create().texOffs(18, 44).addBox(-5.5f, -2.0f, -2.5f, 11.0f, 3.0f, 3.0f),
                PartPose.offsetAndRotation(0.0f, -2.0f, -6.5f, 0.45f, 0.0f, 0.0f));
        // Rear plate flaring up and back over the abdomen.
        body.addOrReplaceChild("rear_plate",
                CubeListBuilder.create().texOffs(83, 20).addBox(-5.5f, -2.0f, 0.0f, 11.0f, 2.5f, 6.0f),
                PartPose.offsetAndRotation(0.0f, -3.5f, 6.0f, -0.5f, 0.0f, 0.0f));
        // Side skirts hanging below the shell's rim at y=17.5, over the hips — the carapace does not
        // reach down here, so they read as its edge continuing past it. They have to clear that rim in
        // y: an earlier pass had them level with the shell instead, where they were entirely swallowed
        // by it and z-fought with its flanks. Held in to x=+/-6.2 so the shins stay the widest part.
        body.addOrReplaceChild("plate_left",
                CubeListBuilder.create().texOffs(102, 0).addBox(-0.9f, 0.0f, -4.5f, 1.8f, 4.0f, 9.0f),
                PartPose.offsetAndRotation(6.2f, 1.5f, -0.5f, 0.0f, 0.0f, 0.18f));
        body.addOrReplaceChild("plate_right",
                CubeListBuilder.create().texOffs(0, 20).addBox(-0.9f, 0.0f, -4.5f, 1.8f, 4.0f, 9.0f),
                PartPose.offsetAndRotation(-6.2f, 1.5f, -0.5f, 0.0f, 0.0f, -0.18f));

        // --- Backswept spines off the tail end of the shell -------------------------------------
        // Raked hard enough that their tips land level with the crown instead of towering over it, and
        // long enough to actually read at this scale — the 4px stubs they replaced disappeared into
        // the shell.
        body.addOrReplaceChild("spine_center",
                CubeListBuilder.create().texOffs(19, 34).addBox(-1.25f, -6.5f, -1.25f, 2.5f, 6.5f, 2.5f),
                PartPose.offsetAndRotation(0.0f, -2.8f, 5.0f, -1.35f, 0.0f, 0.0f));
        body.addOrReplaceChild("spine_left",
                CubeListBuilder.create().texOffs(98, 34).addBox(-1.0f, -5.5f, -1.0f, 2.0f, 5.5f, 2.0f),
                PartPose.offsetAndRotation(4.0f, -2.3f, 4.0f, -1.25f, 0.0f, 0.35f));
        body.addOrReplaceChild("spine_right",
                CubeListBuilder.create().texOffs(107, 34).addBox(-1.0f, -5.5f, -1.0f, 2.0f, 5.5f, 2.0f),
                PartPose.offsetAndRotation(-4.0f, -2.3f, 4.0f, -1.25f, 0.0f, -0.35f));

        // --- Head: small, tucked under the shell's front lip ------------------------------------
        // Only ~2px of it clears the carapace front at z=-7, which is the point: the art's Drone
        // hides its head under the shell rather than leading with it.
        PartDefinition head = body.addOrReplaceChild("head",
                CubeListBuilder.create().texOffs(30, 34).addBox(-2.5f, -2.0f, -4.0f, 5.0f, 4.0f, 4.0f),
                PartPose.offsetAndRotation(0.0f, 0.5f, -5.0f, 0.15f, 0.0f, 0.0f));
        head.addOrReplaceChild("mandible_left",
                CubeListBuilder.create().texOffs(94, 52).addBox(-0.5f, -0.5f, -3.0f, 1.0f, 1.0f, 3.0f),
                PartPose.offsetAndRotation(1.5f, 1.5f, -3.5f, 0.0f, -0.3f, 0.0f));
        head.addOrReplaceChild("mandible_right",
                CubeListBuilder.create().texOffs(103, 52).addBox(-0.5f, -0.5f, -3.0f, 1.0f, 1.0f, 3.0f),
                PartPose.offsetAndRotation(-1.5f, 1.5f, -3.5f, 0.0f, 0.3f, 0.0f));
        head.addOrReplaceChild("eye_left",
                CubeListBuilder.create().texOffs(112, 52).addBox(-0.75f, -0.75f, -0.5f, 1.5f, 1.5f, 1.0f),
                PartPose.offset(1.6f, 0.7f, -4.1f));
        head.addOrReplaceChild("eye_right",
                CubeListBuilder.create().texOffs(118, 52).addBox(-0.75f, -0.75f, -0.5f, 1.5f, 1.5f, 1.0f),
                PartPose.offset(-1.6f, 0.7f, -4.1f));

        // --- Abdomen: two tapering segments drooping off the back --------------------------------
        // Kept short on purpose. The footprint is only 14px square, and a longer tail made the whole
        // unit read tail-first rather than shell-first.
        PartDefinition abdomen = body.addOrReplaceChild("abdomen",
                CubeListBuilder.create().texOffs(58, 20).addBox(-3.5f, -2.25f, 0.0f, 7.0f, 4.5f, 4.5f),
                PartPose.offsetAndRotation(0.0f, -1.0f, 5.0f, 0.2f, 0.0f, 0.0f));
        abdomen.addOrReplaceChild("abdomen_tip",
                CubeListBuilder.create().texOffs(0, 44).addBox(-2.25f, -1.5f, 0.0f, 4.5f, 3.0f, 3.5f),
                PartPose.offsetAndRotation(0.0f, 0.0f, 4.5f, 0.25f, 0.0f, 0.0f));

        // --- Mining sickle: the Drone's one tool, thrown forward off the right shoulder -----------
        // Two arm segments reaching forward, then a blade built as two cubes angled against each
        // other — a single box cannot read as the art's hooked bone claw, but a pair bent at the
        // joint does. clawArm/clawFore are what setupAnim chops with.
        PartDefinition clawArm = body.addOrReplaceChild("claw_arm",
                CubeListBuilder.create().texOffs(0, 34).addBox(-1.5f, -1.5f, -4.0f, 3.0f, 3.0f, 4.0f),
                PartPose.offsetAndRotation(-5.5f, -0.5f, -3.0f, 0.1f, -0.25f, 0.0f));
        PartDefinition clawFore = clawArm.addOrReplaceChild("claw_fore",
                CubeListBuilder.create().texOffs(82, 34).addBox(-1.25f, -1.25f, -3.5f, 2.5f, 2.5f, 3.5f),
                PartPose.offsetAndRotation(0.0f, 0.0f, -4.0f, 0.2f, 0.0f, 0.0f));
        // The bend has to be steep to read as a hook: at a shallow angle the two cubes just look like
        // one straight spike. The base drives down-and-forward, the tip comes back up.
        PartDefinition clawBase = clawFore.addOrReplaceChild("claw_base",
                CubeListBuilder.create().texOffs(49, 34).addBox(-1.0f, -0.75f, -4.0f, 2.0f, 1.5f, 4.0f),
                PartPose.offsetAndRotation(0.0f, 0.0f, -3.5f, 0.75f, 0.0f, 0.0f));
        clawBase.addOrReplaceChild("claw_tip",
                CubeListBuilder.create().texOffs(60, 44).addBox(-0.6f, -0.5f, -3.0f, 1.2f, 1.0f, 3.0f),
                PartPose.offsetAndRotation(0.0f, 0.0f, -4.0f, -1.35f, 0.0f, 0.0f));

        // --- Left manipulator: the smaller opposite limb, no blade -------------------------------
        PartDefinition armLeft = body.addOrReplaceChild("arm_left",
                CubeListBuilder.create().texOffs(66, 34).addBox(-1.25f, -1.25f, -3.5f, 2.5f, 2.5f, 3.5f),
                PartPose.offsetAndRotation(4.5f, -0.5f, -3.0f, 0.2f, 0.2f, 0.0f));
        PartDefinition armLeftFore = armLeft.addOrReplaceChild("arm_left_fore",
                CubeListBuilder.create().texOffs(47, 44).addBox(-1.0f, -1.0f, -3.0f, 2.0f, 2.0f, 3.0f),
                PartPose.offsetAndRotation(0.0f, 0.0f, -3.5f, 0.25f, 0.0f, 0.0f));
        armLeftFore.addOrReplaceChild("arm_left_hook",
                CubeListBuilder.create().texOffs(84, 52).addBox(-0.75f, -0.5f, -2.5f, 1.5f, 1.0f, 2.5f),
                PartPose.offsetAndRotation(0.0f, 0.0f, -3.0f, 0.45f, 0.0f, 0.0f));

        // --- Six legs, hung off the root ---------------------------------------------------------
        // On the root rather than the body, so the idle bob rocks the shell without lifting planted
        // feet with it. (DragoonModel's and ZealotModel's legs hang off root for the same reason.)
        // Their pivots are therefore absolute, not relative to the body's y=16. Three z stations per
        // side: front z=-4, mid z=0, back z=4.5. Each shin carries -HIP_ROLL, the exact inverse of
        // its thigh's roll, so it hangs world-vertical and the foot lands flat on y=24.
        PartDefinition legFrontRight = root.addOrReplaceChild("leg_front_right",
                CubeListBuilder.create().texOffs(42, 52).addBox(-THIGH, -1.25f, -1.25f, THIGH, 2.5f, 2.5f),
                PartPose.offsetAndRotation(-3.0f, 18.0f, -4.0f, 0.0f, 0.0f, -HIP_ROLL));
        legFrontRight.addOrReplaceChild("leg_front_right_shin",
                CubeListBuilder.create().texOffs(100, 44).addBox(-1.0f, 0.0f, -1.0f, 2.0f, SHIN, 2.0f),
                PartPose.offsetAndRotation(-THIGH, 0.0f, 0.0f, 0.0f, 0.0f, HIP_ROLL));

        PartDefinition legMidRight = root.addOrReplaceChild("leg_mid_right",
                CubeListBuilder.create().texOffs(70, 52).addBox(-THIGH, -1.25f, -1.25f, THIGH, 2.5f, 2.5f),
                PartPose.offsetAndRotation(-3.0f, 18.0f, 0.0f, 0.0f, 0.0f, -HIP_ROLL));
        legMidRight.addOrReplaceChild("leg_mid_right_shin",
                CubeListBuilder.create().texOffs(118, 44).addBox(-1.0f, 0.0f, -1.0f, 2.0f, SHIN, 2.0f),
                PartPose.offsetAndRotation(-THIGH, 0.0f, 0.0f, 0.0f, 0.0f, HIP_ROLL));

        PartDefinition legBackRight = root.addOrReplaceChild("leg_back_right",
                CubeListBuilder.create().texOffs(14, 52).addBox(-THIGH, -1.25f, -1.25f, THIGH, 2.5f, 2.5f),
                PartPose.offsetAndRotation(-3.0f, 18.0f, 4.5f, 0.0f, 0.0f, -HIP_ROLL));
        legBackRight.addOrReplaceChild("leg_back_right_shin",
                CubeListBuilder.create().texOffs(82, 44).addBox(-1.0f, 0.0f, -1.0f, 2.0f, SHIN, 2.0f),
                PartPose.offsetAndRotation(-THIGH, 0.0f, 0.0f, 0.0f, 0.0f, HIP_ROLL));

        PartDefinition legFrontLeft = root.addOrReplaceChild("leg_front_left",
                CubeListBuilder.create().texOffs(28, 52).addBox(0.0f, -1.25f, -1.25f, THIGH, 2.5f, 2.5f),
                PartPose.offsetAndRotation(3.0f, 18.0f, -4.0f, 0.0f, 0.0f, HIP_ROLL));
        legFrontLeft.addOrReplaceChild("leg_front_left_shin",
                CubeListBuilder.create().texOffs(91, 44).addBox(-1.0f, 0.0f, -1.0f, 2.0f, SHIN, 2.0f),
                PartPose.offsetAndRotation(THIGH, 0.0f, 0.0f, 0.0f, 0.0f, -HIP_ROLL));

        PartDefinition legMidLeft = root.addOrReplaceChild("leg_mid_left",
                CubeListBuilder.create().texOffs(56, 52).addBox(0.0f, -1.25f, -1.25f, THIGH, 2.5f, 2.5f),
                PartPose.offsetAndRotation(3.0f, 18.0f, 0.0f, 0.0f, 0.0f, HIP_ROLL));
        legMidLeft.addOrReplaceChild("leg_mid_left_shin",
                CubeListBuilder.create().texOffs(109, 44).addBox(-1.0f, 0.0f, -1.0f, 2.0f, SHIN, 2.0f),
                PartPose.offsetAndRotation(THIGH, 0.0f, 0.0f, 0.0f, 0.0f, -HIP_ROLL));

        PartDefinition legBackLeft = root.addOrReplaceChild("leg_back_left",
                CubeListBuilder.create().texOffs(0, 52).addBox(0.0f, -1.25f, -1.25f, THIGH, 2.5f, 2.5f),
                PartPose.offsetAndRotation(3.0f, 18.0f, 4.5f, 0.0f, 0.0f, HIP_ROLL));
        legBackLeft.addOrReplaceChild("leg_back_left_shin",
                CubeListBuilder.create().texOffs(73, 44).addBox(-1.0f, 0.0f, -1.0f, 2.0f, SHIN, 2.0f),
                PartPose.offsetAndRotation(THIGH, 0.0f, 0.0f, 0.0f, 0.0f, -HIP_ROLL));

        // 128 wide is a floor, not a choice: the carapace's island alone is 2*(14+14) = 56 texels
        // across. The packed islands bottom out at v=62, so 64 rows is all this model needs.
        return LayerDefinition.create(mesh, 128, 64);
    }
}
