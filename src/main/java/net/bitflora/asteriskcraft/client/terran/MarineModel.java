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
 * Hand-authored geometry for the Marine: <b>a villager in an open-faced space helmet, a rifle
 * cradled in its folded arms.</b> Three things carry that read, and it collapses if any of them
 * goes:
 *
 * <ul>
 *   <li><b>It is vanilla's villager, dimension for dimension</b> — the same 8x12x6 torso, the same
 *       inflated 20-tall robe falling over the legs, the same 4x12x4 legs on the same hips, and the
 *       same arms folded across the belly at a pivot of {@code (0, 3, -1)} tilted {@code -0.75}.
 *       The same copy {@code client/zerg/InfestedVillagerModel} makes, and for the same reason: the
 *       Terran <em>are</em> villagers (see CLAUDE.md). <b>Folded arms are the Terran default</b>,
 *       not a choice this unit made — a Terran that needs a limb somewhere else moves one off this
 *       pose rather than starting from a different one.</li>
 *   <li><b>The face is not here at all.</b> {@code head} is an <em>empty container part</em>;
 *       {@link MarineHeadLayer} draws vanilla's own villager head on it, off vanilla's own
 *       {@code villager.png}. See that class for why borrowing a vanilla model means borrowing its
 *       texture too.</li>
 *   <li><b>The helmet has no faceplate.</b> It is four separate plates — a crown, a back, and two
 *       ear covers — hung off that same container so they turn with the borrowed head, leaving the
 *       front open and the villager's unibrow and nose showing through. A single enclosing cube
 *       would be a cheaper build and would throw away the only thing on the model that says who
 *       these soldiers are.</li>
 * </ul>
 *
 * <p><b>The rifle is carried, not aimed.</b> It rests across the crossed forearms — butt back by the
 * right hip, barrel angled forward and out to the left — because the arms are a villager's and a
 * villager's arms do not come up. It hangs off the arm container rather than off one arm, so it
 * recoils with the whole folded assembly, and it carries a {@code +0.75} pitch that cancels the
 * container's own tilt: the weapon sits level in the world while the arms stay in vanilla's pose.
 *
 * <p>Authored in true pixel space (16px = 1 block), "up" is negative y, ground is y=24, and -z is
 * forward. The barrel reaches ~11px forward and ~3px across, well inside the overhang the SCV's
 * booms already take — see the entity type's registration in AsteriskCraft.
 *
 * <p>Only {@code helmet_lamp}, {@code antenna_tip} and {@code rifle_muzzle} are painted into
 * {@code marine_glow.png}; everything else must stay transparent there, since
 * {@link net.bitflora.asteriskcraft.client.UnitGlowLayer} re-submits the whole model. The borrowed
 * head is a separate draw and so is never glowed, which is correct — a face does not emit light.
 *
 * <p>Every cube owns its own UV island so it can be hand-painted independently — see
 * {@code tools/blockbench_export.py}, which packs those islands and emits the Blockbench project.
 * That tooling also constrains how this class may be written: one {@code texOffs} literal per cube,
 * builders inlined into their {@code addOrReplaceChild} call, no {@code .mirror()}, and globally
 * unique part names. See docs/texturing.md.
 */
public class MarineModel extends EntityModel<MarineRenderState> {
    private static final float DEG = (float) (Math.PI / 180.0);

    /** Radians per tick of the idle breath. */
    private static final float IDLE_RATE = 0.09f;

    private final ModelPart body;
    private final ModelPart head;
    private final ModelPart arms;
    private final ModelPart legLeft;
    private final ModelPart legRight;

    public MarineModel(ModelPart root) {
        super(root);
        this.body = root.getChild("body");
        this.head = root.getChild("head");
        this.arms = root.getChild("arms");
        this.legLeft = root.getChild("leg_left");
        this.legRight = root.getChild("leg_right");
    }

    @Override
    public void setupAnim(MarineRenderState state) {
        // super restores every part to its baked PartPose, so everything below adds a delta onto it.
        super.setupAnim(state);

        this.head.yRot += state.yRot * DEG;
        this.head.xRot += state.xRot * DEG;

        // The chest works under the suit, so a standing Marine never reads as inert.
        this.body.y -= Mth.sin(state.ageInTicks * IDLE_RATE) * 0.25f;

        // Vanilla's villager gait, damping and all: legs opposed, at half the amplitude a zombie's
        // use. Anything livelier stops reading as a villager and starts reading as a soldier
        // marching, which is the wrong animal — these are farmhands who were handed rifles.
        float pos = state.walkAnimationPos * 0.6662f;
        float speed = state.walkAnimationSpeed;
        this.legLeft.xRot += Mth.cos(pos) * 1.4f * speed * 0.5f;
        this.legRight.xRot += Mth.cos(pos + Mth.PI) * 1.4f * speed * 0.5f;

        float kick = Mth.sin(Mth.clamp(state.attackProgress, 0.0f, 1.0f) * Mth.PI);
        if (kick <= 0.0f) {
            return;
        }
        // One recoil pulse, peaking at the midpoint of the shot: the folded arms lift and drive back
        // and the torso rocks with them. Small on purpose — vanilla villager arms never move at all,
        // so a little goes a long way before the pose stops reading as folded. Idle is
        // attackProgress 0, and sin(0) is 0, so nothing below moves between shots.
        this.arms.xRot -= kick * 0.12f;
        this.arms.z += kick * 0.9f;
        this.body.xRot += kick * 0.05f;
    }

    /**
     * Walks the PoseStack down to the neck, for {@link MarineHeadLayer}. The chain has to be spelled
     * out — root, then head — because a layer is handed the same PoseStack the model was, before any
     * of the model's own parts have been applied to it. Vanilla's {@code VillagerModel.translateToArms}
     * exists for exactly this reason, and {@code ScvModel.translateToPilot} is this mod's other one.
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
        // torso runs 0..12, the hips sit at 12 and the legs land the feet on y=24. The borrowed head
        // hangs above y=0 in the 8x10x8 vanilla occupies, and the helmet crown over it is the apex —
        // which is why the antenna is mounted on the side of the helmet and angled back rather than
        // standing off the crown, where it would add half a block of empty height.

        // --- Head: an empty socket for vanilla's face, wearing this mod's helmet ------------------
        // No cubes. MarineHeadLayer draws ModelLayers.VILLAGER's own head here, off vanilla's
        // villager.png; the plates below are the only geometry this model owns above the neck, and
        // they hang off the same container so face and helmet turn together.
        PartDefinition head = root.addOrReplaceChild("head",
                CubeListBuilder.create(),
                PartPose.ZERO);

        // Four plates rather than one shell, so the front stays open. Each is sized around vanilla's
        // 8x10x8 head box and stands a fraction proud of it, which is what keeps them from
        // z-fighting with a head this model does not own and cannot adjust.
        head.addOrReplaceChild("helmet_crown",
                CubeListBuilder.create().texOffs(30, 0).addBox(-4.5f, -12.0f, -4.5f, 9.0f, 3.0f, 9.0f),
                PartPose.ZERO);
        head.addOrReplaceChild("helmet_back",
                CubeListBuilder.create().texOffs(68, 20).addBox(-4.5f, -9.5f, 2.5f, 9.0f, 8.0f, 2.2f),
                PartPose.ZERO);
        head.addOrReplaceChild("helmet_side_left",
                CubeListBuilder.create().texOffs(68, 0).addBox(4.0f, -9.5f, -3.5f, 1.6f, 7.0f, 8.0f),
                PartPose.ZERO);
        head.addOrReplaceChild("helmet_side_right",
                CubeListBuilder.create().texOffs(90, 0).addBox(-5.6f, -9.5f, -3.5f, 1.6f, 7.0f, 8.0f),
                PartPose.ZERO);
        // The brow lip, standing proud of the face. It is what makes the opening read as a helmet
        // with its visor missing rather than a hood, and it shades the lamp tucked under it. Kept
        // clear of vanilla's nose, which reaches to z=-6 and is the whole point of leaving the
        // front open.
        head.addOrReplaceChild("helmet_brim",
                CubeListBuilder.create().texOffs(28, 68).addBox(-4.5f, -11.0f, -5.6f, 9.0f, 2.2f, 1.6f),
                PartPose.ZERO);
        // Glows.
        head.addOrReplaceChild("helmet_lamp",
                CubeListBuilder.create().texOffs(65, 68).addBox(-1.6f, -10.4f, -6.0f, 3.2f, 1.0f, 0.6f),
                PartPose.ZERO);

        // Off one ear and raked back, so the helmet has a front and a back at a glance. On the side
        // rather than the crown deliberately: the crown is already the model's tallest point, and an
        // aerial standing off it would put the apex half a block above the hitbox.
        PartDefinition antenna = head.addOrReplaceChild("antenna",
                CubeListBuilder.create().texOffs(52, 68).addBox(-0.5f, -4.0f, -0.5f, 1.0f, 4.0f, 1.0f),
                PartPose.offsetAndRotation(4.6f, -8.0f, 2.2f, -0.45f, 0.0f, 0.35f));
        // Glows.
        antenna.addOrReplaceChild("antenna_tip",
                CubeListBuilder.create().texOffs(58, 68).addBox(-0.55f, -0.9f, -0.55f, 1.1f, 0.9f, 1.1f),
                PartPose.offset(0.0f, -4.0f, 0.0f));

        // --- Body: vanilla's villager torso and robe, unchanged -----------------------------------
        // The robe is the inflated 20-tall overlay vanilla calls "jacket" — it is what falls over
        // the hips and turns two bare legs into a villager, so it is not optional decoration.
        PartDefinition body = root.addOrReplaceChild("body",
                CubeListBuilder.create().texOffs(0, 0).addBox(-4.0f, 0.0f, -3.0f, 8.0f, 12.0f, 6.0f),
                PartPose.ZERO);
        body.addOrReplaceChild("robe",
                CubeListBuilder.create().texOffs(0, 20).addBox(-4.0f, 0.0f, -3.0f, 8.0f, 20.0f, 6.0f,
                        new CubeDeformation(0.5f)),
                PartPose.ZERO);
        // The suit's collar. The one piece of hardware on the torso, and the only thing tying the
        // helmet above it to the robe below — without it the helmet reads as a hat.
        body.addOrReplaceChild("neck_ring",
                CubeListBuilder.create().texOffs(34, 20).addBox(-4.5f, -1.0f, -3.5f, 9.0f, 2.0f, 7.0f),
                PartPose.ZERO);

        // --- Arms: folded across the belly, vanilla's pose -----------------------------------------
        // An empty container so the three cubes swing as one limb-group, as vanilla's single part
        // does, while each still owns its own texOffs literal and UV island. Vanilla builds all
        // three through one CubeListBuilder and mirrors one arm off the other; neither is allowed
        // here. Same split client/zerg/InfestedVillagerModel makes.
        PartDefinition arms = root.addOrReplaceChild("arms",
                CubeListBuilder.create(),
                PartPose.offsetAndRotation(0.0f, 3.0f, -1.0f, -0.75f, 0.0f, 0.0f));
        arms.addOrReplaceChild("arm_left",
                CubeListBuilder.create().texOffs(36, 50).addBox(4.0f, -2.0f, -2.0f, 4.0f, 8.0f, 4.0f),
                PartPose.ZERO);
        arms.addOrReplaceChild("arm_right",
                CubeListBuilder.create().texOffs(54, 50).addBox(-8.0f, -2.0f, -2.0f, 4.0f, 8.0f, 4.0f),
                PartPose.ZERO);
        arms.addOrReplaceChild("arms_folded",
                CubeListBuilder.create().texOffs(93, 20).addBox(-4.0f, 2.0f, -2.0f, 8.0f, 4.0f, 4.0f),
                PartPose.ZERO);

        // The weapon, lying across the crossed forearms. The +0.75 pitch cancels the arm group's
        // -0.75, so the rifle sits level in the world while the arms keep vanilla's tilt; the yaw
        // carries it across the body, butt by the right hip and muzzle forward-left, which is what
        // stops a rifle held dead-centre from reading as a pole. The offsets put the mount at world
        // (-1, 4.6, -5.6), just clear of the forearms it rests on.
        PartDefinition rifle = arms.addOrReplaceChild("rifle",
                CubeListBuilder.create(),
                PartPose.offsetAndRotation(-1.0f, 4.31f, -2.28f, 0.75f, -0.30f, 0.0f));
        rifle.addOrReplaceChild("rifle_stock",
                CubeListBuilder.create().texOffs(110, 50).addBox(-1.25f, -1.25f, 1.5f, 2.5f, 2.5f, 4.5f),
                PartPose.ZERO);
        rifle.addOrReplaceChild("rifle_receiver",
                CubeListBuilder.create().texOffs(90, 50).addBox(-1.5f, -1.6f, -4.5f, 3.0f, 3.2f, 6.0f),
                PartPose.ZERO);
        rifle.addOrReplaceChild("rifle_magazine",
                CubeListBuilder.create().texOffs(0, 68).addBox(-1.0f, 1.6f, -3.0f, 2.0f, 4.0f, 2.5f),
                PartPose.ZERO);
        rifle.addOrReplaceChild("rifle_barrel",
                CubeListBuilder.create().texOffs(72, 50).addBox(-0.9f, -1.0f, -10.5f, 1.8f, 1.8f, 6.0f),
                PartPose.ZERO);
        rifle.addOrReplaceChild("rifle_sight",
                CubeListBuilder.create().texOffs(20, 68).addBox(-0.6f, -2.4f, -6.0f, 1.2f, 1.2f, 1.6f),
                PartPose.ZERO);
        // Glows.
        rifle.addOrReplaceChild("rifle_muzzle",
                CubeListBuilder.create().texOffs(11, 68).addBox(-1.15f, -1.25f, -11.5f, 2.3f, 2.3f, 1.0f),
                PartPose.ZERO);

        // --- Legs: vanilla's, on vanilla's hips ---------------------------------------------------
        // Hung off the root rather than the body, as vanilla does, so the torso's idle breath does
        // not drag planted feet with it. 12 + 12 lands the soles on y=24.
        root.addOrReplaceChild("leg_left",
                CubeListBuilder.create().texOffs(0, 50).addBox(-2.0f, 0.0f, -2.0f, 4.0f, 12.0f, 4.0f),
                PartPose.offset(2.0f, 12.0f, 0.0f));
        root.addOrReplaceChild("leg_right",
                CubeListBuilder.create().texOffs(18, 50).addBox(-2.0f, 0.0f, -2.0f, 4.0f, 12.0f, 4.0f),
                PartPose.offset(-2.0f, 12.0f, 0.0f));

        return LayerDefinition.create(mesh, 128, 128);
    }
}
