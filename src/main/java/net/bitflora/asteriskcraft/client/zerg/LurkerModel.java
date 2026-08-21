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
 * Hand-authored geometry for the Lurker: a vanilla Spider's frame carrying a Hydralisk's head.
 *
 * <p>Read front to back — a maw on a short neck nosing at the ground; a plated thorax; a long
 * abdomen behind it carrying three pairs of raked spines; and eight two-segment legs whose knees
 * stand well above the body, the way an arachnid's do and the way the reference art draws them. The
 * legs are the reason the silhouette is far wider than the 0.9-block hitbox: like the Ultralisk's,
 * the model deliberately overhangs, because a hitbox at the true width would make the unit two
 * pathfinding nodes across and unable to fit a one-block gap.
 *
 * <p>The head is {@code HydraliskModel}'s, transplanted and set low: the same short cranium under a
 * broad bone hood, the same brow, the same gaping upper and lower jaws around interlocking fangs and
 * mandible spars. It is the one part of the animal a player has to recognise, since everything else
 * about the shape is spider.
 *
 * <p><b>What stays above the surface.</b> The renderer sinks the whole model by a block while the
 * Lurker is burrowed and lets the terrain clip it (see {@code LurkerRenderer.scale}), so the geometry
 * alone decides what a buried one looks like — there is no second model. The animal stands 20 pixels
 * tall, so a 16-pixel sink leaves the top four: the hood horns, the crest, and the three pairs of
 * back spines, in that order, which reads as a low fringe of spikes with a fin a block ahead of it.
 * Shortening any of those changes what a burrowed Lurker is; lengthening anything else can steal the
 * silhouette from them.
 *
 * <p>Measured against the 0.9 x 1.2 hitbox the model is 1.22 wide, 1.25 tall and 2.10 deep. Only the
 * height is a real constraint (and it is met); the width and depth overhang deliberately and by less
 * than the Dragoon's and the Hydralisk's do, because a hitbox at the true silhouette would cost the
 * unit a second pathfinding node and with it the ability to walk through a one-block gap.
 *
 * <p>Authored in true pixel space (16px = 1 block), "up" is negative y, ground is y=24, and colour
 * comes from {@code textures/entity/lurker.png}, in which <b>every cube owns its own UV island</b> so
 * it can be hand-painted independently — see {@code tools/blockbench_export.py}, which packs those
 * islands and emits the Blockbench project. That tooling also constrains how this class may be
 * written: one {@code texOffs} literal per cube, builders inlined into their
 * {@code addOrReplaceChild} call, and globally unique part names — which is why the sixteen leg
 * segments are written out one by one instead of looped, and why no two of them share a builder.
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

    private final ModelPart thorax;
    private final ModelPart abdomen;
    private final ModelPart neck;
    private final ModelPart head;
    private final ModelPart lowerJaw;
    private final ModelPart spine1;
    private final ModelPart spine2;
    private final ModelPart spine3;
    private final ModelPart spine4;
    private final ModelPart spine5;
    private final ModelPart spine6;
    private final ModelPart legRightHind;
    private final ModelPart legLeftHind;
    private final ModelPart legRightMidHind;
    private final ModelPart legLeftMidHind;
    private final ModelPart legRightMidFront;
    private final ModelPart legLeftMidFront;
    private final ModelPart legRightFront;
    private final ModelPart legLeftFront;

    public LurkerModel(ModelPart root) {
        super(root);
        this.thorax = root.getChild("thorax");
        this.abdomen = this.thorax.getChild("abdomen");
        this.neck = this.thorax.getChild("neck");
        this.head = this.neck.getChild("head");
        this.lowerJaw = this.head.getChild("lower_jaw");
        this.spine1 = this.abdomen.getChild("spine1");
        this.spine2 = this.abdomen.getChild("spine2");
        this.spine3 = this.abdomen.getChild("spine3");
        this.spine4 = this.abdomen.getChild("spine4");
        this.spine5 = this.abdomen.getChild("spine5");
        this.spine6 = this.abdomen.getChild("spine6");
        this.legRightHind = root.getChild("leg_right_hind");
        this.legLeftHind = root.getChild("leg_left_hind");
        this.legRightMidHind = root.getChild("leg_right_mid_hind");
        this.legLeftMidHind = root.getChild("leg_left_mid_hind");
        this.legRightMidFront = root.getChild("leg_right_mid_front");
        this.legLeftMidFront = root.getChild("leg_left_mid_front");
        this.legRightFront = root.getChild("leg_right_front");
        this.legLeftFront = root.getChild("leg_left_front");
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();

        // --- Thorax: the plated middle the legs and the neck both hang off ----------------------------
        PartDefinition thorax = root.addOrReplaceChild("thorax",
                CubeListBuilder.create().texOffs(41, 0).addBox(-3.5f, -3.5f, -4.0f, 7.0f, 7.0f, 8.0f),
                PartPose.offset(0.0f, 15.5f, -1.0f));
        thorax.addOrReplaceChild("carapace",
                CubeListBuilder.create().texOffs(44, 21).addBox(-3.0f, -4.4f, -3.4f, 6.0f, 1.2f, 6.8f),
                PartPose.ZERO);

        // --- Abdomen: the long rear, tipped nose-up so the spine rack rakes back ----------------------
        PartDefinition abdomen = thorax.addOrReplaceChild("abdomen",
                CubeListBuilder.create().texOffs(0, 0).addBox(-4.5f, -4.2f, -5.5f, 9.0f, 8.4f, 11.0f),
                PartPose.offsetAndRotation(0.0f, -0.3f, 8.5f, -0.10f, 0.0f, 0.0f));
        abdomen.addOrReplaceChild("abdomen_plate",
                CubeListBuilder.create().texOffs(72, 0).addBox(-3.0f, -5.2f, -4.5f, 6.0f, 1.2f, 9.0f),
                PartPose.ZERO);

        // Three pairs of spines, the tallest things on the model on purpose: they are what a burrowed
        // Lurker still shows above the ground. The middle pair is the longest, so the rack reads as a
        // ridge rather than a fence.
        abdomen.addOrReplaceChild("spine1",
                CubeListBuilder.create().texOffs(42, 32).addBox(-0.8f, -5.0f, -0.8f, 1.6f, 5.0f, 1.6f),
                PartPose.offsetAndRotation(1.7f, -4.6f, -3.6f, -0.36f, 0.0f, 0.16f));
        abdomen.addOrReplaceChild("spine2",
                CubeListBuilder.create().texOffs(50, 32).addBox(-0.8f, -5.0f, -0.8f, 1.6f, 5.0f, 1.6f),
                PartPose.offsetAndRotation(-1.7f, -4.6f, -3.6f, -0.36f, 0.0f, -0.16f));
        abdomen.addOrReplaceChild("spine3",
                CubeListBuilder.create().texOffs(93, 21).addBox(-0.8f, -5.8f, -0.8f, 1.6f, 5.8f, 1.6f),
                PartPose.offsetAndRotation(2.0f, -4.6f, 0.2f, -0.30f, 0.0f, 0.20f));
        abdomen.addOrReplaceChild("spine4",
                CubeListBuilder.create().texOffs(101, 21).addBox(-0.8f, -5.8f, -0.8f, 1.6f, 5.8f, 1.6f),
                PartPose.offsetAndRotation(-2.0f, -4.6f, 0.2f, -0.30f, 0.0f, -0.20f));
        abdomen.addOrReplaceChild("spine5",
                CubeListBuilder.create().texOffs(58, 32).addBox(-0.75f, -4.6f, -0.75f, 1.5f, 4.6f, 1.5f),
                PartPose.offsetAndRotation(1.6f, -4.2f, 4.0f, -0.24f, 0.0f, 0.22f));
        abdomen.addOrReplaceChild("spine6",
                CubeListBuilder.create().texOffs(65, 32).addBox(-0.75f, -4.6f, -0.75f, 1.5f, 4.6f, 1.5f),
                PartPose.offsetAndRotation(-1.6f, -4.2f, 4.0f, -0.24f, 0.0f, -0.22f));

        // --- Neck: pitched hard forward, so the head noses at the ground instead of rearing -----------
        // Positive pitch tips a part's up-axis forward, so most of this box's length is reach rather
        // than height — which is what keeps the whole animal under 1.2 blocks tall.
        PartDefinition neck = thorax.addOrReplaceChild("neck",
                CubeListBuilder.create().texOffs(24, 21).addBox(-2.3f, -4.6f, -2.3f, 4.6f, 4.6f, 4.6f),
                PartPose.offsetAndRotation(0.0f, -1.8f, -4.0f, 1.35f, 0.0f, 0.0f));

        // --- Head: the Hydralisk's, transplanted whole ------------------------------------------------
        PartDefinition head = neck.addOrReplaceChild("head",
                CubeListBuilder.create().texOffs(103, 0).addBox(-2.9f, -4.0f, -4.6f, 5.8f, 5.2f, 5.0f),
                PartPose.offsetAndRotation(0.0f, -4.6f, 0.0f, -0.60f, 0.0f, 0.0f));
        head.addOrReplaceChild("brow_ridge",
                CubeListBuilder.create().texOffs(72, 32).addBox(-2.1f, -4.8f, -4.2f, 4.2f, 1.0f, 4.4f),
                PartPose.ZERO);
        // Standing proud of the cranium's front face, so the lit pixels read from the side too.
        head.addOrReplaceChild("eye_left",
                CubeListBuilder.create().texOffs(41, 56).addBox(-0.65f, -0.65f, -0.4f, 1.3f, 1.3f, 0.8f),
                PartPose.offset(1.75f, -2.1f, -4.75f));
        head.addOrReplaceChild("eye_right",
                CubeListBuilder.create().texOffs(47, 56).addBox(-0.65f, -0.65f, -0.4f, 1.3f, 1.3f, 0.8f),
                PartPose.offset(-1.75f, -2.1f, -4.75f));

        // The bone frill is what makes the head read as Hydralisk at distance, with a horn hooking
        // back off each upper corner. Shorter than the Hydralisk's, because that model rears and this
        // one crouches — the frill has a whole block less headroom to live in.
        PartDefinition hood = head.addOrReplaceChild("hood",
                CubeListBuilder.create().texOffs(0, 32).addBox(-4.6f, -4.5f, -0.9f, 9.2f, 4.5f, 1.8f),
                PartPose.offsetAndRotation(0.0f, -0.8f, 1.8f, -0.75f, 0.0f, 0.0f));
        hood.addOrReplaceChild("hood_horn_left",
                CubeListBuilder.create().texOffs(117, 32).addBox(-0.8f, -2.6f, -0.8f, 1.6f, 2.6f, 1.6f),
                PartPose.offsetAndRotation(3.4f, -4.2f, 0.0f, 0.55f, 0.0f, 0.15f));
        hood.addOrReplaceChild("hood_horn_right",
                CubeListBuilder.create().texOffs(0, 40).addBox(-0.8f, -2.6f, -0.8f, 1.6f, 2.6f, 1.6f),
                PartPose.offsetAndRotation(-3.4f, -4.2f, 0.0f, 0.55f, 0.0f, -0.15f));
        // Raked far enough back that they sweep over the thorax rather than standing up off the skull:
        // upright they would be the tallest thing on the model and steal the burrowed silhouette from
        // the back spines, which is the one thing that has to own it.
        head.addOrReplaceChild("crest_spine_left",
                CubeListBuilder.create().texOffs(109, 21).addBox(-0.8f, -6.0f, -0.8f, 1.6f, 6.0f, 1.6f),
                PartPose.offsetAndRotation(1.9f, -3.6f, 0.8f, -1.55f, 0.0f, 0.18f));
        head.addOrReplaceChild("crest_spine_right",
                CubeListBuilder.create().texOffs(117, 21).addBox(-0.8f, -6.0f, -0.8f, 1.6f, 6.0f, 1.6f),
                PartPose.offsetAndRotation(-1.9f, -3.6f, 0.8f, -1.55f, 0.0f, -0.18f));

        // The jaws hinge from two different points on the skull so they part around the fangs instead
        // of scissoring flat, and the lower one rests visibly gaping — a closed mouth hides every
        // tooth, and the maw is the whole point of the silhouette.
        PartDefinition upperJaw = head.addOrReplaceChild("upper_jaw",
                CubeListBuilder.create().texOffs(0, 21).addBox(-2.6f, -1.8f, -6.0f, 5.2f, 3.6f, 6.0f),
                PartPose.offsetAndRotation(0.0f, -1.6f, -4.2f, 0.10f, 0.0f, 0.0f));
        upperJaw.addOrReplaceChild("snout_ridge",
                CubeListBuilder.create().texOffs(23, 32).addBox(-1.5f, -1.0f, -5.6f, 3.0f, 1.1f, 5.6f),
                PartPose.offset(0.0f, -1.8f, 0.0f));
        upperJaw.addOrReplaceChild("fang_upper_left",
                CubeListBuilder.create().texOffs(29, 56).addBox(-0.6f, 0.0f, -0.6f, 1.2f, 2.6f, 1.2f),
                PartPose.offsetAndRotation(1.75f, 1.6f, -5.2f, 0.10f, 0.0f, 0.0f));
        upperJaw.addOrReplaceChild("fang_upper_right",
                CubeListBuilder.create().texOffs(35, 56).addBox(-0.6f, 0.0f, -0.6f, 1.2f, 2.6f, 1.2f),
                PartPose.offsetAndRotation(-1.75f, 1.6f, -5.2f, 0.10f, 0.0f, 0.0f));

        PartDefinition lowerJaw = head.addOrReplaceChild("lower_jaw",
                CubeListBuilder.create().texOffs(71, 21).addBox(-2.3f, -1.2f, -5.6f, 4.6f, 2.4f, 5.6f),
                PartPose.offsetAndRotation(0.0f, 0.8f, -3.6f, 0.85f, 0.0f, 0.0f));
        lowerJaw.addOrReplaceChild("fang_lower_left",
                CubeListBuilder.create().texOffs(17, 56).addBox(-0.55f, -2.4f, -0.55f, 1.1f, 2.4f, 1.1f),
                PartPose.offsetAndRotation(1.6f, -1.2f, -4.6f, -0.08f, 0.0f, 0.0f));
        lowerJaw.addOrReplaceChild("fang_lower_right",
                CubeListBuilder.create().texOffs(23, 56).addBox(-0.55f, -2.4f, -0.55f, 1.1f, 2.4f, 1.1f),
                PartPose.offsetAndRotation(-1.6f, -1.2f, -4.6f, -0.08f, 0.0f, 0.0f));
        // Mandible spars running outboard of the teeth, splayed out and tipped down.
        lowerJaw.addOrReplaceChild("mandible_left",
                CubeListBuilder.create().texOffs(91, 32).addBox(-0.7f, -0.7f, -4.2f, 1.4f, 1.4f, 4.2f),
                PartPose.offsetAndRotation(2.3f, -0.2f, -3.0f, 0.20f, 0.0f, 0.30f));
        lowerJaw.addOrReplaceChild("mandible_right",
                CubeListBuilder.create().texOffs(104, 32).addBox(-0.7f, -0.7f, -4.2f, 1.4f, 1.4f, 4.2f),
                PartPose.offsetAndRotation(-2.3f, -0.2f, -3.0f, 0.20f, 0.0f, -0.30f));

        // --- Legs: eight, each an upper segment kicking up and out to a knee above the body and a -----
        // longer lower segment dropping from it to the ground. Written out one at a time rather than
        // looped, because the export tool needs one editable texOffs literal per cube (docs/texturing.md).
        PartDefinition legRightHind = root.addOrReplaceChild("leg_right_hind",
                CubeListBuilder.create().texOffs(85, 51).addBox(-5.8f, -1.0f, -1.0f, 5.8f, 2.0f, 2.0f),
                PartPose.offsetAndRotation(-2.8f, 15.0f, 3.5f, 0.0f, 0.7854f, 0.735f));
        legRightHind.addOrReplaceChild("shin_right_hind",
                CubeListBuilder.create().texOffs(30, 46).addBox(-12.4f, -0.9f, -0.9f, 12.4f, 1.8f, 1.8f),
                PartPose.offsetAndRotation(-5.8f, 0.0f, 0.0f, 0.0f, 0.0f, -2.160f));

        PartDefinition legLeftHind = root.addOrReplaceChild("leg_left_hind",
                CubeListBuilder.create().texOffs(17, 51).addBox(0.0f, -1.0f, -1.0f, 5.8f, 2.0f, 2.0f),
                PartPose.offsetAndRotation(2.8f, 15.0f, 3.5f, 0.0f, -0.7854f, -0.735f));
        legLeftHind.addOrReplaceChild("shin_left_hind",
                CubeListBuilder.create().texOffs(38, 40).addBox(0.0f, -0.9f, -0.9f, 12.4f, 1.8f, 1.8f),
                PartPose.offsetAndRotation(5.8f, 0.0f, 0.0f, 0.0f, 0.0f, 2.160f));

        PartDefinition legRightMidHind = root.addOrReplaceChild("leg_right_mid_hind",
                CubeListBuilder.create().texOffs(0, 56).addBox(-5.8f, -1.0f, -1.0f, 5.8f, 2.0f, 2.0f),
                PartPose.offsetAndRotation(-2.8f, 15.0f, 1.5f, 0.0f, 0.3927f, 0.700f));
        legRightMidHind.addOrReplaceChild("shin_right_mid_hind",
                CubeListBuilder.create().texOffs(90, 46).addBox(-12.4f, -0.9f, -0.9f, 12.4f, 1.8f, 1.8f),
                PartPose.offsetAndRotation(-5.8f, 0.0f, 0.0f, 0.0f, 0.0f, -2.125f));

        PartDefinition legLeftMidHind = root.addOrReplaceChild("leg_left_mid_hind",
                CubeListBuilder.create().texOffs(51, 51).addBox(0.0f, -1.0f, -1.0f, 5.8f, 2.0f, 2.0f),
                PartPose.offsetAndRotation(2.8f, 15.0f, 1.5f, 0.0f, -0.3927f, -0.700f));
        legLeftMidHind.addOrReplaceChild("shin_left_mid_hind",
                CubeListBuilder.create().texOffs(98, 40).addBox(0.0f, -0.9f, -0.9f, 12.4f, 1.8f, 1.8f),
                PartPose.offsetAndRotation(5.8f, 0.0f, 0.0f, 0.0f, 0.0f, 2.125f));

        PartDefinition legRightMidFront = root.addOrReplaceChild("leg_right_mid_front",
                CubeListBuilder.create().texOffs(102, 51).addBox(-5.8f, -1.0f, -1.0f, 5.8f, 2.0f, 2.0f),
                PartPose.offsetAndRotation(-2.8f, 15.0f, -0.5f, 0.0f, -0.3927f, 0.700f));
        legRightMidFront.addOrReplaceChild("shin_right_mid_front",
                CubeListBuilder.create().texOffs(60, 46).addBox(-12.4f, -0.9f, -0.9f, 12.4f, 1.8f, 1.8f),
                PartPose.offsetAndRotation(-5.8f, 0.0f, 0.0f, 0.0f, 0.0f, -2.125f));

        PartDefinition legLeftMidFront = root.addOrReplaceChild("leg_left_mid_front",
                CubeListBuilder.create().texOffs(34, 51).addBox(0.0f, -1.0f, -1.0f, 5.8f, 2.0f, 2.0f),
                PartPose.offsetAndRotation(2.8f, 15.0f, -0.5f, 0.0f, 0.3927f, -0.700f));
        legLeftMidFront.addOrReplaceChild("shin_left_mid_front",
                CubeListBuilder.create().texOffs(68, 40).addBox(0.0f, -0.9f, -0.9f, 12.4f, 1.8f, 1.8f),
                PartPose.offsetAndRotation(5.8f, 0.0f, 0.0f, 0.0f, 0.0f, 2.125f));

        PartDefinition legRightFront = root.addOrReplaceChild("leg_right_front",
                CubeListBuilder.create().texOffs(68, 51).addBox(-5.8f, -1.0f, -1.0f, 5.8f, 2.0f, 2.0f),
                PartPose.offsetAndRotation(-2.8f, 15.0f, -2.5f, 0.0f, -0.7854f, 0.735f));
        legRightFront.addOrReplaceChild("shin_right_front",
                CubeListBuilder.create().texOffs(0, 46).addBox(-12.4f, -0.9f, -0.9f, 12.4f, 1.8f, 1.8f),
                PartPose.offsetAndRotation(-5.8f, 0.0f, 0.0f, 0.0f, 0.0f, -2.160f));

        PartDefinition legLeftFront = root.addOrReplaceChild("leg_left_front",
                CubeListBuilder.create().texOffs(0, 51).addBox(0.0f, -1.0f, -1.0f, 5.8f, 2.0f, 2.0f),
                PartPose.offsetAndRotation(2.8f, 15.0f, -2.5f, 0.0f, 0.7854f, -0.735f));
        legLeftFront.addOrReplaceChild("shin_left_front",
                CubeListBuilder.create().texOffs(8, 40).addBox(0.0f, -0.9f, -0.9f, 12.4f, 1.8f, 1.8f),
                PartPose.offsetAndRotation(5.8f, 0.0f, 0.0f, 0.0f, 0.0f, 2.160f));

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

    /** Split across the neck and the skull, so the whole front end turns rather than just the head. */
    private void headLook(LurkerRenderState state) {
        float yaw = state.yRot * Mth.DEG_TO_RAD;
        float pitch = state.xRot * Mth.DEG_TO_RAD;
        this.neck.yRot += yaw * 0.4f;
        this.head.yRot += yaw * 0.6f;
        this.neck.xRot += pitch * 0.3f;
        this.head.xRot += pitch * 0.7f;
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
     * Vanilla's spider gait, driven onto the upper leg segments only: the shins hang off them and
     * inherit the swing, which is what makes a two-segment leg fold instead of sweeping rigidly.
     * The four phases are a quarter-cycle apart, so opposite legs are always out of step.
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

        this.legRightHind.yRot += swingHind;
        this.legLeftHind.yRot -= swingHind;
        this.legRightMidHind.yRot += swingMidHind;
        this.legLeftMidHind.yRot -= swingMidHind;
        this.legRightMidFront.yRot += swingMidFront;
        this.legLeftMidFront.yRot -= swingMidFront;
        this.legRightFront.yRot += swingFront;
        this.legLeftFront.yRot -= swingFront;

        this.legRightHind.zRot -= stepHind;
        this.legLeftHind.zRot += stepHind;
        this.legRightMidHind.zRot -= stepMidHind;
        this.legLeftMidHind.zRot += stepMidHind;
        this.legRightMidFront.zRot -= stepMidFront;
        this.legLeftMidFront.zRot += stepMidFront;
        this.legRightFront.zRot -= stepFront;
        this.legLeftFront.zRot += stepFront;
    }

    /**
     * The volley: the animal hunches down over the ground, the spine rack folds flat, and then the
     * whole body kicks as the row goes out. It is a shove into the earth rather than a lunge forward —
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

        this.thorax.xRot += windup * 0.16f - kick * 0.22f;
        this.neck.xRot += windup * 0.20f - kick * 0.30f;
        this.lowerJaw.xRot += windup * 0.15f + kick * 0.55f;

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
