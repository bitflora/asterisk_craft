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
 * Hand-authored geometry for the Hydralisk, shaped after the reference art rather than the handful
 * of boxes this used to be. It is a <b>rearing serpent</b>, read bottom to top: a coiled lower body
 * flanked by segmented plates, with a long tail that sweeps back and up before tipping down; a trunk
 * rising out of the coil, pale-plated down the throat and belly and carrying a rack of four needle
 * spines up its back; a neck that leans forward into a head that is <b>mostly maw</b> — a short
 * cranium under a wide bone hood, jaws that rest gaping around eight interlocking fangs, and two
 * mandible arms alongside them. Its signature is the pair of <b>bone scythes</b>: four-segment arms
 * that drop from the shoulders, kick forward at the elbow and then straighten back toward vertical,
 * so the blades hang <b>points down at the ground</b> just clear of the coil, exactly as the art
 * carries them. It has <b>no legs</b> — it slithers, and the coil stays on {@code root} so the sway
 * rocks the body over a planted base.
 *
 * <p>Authored in true pixel space (16px = 1 block), "up" is negative y, ground is y=24, and colour
 * comes from {@code textures/entity/hydralisk.png}, in which <b>every cube owns its own UV island</b>
 * so it can be hand-painted independently — see {@code tools/blockbench_export.py}, which packs those
 * islands and emits the Blockbench project. That tooling also constrains how this class may be
 * written: one {@code texOffs} literal per cube, builders inlined into their
 * {@code addOrReplaceChild} call, and globally unique part names (the round-trip verifier keys parts
 * by leaf name, so the two scythes may not both be called {@code scythe}). See docs/texturing.md.
 *
 * <p>Only the eyes and the lit tips of the back and crest spines are painted into
 * {@code hydralisk_glow.png}, so the emissive {@link net.bitflora.asteriskcraft.client.UnitGlowLayer}
 * lights just those; everything else must stay transparent there, since that layer re-submits the
 * whole model.
 *
 * <p>{@link #setupAnim} drives head-look, an idle breath and tail sway, a legless slither, and a
 * spine volley off {@link HydraliskRenderState#attackProgress}. Everything stacks as a delta onto the
 * baked pose, since {@code super.setupAnim} restores every part first.
 */
public class HydraliskModel extends EntityModel<HydraliskRenderState> {
    private static final float DEG = (float) (Math.PI / 180.0);

    /** Radians per tick of the idle breath. */
    private static final float IDLE_RATE = 0.08f;
    /** Fraction of a volley spent rearing back before the spines launch. */
    private static final float WINDUP_END = 0.35f;

    private final ModelPart coil;
    private final ModelPart trunk;
    private final ModelPart neck;
    private final ModelPart head;
    private final ModelPart hood;
    private final ModelPart lowerJaw;
    private final ModelPart tail1;
    private final ModelPart tail2;
    private final ModelPart tail3;
    private final ModelPart tailTip;
    private final ModelPart backSpine1;
    private final ModelPart backSpine2;
    private final ModelPart backSpine3;
    private final ModelPart backSpine4;
    private final ModelPart armLeft;
    private final ModelPart armRight;
    private final ModelPart foreArmLeft;
    private final ModelPart foreArmRight;
    private final ModelPart scytheLeft;
    private final ModelPart scytheRight;

    public HydraliskModel(ModelPart root) {
        super(root);
        this.coil = root.getChild("coil");
        this.trunk = this.coil.getChild("trunk");
        this.neck = this.trunk.getChild("neck");
        this.head = this.neck.getChild("head");
        this.hood = this.head.getChild("hood");
        this.lowerJaw = this.head.getChild("lower_jaw");
        this.tail1 = this.coil.getChild("tail1");
        this.tail2 = this.tail1.getChild("tail2");
        this.tail3 = this.tail2.getChild("tail3");
        this.tailTip = this.tail3.getChild("tail_tip");
        this.backSpine1 = this.trunk.getChild("back_spine1");
        this.backSpine2 = this.trunk.getChild("back_spine2");
        this.backSpine3 = this.trunk.getChild("back_spine3");
        this.backSpine4 = this.trunk.getChild("back_spine4");
        this.armLeft = this.trunk.getChild("arm_left");
        this.armRight = this.trunk.getChild("arm_right");
        this.foreArmLeft = this.armLeft.getChild("fore_arm_left");
        this.foreArmRight = this.armRight.getChild("fore_arm_right");
        this.scytheLeft = this.foreArmLeft.getChild("scythe_left");
        this.scytheRight = this.foreArmRight.getChild("scythe_right");
    }

    @Override
    public void setupAnim(HydraliskRenderState state) {
        // super restores every part to its baked PartPose, so everything below adds a delta onto it.
        super.setupAnim(state);

        // The neck takes a share of the look so the whole front end turns, not just the skull — on a
        // serpent a head that swivels alone on a rigid neck reads as broken.
        this.head.yRot += state.yRot * DEG * 0.65f;
        this.neck.yRot += state.yRot * DEG * 0.35f;
        this.head.xRot += state.xRot * DEG * 0.7f;
        this.neck.xRot += state.xRot * DEG * 0.3f;

        // Idle: the coil breathes, the tail drifts, the jaw works. Each tail segment lags the one
        // before it by a quarter cycle, which is what turns four boxes into a travelling wave.
        float idle = Mth.sin(state.ageInTicks * IDLE_RATE);
        this.trunk.y -= idle * 0.3f;
        this.lowerJaw.xRot += 0.08f + idle * 0.10f;
        this.tail1.yRot += idle * 0.10f;
        this.tail2.yRot += Mth.sin(state.ageInTicks * IDLE_RATE - 0.8f) * 0.14f;
        this.tail3.yRot += Mth.sin(state.ageInTicks * IDLE_RATE - 1.6f) * 0.18f;
        this.tailTip.yRot += Mth.sin(state.ageInTicks * IDLE_RATE - 2.4f) * 0.22f;

        // Slither: with no legs to cycle, travel is sold by the body itself. The coil yaws side to
        // side and rolls into each stroke, the trunk counter-rotates so the head stays pointed where
        // it is going, and the tail lashes a half-cycle behind the coil.
        float pos = state.walkAnimationPos * 0.5f;
        float speed = state.walkAnimationSpeed;
        float sway = Mth.cos(pos) * speed;
        this.coil.yRot += sway * 0.30f;
        this.coil.zRot += sway * 0.12f;
        this.trunk.yRot -= sway * 0.18f;
        this.trunk.zRot -= sway * 0.10f;
        this.tail1.yRot -= sway * 0.35f;
        this.tail2.yRot -= Mth.cos(pos - 0.7f) * speed * 0.40f;
        this.tail3.yRot -= Mth.cos(pos - 1.4f) * speed * 0.45f;
        this.tailTip.yRot -= Mth.cos(pos - 2.1f) * speed * 0.50f;
        // Twice the sway frequency: the body heaves once per stroke, either side.
        this.trunk.y -= Mth.abs(Mth.cos(pos)) * 0.6f * speed;

        float p = state.attackProgress;
        if (p <= 0.0f) {
            return;
        }
        // Volley: the front end rears back and the hood flares while the spines load, then the whole
        // neck whips forward as they launch. The wind-up is the longer half, so the launch reads as a
        // snap rather than a lean.
        float windup = p < WINDUP_END ? p / WINDUP_END : 0.0f;
        float launch = p < WINDUP_END ? 0.0f : (p - WINDUP_END) / (1.0f - WINDUP_END);
        float whip = Mth.sin(launch * Mth.PI);

        this.neck.xRot -= windup * 0.35f;
        this.head.xRot -= windup * 0.20f;
        this.hood.xRot -= windup * 0.30f;       // the frill fans wider as it loads
        this.neck.xRot += whip * 0.55f;
        this.head.xRot += whip * 0.30f;
        this.lowerJaw.xRot += (windup + whip) * 0.30f;

        // The spines are what is actually being fired: they rake forward off the back as the launch
        // passes, front pair leading, back pair a beat behind.
        this.backSpine1.xRot += whip * 1.20f;
        this.backSpine2.xRot += whip * 1.10f;
        this.backSpine3.xRot += Mth.sin(Math.max(0.0f, launch - 0.15f) * Mth.PI) * 0.95f;
        this.backSpine4.xRot += Mth.sin(Math.max(0.0f, launch - 0.15f) * Mth.PI) * 0.95f;

        // The scythes brace against the recoil instead of swinging — this is a ranged unit, and arms
        // that slash at nothing on every shot read as a bug. Most of the brace is pitch, not roll:
        // these limbs hang, so roll at the shoulder is multiplied by their whole length on the way
        // down, and even a small angle throws the blade tips well outside the 0.6 hitbox.
        float brace = windup + whip;
        this.armLeft.zRot += brace * 0.07f;
        this.armRight.zRot -= brace * 0.07f;
        this.armLeft.xRot -= brace * 0.18f;
        this.armRight.xRot -= brace * 0.18f;
        this.foreArmLeft.xRot += brace * 0.25f;
        this.foreArmRight.xRot += brace * 0.25f;
        this.scytheLeft.xRot += brace * 0.20f;
        this.scytheRight.xRot += brace * 0.20f;
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();

        // Each cube's texOffs points at its own packed UV island — do not hand-edit these; they are
        // assigned by tools/blockbench_export.py and guarded by ModelUvLayoutTest.
        //
        // Vertical layout, y+ pointing down: the coil rests on y=24 and tops out at 17.5, the trunk
        // carries up to y≈8.9, the shoulders sit at y≈10.5, the skull is centred near y=4.3 and the
        // crest spines are the apex at y=-7.84. That apex is what the entity's height is sized
        // against: (24 - -7.84) / 16 = 1.99 blocks at the renderer's 1.0 scale, exactly the hitbox.
        // The widest texel is 4.67 from centre (the rib plates and the flank plates), i.e. 0.58
        // blocks across against the 0.6 hitbox.
        //
        // Both of those are measured off the baked geometry, not estimated part by part: rotations
        // compound down a chain and a limb's own roll adds width over its whole length, so the
        // widest cube is rarely the one with the largest x in its PartPose. The hood horns were 5.9
        // — a pixel outside the hitbox — while every number written next to them looked fine.
        //
        // Sign note, because it bites on every second part here: a positive xRot tips a part's own
        // long axis forward. For a box that grows *up* from its pivot (a spine, the hood, the trunk)
        // that means negative rakes it back; for one that grows *down* (the arms) or along +z (the
        // tail) the same number does the opposite. Read each rotation against its box's direction.

        // --- Coiled lower body ---------------------------------------------------------------------
        // A serpent at rest gathers rather than stands. The core is one heavy mass with a raised
        // ridge along its spine and a segmented plate rolled onto each flank, so the ground end reads
        // as coiled muscle instead of a slab.
        PartDefinition coil = root.addOrReplaceChild("coil",
                CubeListBuilder.create().texOffs(0, 0).addBox(-4.4f, -6.5f, -5.5f, 8.8f, 6.5f, 11.0f),
                PartPose.offset(0.0f, 24.0f, 0.5f));
        coil.addOrReplaceChild("coil_ridge",
                CubeListBuilder.create().texOffs(62, 32).addBox(-3.4f, -8.1f, -3.2f, 6.8f, 2.0f, 7.6f),
                PartPose.ZERO);
        coil.addOrReplaceChild("flank_left",
                CubeListBuilder.create().texOffs(69, 0).addBox(-1.2f, -2.8f, -4.6f, 2.4f, 5.6f, 9.4f),
                PartPose.offsetAndRotation(2.9f, -3.2f, 0.5f, 0.0f, 0.0f, 0.20f));
        coil.addOrReplaceChild("flank_right",
                CubeListBuilder.create().texOffs(94, 0).addBox(-1.2f, -2.8f, -4.6f, 2.4f, 5.6f, 9.4f),
                PartPose.offsetAndRotation(-2.9f, -3.2f, 0.5f, 0.0f, 0.0f, -0.20f));

        // --- Tail: four segments in an S ------------------------------------------------------------
        // The first two rise off the back of the coil, the last two tip down again, so the tail arcs
        // instead of pointing at the horizon. Each segment is shorter and thinner than the one before
        // it. Sign note: these boxes grow along +z, not up the y axis, so a *positive* pitch lifts
        // them — the opposite of what the same number does to a spine.
        PartDefinition tail1 = coil.addOrReplaceChild("tail1",
                CubeListBuilder.create().texOffs(0, 19).addBox(-2.6f, -2.6f, 0.0f, 5.2f, 5.2f, 6.0f),
                PartPose.offsetAndRotation(0.0f, -3.4f, 5.5f, 0.30f, 0.0f, 0.0f));
        PartDefinition tail2 = tail1.addOrReplaceChild("tail2",
                CubeListBuilder.create().texOffs(92, 32).addBox(-2.0f, -2.0f, 0.0f, 4.0f, 4.0f, 6.0f),
                PartPose.offsetAndRotation(0.0f, 0.0f, 5.0f, 0.20f, 0.0f, 0.0f));
        PartDefinition tail3 = tail2.addOrReplaceChild("tail3",
                CubeListBuilder.create().texOffs(54, 44).addBox(-1.4f, -1.4f, 0.0f, 2.8f, 2.8f, 5.5f),
                PartPose.offsetAndRotation(0.0f, 0.0f, 5.0f, -0.35f, 0.0f, 0.0f));
        tail3.addOrReplaceChild("tail_tip",
                CubeListBuilder.create().texOffs(54, 55).addBox(-0.8f, -0.8f, 0.0f, 1.6f, 1.6f, 4.5f),
                PartPose.offsetAndRotation(0.0f, 0.0f, 4.5f, -0.45f, 0.0f, 0.0f));

        // --- Trunk rising out of the coil ------------------------------------------------------------
        // Pitched slightly back at the base so that, with the neck leaning forward above it, the whole
        // animal describes an S rather than a post. The belly plate and the two rib plates are
        // overlays on the same pivot: one narrow pale slab down the front and a curved plate rolled
        // onto each side, which is what gives the torso its barrel instead of a flat box.
        PartDefinition trunk = coil.addOrReplaceChild("trunk",
                CubeListBuilder.create().texOffs(41, 0).addBox(-3.6f, -10.0f, -3.0f, 7.2f, 10.0f, 6.2f),
                PartPose.offsetAndRotation(0.0f, -5.2f, -0.5f, -0.12f, 0.0f, 0.0f));
        trunk.addOrReplaceChild("belly",
                CubeListBuilder.create().texOffs(113, 32).addBox(-2.6f, -9.2f, -3.6f, 5.2f, 8.4f, 0.9f),
                PartPose.ZERO);
        trunk.addOrReplaceChild("rib_plate_left",
                CubeListBuilder.create().texOffs(24, 19).addBox(-1.0f, -3.4f, -2.6f, 2.0f, 6.8f, 5.2f),
                PartPose.offsetAndRotation(3.0f, -5.6f, -0.3f, 0.0f, 0.0f, 0.20f));
        trunk.addOrReplaceChild("rib_plate_right",
                CubeListBuilder.create().texOffs(40, 19).addBox(-1.0f, -3.4f, -2.6f, 2.0f, 6.8f, 5.2f),
                PartPose.offsetAndRotation(-3.0f, -5.6f, -0.3f, 0.0f, 0.0f, -0.20f));

        // --- Needle spines up the back ---------------------------------------------------------------
        // The ammunition, so they are the length of a real weapon rather than the stubs they were:
        // two long ones on the centre line and a shorter splayed pair above them, all raked hard back
        // (negative pitch tips a spine's up-axis toward +z) so they frame the hood from the front
        // instead of hiding behind it. setupAnim rakes them forward as they launch.
        trunk.addOrReplaceChild("back_spine1",
                CubeListBuilder.create().texOffs(94, 44).addBox(-0.9f, -7.0f, -0.9f, 1.8f, 7.0f, 1.8f),
                PartPose.offsetAndRotation(0.0f, -2.4f, 2.6f, -1.05f, 0.0f, 0.0f));
        trunk.addOrReplaceChild("back_spine2",
                CubeListBuilder.create().texOffs(0, 44).addBox(-0.85f, -7.8f, -0.85f, 1.7f, 7.8f, 1.7f),
                PartPose.offsetAndRotation(0.0f, -5.8f, 2.8f, -1.00f, 0.0f, 0.0f));
        trunk.addOrReplaceChild("back_spine3",
                CubeListBuilder.create().texOffs(40, 55).addBox(-0.75f, -6.4f, -0.75f, 1.5f, 6.4f, 1.5f),
                PartPose.offsetAndRotation(1.9f, -8.6f, 2.4f, -0.95f, 0.0f, 0.30f));
        trunk.addOrReplaceChild("back_spine4",
                CubeListBuilder.create().texOffs(47, 55).addBox(-0.75f, -6.4f, -0.75f, 1.5f, 6.4f, 1.5f),
                PartPose.offsetAndRotation(-1.9f, -8.6f, 2.4f, -0.95f, 0.0f, -0.30f));

        // --- Bone scythes: four segments per side -----------------------------------------------------
        // A mantis arm, and the whole point of it is the sharp Z: down, up, down. These boxes grow
        // downward from their pivot, so the cumulative pitch tells you where each segment actually
        // points — +0.25 at the upper arm (down and slightly *back*), -2.45 at the forearm (past
        // vertical, so it runs back up and forward), then -0.30 at the blade and -0.15 at its tip
        // (down again, near-vertical). The elbow is the low point at y≈15.5, z≈-0.6; the wrist the
        // high one at y≈11.2, z≈-4.1; and the blade falls from there to a point at y≈22.8, a pixel
        // of ground clearance.
        //
        // The 3.5px of z between elbow and wrist is doing the real work. An earlier pass had the
        // same three reversals but stacked them all on one vertical line, and the limb read as a
        // single bone column — the zigzag is only legible if consecutive segments disagree about
        // which way they lean, not just whether they go up or down.
        //
        // The limb hangs at z≈-5.7 to -7.9, forward of both the trunk's front face and the coil's at
        // z=-5, so nothing sinks into the body. The blades are thin across (1.6px) and deep (4.4px):
        // a sickle seen edge-on from the front and broad from the side.
        //
        // Roll stays tiny — 0.08 at the shoulder, cancelled by -0.08 at the elbow — because a limb
        // this long converts roll into width over its entire 22px path. That cancellation is what
        // keeps the outermost texel at 4.2 against the 4.8px half-width budget.
        PartDefinition armLeft = trunk.addOrReplaceChild("arm_left",
                CubeListBuilder.create().texOffs(0, 55).addBox(-1.2f, -0.8f, -1.2f, 2.4f, 5.0f, 2.4f),
                PartPose.offsetAndRotation(3.0f, -8.0f, -2.8f, 0.37f, 0.0f, 0.08f));
        PartDefinition foreArmLeft = armLeft.addOrReplaceChild("fore_arm_left",
                CubeListBuilder.create().texOffs(22, 55).addBox(-1.0f, 0.0f, -1.0f, 2.0f, 5.5f, 2.0f),
                PartPose.offsetAndRotation(0.0f, 5.0f, 0.0f, -2.70f, 0.0f, -0.08f));
        PartDefinition scytheLeft = foreArmLeft.addOrReplaceChild("scythe_left",
                CubeListBuilder.create().texOffs(56, 19).addBox(-0.8f, 0.0f, -2.2f, 1.6f, 7.5f, 4.4f),
                PartPose.offsetAndRotation(0.0f, 5.5f, -0.3f, 2.15f, 0.0f, 0.0f));
        scytheLeft.addOrReplaceChild("scythe_tip_left",
                CubeListBuilder.create().texOffs(72, 44).addBox(-0.65f, 0.0f, -1.8f, 1.3f, 4.5f, 3.6f),
                PartPose.offsetAndRotation(0.0f, 7.5f, -0.4f, 0.15f, 0.0f, 0.0f));

        PartDefinition armRight = trunk.addOrReplaceChild("arm_right",
                CubeListBuilder.create().texOffs(11, 55).addBox(-1.2f, -0.8f, -1.2f, 2.4f, 5.0f, 2.4f),
                PartPose.offsetAndRotation(-3.0f, -8.0f, -2.8f, 0.37f, 0.0f, -0.08f));
        PartDefinition foreArmRight = armRight.addOrReplaceChild("fore_arm_right",
                CubeListBuilder.create().texOffs(31, 55).addBox(-1.0f, 0.0f, -1.0f, 2.0f, 5.5f, 2.0f),
                PartPose.offsetAndRotation(0.0f, 5.0f, 0.0f, -2.70f, 0.0f, 0.08f));
        PartDefinition scytheRight = foreArmRight.addOrReplaceChild("scythe_right",
                CubeListBuilder.create().texOffs(69, 19).addBox(-0.8f, 0.0f, -2.2f, 1.6f, 7.5f, 4.4f),
                PartPose.offsetAndRotation(0.0f, 5.5f, -0.3f, 2.15f, 0.0f, 0.0f));
        scytheRight.addOrReplaceChild("scythe_tip_right",
                CubeListBuilder.create().texOffs(83, 44).addBox(-0.65f, 0.0f, -1.8f, 1.3f, 4.5f, 3.6f),
                PartPose.offsetAndRotation(0.0f, 7.5f, -0.4f, 0.15f, 0.0f, 0.0f));

        // --- Neck, leaning forward off the trunk -------------------------------------------------------
        // Positive pitch tips a part's up-axis forward, so this is the counter-curve to the trunk's
        // backward lean. The throat plate is the same pale overlay trick as the belly.
        PartDefinition neck = trunk.addOrReplaceChild("neck",
                CubeListBuilder.create().texOffs(0, 32).addBox(-2.7f, -5.5f, -2.5f, 5.4f, 5.5f, 5.2f),
                PartPose.offsetAndRotation(0.0f, -9.4f, -1.0f, 0.42f, 0.0f, 0.0f));
        neck.addOrReplaceChild("throat",
                CubeListBuilder.create().texOffs(0, 64).addBox(-2.1f, -5.1f, -3.1f, 4.2f, 4.8f, 1.0f),
                PartPose.ZERO);

        // --- Head: a short cranium under a wide hood, and jaws that are most of its length -------------
        PartDefinition head = neck.addOrReplaceChild("head",
                CubeListBuilder.create().texOffs(23, 32).addBox(-2.9f, -4.0f, -4.6f, 5.8f, 5.2f, 5.0f),
                PartPose.offsetAndRotation(0.0f, -5.4f, -0.4f, -0.15f, 0.0f, 0.0f));
        head.addOrReplaceChild("brow_ridge",
                CubeListBuilder.create().texOffs(84, 55).addBox(-2.1f, -4.8f, -4.2f, 4.2f, 1.0f, 4.4f),
                PartPose.ZERO);
        // High on the brow and standing 0.55px proud of the cranium's front face, so the lit pixels
        // read from the side as well as head-on.
        head.addOrReplaceChild("eye_left",
                CubeListBuilder.create().texOffs(58, 64).addBox(-0.65f, -0.65f, -0.4f, 1.3f, 1.3f, 0.8f),
                PartPose.offset(1.75f, -2.1f, -4.75f));
        head.addOrReplaceChild("eye_right",
                CubeListBuilder.create().texOffs(64, 64).addBox(-0.65f, -0.65f, -0.4f, 1.3f, 1.3f, 0.8f),
                PartPose.offset(-1.75f, -2.1f, -4.75f));

        // The hood is the widest thing on the model and the reason the head reads at distance: a
        // broad bone frill raked back off the skull, with a horn hooking forward off each upper
        // corner and a long lit spine outboard of it. Three cubes, not the one flat slab it was.
        PartDefinition hood = head.addOrReplaceChild("hood",
                CubeListBuilder.create().texOffs(8, 44).addBox(-4.4f, -7.0f, -0.9f, 8.8f, 7.0f, 1.8f),
                PartPose.offsetAndRotation(0.0f, -0.8f, 1.6f, -0.55f, 0.0f, 0.0f));
        // Rolled outward only 0.15: a horn this long turns roll into width over its whole length, and
        // the 0.35 it carried at first put the tips at 5.9px — the widest thing on the model by over
        // a pixel, and outside the 0.6 hitbox everything else is built to fit.
        hood.addOrReplaceChild("hood_horn_left",
                CubeListBuilder.create().texOffs(68, 55).addBox(-0.8f, -4.6f, -0.8f, 1.6f, 4.6f, 1.6f),
                PartPose.offsetAndRotation(3.2f, -6.2f, 0.0f, 0.40f, 0.0f, 0.15f));
        hood.addOrReplaceChild("hood_horn_right",
                CubeListBuilder.create().texOffs(76, 55).addBox(-0.8f, -4.6f, -0.8f, 1.6f, 4.6f, 1.6f),
                PartPose.offsetAndRotation(-3.2f, -6.2f, 0.0f, 0.40f, 0.0f, -0.15f));
        head.addOrReplaceChild("crest_spine_left",
                CubeListBuilder.create().texOffs(46, 32).addBox(-0.8f, -9.0f, -0.8f, 1.6f, 9.0f, 1.6f),
                PartPose.offsetAndRotation(1.9f, -4.2f, 0.6f, -0.75f, 0.0f, 0.18f));
        head.addOrReplaceChild("crest_spine_right",
                CubeListBuilder.create().texOffs(54, 32).addBox(-0.8f, -9.0f, -0.8f, 1.6f, 9.0f, 1.6f),
                PartPose.offsetAndRotation(-1.9f, -4.2f, 0.6f, -0.75f, 0.0f, -0.18f));

        // The jaws hinge from two different points on the skull — the upper one from the brow, the
        // lower from further back and down — so they part around the fangs instead of scissoring
        // flat. The lower one rests visibly gaping: a closed mouth hides every tooth, and the maw is
        // the whole point of this silhouette. Eight fangs, the outer pair long and the inner pair
        // short, interlock front to back rather than meeting in one row.
        PartDefinition upperJaw = head.addOrReplaceChild("upper_jaw",
                CubeListBuilder.create().texOffs(82, 19).addBox(-2.6f, -1.8f, -6.8f, 5.2f, 3.6f, 6.8f),
                PartPose.offsetAndRotation(0.0f, -1.6f, -4.2f, 0.12f, 0.0f, 0.0f));
        upperJaw.addOrReplaceChild("snout_ridge",
                CubeListBuilder.create().texOffs(103, 44).addBox(-1.5f, -1.0f, -6.4f, 3.0f, 1.1f, 6.4f),
                PartPose.offset(0.0f, -1.8f, 0.0f));
        upperJaw.addOrReplaceChild("fang_upper_left",
                CubeListBuilder.create().texOffs(12, 64).addBox(-0.6f, 0.0f, -0.6f, 1.2f, 2.9f, 1.2f),
                PartPose.offsetAndRotation(1.75f, 1.6f, -5.8f, 0.10f, 0.0f, 0.0f));
        upperJaw.addOrReplaceChild("fang_upper_right",
                CubeListBuilder.create().texOffs(18, 64).addBox(-0.6f, 0.0f, -0.6f, 1.2f, 2.9f, 1.2f),
                PartPose.offsetAndRotation(-1.75f, 1.6f, -5.8f, 0.10f, 0.0f, 0.0f));
        upperJaw.addOrReplaceChild("fang_upper_mid_left",
                CubeListBuilder.create().texOffs(36, 64).addBox(-0.55f, 0.0f, -0.55f, 1.1f, 2.4f, 1.1f),
                PartPose.offsetAndRotation(1.9f, 1.6f, -2.9f, 0.06f, 0.0f, 0.0f));
        upperJaw.addOrReplaceChild("fang_upper_mid_right",
                CubeListBuilder.create().texOffs(42, 64).addBox(-0.55f, 0.0f, -0.55f, 1.1f, 2.4f, 1.1f),
                PartPose.offsetAndRotation(-1.9f, 1.6f, -2.9f, 0.06f, 0.0f, 0.0f));

        PartDefinition lowerJaw = head.addOrReplaceChild("lower_jaw",
                CubeListBuilder.create().texOffs(31, 44).addBox(-2.3f, -1.2f, -6.4f, 4.6f, 2.4f, 6.4f),
                PartPose.offsetAndRotation(0.0f, 0.8f, -3.8f, 1.00f, 0.0f, 0.0f));
        lowerJaw.addOrReplaceChild("fang_lower_left",
                CubeListBuilder.create().texOffs(24, 64).addBox(-0.55f, -2.7f, -0.55f, 1.1f, 2.7f, 1.1f),
                PartPose.offsetAndRotation(1.6f, -1.2f, -5.3f, -0.08f, 0.0f, 0.0f));
        lowerJaw.addOrReplaceChild("fang_lower_right",
                CubeListBuilder.create().texOffs(30, 64).addBox(-0.55f, -2.7f, -0.55f, 1.1f, 2.7f, 1.1f),
                PartPose.offsetAndRotation(-1.6f, -1.2f, -5.3f, -0.08f, 0.0f, 0.0f));
        lowerJaw.addOrReplaceChild("fang_lower_mid_left",
                CubeListBuilder.create().texOffs(48, 64).addBox(-0.5f, -2.2f, -0.5f, 1.0f, 2.2f, 1.0f),
                PartPose.offsetAndRotation(1.7f, -1.2f, -2.7f, -0.05f, 0.0f, 0.0f));
        lowerJaw.addOrReplaceChild("fang_lower_mid_right",
                CubeListBuilder.create().texOffs(53, 64).addBox(-0.5f, -2.2f, -0.5f, 1.0f, 2.2f, 1.0f),
                PartPose.offsetAndRotation(-1.7f, -1.2f, -2.7f, -0.05f, 0.0f, 0.0f));
        // Mandible arms running alongside the lower jaw — the pair of bony spars the art carries
        // outboard of the teeth, splayed out and tipped down.
        lowerJaw.addOrReplaceChild("mandible_left",
                CubeListBuilder.create().texOffs(103, 55).addBox(-0.7f, -0.7f, -4.6f, 1.4f, 1.4f, 4.6f),
                PartPose.offsetAndRotation(2.3f, -0.2f, -3.4f, 0.20f, 0.0f, 0.30f));
        lowerJaw.addOrReplaceChild("mandible_right",
                CubeListBuilder.create().texOffs(116, 55).addBox(-0.7f, -0.7f, -4.6f, 1.4f, 1.4f, 4.6f),
                PartPose.offsetAndRotation(-2.3f, -0.2f, -3.4f, 0.20f, 0.0f, -0.30f));

        // 128 wide is a floor, not a choice: the coil's island alone is 2*(8.8+11) = 40 texels across
        // and the packer shelves the rest against that width.
        return LayerDefinition.create(mesh, 128, 128);
    }
}
