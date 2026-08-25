package net.bitflora.asteriskcraft.client.terran;

import com.mojang.blaze3d.vertex.PoseStack;
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
 * Hand-authored geometry for the Firebat: <b>a pillager in red plate, a twin-barrelled flamethrower
 * cradled in its folded arms and a fuel tank on its back.</b>
 *
 * <p>It is the {@link MarineModel}'s frame, deliberately — vanilla's villager, dimension for
 * dimension: the same 8x12x6 torso, the same inflated 20-tall robe falling over the legs, the same
 * 4x12x4 legs on the same hips, and the same arms folded across the belly at a pivot of
 * {@code (0, 3, -1)} tilted {@code -0.75}. <b>Folded arms are the Terran default</b> (see CLAUDE.md),
 * and this unit had no reason to move off it: the weapon it carries is cradled exactly as the
 * Marine's rifle is.
 *
 * <p><b>Three things separate it from a Marine at a glance</b>, and each is doing a job:
 *
 * <ul>
 *   <li><b>The head is a pillager's, and there is no helmet over it.</b> {@code head} is an
 *       <em>empty container part</em>; {@link FirebatHeadLayer} draws vanilla's own pillager head on
 *       it, off vanilla's own {@code pillager.png}. The Marine's four helmet plates are what says
 *       "Marine"; hanging plates here too would bury the one feature that says this is not one. The
 *       grey crown, the heavy brow and the long nose are the identity, so they are left bare.</li>
 *   <li><b>The weapon is twin-barrelled</b> rather than a single rifle line, which is what reads as
 *       a flamethrower from a distance instead of as a gun. Two pilot lights burn at the muzzles.</li>
 *   <li><b>A fuel tank rides on the back</b>, the one piece of silhouette outside the villager
 *       outline. It is what stops a red Marine and a Firebat being the same shape in a crowd.</li>
 * </ul>
 *
 * <p><b>The weapon is carried, not aimed</b>, for the reason the Marine's rifle is: the arms are a
 * villager's and a villager's arms do not come up. It hangs off the arm container rather than off one
 * arm, so it moves with the whole folded assembly, and it carries a {@code +0.75} pitch that cancels
 * the container's own tilt — the weapon sits level in the world while the arms keep vanilla's pose.
 *
 * <p>Authored in true pixel space (16px = 1 block), "up" is negative y, ground is y=24, and -z is
 * forward. The barrels reach ~11px forward, the same overhang the Marine's rifle already takes.
 *
 * <p>Only {@code pilot_light_left} and {@code pilot_light_right} are painted into
 * {@code firebat_glow.png}; everything else must stay transparent there, since
 * {@link net.bitflora.asteriskcraft.client.UnitGlowLayer} re-submits the whole model. The borrowed
 * head is a separate draw and so is never glowed, which is correct — a face does not emit light.
 *
 * <p>Every cube owns its own UV island so it can be hand-painted independently — see
 * {@code tools/blockbench_export.py}, which packs those islands and emits the Blockbench project.
 * That tooling also constrains how this class may be written: one {@code texOffs} literal per cube,
 * builders inlined into their {@code addOrReplaceChild} call, no {@code .mirror()}, and globally
 * unique part names. See docs/texturing.md.
 */
public class FirebatModel extends EntityModel<FirebatRenderState> {
    private static final float DEG = (float) (Math.PI / 180.0);

    /** Radians per tick of the idle breath. */
    private static final float IDLE_RATE = 0.09f;

    private final ModelPart body;
    private final ModelPart head;
    private final ModelPart arms;
    private final ModelPart legLeft;
    private final ModelPart legRight;

    public FirebatModel(ModelPart root) {
        super(root);
        this.body = root.getChild("body");
        this.head = root.getChild("head");
        this.arms = root.getChild("arms");
        this.legLeft = root.getChild("leg_left");
        this.legRight = root.getChild("leg_right");
    }

    @Override
    public void setupAnim(FirebatRenderState state) {
        // super restores every part to its baked PartPose, so everything below adds a delta onto it.
        super.setupAnim(state);

        this.head.yRot += state.yRot * DEG;
        this.head.xRot += state.xRot * DEG;

        // The chest works under the plate, so a standing Firebat never reads as inert.
        this.body.y -= Mth.sin(state.ageInTicks * IDLE_RATE) * 0.25f;

        // Vanilla's villager gait, damping and all: legs opposed, at half the amplitude a zombie's
        // use. The Marine's reasoning applies unchanged — these are farmhands who were handed
        // weapons, not soldiers marching.
        float pos = state.walkAnimationPos * 0.6662f;
        float speed = state.walkAnimationSpeed;
        this.legLeft.xRot += Mth.cos(pos) * 1.4f * speed * 0.5f;
        this.legRight.xRot += Mth.cos(pos + Mth.PI) * 1.4f * speed * 0.5f;

        float thrust = Mth.sin(Mth.clamp(state.attackProgress, 0.0f, 1.0f) * Mth.PI);
        if (thrust <= 0.0f) {
            return;
        }
        // One push, peaking at the midpoint of the sweep — and it goes the opposite way to the
        // Marine's. A rifle kicks back into the shoulder; a flamethrower is shoved out at what it is
        // burning, so the folded arms drive forward and the torso leans after them. Idle is
        // attackProgress 0, and sin(0) is 0, so nothing below moves between sweeps.
        this.arms.xRot += thrust * 0.14f;
        this.arms.z -= thrust * 1.1f;
        this.body.xRot += thrust * 0.07f;
    }

    /**
     * Walks the PoseStack down to the neck, for {@link FirebatHeadLayer}. The chain has to be spelled
     * out — root, then head — because a layer is handed the same PoseStack the model was, before any
     * of the model's own parts have been applied to it. {@code MarineModel.translateToHead} and
     * {@code ScvModel.translateToPilot} are this mod's other two.
     */
    public void translateToHead(PoseStack poseStack) {
        this.root.translateAndRotate(poseStack);
        this.head.translateAndRotate(poseStack);
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();

        // Each cube's texOffs points at its own packed UV island — do not hand-edit these; they are
        // assigned by tools/blockbench_export.py and guarded by ModelUvLayoutTest.
        //
        // Vertical layout, y+ pointing down, straight off vanilla's villager: the neck is y=0, the
        // torso runs 0..12, the hips sit at 12 and the legs land the feet on y=24. The borrowed
        // pillager head hangs above y=0 in the 8x10x8 vanilla occupies, and — unlike the Marine —
        // nothing is stacked on top of it, so the crown of the head is the model's apex.

        // --- Head: an empty socket for vanilla's pillager face ------------------------------------
        // No cubes at all, and no helmet either. FirebatHeadLayer draws ModelLayers.PILLAGER's own
        // head here, off vanilla's pillager.png. See that class, and the class docs above, for why
        // this unit deliberately wears nothing over it.
        root.addOrReplaceChild("head",
                CubeListBuilder.create(),
                PartPose.ZERO);

        // --- Body: vanilla's villager torso and robe, unchanged -----------------------------------
        // The robe is the inflated 20-tall overlay vanilla calls "jacket" — it is what falls over
        // the hips and turns two bare legs into a villager, so it is not optional decoration.
        PartDefinition body = root.addOrReplaceChild("body",
                CubeListBuilder.create().texOffs(29, 0).addBox(-4.0f, 0.0f, -3.0f, 8.0f, 12.0f, 6.0f),
                PartPose.ZERO);
        body.addOrReplaceChild("robe",
                CubeListBuilder.create().texOffs(0, 0).addBox(-4.0f, 0.0f, -3.0f, 8.0f, 20.0f, 6.0f,
                        new CubeDeformation(0.5f)),
                PartPose.ZERO);
        // The suit's collar — the Marine's neck ring, kept because it is what ties a bare head to
        // armour below it rather than leaving the head looking dropped on.
        body.addOrReplaceChild("neck_ring",
                CubeListBuilder.create().texOffs(0, 40).addBox(-4.5f, -1.0f, -3.5f, 9.0f, 2.0f, 7.0f),
                PartPose.ZERO);
        // Shoulder plates. The Firebat's armour is the heavy version of the Marine's suit, and these
        // are the cheapest way to say so on a torso that is otherwise vanilla's villager exactly.
        body.addOrReplaceChild("pauldron_left",
                CubeListBuilder.create().texOffs(92, 0).addBox(3.5f, 0.5f, -3.5f, 2.0f, 5.0f, 7.0f),
                PartPose.ZERO);
        body.addOrReplaceChild("pauldron_right",
                CubeListBuilder.create().texOffs(0, 27).addBox(-5.5f, 0.5f, -3.5f, 2.0f, 5.0f, 7.0f),
                PartPose.ZERO);

        // The fuel tank, on the back — the one part of this model outside the villager outline, and
        // the reason a Firebat and a Marine are not the same silhouette in a crowd. Twin bottles
        // rather than one slab, so it reads as pressurised fuel rather than as a backpack.
        PartDefinition tank = body.addOrReplaceChild("fuel_tank",
                CubeListBuilder.create(),
                PartPose.offset(0.0f, 2.0f, 3.0f));
        tank.addOrReplaceChild("tank_left",
                CubeListBuilder.create().texOffs(53, 27).addBox(0.4f, 0.0f, 0.0f, 3.0f, 8.0f, 3.0f),
                PartPose.ZERO);
        tank.addOrReplaceChild("tank_right",
                CubeListBuilder.create().texOffs(66, 27).addBox(-3.4f, 0.0f, 0.0f, 3.0f, 8.0f, 3.0f),
                PartPose.ZERO);
        tank.addOrReplaceChild("tank_yoke",
                CubeListBuilder.create().texOffs(0, 50).addBox(-3.4f, -1.4f, 0.4f, 6.8f, 1.6f, 2.2f),
                PartPose.ZERO);
        // The hose, arcing round the flank towards the weapon in front.
        tank.addOrReplaceChild("fuel_hose",
                CubeListBuilder.create().texOffs(106, 40).addBox(-4.6f, 1.0f, -3.0f, 1.2f, 1.2f, 4.0f),
                PartPose.ZERO);

        // --- Arms: folded across the belly, vanilla's pose -----------------------------------------
        // An empty container so the three cubes swing as one limb-group, as vanilla's single part
        // does, while each still owns its own texOffs literal and UV island. Vanilla builds all
        // three through one CubeListBuilder and mirrors one arm off the other; neither is allowed
        // here. Same split MarineModel and client/zerg/InfestedVillagerModel make.
        PartDefinition arms = root.addOrReplaceChild("arms",
                CubeListBuilder.create(),
                PartPose.offsetAndRotation(0.0f, 3.0f, -1.0f, -0.75f, 0.0f, 0.0f));
        arms.addOrReplaceChild("arm_left",
                CubeListBuilder.create().texOffs(19, 27).addBox(4.0f, -2.0f, -2.0f, 4.0f, 8.0f, 4.0f),
                PartPose.ZERO);
        arms.addOrReplaceChild("arm_right",
                CubeListBuilder.create().texOffs(36, 27).addBox(-8.0f, -2.0f, -2.0f, 4.0f, 8.0f, 4.0f),
                PartPose.ZERO);
        arms.addOrReplaceChild("arms_folded",
                CubeListBuilder.create().texOffs(33, 40).addBox(-4.0f, 2.0f, -2.0f, 8.0f, 4.0f, 4.0f),
                PartPose.ZERO);

        // The flamethrower, lying across the crossed forearms. The +0.75 pitch cancels the arm
        // group's -0.75, so the weapon sits level in the world while the arms keep vanilla's tilt.
        // Held square to the body rather than raked across it like the Marine's rifle: twin barrels
        // pointing straight ahead is what makes the spread they throw legible before it is fired.
        PartDefinition flamer = arms.addOrReplaceChild("flamer",
                CubeListBuilder.create(),
                PartPose.offsetAndRotation(0.0f, 4.31f, -2.28f, 0.75f, 0.0f, 0.0f));
        flamer.addOrReplaceChild("flamer_body",
                CubeListBuilder.create().texOffs(79, 27).addBox(-2.5f, -1.6f, -4.5f, 5.0f, 3.2f, 6.0f),
                PartPose.ZERO);
        flamer.addOrReplaceChild("flamer_grip",
                CubeListBuilder.create().texOffs(118, 40).addBox(-1.0f, 1.6f, -3.0f, 2.0f, 3.4f, 2.5f),
                PartPose.ZERO);
        flamer.addOrReplaceChild("flamer_feed",
                CubeListBuilder.create().texOffs(92, 40).addBox(-1.1f, -1.2f, 1.5f, 2.2f, 2.2f, 4.0f),
                PartPose.ZERO);
        flamer.addOrReplaceChild("flamer_barrel_left",
                CubeListBuilder.create().texOffs(58, 40).addBox(0.5f, -1.0f, -10.5f, 1.8f, 1.8f, 6.0f),
                PartPose.ZERO);
        flamer.addOrReplaceChild("flamer_barrel_right",
                CubeListBuilder.create().texOffs(75, 40).addBox(-2.3f, -1.0f, -10.5f, 1.8f, 1.8f, 6.0f),
                PartPose.ZERO);
        // Glows.
        flamer.addOrReplaceChild("pilot_light_left",
                CubeListBuilder.create().texOffs(19, 50).addBox(0.35f, -1.15f, -11.4f, 2.1f, 2.1f, 1.0f),
                PartPose.ZERO);
        // Glows.
        flamer.addOrReplaceChild("pilot_light_right",
                CubeListBuilder.create().texOffs(27, 50).addBox(-2.45f, -1.15f, -11.4f, 2.1f, 2.1f, 1.0f),
                PartPose.ZERO);

        // --- Legs: vanilla's, on vanilla's hips ---------------------------------------------------
        // Hung off the root rather than the body, as vanilla does, so the torso's idle breath does
        // not drag planted feet with it. 12 + 12 lands the soles on y=24.
        root.addOrReplaceChild("leg_left",
                CubeListBuilder.create().texOffs(58, 0).addBox(-2.0f, 0.0f, -2.0f, 4.0f, 12.0f, 4.0f),
                PartPose.offset(2.0f, 12.0f, 0.0f));
        root.addOrReplaceChild("leg_right",
                CubeListBuilder.create().texOffs(75, 0).addBox(-2.0f, 0.0f, -2.0f, 4.0f, 12.0f, 4.0f),
                PartPose.offset(-2.0f, 12.0f, 0.0f));

        return LayerDefinition.create(mesh, 128, 128);
    }
}
