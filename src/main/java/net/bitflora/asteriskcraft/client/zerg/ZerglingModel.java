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
 * Hand-authored geometry for the Zergling, shaped after the reference art rather than the handful of
 * boxes this used to be. Working front to back: the <b>head is mostly maw</b> — a short crimson cranium
 * carrying two backswept horns and a pair of small lit eyes, hinged onto an upper and a lower jaw that
 * rest parted around four interlocking bone fangs; a narrow thorax under a wide shoulder plate that
 * tapers back into a taller, narrower hip plate; two pairs of backswept dorsal spines; and a short
 * whipping tail. Its signature is the pair of <b>kaiser blades</b>: three-segment bone scythes rising
 * off the shoulders and hooking forward, so their tips arch over the head and frame it exactly as they
 * do in the art. It walks on four legs — short forelimbs ahead of the shoulders, raptor-jointed hind
 * legs (femur forward, tibia back, foot flat) behind them.
 *
 * <p>Authored in true pixel space (16px = 1 block), "up" is negative y, ground is y=24, and colour
 * comes from {@code textures/entity/zergling.png}, in which <b>every cube owns its own UV island</b> so
 * it can be hand-painted independently — see {@code tools/blockbench_export.py}, which packs those
 * islands and emits the Blockbench project. That tooling also constrains how this class may be written:
 * one {@code texOffs} literal per cube, builders inlined into their {@code addOrReplaceChild} call, and
 * globally unique part names (the round-trip verifier keys parts by leaf name, so the two scythes may
 * not both be called {@code scythe}). See docs/texturing.md.
 *
 * <p>Only the eyes and the faint ember in the throat are painted into {@code zergling_glow.png}, so the
 * emissive {@link net.bitflora.asteriskcraft.client.UnitGlowLayer} lights just those; everything else
 * must stay transparent there, since that layer re-submits the whole model.
 *
 * <p>{@link #setupAnim} drives head-look, an idle breath and jaw chatter, a diagonal-pair gallop, and a
 * bite-and-slash off {@link ZerglingRenderState#attackProgress}. Everything stacks as a delta onto the
 * baked pose, since {@code super.setupAnim} restores every part first.
 */
public class ZerglingModel extends EntityModel<ZerglingRenderState> {
    private static final float DEG = (float) (Math.PI / 180.0);

    /** Radians per tick of the idle breath — a resting Zergling still twitches. */
    private static final float IDLE_RATE = 0.11f;
    /** Fraction of a strike spent gaping before the jaws snap shut. */
    private static final float GAPE_END = 0.45f;

    private final ModelPart body;
    private final ModelPart head;
    private final ModelPart lowerJaw;
    private final ModelPart tail;
    private final ModelPart tailTip;
    private final ModelPart scytheArmLeft;
    private final ModelPart scytheArmRight;
    private final ModelPart scytheBladeLeft;
    private final ModelPart scytheBladeRight;
    private final ModelPart legFrontLeft;
    private final ModelPart legFrontRight;
    private final ModelPart legBackLeft;
    private final ModelPart legBackRight;
    private final ModelPart shinFrontLeft;
    private final ModelPart shinFrontRight;
    private final ModelPart shinBackLeft;
    private final ModelPart shinBackRight;

    public ZerglingModel(ModelPart root) {
        super(root);
        this.body = root.getChild("body");
        this.head = this.body.getChild("head");
        this.lowerJaw = this.head.getChild("lower_jaw");
        this.tail = this.body.getChild("tail");
        this.tailTip = this.tail.getChild("tail_tip");
        this.scytheArmLeft = this.body.getChild("scythe_arm_left");
        this.scytheArmRight = this.body.getChild("scythe_arm_right");
        this.scytheBladeLeft = this.scytheArmLeft.getChild("scythe_blade_left");
        this.scytheBladeRight = this.scytheArmRight.getChild("scythe_blade_right");
        this.legFrontLeft = root.getChild("leg_front_left");
        this.legFrontRight = root.getChild("leg_front_right");
        this.legBackLeft = root.getChild("leg_back_left");
        this.legBackRight = root.getChild("leg_back_right");
        this.shinFrontLeft = this.legFrontLeft.getChild("shin_front_left");
        this.shinFrontRight = this.legFrontRight.getChild("shin_front_right");
        this.shinBackLeft = this.legBackLeft.getChild("shin_back_left");
        this.shinBackRight = this.legBackRight.getChild("shin_back_right");
    }

    @Override
    public void setupAnim(ZerglingRenderState state) {
        // super restores every part to its baked PartPose, so everything below adds a delta onto it.
        super.setupAnim(state);

        this.head.yRot += state.yRot * DEG;
        this.head.xRot += state.xRot * DEG;

        // Idle: the carapace breathes and the jaw works, so a standing Zergling never reads as inert.
        float idle = Mth.sin(state.ageInTicks * IDLE_RATE);
        this.body.y -= idle * 0.25f;
        this.lowerJaw.xRot += 0.10f + idle * 0.08f;
        this.tail.yRot += idle * 0.12f;
        this.tailTip.yRot += idle * 0.16f;

        // Gallop: the diagonal pairs (front-left with back-right) travel together, which is how a
        // four-legged sprinter moves and what keeps this from looking like two independent bipeds.
        float pos = state.walkAnimationPos * 0.6662f;
        float speed = state.walkAnimationSpeed;
        float swing = Mth.cos(pos) * 1.0f * speed;
        stepLeg(this.legFrontLeft, this.shinFrontLeft, swing);
        stepLeg(this.legBackRight, this.shinBackRight, swing);
        stepLeg(this.legFrontRight, this.shinFrontRight, -swing);
        stepLeg(this.legBackLeft, this.shinBackLeft, -swing);
        // The body pumps at twice leg frequency — once per diagonal pair — so the run bounds rather
        // than gliding. Legs hang off the root, not the body, so this rocks the carapace over feet
        // that stay planted. (DroneModel's idle bob does the same.)
        this.body.y -= Mth.abs(Mth.cos(pos)) * 0.7f * speed;
        this.tail.yRot += Mth.cos(pos * 0.5f) * 0.35f * speed;

        float p = state.attackProgress;
        if (p <= 0.0f) {
            return;
        }
        // Bite: the maw gapes open through the first half of the strike, then snaps shut hard. The
        // recovery is the shorter of the two, which is what makes it read as a snap and not a yawn.
        float gape = p < GAPE_END
                ? p / GAPE_END
                : Math.max(0.0f, 1.0f - (p - GAPE_END) / (1.0f - GAPE_END) * 1.6f);
        this.lowerJaw.xRot += gape * 0.55f;
        this.head.xRot += gape * 0.25f;
        // Lunge: the whole front end drives at the target and settles back.
        this.body.z -= gape * 1.2f;

        // Slash: the scythes come down and forward off the shoulders and scissor inward across the
        // body, so the two blades cross in front of the maw at the moment the jaws close on it. A
        // positive xRot tips a raised limb's up-axis forward, so the arc is positive throughout.
        float slash = Mth.sin(p * Mth.PI);
        this.scytheArmLeft.xRot += slash * 1.1f;
        this.scytheArmRight.xRot += slash * 1.1f;
        this.scytheArmLeft.zRot -= slash * 0.45f;
        this.scytheArmRight.zRot += slash * 0.45f;
        // The blades trail the shoulders slightly, which is what sells the whip through the target.
        this.scytheBladeLeft.xRot += slash * 0.35f;
        this.scytheBladeRight.xRot += slash * 0.35f;
    }

    /**
     * Applies one leg's gallop delta: a fore/aft swing at the hip, with the shin tucking under on the
     * forward half of the stroke and straightening as the foot goes back to bear weight. Legs are
     * short here, so the tuck is what gives the foot its ground clearance rather than the stride.
     */
    private static void stepLeg(ModelPart thigh, ModelPart shin, float swing) {
        thigh.xRot += swing;
        shin.xRot -= Math.max(0.0f, swing) * 0.8f;
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();

        // Each cube's texOffs points at its own packed UV island — do not hand-edit these; they are
        // assigned by tools/blockbench_export.py and guarded by ModelUvLayoutTest.
        //
        // Vertical layout, y+ pointing down: the feet plant on y=24, the shoulder plate tops out at
        // y=9.0, the dorsal spines rake back to y=6.4 and the hooked scythe tips are the apex at
        // y=4.0. That apex is what the entity's height is sized against: (24 - 4.0) * 0.9 render
        // scale / 16 = 1.13 blocks, a low beast that still reads as armed. Half-width stays inside
        // 5.3px (the scythes are the widest points) so the silhouette sits within the 0.6 hitbox.

        // --- Thorax, carapace and haunch ---------------------------------------------------------
        // Pitched nose-down so the whole animal reads as hunched over its prey rather than level.
        // The thorax is a narrow core; what gives the body its shape are the two plates over it, a
        // wide one across the shoulders and a narrower, taller one over the hips. That taper — 8px
        // across the front, 6.4 across the back — is why this is three cubes and not one slab.
        PartDefinition body = root.addOrReplaceChild("body",
                CubeListBuilder.create().texOffs(0, 0).addBox(-3.2f, -2.8f, -5.0f, 6.4f, 5.6f, 10.0f),
                PartPose.offsetAndRotation(0.0f, 13.8f, 0.0f, 0.12f, 0.0f, 0.0f));
        body.addOrReplaceChild("carapace",
                CubeListBuilder.create().texOffs(34, 0).addBox(-4.0f, -4.8f, -5.0f, 8.0f, 2.6f, 7.0f),
                PartPose.ZERO);
        body.addOrReplaceChild("haunch",
                CubeListBuilder.create().texOffs(65, 0).addBox(-3.2f, -5.4f, 1.6f, 6.4f, 3.2f, 5.4f),
                PartPose.ZERO);

        // --- Dorsal spines, raked hard back -------------------------------------------------------
        // Long enough that their tips stand clear of the plates they grow from — the short stubs of
        // the first pass vanished into the shell. Splayed outward as well as back, so from the front
        // they frame the carapace instead of hiding in its centre line.
        body.addOrReplaceChild("spine_left",
                CubeListBuilder.create().texOffs(64, 17).addBox(-0.85f, -5.4f, -0.85f, 1.7f, 5.4f, 1.7f),
                PartPose.offsetAndRotation(2.6f, -4.7f, -1.2f, -1.05f, 0.0f, 0.25f));
        body.addOrReplaceChild("spine_right",
                CubeListBuilder.create().texOffs(72, 17).addBox(-0.85f, -5.4f, -0.85f, 1.7f, 5.4f, 1.7f),
                PartPose.offsetAndRotation(-2.6f, -4.7f, -1.2f, -1.05f, 0.0f, -0.25f));
        body.addOrReplaceChild("spine_back_left",
                CubeListBuilder.create().texOffs(107, 26).addBox(-0.7f, -4.2f, -0.7f, 1.4f, 4.2f, 1.4f),
                PartPose.offsetAndRotation(2.0f, -5.2f, 3.4f, -1.25f, 0.0f, 0.32f));
        body.addOrReplaceChild("spine_back_right",
                CubeListBuilder.create().texOffs(114, 26).addBox(-0.7f, -4.2f, -0.7f, 1.4f, 4.2f, 1.4f),
                PartPose.offsetAndRotation(-2.0f, -5.2f, 3.4f, -1.25f, 0.0f, -0.32f));

        // --- Tail: two segments, lifted clear of the ground ---------------------------------------
        PartDefinition tail = body.addOrReplaceChild("tail",
                CubeListBuilder.create().texOffs(99, 17).addBox(-1.4f, -1.4f, 0.0f, 2.8f, 2.8f, 3.4f),
                PartPose.offsetAndRotation(0.0f, -1.8f, 5.2f, 0.35f, 0.0f, 0.0f));
        tail.addOrReplaceChild("tail_tip",
                CubeListBuilder.create().texOffs(15, 34).addBox(-0.9f, -0.9f, 0.0f, 1.8f, 1.8f, 2.6f),
                PartPose.offsetAndRotation(0.0f, 0.0f, 3.4f, 0.40f, 0.0f, 0.0f));

        // --- Head: a short cranium, and jaws that are most of its length --------------------------
        // Slung forward and low off the front of the thorax, well below the carapace line: shoulders
        // high, head down and hunting, which is the posture the art has.
        PartDefinition head = body.addOrReplaceChild("head",
                CubeListBuilder.create().texOffs(0, 17).addBox(-2.9f, -3.7f, -3.4f, 5.8f, 4.6f, 3.4f),
                PartPose.offsetAndRotation(0.0f, 0.2f, -4.6f, 0.05f, 0.0f, 0.0f));
        head.addOrReplaceChild("horn_left",
                CubeListBuilder.create().texOffs(25, 34).addBox(-0.75f, -3.2f, -0.75f, 1.5f, 3.2f, 1.5f),
                PartPose.offsetAndRotation(2.2f, -3.2f, -1.4f, -0.75f, 0.0f, 0.32f));
        head.addOrReplaceChild("horn_right",
                CubeListBuilder.create().texOffs(32, 34).addBox(-0.75f, -3.2f, -0.75f, 1.5f, 3.2f, 1.5f),
                PartPose.offsetAndRotation(-2.2f, -3.2f, -1.4f, -0.75f, 0.0f, -0.32f));
        // High on the brow, clear of the snout ridge — set any lower and the upper jaw simply hides
        // them — and standing 0.55px proud of the cranium's front face so the lit pixels read from
        // the side as well as head-on.
        head.addOrReplaceChild("eye_left",
                CubeListBuilder.create().texOffs(87, 34).addBox(-0.6f, -0.6f, -0.4f, 1.2f, 1.2f, 0.8f),
                PartPose.offset(1.65f, -2.6f, -3.55f));
        head.addOrReplaceChild("eye_right",
                CubeListBuilder.create().texOffs(92, 34).addBox(-0.6f, -0.6f, -0.4f, 1.2f, 1.2f, 0.8f),
                PartPose.offset(-1.65f, -2.6f, -3.55f));

        // The jaws hinge from two different points on the skull — the upper one from the brow, the
        // lower from further back and down — so they part around the fangs instead of scissoring
        // flat. It rests visibly parted — a closed mouth hides every tooth, and the maw is the
        // whole point of this silhouette — and setupAnim drives it wider still on a bite.
        PartDefinition upperJaw = head.addOrReplaceChild("upper_jaw",
                CubeListBuilder.create().texOffs(90, 0).addBox(-2.5f, -1.5f, -4.6f, 5.0f, 3.0f, 4.6f),
                PartPose.offsetAndRotation(0.0f, -0.6f, -3.2f, 0.18f, 0.0f, 0.0f));
        upperJaw.addOrReplaceChild("snout_ridge",
                CubeListBuilder.create().texOffs(0, 34).addBox(-1.4f, -0.7f, -4.2f, 2.8f, 0.8f, 4.2f),
                PartPose.offset(0.0f, -1.5f, 0.0f));
        upperJaw.addOrReplaceChild("fang_upper_left",
                CubeListBuilder.create().texOffs(39, 34).addBox(-0.7f, 0.0f, -0.7f, 1.4f, 2.8f, 1.4f),
                PartPose.offsetAndRotation(1.55f, 1.3f, -3.6f, 0.10f, 0.0f, 0.0f));
        upperJaw.addOrReplaceChild("fang_upper_right",
                CubeListBuilder.create().texOffs(46, 34).addBox(-0.7f, 0.0f, -0.7f, 1.4f, 2.8f, 1.4f),
                PartPose.offsetAndRotation(-1.55f, 1.3f, -3.6f, 0.10f, 0.0f, 0.0f));
        PartDefinition lowerJaw = head.addOrReplaceChild("lower_jaw",
                CubeListBuilder.create().texOffs(80, 17).addBox(-2.2f, -1.1f, -4.4f, 4.4f, 2.2f, 4.4f),
                PartPose.offsetAndRotation(0.0f, 1.0f, -3.0f, 1.25f, 0.0f, 0.0f));
        lowerJaw.addOrReplaceChild("fang_lower_left",
                CubeListBuilder.create().texOffs(73, 34).addBox(-0.65f, -2.6f, -0.65f, 1.3f, 2.6f, 1.3f),
                PartPose.offsetAndRotation(1.4f, -1.1f, -3.4f, -0.10f, 0.0f, 0.0f));
        lowerJaw.addOrReplaceChild("fang_lower_right",
                CubeListBuilder.create().texOffs(80, 34).addBox(-0.65f, -2.6f, -0.65f, 1.3f, 2.6f, 1.3f),
                PartPose.offsetAndRotation(-1.4f, -1.1f, -3.4f, -0.10f, 0.0f, 0.0f));

        // --- Kaiser blades: three segments per side ----------------------------------------------
        // The curve is faked the only way box geometry can: each segment carries more forward pitch
        // than the one below it (-0.55 at the shoulder, +0.80 cumulative at the blade, +1.55 at the
        // tip, i.e. past horizontal), so the scythe rises off the back, arcs over and hooks out to
        // z=-7.2, above the middle of the head. A single straight spike at any one of those angles
        // reads as a pole. The blades are deliberately thin across (1.4px) and deep (2.8px) — a sabre
        // seen edge-on from the front and broad from the side, not the square posts of the first pass.
        PartDefinition scytheArmLeft = body.addOrReplaceChild("scythe_arm_left",
                CubeListBuilder.create().texOffs(55, 26).addBox(-1.0f, -3.2f, -1.0f, 2.0f, 3.2f, 2.0f),
                PartPose.offsetAndRotation(3.5f, -3.4f, -1.5f, -0.55f, 0.0f, 0.30f));
        PartDefinition scytheBladeLeft = scytheArmLeft.addOrReplaceChild("scythe_blade_left",
                CubeListBuilder.create().texOffs(44, 17).addBox(-0.7f, -5.2f, -1.4f, 1.4f, 5.2f, 2.8f),
                PartPose.offsetAndRotation(0.0f, -3.2f, 0.0f, 1.35f, 0.0f, -0.12f));
        scytheBladeLeft.addOrReplaceChild("scythe_tip_left",
                CubeListBuilder.create().texOffs(91, 26).addBox(-0.55f, -3.6f, -1.1f, 1.1f, 3.6f, 2.2f),
                PartPose.offsetAndRotation(0.0f, -5.2f, 0.0f, 0.75f, 0.0f, 0.0f));

        PartDefinition scytheArmRight = body.addOrReplaceChild("scythe_arm_right",
                CubeListBuilder.create().texOffs(64, 26).addBox(-1.0f, -3.2f, -1.0f, 2.0f, 3.2f, 2.0f),
                PartPose.offsetAndRotation(-3.5f, -3.4f, -1.5f, -0.55f, 0.0f, -0.30f));
        PartDefinition scytheBladeRight = scytheArmRight.addOrReplaceChild("scythe_blade_right",
                CubeListBuilder.create().texOffs(54, 17).addBox(-0.7f, -5.2f, -1.4f, 1.4f, 5.2f, 2.8f),
                PartPose.offsetAndRotation(0.0f, -3.2f, 0.0f, 1.35f, 0.0f, 0.12f));
        scytheBladeRight.addOrReplaceChild("scythe_tip_right",
                CubeListBuilder.create().texOffs(99, 26).addBox(-0.55f, -3.6f, -1.1f, 1.1f, 3.6f, 2.2f),
                PartPose.offsetAndRotation(0.0f, -5.2f, 0.0f, 0.75f, 0.0f, 0.0f));

        // --- Four legs, hung off the root ---------------------------------------------------------
        // On the root rather than the body, so the gallop's bound rocks the carapace without dragging
        // planted feet with it; their pivots are therefore absolute, not relative to the body's y=13.8.
        // Written out longhand rather than built by a shared helper because every cube needs its own
        // editable texOffs literal — see docs/texturing.md.
        //
        // Forelimbs: short, reaching forward, ending in a flat ground claw. Hip 15.8 + thigh 3.76 +
        // shin 3.46 + claw 1.0 puts the sole on y=24.
        PartDefinition legFrontLeft = root.addOrReplaceChild("leg_front_left",
                CubeListBuilder.create().texOffs(113, 17).addBox(-1.15f, -0.6f, -1.15f, 2.3f, 4.0f, 2.3f),
                PartPose.offsetAndRotation(2.8f, 15.8f, -3.4f, -0.35f, 0.0f, 0.10f));
        PartDefinition shinFrontLeft = legFrontLeft.addOrReplaceChild("shin_front_left",
                CubeListBuilder.create().texOffs(73, 26).addBox(-0.9f, -0.4f, -0.95f, 1.8f, 3.5f, 1.9f),
                PartPose.offsetAndRotation(0.0f, 4.0f, 0.0f, 0.50f, 0.0f, -0.10f));
        shinFrontLeft.addOrReplaceChild("claw_front_left",
                CubeListBuilder.create().texOffs(53, 34).addBox(-0.85f, 0.0f, -2.6f, 1.7f, 1.0f, 2.8f),
                PartPose.offsetAndRotation(0.0f, 3.5f, 0.0f, -0.15f, 0.0f, 0.0f));

        PartDefinition legFrontRight = root.addOrReplaceChild("leg_front_right",
                CubeListBuilder.create().texOffs(0, 26).addBox(-1.15f, -0.6f, -1.15f, 2.3f, 4.0f, 2.3f),
                PartPose.offsetAndRotation(-2.8f, 15.8f, -3.4f, -0.35f, 0.0f, -0.10f));
        PartDefinition shinFrontRight = legFrontRight.addOrReplaceChild("shin_front_right",
                CubeListBuilder.create().texOffs(82, 26).addBox(-0.9f, -0.4f, -0.95f, 1.8f, 3.5f, 1.9f),
                PartPose.offsetAndRotation(0.0f, 4.0f, 0.0f, 0.50f, 0.0f, 0.10f));
        shinFrontRight.addOrReplaceChild("claw_front_right",
                CubeListBuilder.create().texOffs(63, 34).addBox(-0.85f, 0.0f, -2.6f, 1.7f, 1.0f, 2.8f),
                PartPose.offsetAndRotation(0.0f, 3.5f, 0.0f, -0.15f, 0.0f, 0.0f));

        // Hind legs: raptor-jointed, and the joint angles are steep on purpose. Femur drives down and
        // forward (-0.75), tibia comes back under the hips (+0.70 cumulative), foot lands flat (0.0)
        // on y=24. That deep Z is what makes the back end read as coiled to spring; the shallower
        // version it replaced just looked like two more props.
        PartDefinition legBackLeft = root.addOrReplaceChild("leg_back_left",
                CubeListBuilder.create().texOffs(20, 17).addBox(-1.3f, -0.6f, -1.35f, 2.6f, 4.8f, 2.7f),
                PartPose.offsetAndRotation(2.6f, 15.2f, 3.6f, -0.75f, 0.0f, 0.12f));
        PartDefinition shinBackLeft = legBackLeft.addOrReplaceChild("shin_back_left",
                CubeListBuilder.create().texOffs(11, 26).addBox(-0.95f, -0.5f, -1.05f, 1.9f, 4.8f, 2.1f),
                PartPose.offsetAndRotation(0.0f, 4.8f, 0.0f, 1.45f, 0.0f, -0.12f));
        shinBackLeft.addOrReplaceChild("foot_back_left",
                CubeListBuilder.create().texOffs(29, 26).addBox(-1.0f, 0.0f, -3.4f, 2.0f, 1.6f, 3.9f),
                PartPose.offsetAndRotation(0.0f, 4.8f, 0.0f, -0.70f, 0.0f, 0.0f));

        PartDefinition legBackRight = root.addOrReplaceChild("leg_back_right",
                CubeListBuilder.create().texOffs(32, 17).addBox(-1.3f, -0.6f, -1.35f, 2.6f, 4.8f, 2.7f),
                PartPose.offsetAndRotation(-2.6f, 15.2f, 3.6f, -0.75f, 0.0f, -0.12f));
        PartDefinition shinBackRight = legBackRight.addOrReplaceChild("shin_back_right",
                CubeListBuilder.create().texOffs(20, 26).addBox(-0.95f, -0.5f, -1.05f, 1.9f, 4.8f, 2.1f),
                PartPose.offsetAndRotation(0.0f, 4.8f, 0.0f, 1.45f, 0.0f, 0.12f));
        shinBackRight.addOrReplaceChild("foot_back_right",
                CubeListBuilder.create().texOffs(42, 26).addBox(-1.0f, 0.0f, -3.4f, 2.0f, 1.6f, 3.9f),
                PartPose.offsetAndRotation(0.0f, 4.8f, 0.0f, -0.70f, 0.0f, 0.0f));

        // 128 wide is a floor, not a choice: the thorax's island alone is 2*(6.4+10) = 33 texels
        // across and the packer shelves the rest against that width.
        return LayerDefinition.create(mesh, 128, 64);
    }
}
