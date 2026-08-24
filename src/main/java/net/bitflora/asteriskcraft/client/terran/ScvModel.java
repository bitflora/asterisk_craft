package net.bitflora.asteriskcraft.client.terran;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.util.Mth;

/**
 * Hand-authored geometry for the SCV, from the reference art. <b>It is a mech, not a suit of
 * armour</b>, and three things carry that read — get any of them wrong and it collapses back into a
 * humanoid wearing a big helmet:
 *
 * <ul>
 *   <li><b>It has no head.</b> The top of the model is two heavy shoulder pods with nothing between
 *       them. No neck, no skull, no crest.</li>
 *   <li><b>Its arms are booms, not limbs.</b> They project straight forward out of the hull under
 *       those pods, held horizontal, and end in tools — a gold parted claw on one side, the fusion
 *       cutter on the other. They are deliberately <em>asymmetric</em>: do not mirror them, and do
 *       not let them hang.</li>
 *   <li><b>The pilot sits in an open recess in the middle of the chest</b>, framed by a hood, a sill
 *       and two uprights, leaning out through the opening. That is the only reason a villager head
 *       is legible on this model at all — it reads as a cockpit rather than a face, and it only
 *       reads that way while the recess reads as a hole.</li>
 * </ul>
 *
 * <p><b>The pilot is not part of this model.</b> {@code pilot_mount} is an empty container part —
 * position and rotation, no cubes — and {@link ScvPilotLayer} hangs the real thing off it:
 * {@code ModelLayers.VILLAGER_BABY}'s own head, drawn with vanilla's own
 * {@code villager_baby.png}. Reproducing it here would have meant hand-copying four cubes and then
 * hand-painting a villager into {@code scv.png}, which is a copy that silently goes stale the next
 * time vanilla retouches a villager. {@code client/zerg/InfestedVillagerModel} copies villager
 * <em>proportions</em> for the opposite reason — it needs a body it can then infest — but nothing
 * here needs to modify the head, so nothing here should own one.
 *
 * <p>Authored in true pixel space (16px = 1 block), "up" is negative y, ground is y=24, and -z is
 * forward. The legs stand wider apart than the hull is broad, which is what gives the art its
 * planted stance; the booms reach ~15px forward and the pods ~9.5px to each side, both deliberately
 * overhanging the 0.8x1.8 hitbox — see the entity type's registration in AsteriskCraft.
 *
 * <p>Only {@code cockpit_lamp} and {@code cutter_tip} are painted into {@code scv_glow.png};
 * everything else must stay transparent there, since
 * {@link net.bitflora.asteriskcraft.client.UnitGlowLayer} re-submits the whole model.
 *
 * <p>Every cube owns its own UV island; the texture is hand-painted and the layout is assigned by
 * tools/blockbench_export.py. That tooling constrains how this class may be written — one
 * {@code texOffs} literal per cube, builders inlined into their {@code addOrReplaceChild} call, and
 * globally unique part names (the round-trip verifier keys parts by leaf name, so
 * {@code shoulder_left} and {@code shoulder_right} may not both be called {@code shoulder}). See
 * docs/texturing.md.
 */
public class ScvModel extends EntityModel<ScvRenderState> {
    private static final float DEG = (float) (Math.PI / 180.0);

    /** Radians per tick of the idle chassis bob. */
    private static final float BOB_RATE = 0.08f;
    /**
     * How much of the head-look the pilot actually gets, and how far it may reach. Damped and
     * clamped because the pilot sits in a socket barely wider than the hat brim is: at full range
     * it sweeps straight through the hull.
     */
    private static final float LOOK_DAMPING = 0.4f;
    private static final float LOOK_LIMIT_YAW = 45.0f;
    private static final float LOOK_LIMIT_PITCH = 35.0f;

    private final ModelPart chest;
    private final ModelPart pilotMount;
    private final ModelPart armRight;
    private final ModelPart clawUpper;
    private final ModelPart clawLower;
    private final ModelPart thighLeft;
    private final ModelPart thighRight;

    public ScvModel(ModelPart root) {
        super(root);
        this.chest = root.getChild("chest");
        this.pilotMount = this.chest.getChild("pilot_mount");
        ModelPart armLeft = this.chest.getChild("arm_left");
        this.armRight = this.chest.getChild("arm_right");
        this.clawUpper = armLeft.getChild("claw_upper");
        this.clawLower = armLeft.getChild("claw_lower");
        this.thighLeft = root.getChild("thigh_left");
        this.thighRight = root.getChild("thigh_right");
    }

    @Override
    public void setupAnim(ScvRenderState state) {
        // super restores every part to its baked PartPose, so everything below adds a delta onto it.
        super.setupAnim(state);

        // Only the pilot looks around. The chassis does not turn its head, because it has none.
        // The mount carries no geometry: what it aims is whatever ScvPilotLayer draws on it.
        this.pilotMount.yRot += Mth.clamp(state.yRot, -LOOK_LIMIT_YAW, LOOK_LIMIT_YAW) * DEG * LOOK_DAMPING;
        this.pilotMount.xRot += Mth.clamp(state.xRot, -LOOK_LIMIT_PITCH, LOOK_LIMIT_PITCH) * DEG * LOOK_DAMPING;

        float pos = state.walkAnimationPos * 0.6662f;
        float speed = state.walkAnimationSpeed;
        float swing = Mth.cos(pos) * 1.4f * speed;
        this.thighLeft.xRot += swing;
        this.thighRight.xRot -= swing;

        // The booms deliberately do not counter-swing the way a biped's arms would: they are bolted
        // to the hull, not hung off shoulders, so they hold their pose through the walk. What moves
        // instead is the hull itself, rolling over whichever leg is planted. The legs hang off root
        // rather than off the chest, so this rocks the machine without dragging the feet with it.
        this.chest.y -= Mth.sin(state.ageInTicks * BOB_RATE) * 0.35f;
        this.chest.zRot += Mth.sin(pos) * 0.07f * speed;

        float stroke = state.cutterProgress;
        if (stroke <= 0.0f) {
            return;
        }
        // One out-and-back piston stroke, peaking at the midpoint: the cutter boom drives forward and
        // retracts. Not a swing — the boom has no shoulder to rotate at. The claw on the other side
        // clacks in time with it, so the whole machine reads as working rather than one arm twitching.
        float punch = Mth.sin(stroke * Mth.PI);
        this.armRight.z -= punch * 2.5f;
        this.armRight.xRot -= punch * 0.10f;
        this.clawUpper.xRot += punch * 0.35f;
        this.clawLower.xRot -= punch * 0.35f;
    }

    /**
     * Walks the PoseStack down to the pilot's seat, for {@link ScvPilotLayer}. The chain has to be
     * spelled out — root, hull, mount — because a layer is handed the same PoseStack the model was,
     * before any of the model's own parts have been applied to it. Vanilla's
     * {@code VillagerModel.translateToArms} exists for exactly this reason.
     */
    public void translateToPilot(PoseStack poseStack) {
        this.root.translateAndRotate(poseStack);
        this.chest.translateAndRotate(poseStack);
        this.pilotMount.translateAndRotate(poseStack);
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();

        // Each cube's texOffs points at its own packed UV island — do not hand-edit these; they are
        // assigned by tools/blockbench_export.py and guarded by ModelUvLayoutTest.
        //
        // Vertical layout, y+ pointing down: pod tops -5.5, hull 0..11, hips 11..14, thigh 14..19,
        // shin 19..21, foot 21..24. Roughly even between hull and legs, which is how the art stands.

        // --- Chest pod: the hull everything else hangs off ---------------------------------------
        PartDefinition chest = root.addOrReplaceChild("chest",
                CubeListBuilder.create().texOffs(0, 0).addBox(-5.5f, -1.0f, -4.5f, 11.0f, 12.0f, 9.0f),
                PartPose.ZERO);
        // Rear heat vent — the one thing keeping the back of the hull from being a blank slab.
        chest.addOrReplaceChild("chest_vent",
                CubeListBuilder.create().texOffs(97, 37).addBox(-3.0f, 1.0f, 4.5f, 6.0f, 7.0f, 1.0f),
                PartPose.ZERO);

        // --- Cockpit: a hole in the front of the hull, framed on four sides -----------------------
        // The frame is what makes the recess read as an opening rather than a painted panel, so the
        // hood and sill stand proud of the hull face and the two posts bracket the gap between them.
        chest.addOrReplaceChild("canopy_hood",
                CubeListBuilder.create().texOffs(82, 49).addBox(-4.5f, -1.0f, -5.5f, 9.0f, 2.0f, 3.0f),
                PartPose.ZERO);
        chest.addOrReplaceChild("canopy_sill",
                CubeListBuilder.create().texOffs(0, 57).addBox(-4.5f, 7.0f, -5.5f, 9.0f, 2.0f, 3.0f),
                PartPose.ZERO);
        // At the hull's outer corners, not tight against the opening: the pilot's hat brim is 14
        // texels across before scaling and would clip straight through a pair of inboard posts.
        chest.addOrReplaceChild("canopy_post_left",
                CubeListBuilder.create().texOffs(83, 37).addBox(4.5f, -1.0f, -5.2f, 1.0f, 8.0f, 2.0f),
                PartPose.ZERO);
        chest.addOrReplaceChild("canopy_post_right",
                CubeListBuilder.create().texOffs(90, 37).addBox(-5.5f, -1.0f, -5.2f, 1.0f, 8.0f, 2.0f),
                PartPose.ZERO);
        // A lit strip under the hood lip, washing the cockpit. Glows.
        chest.addOrReplaceChild("cockpit_lamp",
                CubeListBuilder.create().texOffs(32, 57).addBox(-3.0f, 0.6f, -5.8f, 6.0f, 0.8f, 0.6f),
                PartPose.ZERO);

        // --- The pilot's mount ---------------------------------------------------------------------
        // No cubes: ScvPilotLayer draws vanilla's own baby-villager head here. The offsets are what
        // seat it in the cab, so they are chosen against that head's real dimensions at
        // ScvPilotLayer.SCALE (0.6): the head box runs y -8..0 and z -3.5..3.5 in its own space, so
        // y=6.5 lands it at y 1.7..6.5 — inside the opening the hood (y..1) and sill (7..) bracket —
        // and z=-4.5 puts its face at z=-6.6, a texel proud of the hood's front at -5.5, which is the
        // difference between a pilot you can see and a pilot buried in the hull.
        chest.addOrReplaceChild("pilot_mount",
                CubeListBuilder.create(),
                PartPose.offset(0.0f, 6.5f, -4.5f));

        // --- Shoulder pods: the top of the model --------------------------------------------------
        // Written out per side rather than through a helper: the texture tooling needs one editable
        // texOffs literal per cube, and the round-trip verifier needs unique part names.
        chest.addOrReplaceChild("shoulder_left",
                CubeListBuilder.create().texOffs(41, 0).addBox(-2.5f, -4.5f, -3.5f, 5.0f, 9.0f, 7.0f),
                PartPose.offset(7.0f, -1.0f, 0.0f));
        chest.addOrReplaceChild("shoulder_right",
                CubeListBuilder.create().texOffs(66, 0).addBox(-2.5f, -4.5f, -3.5f, 5.0f, 9.0f, 7.0f),
                PartPose.offset(-7.0f, -1.0f, 0.0f));

        // --- Booms: straight out the front, one tool each -----------------------------------------
        // Their baked pose is horizontal and stays that way. The claw's two prongs pivot at their own
        // bases, so they can part and close around whatever the boom is holding.
        PartDefinition armLeft = chest.addOrReplaceChild("arm_left",
                CubeListBuilder.create().texOffs(91, 0).addBox(-2.0f, -2.0f, -9.0f, 4.0f, 4.0f, 10.0f),
                PartPose.offset(7.0f, 3.5f, -1.0f));
        armLeft.addOrReplaceChild("claw_upper",
                CubeListBuilder.create().texOffs(67, 49).addBox(-1.5f, -1.5f, -4.0f, 3.0f, 1.5f, 4.0f),
                PartPose.offset(0.0f, -1.5f, -9.0f));
        armLeft.addOrReplaceChild("claw_lower",
                CubeListBuilder.create().texOffs(52, 49).addBox(-1.5f, 0.0f, -4.0f, 3.0f, 1.5f, 4.0f),
                PartPose.offset(0.0f, 1.5f, -9.0f));

        PartDefinition armRight = chest.addOrReplaceChild("arm_right",
                CubeListBuilder.create().texOffs(0, 22).addBox(-2.0f, -2.0f, -9.0f, 4.0f, 4.0f, 10.0f),
                PartPose.offset(-7.0f, 3.5f, -1.0f));
        armRight.addOrReplaceChild("cutter_barrel",
                CubeListBuilder.create().texOffs(38, 49).addBox(-1.25f, -1.25f, -13.0f, 2.5f, 2.5f, 4.0f),
                PartPose.ZERO);
        // The business end. Glows.
        armRight.addOrReplaceChild("cutter_tip",
                CubeListBuilder.create().texOffs(25, 57).addBox(-0.75f, -0.75f, -14.2f, 1.5f, 1.5f, 1.2f),
                PartPose.ZERO);

        // --- Undercarriage ------------------------------------------------------------------------
        // Hips and legs hang off root rather than off the chest, so the hull's bob and roll do not
        // drag planted feet with them — the same reason DragoonModel hangs its legs there.
        root.addOrReplaceChild("hips",
                CubeListBuilder.create().texOffs(0, 37).addBox(-5.0f, 11.0f, -4.0f, 10.0f, 3.0f, 8.0f),
                PartPose.ZERO);

        // Splayed to x = +-4.5 with 6-wide feet, so the stance is wider than the hull is broad.
        PartDefinition thighLeft = root.addOrReplaceChild("thigh_left",
                CubeListBuilder.create().texOffs(37, 37).addBox(-2.5f, 0.0f, -3.0f, 5.0f, 5.0f, 6.0f),
                PartPose.offset(4.5f, 14.0f, 0.0f));
        PartDefinition shinLeft = thighLeft.addOrReplaceChild("shin_left",
                CubeListBuilder.create().texOffs(0, 49).addBox(-2.25f, 0.0f, -2.25f, 4.5f, 2.0f, 4.5f),
                PartPose.offset(0.0f, 5.0f, 0.0f));
        shinLeft.addOrReplaceChild("foot_left",
                CubeListBuilder.create().texOffs(29, 22).addBox(-3.0f, 0.0f, -7.0f, 6.0f, 3.0f, 10.0f),
                PartPose.offset(0.0f, 2.0f, 0.0f));

        PartDefinition thighRight = root.addOrReplaceChild("thigh_right",
                CubeListBuilder.create().texOffs(60, 37).addBox(-2.5f, 0.0f, -3.0f, 5.0f, 5.0f, 6.0f),
                PartPose.offset(-4.5f, 14.0f, 0.0f));
        PartDefinition shinRight = thighRight.addOrReplaceChild("shin_right",
                CubeListBuilder.create().texOffs(19, 49).addBox(-2.25f, 0.0f, -2.25f, 4.5f, 2.0f, 4.5f),
                PartPose.offset(0.0f, 5.0f, 0.0f));
        shinRight.addOrReplaceChild("foot_right",
                CubeListBuilder.create().texOffs(62, 22).addBox(-3.0f, 0.0f, -7.0f, 6.0f, 3.0f, 10.0f),
                PartPose.offset(0.0f, 2.0f, 0.0f));

        return LayerDefinition.create(mesh, 128, 128);
    }
}
