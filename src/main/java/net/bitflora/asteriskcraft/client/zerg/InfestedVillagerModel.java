package net.bitflora.asteriskcraft.client.zerg;

import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.util.Mth;

/**
 * Hand-authored geometry for the Infested Villager: <b>a villager, with a Zergling's head, and a few
 * horns pushing out through the robe.</b> That is the whole design, and the restraint is the point —
 * this is a person something got into, not a new Zerg unit, and it only reads that way if the body is
 * left recognisably alone.
 *
 * <p>So the body below is not "villager-like", it is <b>vanilla's villager, dimension for
 * dimension</b>: the same 8x12x6 torso, the same inflated 20-tall robe falling over the legs, the same
 * 4x12x4 legs on the same hips, and the same folded arms — two 4x8x4 uppers and one 8x4x4 block of
 * crossed forearms, hung off a pivot at {@code (0, 3, -1)} tilted {@code -0.75}. Copying the numbers
 * rather than approximating them is what makes the silhouette pass as a villager at a glance.
 *
 * <p>Two things depart from vanilla, both deliberately. Vanilla builds all three arm cubes through
 * <em>one</em> {@code CubeListBuilder} and paints both arms from a single {@code texOffs(44, 22)} via
 * {@code .mirror()}; neither is allowed here, since every cube needs its own editable {@code texOffs}
 * literal and its own UV island. So {@code arms} is an empty container part with three single-cube
 * children, which still move as one. And vanilla's {@code hat}, {@code hat_rim} and {@code nose} are
 * simply gone: there is no villager head left to wear a hat or carry a nose.
 *
 * <p><b>The head is the only part that is not a villager</b> — a Zergling's skull, sized into the
 * volume vanilla's 8x10x8 head occupied: a short cranium under a heavy brow, two backswept horns,
 * two small lit eyes, and an upper and lower jaw resting parted around four bone fangs.
 *
 * <p><b>The horns are scattered on purpose.</b> Five of them, each its own part at its own angle,
 * unmirrored and asymmetric — one shoulder, one off-centre in the back, one low on the right flank,
 * one through a forearm, one out of a thigh. Mirrored pairs would read as armour somebody put on;
 * lone crooked spikes read as something growing out from underneath, which is what happened to this
 * villager.
 *
 * <p>Authored in true pixel space (16px = 1 block), "up" is negative y, ground is y=24, and colour
 * comes from {@code textures/entity/infested_villager.png}, in which <b>every cube owns its own UV
 * island</b> so it can be hand-painted independently — see {@code tools/blockbench_export.py}, which
 * packs those islands and emits the Blockbench project. That tooling also constrains how this class
 * may be written: one {@code texOffs} literal per cube, builders inlined into their
 * {@code addOrReplaceChild} call, and globally unique part names. See docs/texturing.md.
 *
 * <p>Only the eyes are painted into {@code infested_villager_glow.png}; everything else must stay
 * transparent there, since {@link net.bitflora.asteriskcraft.client.UnitGlowLayer} re-submits the
 * whole model.
 *
 * <p>{@link #setupAnim} keeps vanilla's villager walk and adds the one thing a villager never does:
 * a <b>windup off {@link InfestedVillagerRenderState#swelling}</b>. Vanilla villager arms do not
 * animate at all, so a standing one is pure villager, and the folded arms coming open as the fuse
 * burns is the entire tell. Everything stacks as a delta onto the baked pose, since
 * {@code super.setupAnim} restores every part first.
 */
public class InfestedVillagerModel extends EntityModel<InfestedVillagerRenderState> {
    private static final float DEG = (float) (Math.PI / 180.0);

    /** Radians per tick of the idle breath. */
    private static final float IDLE_RATE = 0.09f;

    private final ModelPart body;
    private final ModelPart head;
    private final ModelPart lowerJaw;
    private final ModelPart arms;
    private final ModelPart armLeft;
    private final ModelPart armRight;
    private final ModelPart legLeft;
    private final ModelPart legRight;

    public InfestedVillagerModel(ModelPart root) {
        super(root);
        this.body = root.getChild("body");
        this.head = root.getChild("head");
        this.lowerJaw = this.head.getChild("lower_jaw");
        this.arms = root.getChild("arms");
        this.armLeft = this.arms.getChild("arm_left");
        this.armRight = this.arms.getChild("arm_right");
        this.legLeft = root.getChild("leg_left");
        this.legRight = root.getChild("leg_right");
    }

    @Override
    public void setupAnim(InfestedVillagerRenderState state) {
        // super restores every part to its baked PartPose, so everything below adds a delta onto it.
        super.setupAnim(state);

        this.head.yRot += state.yRot * DEG;
        this.head.xRot += state.xRot * DEG;

        // Idle: the ribcage works and the jaw hangs and shuts, so a standing one never reads as inert.
        float idle = Mth.sin(state.ageInTicks * IDLE_RATE);
        this.body.y -= idle * 0.25f;
        this.lowerJaw.xRot += 0.08f + idle * 0.10f;

        // Vanilla's villager gait, damping and all: legs opposed, at half the amplitude a zombie's
        // use. Anything livelier stops reading as a villager shuffling and starts reading as a
        // soldier marching, which is the wrong animal entirely.
        float pos = state.walkAnimationPos * 0.6662f;
        float speed = state.walkAnimationSpeed;
        this.legLeft.xRot += Mth.cos(pos) * 1.4f * speed * 0.5f;
        this.legRight.xRot += Mth.cos(pos + Mth.PI) * 1.4f * speed * 0.5f;

        float fuse = Mth.clamp(state.swelling, 0.0f, 1.0f);
        if (fuse <= 0.0f) {
            return;
        }
        // Windup. Vanilla villager arms never move, so this is the only motion on the model that is
        // not a villager's, and that is what makes it legible: the folded arms come open and up, the
        // head rears back and the jaws gape. Squared, so almost all of it lands in the last few ticks.
        float arch = fuse * fuse;
        this.arms.xRot -= arch * 1.15f;
        this.armLeft.zRot -= arch * 0.75f;
        this.armRight.zRot += arch * 0.75f;
        this.body.xRot -= arch * 0.30f;
        this.head.xRot -= arch * 0.55f;
        this.lowerJaw.xRot += arch * 0.70f;
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();

        // Each cube's texOffs points at its own packed UV island — do not hand-edit these; they are
        // assigned by tools/blockbench_export.py and guarded by ModelUvLayoutTest.
        //
        // Vertical layout, y+ pointing down, straight off vanilla's villager: the neck is y=0, the
        // torso runs 0..12, the hips sit at 12 and the legs land the feet on y=24. The head hangs
        // above y=0, where vanilla's 10-tall villager head was; the horn tips are the apex.

        // --- Head: the one part that is not a villager -------------------------------------------
        // Pivoted at the neck exactly where vanilla's head is, so it turns and looks the way a
        // villager's does. Everything inside it is a Zergling's.
        PartDefinition head = root.addOrReplaceChild("head",
                CubeListBuilder.create().texOffs(0, 0).addBox(-3.5f, -6.8f, -3.0f, 7.0f, 6.8f, 3.6f),
                PartPose.ZERO);
        // A heavy skull ridge that also sits exactly where a villager's brow does — the one cube on
        // the model that reads as both animals at once. High on the crown, so the maw below it has
        // the whole front of the skull to itself.
        head.addOrReplaceChild("brow_ridge",
                CubeListBuilder.create().texOffs(22, 0).addBox(-3.7f, -0.7f, -1.0f, 7.4f, 1.4f, 1.1f),
                PartPose.offset(0.0f, -6.0f, -3.0f));
        head.addOrReplaceChild("horn_head_left",
                CubeListBuilder.create().texOffs(46, 0).addBox(-0.8f, -3.8f, -0.8f, 1.6f, 3.8f, 1.6f),
                PartPose.offsetAndRotation(2.6f, -6.2f, -0.8f, -0.80f, 0.0f, 0.34f));
        head.addOrReplaceChild("horn_head_right",
                CubeListBuilder.create().texOffs(54, 0).addBox(-0.8f, -3.8f, -0.8f, 1.6f, 3.8f, 1.6f),
                PartPose.offsetAndRotation(-2.6f, -6.2f, -0.8f, -0.80f, 0.0f, -0.34f));
        // Tucked under the brow and standing proud of the cranium's front face, so the lit pixels
        // read from the side as well as head-on — and clear of the upper jaw hinged below them.
        head.addOrReplaceChild("eye_left",
                CubeListBuilder.create().texOffs(62, 0).addBox(-0.6f, -0.6f, -0.4f, 1.2f, 1.2f, 0.8f),
                PartPose.offset(1.8f, -4.9f, -3.15f));
        head.addOrReplaceChild("eye_right",
                CubeListBuilder.create().texOffs(67, 0).addBox(-0.6f, -0.6f, -0.4f, 1.2f, 1.2f, 0.8f),
                PartPose.offset(-1.8f, -4.9f, -3.15f));

        // The jaws hinge from two different points on the skull, so they part around the fangs
        // instead of scissoring flat, and rest visibly open — a closed mouth hides every tooth, and
        // the maw is what makes this head read as Zerg at all. Both are pitched to sit *within* the
        // skull's own depth: a longer or steeper lower jaw drapes down the chest and stops reading as
        // a head at all.
        PartDefinition upperJaw = head.addOrReplaceChild("upper_jaw",
                CubeListBuilder.create().texOffs(72, 0).addBox(-2.8f, -1.4f, -4.4f, 5.6f, 2.8f, 4.4f),
                PartPose.offsetAndRotation(0.0f, -3.6f, -3.0f, 0.14f, 0.0f, 0.0f));
        upperJaw.addOrReplaceChild("snout_ridge",
                CubeListBuilder.create().texOffs(0, 12).addBox(-1.5f, -0.7f, -4.0f, 3.0f, 0.8f, 4.0f),
                PartPose.offset(0.0f, -1.4f, 0.0f));
        upperJaw.addOrReplaceChild("fang_upper_left",
                CubeListBuilder.create().texOffs(17, 12).addBox(-0.7f, 0.0f, -0.7f, 1.4f, 2.6f, 1.4f),
                PartPose.offsetAndRotation(1.7f, 1.3f, -3.4f, 0.10f, 0.0f, 0.0f));
        upperJaw.addOrReplaceChild("fang_upper_right",
                CubeListBuilder.create().texOffs(24, 12).addBox(-0.7f, 0.0f, -0.7f, 1.4f, 2.6f, 1.4f),
                PartPose.offsetAndRotation(-1.7f, 1.3f, -3.4f, 0.10f, 0.0f, 0.0f));
        PartDefinition lowerJaw = head.addOrReplaceChild("lower_jaw",
                CubeListBuilder.create().texOffs(31, 12).addBox(-2.4f, -1.05f, -3.6f, 4.8f, 2.1f, 3.6f),
                PartPose.offsetAndRotation(0.0f, -1.6f, -2.9f, 0.55f, 0.0f, 0.0f));
        lowerJaw.addOrReplaceChild("fang_lower_left",
                CubeListBuilder.create().texOffs(55, 12).addBox(-0.65f, -2.4f, -0.65f, 1.3f, 2.4f, 1.3f),
                PartPose.offsetAndRotation(1.5f, -1.05f, -2.9f, -0.10f, 0.0f, 0.0f));
        lowerJaw.addOrReplaceChild("fang_lower_right",
                CubeListBuilder.create().texOffs(62, 12).addBox(-0.65f, -2.4f, -0.65f, 1.3f, 2.4f, 1.3f),
                PartPose.offsetAndRotation(-1.5f, -1.05f, -2.9f, -0.10f, 0.0f, 0.0f));

        // --- Body: vanilla's villager torso and robe, unchanged -----------------------------------
        // The robe is the inflated 20-tall overlay vanilla calls "jacket" — it is what falls over the
        // hips and turns two bare legs into a villager, so it is not optional decoration.
        PartDefinition body = root.addOrReplaceChild("body",
                CubeListBuilder.create().texOffs(69, 12).addBox(-4.0f, 0.0f, -3.0f, 8.0f, 12.0f, 6.0f),
                PartPose.ZERO);
        body.addOrReplaceChild("robe",
                CubeListBuilder.create().texOffs(0, 32).addBox(-4.0f, 0.0f, -3.0f, 8.0f, 20.0f, 6.0f,
                        new CubeDeformation(0.5f)),
                PartPose.ZERO);

        // --- Arms: folded across the belly, vanilla's pose ----------------------------------------
        // An empty container so the three cubes swing as one limb-group, as vanilla's single part
        // does, while each still owns its own texOffs literal and UV island.
        PartDefinition arms = root.addOrReplaceChild("arms",
                CubeListBuilder.create(),
                PartPose.offsetAndRotation(0.0f, 3.0f, -1.0f, -0.75f, 0.0f, 0.0f));
        arms.addOrReplaceChild("arm_left",
                CubeListBuilder.create().texOffs(41, 32).addBox(4.0f, -2.0f, -2.0f, 4.0f, 8.0f, 4.0f),
                PartPose.ZERO);
        arms.addOrReplaceChild("arm_right",
                CubeListBuilder.create().texOffs(65, 32).addBox(-8.0f, -2.0f, -2.0f, 4.0f, 8.0f, 4.0f),
                PartPose.ZERO);
        arms.addOrReplaceChild("arms_folded",
                CubeListBuilder.create().texOffs(89, 32).addBox(-4.0f, 2.0f, -2.0f, 8.0f, 4.0f, 4.0f),
                PartPose.ZERO);

        // --- Legs: vanilla's, on vanilla's hips ---------------------------------------------------
        // Hung off the root rather than the body, as vanilla does, so the torso's idle breath does
        // not drag planted feet with it. 12 + 12 lands the soles on y=24.
        root.addOrReplaceChild("leg_left",
                CubeListBuilder.create().texOffs(0, 58).addBox(-2.0f, 0.0f, -2.0f, 4.0f, 12.0f, 4.0f),
                PartPose.offset(2.0f, 12.0f, 0.0f));
        root.addOrReplaceChild("leg_right",
                CubeListBuilder.create().texOffs(17, 58).addBox(-2.0f, 0.0f, -2.0f, 4.0f, 12.0f, 4.0f),
                PartPose.offset(-2.0f, 12.0f, 0.0f));

        // --- Five horns, pushing out through the robe ---------------------------------------------
        // Scattered rather than paired, and no two at the same angle. Attached to the parts they grow
        // out of — the back one rides the torso, the arm one rides the folded arms, the thigh one
        // rides a leg — so each moves with the flesh it came through instead of floating.
        body.addOrReplaceChild("horn_shoulder_left",
                CubeListBuilder.create().texOffs(34, 58).addBox(-0.75f, -4.4f, -0.75f, 1.5f, 4.4f, 1.5f),
                PartPose.offsetAndRotation(3.5f, 1.4f, 0.4f, -0.42f, 0.30f, 0.62f));
        body.addOrReplaceChild("horn_back",
                CubeListBuilder.create().texOffs(41, 58).addBox(-0.7f, -5.0f, -0.7f, 1.4f, 5.0f, 1.4f),
                PartPose.offsetAndRotation(-1.3f, 2.6f, 3.0f, -1.18f, -0.22f, -0.18f));
        body.addOrReplaceChild("horn_flank_right",
                CubeListBuilder.create().texOffs(48, 58).addBox(-0.6f, -3.4f, -0.6f, 1.2f, 3.4f, 1.2f),
                PartPose.offsetAndRotation(-4.0f, 8.6f, -0.6f, 0.24f, -0.36f, -1.34f));
        arms.addOrReplaceChild("horn_arm_left",
                CubeListBuilder.create().texOffs(54, 58).addBox(-0.6f, -3.2f, -0.6f, 1.2f, 3.2f, 1.2f),
                PartPose.offsetAndRotation(5.8f, 0.4f, -1.9f, -1.05f, 0.18f, 0.28f));
        root.addOrReplaceChild("horn_thigh_right",
                CubeListBuilder.create().texOffs(60, 58).addBox(-0.55f, -3.0f, -0.55f, 1.1f, 3.0f, 1.1f),
                PartPose.offsetAndRotation(-3.4f, 15.4f, 0.8f, 0.30f, 0.26f, -1.12f));

        return LayerDefinition.create(mesh, 128, 128);
    }
}
