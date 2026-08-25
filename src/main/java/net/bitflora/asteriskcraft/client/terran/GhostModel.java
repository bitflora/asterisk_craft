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
 * Hand-authored geometry for the Ghost: <b>a villager in grey fatigues behind a welding mask and a
 * cartographer's eyepiece, a long-barrelled rifle cradled in its folded arms.</b>
 *
 * <p>It is {@link MarineModel}'s frame, dimension for dimension — the same 8x12x6 villager torso,
 * the same inflated 20-tall robe, the same 4x12x4 legs on the same hips, the same arms folded
 * across the belly at {@code (0, 3, -1)} tilted {@code -0.75}. That is the Terran default pose and
 * not a choice this unit made (see CLAUDE.md). Three things separate it from a Marine at a glance:
 *
 * <ul>
 *   <li><b>The head, which this model does not own at all.</b> {@code head} is an empty container;
 *       {@link GhostHeadLayer} draws vanilla's own villager head into it and then paints vanilla's
 *       own armorer mask and cartographer eyepiece over it, off vanilla's own textures. <b>No
 *       helmet.</b> The Marine's four plates would bury the one feature that says who this is —
 *       the same call {@link FirebatModel} makes for its bare pillager head.</li>
 *   <li><b>Grey.</b> The Marine is blue-steel and the Firebat is red; a Ghost is the same
 *       silhouette in flat, unlit grey, which is the whole visual argument for a unit that is
 *       trying not to be looked at.</li>
 *   <li><b>The rifle is longer.</b> Same cradle, same {@code +0.75} pitch cancellation, but the
 *       barrel runs three pixels further out and carries a scope where the Marine's carries iron
 *       sights — the model's statement that this one shoots from further away.</li>
 * </ul>
 *
 * <p>The one part outside the villager outline is {@code comms_pack}, a slim box on the back. The
 * Firebat's fuel tank is the equivalent and the reason is the same: three humanoids on one frame
 * need something above the waist that differs in profile, not only in colour.
 *
 * <p>Authored in true pixel space (16px = 1 block), "up" is negative y, ground is y=24, and -z is
 * forward.
 *
 * <p>Only {@code rifle_muzzle} is painted into {@code ghost_glow.png}; everything else must stay
 * transparent there, since {@link net.bitflora.asteriskcraft.client.UnitGlowLayer} re-submits the
 * whole model. The three green eyeholes are <em>not</em> in that file — they are on the borrowed
 * head, which is a separate draw, and {@link GhostHeadLayer} lights them itself.
 *
 * <p>Every cube owns its own UV island so it can be hand-painted independently — see
 * {@code tools/blockbench_export.py}, which packs those islands and emits the Blockbench project.
 * That tooling also constrains how this class may be written: one {@code texOffs} literal per cube,
 * builders inlined into their {@code addOrReplaceChild} call, no {@code .mirror()}, and globally
 * unique part names. See docs/texturing.md.
 */
public class GhostModel extends EntityModel<GhostRenderState> {
    private static final float DEG = (float) (Math.PI / 180.0);

    /** Radians per tick of the idle breath. */
    private static final float IDLE_RATE = 0.09f;

    private final ModelPart body;
    private final ModelPart head;
    private final ModelPart arms;
    private final ModelPart legLeft;
    private final ModelPart legRight;

    public GhostModel(ModelPart root) {
        super(root);
        this.body = root.getChild("body");
        this.head = root.getChild("head");
        this.arms = root.getChild("arms");
        this.legLeft = root.getChild("leg_left");
        this.legRight = root.getChild("leg_right");
    }

    @Override
    public void setupAnim(GhostRenderState state) {
        // super restores every part to its baked PartPose, so everything below adds a delta onto it.
        super.setupAnim(state);

        this.head.yRot += state.yRot * DEG;
        this.head.xRot += state.xRot * DEG;

        this.body.y -= Mth.sin(state.ageInTicks * IDLE_RATE) * 0.25f;

        // Vanilla's villager gait, at half a zombie's amplitude — the Marine's, and for the same
        // reason: anything livelier stops reading as a villager.
        float pos = state.walkAnimationPos * 0.6662f;
        float speed = state.walkAnimationSpeed;
        this.legLeft.xRot += Mth.cos(pos) * 1.4f * speed * 0.5f;
        this.legRight.xRot += Mth.cos(pos + Mth.PI) * 1.4f * speed * 0.5f;

        float kick = Mth.sin(Mth.clamp(state.attackProgress, 0.0f, 1.0f) * Mth.PI);
        if (kick <= 0.0f) {
            return;
        }
        // The Marine's recoil pulse, damped: a canister rifle fired from a braced cradle every 1.5
        // seconds should look like a shot being placed, not a burst being sprayed.
        this.arms.xRot -= kick * 0.08f;
        this.arms.z += kick * 0.6f;
        this.body.xRot += kick * 0.03f;
    }

    /**
     * Walks the PoseStack down to the neck, for {@link GhostHeadLayer}. The chain has to be spelled
     * out — root, then head — because a layer is handed the same PoseStack the model was, before any
     * of the model's own parts have been applied to it. Same as {@code MarineModel.translateToHead}.
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
        // hangs above y=0 in the 8x10x8 vanilla occupies, and nothing is stacked over it, so the
        // crown of that head is the model's apex.

        // --- Head: an empty socket for vanilla's face, mask and eyepiece ---------------------------
        // No cubes, and deliberately no helmet: GhostHeadLayer draws ModelLayers.VILLAGER's head
        // here and paints the armorer mask and cartographer eyepiece onto it from vanilla's own
        // profession textures. Plating over that would hide the whole unit.
        root.addOrReplaceChild("head",
                CubeListBuilder.create(),
                PartPose.ZERO);

        // --- Body: vanilla's villager torso and robe, unchanged -----------------------------------
        PartDefinition body = root.addOrReplaceChild("body",
                CubeListBuilder.create().texOffs(29, 0).addBox(-4.0f, 0.0f, -3.0f, 8.0f, 12.0f, 6.0f),
                PartPose.ZERO);
        body.addOrReplaceChild("robe",
                CubeListBuilder.create().texOffs(0, 0).addBox(-4.0f, 0.0f, -3.0f, 8.0f, 20.0f, 6.0f,
                        new CubeDeformation(0.5f)),
                PartPose.ZERO);
        // The suit's collar, as on the Marine and the Firebat — what ties a borrowed head to the
        // armour under it instead of leaving it looking dropped on.
        body.addOrReplaceChild("neck_ring",
                CubeListBuilder.create().texOffs(40, 27).addBox(-4.5f, -1.0f, -3.5f, 9.0f, 2.0f, 7.0f),
                PartPose.ZERO);
        // The one thing outside the villager outline: a flat comms slab rather than the Firebat's
        // twin bottles, kept thin because a Ghost's whole read is "carries less than a Marine".
        body.addOrReplaceChild("comms_pack",
                CubeListBuilder.create().texOffs(73, 27).addBox(-2.5f, 1.0f, 3.0f, 5.0f, 7.0f, 1.6f),
                PartPose.ZERO);

        // --- Arms: folded across the belly, vanilla's pose -----------------------------------------
        PartDefinition arms = root.addOrReplaceChild("arms",
                CubeListBuilder.create(),
                PartPose.offsetAndRotation(0.0f, 3.0f, -1.0f, -0.75f, 0.0f, 0.0f));
        arms.addOrReplaceChild("arm_left",
                CubeListBuilder.create().texOffs(92, 0).addBox(4.0f, -2.0f, -2.0f, 4.0f, 8.0f, 4.0f),
                PartPose.ZERO);
        arms.addOrReplaceChild("arm_right",
                CubeListBuilder.create().texOffs(109, 0).addBox(-8.0f, -2.0f, -2.0f, 4.0f, 8.0f, 4.0f),
                PartPose.ZERO);
        arms.addOrReplaceChild("arms_folded",
                CubeListBuilder.create().texOffs(88, 27).addBox(-4.0f, 2.0f, -2.0f, 8.0f, 4.0f, 4.0f),
                PartPose.ZERO);

        // The C-10, lying across the crossed forearms exactly as the Marine's rifle does: the +0.75
        // pitch cancels the arm group's -0.75 so the weapon sits level in the world, and the yaw
        // rakes it across the body so it does not read as a pole. Everything forward of the receiver
        // is longer than the Marine's — that overhang IS the two extra blocks of range.
        PartDefinition rifle = arms.addOrReplaceChild("rifle",
                CubeListBuilder.create(),
                PartPose.offsetAndRotation(-1.0f, 4.31f, -2.28f, 0.75f, -0.30f, 0.0f));
        rifle.addOrReplaceChild("rifle_stock",
                CubeListBuilder.create().texOffs(113, 27).addBox(-1.25f, -1.25f, 1.5f, 2.5f, 2.5f, 4.5f),
                PartPose.ZERO);
        rifle.addOrReplaceChild("rifle_receiver",
                CubeListBuilder.create().texOffs(21, 27).addBox(-1.5f, -1.6f, -4.5f, 3.0f, 3.2f, 6.0f),
                PartPose.ZERO);
        rifle.addOrReplaceChild("rifle_magazine",
                CubeListBuilder.create().texOffs(14, 38).addBox(-1.0f, 1.6f, -3.0f, 2.0f, 4.0f, 2.5f),
                PartPose.ZERO);
        rifle.addOrReplaceChild("rifle_barrel",
                CubeListBuilder.create().texOffs(0, 27).addBox(-0.8f, -1.0f, -12.5f, 1.6f, 1.6f, 8.0f),
                PartPose.ZERO);
        // A scope where the Marine carries iron sights, sitting over the receiver rather than out on
        // the barrel so it stays clear of the muzzle flash below it.
        rifle.addOrReplaceChild("rifle_scope",
                CubeListBuilder.create().texOffs(0, 38).addBox(-0.7f, -3.4f, -5.5f, 1.4f, 1.4f, 5.0f),
                PartPose.ZERO);
        // Glows.
        rifle.addOrReplaceChild("rifle_muzzle",
                CubeListBuilder.create().texOffs(24, 38).addBox(-1.05f, -1.25f, -13.5f, 2.1f, 2.1f, 1.0f),
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
