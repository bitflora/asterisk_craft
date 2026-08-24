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
 * Hand-authored geometry for the Lurker: <b>vanilla's spider, with a Hydralisk's head on it</b>.
 *
 * <p>The frame is {@code net.minecraft.client.model.monster.spider.SpiderModel}'s, box for box and
 * pose for pose — the 6x6x6 cephalothorax at y=15, the 10x8x12 abdomen nine texels behind it, and
 * eight single-segment 16-texel legs splayed off the sides at vanilla's own yaw/roll pairs, driven
 * by vanilla's own gait. Reusing the vanilla shape is the point (see CLAUDE.md's guidelines): the
 * animal reads as a Minecraft spider at a glance, and only its front end says Zerg.
 *
 * <p>Two deliberate departures from a literal copy, both forced:
 * <ul>
 *   <li><b>The head is the Hydralisk's</b>, transplanted whole in place of the spider's 8x8x8 skull
 *       and mounted on that same pivot — the same short cranium under a broad bone hood, the same
 *       brow, the same gaping jaws around interlocking fangs and mandible spars. It is the one part
 *       a player has to recognise, since everything else about the shape is spider.
 *   <li><b>The abdomen keeps its three pairs of raked spines.</b> They are not spider, but they are
 *       what a <i>burrowed</i> Lurker is: the renderer sinks the whole model by a block while it is
 *       dug in and lets the terrain clip it (see {@code LurkerRenderer.scale}), so the geometry
 *       alone decides what a buried one looks like — there is no second model. A bare spider
 *       abdomen tops out 13 texels up and would vanish completely under a 16-texel sink; the spines
 *       reach about 19 and are the low fringe that stays above the ground. Shortening them changes
 *       what a burrowed Lurker is.
 * </ul>
 *
 * <p>The legs are also why the silhouette is far wider than the 0.9-block hitbox. Measured off the
 * baked geometry the model is 2.06 blocks wide, 1.77 deep and 1.18 tall: only the height is a real
 * constraint (and it is met, against a 1.2 hitbox), while the width and depth overhang deliberately
 * — vanilla's spider overhangs its own hitbox the same way, and a hitbox at the true leg span would
 * make the unit three pathfinding nodes across and unable to fit a one-block gap.
 *
 * <p>Authored in true pixel space (16px = 1 block), "up" is negative y, ground is y=24, and colour
 * comes from {@code textures/entity/lurker.png} — a mottled brown hide over darker chitin, painted
 * in a texture where <b>every cube owns its own UV island</b> so it can be hand-painted
 * independently. See {@code tools/blockbench_export.py}, which packs those islands and emits the
 * Blockbench project. That tooling also constrains how this class may be written: one
 * {@code texOffs} literal per cube, builders inlined into their {@code addOrReplaceChild} call, and
 * globally unique part names — which is why the eight legs are written out one by one rather than
 * sharing vanilla's two {@code CubeListBuilder}s, and why none of them uses {@code mirror()} (each
 * leg has its own island to be painted on now, so there is nothing left to mirror onto).
 * See docs/texturing.md.
 *
 * <p>Only the eyes and the lit tips of the back and crest spines belong in {@code lurker_glow.png};
 * everything else must stay transparent there, since {@code UnitGlowLayer} re-submits the whole model.
 */
public class LurkerModel extends EntityModel<LurkerRenderState> {
    /** Radians per tick of the idle breath. */
    private static final float IDLE_RATE = 0.07f;
    /** Fraction of a volley spent rearing back before the spines go out. */
    private static final float WINDUP_END = 0.35f;
    /** Vanilla's spider leg angles: a quarter turn at the ends, an eighth in the middle. */
    private static final float LEG_YAW_WIDE = Mth.PI / 4.0f;
    private static final float LEG_YAW_NARROW = Mth.PI / 8.0f;
    private static final float LEG_ROLL_WIDE = Mth.PI / 4.0f;
    /** Vanilla's own literal for the middle legs' roll — not a rounded fraction of PI. */
    private static final float LEG_ROLL_NARROW = 0.58119464f;

    private final ModelPart head;
    private final ModelPart lowerJaw;
    private final ModelPart abdomen;
    private final ModelPart spine1;
    private final ModelPart spine2;
    private final ModelPart spine3;
    private final ModelPart spine4;
    private final ModelPart spine5;
    private final ModelPart spine6;
    private final ModelPart rightHindLeg;
    private final ModelPart leftHindLeg;
    private final ModelPart rightMiddleHindLeg;
    private final ModelPart leftMiddleHindLeg;
    private final ModelPart rightMiddleFrontLeg;
    private final ModelPart leftMiddleFrontLeg;
    private final ModelPart rightFrontLeg;
    private final ModelPart leftFrontLeg;

    public LurkerModel(ModelPart root) {
        super(root);
        this.head = root.getChild("head");
        this.lowerJaw = this.head.getChild("lower_jaw");
        this.abdomen = root.getChild("abdomen");
        this.spine1 = this.abdomen.getChild("spine1");
        this.spine2 = this.abdomen.getChild("spine2");
        this.spine3 = this.abdomen.getChild("spine3");
        this.spine4 = this.abdomen.getChild("spine4");
        this.spine5 = this.abdomen.getChild("spine5");
        this.spine6 = this.abdomen.getChild("spine6");
        this.rightHindLeg = root.getChild("right_hind_leg");
        this.leftHindLeg = root.getChild("left_hind_leg");
        this.rightMiddleHindLeg = root.getChild("right_middle_hind_leg");
        this.leftMiddleHindLeg = root.getChild("left_middle_hind_leg");
        this.rightMiddleFrontLeg = root.getChild("right_middle_front_leg");
        this.leftMiddleFrontLeg = root.getChild("left_middle_front_leg");
        this.rightFrontLeg = root.getChild("right_front_leg");
        this.leftFrontLeg = root.getChild("left_front_leg");
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();

        // --- Cephalothorax and abdomen: vanilla's two body boxes, on vanilla's pivots -----------------
        root.addOrReplaceChild("thorax",
                CubeListBuilder.create().texOffs(45, 0).addBox(-3.0f, -3.0f, -3.0f, 6.0f, 6.0f, 6.0f),
                PartPose.offset(0.0f, 15.0f, 0.0f));

        PartDefinition abdomen = root.addOrReplaceChild("abdomen",
                CubeListBuilder.create().texOffs(0, 0).addBox(-5.0f, -4.0f, -6.0f, 10.0f, 8.0f, 12.0f),
                PartPose.offset(0.0f, 15.0f, 9.0f));

        // Three pairs of spines off the abdomen's back, the tallest things on the model on purpose:
        // they are what a burrowed Lurker still shows above the ground. The middle pair is the
        // longest, so the rack reads as a ridge rather than a fence.
        abdomen.addOrReplaceChild("spine1",
                CubeListBuilder.create().texOffs(96, 21).addBox(-0.8f, -5.0f, -0.8f, 1.6f, 5.0f, 1.6f),
                PartPose.offsetAndRotation(1.7f, -4.0f, -3.6f, -0.36f, 0.0f, 0.16f));
        abdomen.addOrReplaceChild("spine2",
                CubeListBuilder.create().texOffs(104, 21).addBox(-0.8f, -5.0f, -0.8f, 1.6f, 5.0f, 1.6f),
                PartPose.offsetAndRotation(-1.7f, -4.0f, -3.6f, -0.36f, 0.0f, -0.16f));
        abdomen.addOrReplaceChild("spine3",
                CubeListBuilder.create().texOffs(22, 21).addBox(-0.8f, -5.8f, -0.8f, 1.6f, 5.8f, 1.6f),
                PartPose.offsetAndRotation(2.0f, -4.0f, 0.2f, -0.30f, 0.0f, 0.20f));
        abdomen.addOrReplaceChild("spine4",
                CubeListBuilder.create().texOffs(30, 21).addBox(-0.8f, -5.8f, -0.8f, 1.6f, 5.8f, 1.6f),
                PartPose.offsetAndRotation(-2.0f, -4.0f, 0.2f, -0.30f, 0.0f, -0.20f));
        abdomen.addOrReplaceChild("spine5",
                CubeListBuilder.create().texOffs(112, 21).addBox(-0.75f, -4.6f, -0.75f, 1.5f, 4.6f, 1.5f),
                PartPose.offsetAndRotation(1.6f, -3.8f, 4.0f, -0.24f, 0.0f, 0.22f));
        abdomen.addOrReplaceChild("spine6",
                CubeListBuilder.create().texOffs(119, 21).addBox(-0.75f, -4.6f, -0.75f, 1.5f, 4.6f, 1.5f),
                PartPose.offsetAndRotation(-1.6f, -3.8f, 4.0f, -0.24f, 0.0f, -0.22f));

        // --- Head: the Hydralisk's, on the spider's own head pivot ------------------------------------
        // Vanilla puts an 8x8x8 skull at (0, 15, -3) and hangs nothing else off it, so the whole
        // Hydralisk head assembly goes on that same pivot and the swap stays a swap, not a re-rig.
        PartDefinition head = root.addOrReplaceChild("head",
                CubeListBuilder.create().texOffs(70, 0).addBox(-2.9f, -4.0f, -4.6f, 5.8f, 5.2f, 5.0f),
                PartPose.offset(0.0f, 15.0f, -3.0f));
        head.addOrReplaceChild("brow_ridge",
                CubeListBuilder.create().texOffs(0, 30).addBox(-2.1f, -4.8f, -4.2f, 4.2f, 1.0f, 4.4f),
                PartPose.ZERO);
        // Standing proud of the cranium's front face, so the lit pixels read from the side too.
        head.addOrReplaceChild("eye_left",
                CubeListBuilder.create().texOffs(61, 47).addBox(-0.65f, -0.65f, -0.4f, 1.3f, 1.3f, 0.8f),
                PartPose.offset(1.75f, -2.1f, -4.75f));
        head.addOrReplaceChild("eye_right",
                CubeListBuilder.create().texOffs(67, 47).addBox(-0.65f, -0.65f, -0.4f, 1.3f, 1.3f, 0.8f),
                PartPose.offset(-1.75f, -2.1f, -4.75f));

        // The bone frill is what makes the head read as Hydralisk at distance, with a horn hooking
        // back off each upper corner. Shorter than the Hydralisk's, because that model rears and this
        // one crouches on a spider's frame — the frill has a whole block less headroom to live in.
        PartDefinition hood = head.addOrReplaceChild("hood",
                CubeListBuilder.create().texOffs(54, 21).addBox(-4.6f, -4.5f, -0.9f, 9.2f, 4.5f, 1.8f),
                PartPose.offsetAndRotation(0.0f, -0.8f, 1.8f, -0.75f, 0.0f, 0.0f));
        hood.addOrReplaceChild("hood_horn_left",
                CubeListBuilder.create().texOffs(45, 30).addBox(-0.8f, -2.6f, -0.8f, 1.6f, 2.6f, 1.6f),
                PartPose.offsetAndRotation(3.4f, -4.2f, 0.0f, 0.55f, 0.0f, 0.15f));
        hood.addOrReplaceChild("hood_horn_right",
                CubeListBuilder.create().texOffs(53, 30).addBox(-0.8f, -2.6f, -0.8f, 1.6f, 2.6f, 1.6f),
                PartPose.offsetAndRotation(-3.4f, -4.2f, 0.0f, 0.55f, 0.0f, -0.15f));
        // Raked far enough back that they sweep over the thorax rather than standing up off the skull:
        // upright they would be the tallest thing on the model and steal the burrowed silhouette from
        // the back spines, which is the one thing that has to own it.
        head.addOrReplaceChild("crest_spine_left",
                CubeListBuilder.create().texOffs(38, 21).addBox(-0.8f, -6.0f, -0.8f, 1.6f, 6.0f, 1.6f),
                PartPose.offsetAndRotation(1.9f, -3.6f, 0.8f, -1.55f, 0.0f, 0.18f));
        head.addOrReplaceChild("crest_spine_right",
                CubeListBuilder.create().texOffs(46, 21).addBox(-0.8f, -6.0f, -0.8f, 1.6f, 6.0f, 1.6f),
                PartPose.offsetAndRotation(-1.9f, -3.6f, 0.8f, -1.55f, 0.0f, -0.18f));

        // The jaws hinge from two different points on the skull so they part around the fangs instead
        // of scissoring flat, and the lower one rests visibly gaping — a closed mouth hides every
        // tooth, and the maw is the whole point of the silhouette.
        PartDefinition upperJaw = head.addOrReplaceChild("upper_jaw",
                CubeListBuilder.create().texOffs(93, 0).addBox(-2.6f, -1.8f, -6.0f, 5.2f, 3.6f, 6.0f),
                PartPose.offsetAndRotation(0.0f, -1.6f, -4.2f, 0.10f, 0.0f, 0.0f));
        upperJaw.addOrReplaceChild("snout_ridge",
                CubeListBuilder.create().texOffs(77, 21).addBox(-1.5f, -1.0f, -5.6f, 3.0f, 1.1f, 5.6f),
                PartPose.offset(0.0f, -1.8f, 0.0f));
        upperJaw.addOrReplaceChild("fang_upper_left",
                CubeListBuilder.create().texOffs(49, 47).addBox(-0.6f, 0.0f, -0.6f, 1.2f, 2.6f, 1.2f),
                PartPose.offsetAndRotation(1.75f, 1.6f, -5.2f, 0.10f, 0.0f, 0.0f));
        upperJaw.addOrReplaceChild("fang_upper_right",
                CubeListBuilder.create().texOffs(55, 47).addBox(-0.6f, 0.0f, -0.6f, 1.2f, 2.6f, 1.2f),
                PartPose.offsetAndRotation(-1.75f, 1.6f, -5.2f, 0.10f, 0.0f, 0.0f));

        PartDefinition lowerJaw = head.addOrReplaceChild("lower_jaw",
                CubeListBuilder.create().texOffs(0, 21).addBox(-2.3f, -1.2f, -5.6f, 4.6f, 2.4f, 5.6f),
                PartPose.offsetAndRotation(0.0f, 0.8f, -3.6f, 0.85f, 0.0f, 0.0f));
        lowerJaw.addOrReplaceChild("fang_lower_left",
                CubeListBuilder.create().texOffs(37, 47).addBox(-0.55f, -2.4f, -0.55f, 1.1f, 2.4f, 1.1f),
                PartPose.offsetAndRotation(1.6f, -1.2f, -4.6f, -0.08f, 0.0f, 0.0f));
        lowerJaw.addOrReplaceChild("fang_lower_right",
                CubeListBuilder.create().texOffs(43, 47).addBox(-0.55f, -2.4f, -0.55f, 1.1f, 2.4f, 1.1f),
                PartPose.offsetAndRotation(-1.6f, -1.2f, -4.6f, -0.08f, 0.0f, 0.0f));
        // Mandible spars running outboard of the teeth, splayed out and tipped down.
        lowerJaw.addOrReplaceChild("mandible_left",
                CubeListBuilder.create().texOffs(19, 30).addBox(-0.7f, -0.7f, -4.2f, 1.4f, 1.4f, 4.2f),
                PartPose.offsetAndRotation(2.3f, -0.2f, -3.0f, 0.20f, 0.0f, 0.30f));
        lowerJaw.addOrReplaceChild("mandible_right",
                CubeListBuilder.create().texOffs(32, 30).addBox(-0.7f, -0.7f, -4.2f, 1.4f, 1.4f, 4.2f),
                PartPose.offsetAndRotation(-2.3f, -0.2f, -3.0f, 0.20f, 0.0f, -0.30f));

        // --- Legs: vanilla's eight, each one 16-texel segment on vanilla's own pivot and angles -------
        root.addOrReplaceChild("right_hind_leg",
                CubeListBuilder.create().texOffs(37, 42).addBox(-15.0f, -1.0f, -1.0f, 16.0f, 2.0f, 2.0f),
                PartPose.offsetAndRotation(-4.0f, 15.0f, 2.0f, 0.0f, LEG_YAW_WIDE, -LEG_ROLL_WIDE));
        root.addOrReplaceChild("left_hind_leg",
                CubeListBuilder.create().texOffs(0, 37).addBox(-1.0f, -1.0f, -1.0f, 16.0f, 2.0f, 2.0f),
                PartPose.offsetAndRotation(4.0f, 15.0f, 2.0f, 0.0f, -LEG_YAW_WIDE, LEG_ROLL_WIDE));
        root.addOrReplaceChild("right_middle_hind_leg",
                CubeListBuilder.create().texOffs(0, 47).addBox(-15.0f, -1.0f, -1.0f, 16.0f, 2.0f, 2.0f),
                PartPose.offsetAndRotation(-4.0f, 15.0f, 1.0f, 0.0f, LEG_YAW_NARROW, -LEG_ROLL_NARROW));
        root.addOrReplaceChild("left_middle_hind_leg",
                CubeListBuilder.create().texOffs(74, 37).addBox(-1.0f, -1.0f, -1.0f, 16.0f, 2.0f, 2.0f),
                PartPose.offsetAndRotation(4.0f, 15.0f, 1.0f, 0.0f, -LEG_YAW_NARROW, LEG_ROLL_NARROW));
        root.addOrReplaceChild("right_middle_front_leg",
                CubeListBuilder.create().texOffs(74, 42).addBox(-15.0f, -1.0f, -1.0f, 16.0f, 2.0f, 2.0f),
                PartPose.offsetAndRotation(-4.0f, 15.0f, 0.0f, 0.0f, -LEG_YAW_NARROW, -LEG_ROLL_NARROW));
        root.addOrReplaceChild("left_middle_front_leg",
                CubeListBuilder.create().texOffs(37, 37).addBox(-1.0f, -1.0f, -1.0f, 16.0f, 2.0f, 2.0f),
                PartPose.offsetAndRotation(4.0f, 15.0f, 0.0f, 0.0f, LEG_YAW_NARROW, LEG_ROLL_NARROW));
        root.addOrReplaceChild("right_front_leg",
                CubeListBuilder.create().texOffs(0, 42).addBox(-15.0f, -1.0f, -1.0f, 16.0f, 2.0f, 2.0f),
                PartPose.offsetAndRotation(-4.0f, 15.0f, -1.0f, 0.0f, -LEG_YAW_WIDE, -LEG_ROLL_WIDE));
        root.addOrReplaceChild("left_front_leg",
                CubeListBuilder.create().texOffs(61, 30).addBox(-1.0f, -1.0f, -1.0f, 16.0f, 2.0f, 2.0f),
                PartPose.offsetAndRotation(4.0f, 15.0f, -1.0f, 0.0f, LEG_YAW_WIDE, LEG_ROLL_WIDE));

        return LayerDefinition.create(mesh, 128, 128);
    }

    @Override
    public void setupAnim(LurkerRenderState state) {
        // super restores every part to its baked pose first, so everything below stacks as a delta.
        super.setupAnim(state);

        headLook(state);
        idleBreath(state);
        walkGait(state);
        strike(state);
    }

    /** The head hangs off the root, the way vanilla's spider's does, so it carries the whole look. */
    private void headLook(LurkerRenderState state) {
        this.head.yRot += state.yRot * Mth.DEG_TO_RAD;
        this.head.xRot += state.xRot * Mth.DEG_TO_RAD;
    }

    /** A slow swell through the abdomen, with the spine rack and the jaw riding a beat behind it. */
    private void idleBreath(LurkerRenderState state) {
        float breath = Mth.sin(state.ageInTicks * IDLE_RATE);
        float lag = Mth.sin(state.ageInTicks * IDLE_RATE - 0.6f);
        this.abdomen.xRot += breath * 0.045f;
        this.spine1.xRot -= lag * 0.09f;
        this.spine2.xRot -= lag * 0.09f;
        this.spine3.xRot -= lag * 0.11f;
        this.spine4.xRot -= lag * 0.11f;
        this.spine5.xRot -= lag * 0.07f;
        this.spine6.xRot -= lag * 0.07f;
        this.lowerJaw.xRot += breath * 0.06f;
    }

    /**
     * Vanilla's spider gait verbatim: the four phases are a quarter-cycle apart, so opposite legs are
     * always out of step, and each leg swings in yaw while lifting in roll.
     */
    private void walkGait(LurkerRenderState state) {
        float pos = state.walkAnimationPos * 0.6662f;
        float speed = state.walkAnimationSpeed;

        float swingHind = -(Mth.cos(pos * 2.0f) * 0.4f) * speed;
        float swingMidHind = -(Mth.cos(pos * 2.0f + Mth.PI) * 0.4f) * speed;
        float swingMidFront = -(Mth.cos(pos * 2.0f + Mth.HALF_PI) * 0.4f) * speed;
        float swingFront = -(Mth.cos(pos * 2.0f + Mth.PI + Mth.HALF_PI) * 0.4f) * speed;

        float stepHind = Math.abs(Mth.sin(pos) * 0.4f) * speed;
        float stepMidHind = Math.abs(Mth.sin(pos + Mth.PI) * 0.4f) * speed;
        float stepMidFront = Math.abs(Mth.sin(pos + Mth.HALF_PI) * 0.4f) * speed;
        float stepFront = Math.abs(Mth.sin(pos + Mth.PI + Mth.HALF_PI) * 0.4f) * speed;

        this.rightHindLeg.yRot += swingHind;
        this.leftHindLeg.yRot -= swingHind;
        this.rightMiddleHindLeg.yRot += swingMidHind;
        this.leftMiddleHindLeg.yRot -= swingMidHind;
        this.rightMiddleFrontLeg.yRot += swingMidFront;
        this.leftMiddleFrontLeg.yRot -= swingMidFront;
        this.rightFrontLeg.yRot += swingFront;
        this.leftFrontLeg.yRot -= swingFront;

        this.rightHindLeg.zRot += stepHind;
        this.leftHindLeg.zRot -= stepHind;
        this.rightMiddleHindLeg.zRot += stepMidHind;
        this.leftMiddleHindLeg.zRot -= stepMidHind;
        this.rightMiddleFrontLeg.zRot += stepMidFront;
        this.leftMiddleFrontLeg.zRot -= stepMidFront;
        this.rightFrontLeg.zRot += stepFront;
        this.leftFrontLeg.zRot -= stepFront;
    }

    /**
     * The volley: the head hunches down over the ground, the spine rack folds flat, and then the
     * abdomen kicks as the row goes out. It is a shove into the earth rather than a lunge forward —
     * the spines come up somewhere else, and nothing about the Lurker moves toward its target.
     */
    private void strike(LurkerRenderState state) {
        float progress = state.attackProgress;
        if (progress <= 0.0f) {
            return;
        }
        float windup = progress < WINDUP_END ? progress / WINDUP_END : 0.0f;
        float launch = progress < WINDUP_END ? 0.0f : (progress - WINDUP_END) / (1.0f - WINDUP_END);
        float kick = Mth.sin(launch * Mth.PI);

        this.head.xRot += windup * 0.20f - kick * 0.30f;
        this.lowerJaw.xRot += windup * 0.15f + kick * 0.55f;
        this.abdomen.xRot += windup * 0.16f - kick * 0.22f;

        // Flattened during the wind-up, flung back as the row erupts.
        float rack = -windup * 0.35f + kick * 0.75f;
        this.spine1.xRot += rack;
        this.spine2.xRot += rack;
        this.spine3.xRot += rack;
        this.spine4.xRot += rack;
        this.spine5.xRot += rack * 0.8f;
        this.spine6.xRot += rack * 0.8f;
    }
}
