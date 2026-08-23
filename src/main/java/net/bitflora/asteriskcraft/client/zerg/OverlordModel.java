package net.bitflora.asteriskcraft.client.zerg;

import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.util.Mth;

/**
 * The Overlord: a Zergling's head on a Ghast's body. The body is vanilla Ghast proportions — one
 * 16-unit cube with nine tentacles trailing under it — and the head is the Zergling's skull,
 * horns, eyes and parted jaws lifted wholesale and mounted on the front face, so the silhouette
 * reads as "swarm" rather than as a Nether mob wearing a hat.
 *
 * <p>The head is kept at the Zergling's own dimensions rather than scaled up with the body. A small
 * head on a vast sac is the proportion the reference art has, and it is what sells the size: the
 * body only looks enormous next to something the eye already knows the scale of.
 *
 * <p>Rendered at {@code 4.0f} by {@code OverlordRenderer} — the geometry here is built at ordinary
 * unit scale and the size comes from that one multiplier, the same arrangement the Ultralisk uses.
 * A 16-unit body cube therefore lands at four blocks, matching the entity's hitbox.
 *
 * <p>Everything animates off {@code ageInTicks} alone: a slow vertical bob on the body and a
 * lagging sway on the tentacles, each one trailing a little further behind the last so the fringe
 * ripples rather than swinging as a slab. There is no walk cycle — {@code walkAnimationSpeed} stays
 * near zero on a flyer, which would freeze a limb-swing model solid. The rate is a fraction of the
 * Mutalisk's wing beat because this thing drifts; a fast bob would read as effort.
 */
public class OverlordModel extends EntityModel<LivingEntityRenderState> {
    /** Ticks per radian of the body's drift — about one full bob every 60 ticks. */
    private static final float DRIFT_RATE = 0.1f;
    /** How far behind the body each successive tentacle trails, in radians of the drift cycle. */
    private static final float TENTACLE_LAG = 0.35f;
    /** How far the tentacles swing, in radians. */
    private static final float SWAY = 0.22f;

    private static final int TENTACLES = 9;

    private final ModelPart body;
    private final ModelPart head;
    private final ModelPart[] tentacles = new ModelPart[TENTACLES];

    public OverlordModel(ModelPart root) {
        super(root);
        this.body = root.getChild("body");
        this.head = this.body.getChild("head");
        for (int i = 0; i < TENTACLES; i++) {
            this.tentacles[i] = this.body.getChild("tentacle" + (i + 1));
        }
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();

        // Each cube's texOffs points at its own packed UV island — do not hand-edit these; they are
        // assigned by tools/blockbench_export.py and guarded by ModelUvLayoutTest.

        // --- Body: one Ghast cube ----------------------------------------------------------------
        // Centred at y=14 so it spans y=6..22, i.e. it fills the entity's four-block hitbox from the
        // ground plane (y=24) upward with a little clearance. The tentacles hang below that on
        // purpose — the fringe trails under the hitbox exactly as a Ghast's does.
        PartDefinition body = root.addOrReplaceChild("body",
                CubeListBuilder.create().texOffs(0, 0).addBox(-8.0f, -8.0f, -8.0f, 16.0f, 16.0f, 16.0f),
                PartPose.offset(0.0f, 14.0f, 0.0f));

        // --- Tentacles: a 3x3 fringe under the sac ------------------------------------------------
        // Written out one by one rather than through a loop or a shared builder: the texture tooling
        // needs one editable texOffs literal per cube, and the round-trip verifier keys parts by leaf
        // name, so all nine need distinct names. See docs/texturing.md.
        body.addOrReplaceChild("tentacle1",
                CubeListBuilder.create().texOffs(64, 0).addBox(-1.0f, 0.0f, -1.0f, 2.0f, 8.0f, 2.0f),
                PartPose.offset(-5.0f, 8.0f, -5.0f));
        body.addOrReplaceChild("tentacle2",
                CubeListBuilder.create().texOffs(72, 0).addBox(-1.0f, 0.0f, -1.0f, 2.0f, 8.0f, 2.0f),
                PartPose.offset(0.0f, 8.0f, -5.0f));
        body.addOrReplaceChild("tentacle3",
                CubeListBuilder.create().texOffs(80, 0).addBox(-1.0f, 0.0f, -1.0f, 2.0f, 8.0f, 2.0f),
                PartPose.offset(5.0f, 8.0f, -5.0f));
        body.addOrReplaceChild("tentacle4",
                CubeListBuilder.create().texOffs(88, 0).addBox(-1.0f, 0.0f, -1.0f, 2.0f, 8.0f, 2.0f),
                PartPose.offset(-5.0f, 8.0f, 0.0f));
        body.addOrReplaceChild("tentacle5",
                CubeListBuilder.create().texOffs(96, 0).addBox(-1.0f, 0.0f, -1.0f, 2.0f, 8.0f, 2.0f),
                PartPose.offset(0.0f, 8.0f, 0.0f));
        body.addOrReplaceChild("tentacle6",
                CubeListBuilder.create().texOffs(104, 0).addBox(-1.0f, 0.0f, -1.0f, 2.0f, 8.0f, 2.0f),
                PartPose.offset(5.0f, 8.0f, 0.0f));
        body.addOrReplaceChild("tentacle7",
                CubeListBuilder.create().texOffs(112, 0).addBox(-1.0f, 0.0f, -1.0f, 2.0f, 8.0f, 2.0f),
                PartPose.offset(-5.0f, 8.0f, 5.0f));
        body.addOrReplaceChild("tentacle8",
                CubeListBuilder.create().texOffs(120, 0).addBox(-1.0f, 0.0f, -1.0f, 2.0f, 8.0f, 2.0f),
                PartPose.offset(0.0f, 8.0f, 5.0f));
        body.addOrReplaceChild("tentacle9",
                CubeListBuilder.create().texOffs(64, 12).addBox(-1.0f, 0.0f, -1.0f, 2.0f, 8.0f, 2.0f),
                PartPose.offset(5.0f, 8.0f, 5.0f));

        // --- Head: the Zergling's, mounted on the front face --------------------------------------
        // Geometry lifted from ZerglingModel unchanged — same cranium, horns, eyes and two-hinge
        // jaws. Slung just below the body's centre line on the -z face, so it looks down over what
        // the sac is drifting across.
        PartDefinition head = body.addOrReplaceChild("head",
                CubeListBuilder.create().texOffs(0, 34).addBox(-2.9f, -3.7f, -3.4f, 5.8f, 4.6f, 3.4f),
                PartPose.offsetAndRotation(0.0f, 2.0f, -8.0f, 0.05f, 0.0f, 0.0f));
        head.addOrReplaceChild("horn_left",
                CubeListBuilder.create().texOffs(20, 34).addBox(-0.75f, -3.2f, -0.75f, 1.5f, 3.2f, 1.5f),
                PartPose.offsetAndRotation(2.2f, -3.2f, -1.4f, -0.75f, 0.0f, 0.32f));
        head.addOrReplaceChild("horn_right",
                CubeListBuilder.create().texOffs(27, 34).addBox(-0.75f, -3.2f, -0.75f, 1.5f, 3.2f, 1.5f),
                PartPose.offsetAndRotation(-2.2f, -3.2f, -1.4f, -0.75f, 0.0f, -0.32f));
        // Standing proud of the cranium's front face so the lit pixels (UnitGlowLayer) read from the
        // side as well as head-on — at this render scale the eyes are the only small detail that has
        // to survive being seen from a distance.
        head.addOrReplaceChild("eye_left",
                CubeListBuilder.create().texOffs(34, 34).addBox(-0.6f, -0.6f, -0.4f, 1.2f, 1.2f, 0.8f),
                PartPose.offset(1.65f, -2.6f, -3.55f));
        head.addOrReplaceChild("eye_right",
                CubeListBuilder.create().texOffs(39, 34).addBox(-0.6f, -0.6f, -0.4f, 1.2f, 1.2f, 0.8f),
                PartPose.offset(-1.65f, -2.6f, -3.55f));

        // The jaws hinge from two different points on the skull so they part around the fangs
        // instead of scissoring flat, and they rest visibly parted — a closed mouth hides every
        // tooth. Nothing animates them here: the Overlord never bites anything.
        PartDefinition upperJaw = head.addOrReplaceChild("upper_jaw",
                CubeListBuilder.create().texOffs(44, 34).addBox(-2.5f, -1.5f, -4.6f, 5.0f, 3.0f, 4.6f),
                PartPose.offsetAndRotation(0.0f, -0.6f, -3.2f, 0.18f, 0.0f, 0.0f));
        upperJaw.addOrReplaceChild("snout_ridge",
                CubeListBuilder.create().texOffs(65, 34).addBox(-1.4f, -0.7f, -4.2f, 2.8f, 0.8f, 4.2f),
                PartPose.offset(0.0f, -1.5f, 0.0f));
        upperJaw.addOrReplaceChild("fang_upper_left",
                CubeListBuilder.create().texOffs(80, 34).addBox(-0.7f, 0.0f, -0.7f, 1.4f, 2.8f, 1.4f),
                PartPose.offsetAndRotation(1.55f, 1.3f, -3.6f, 0.10f, 0.0f, 0.0f));
        upperJaw.addOrReplaceChild("fang_upper_right",
                CubeListBuilder.create().texOffs(87, 34).addBox(-0.7f, 0.0f, -0.7f, 1.4f, 2.8f, 1.4f),
                PartPose.offsetAndRotation(-1.55f, 1.3f, -3.6f, 0.10f, 0.0f, 0.0f));
        PartDefinition lowerJaw = head.addOrReplaceChild("lower_jaw",
                CubeListBuilder.create().texOffs(94, 34).addBox(-2.2f, -1.1f, -4.4f, 4.4f, 2.2f, 4.4f),
                PartPose.offsetAndRotation(0.0f, 1.0f, -3.0f, 1.25f, 0.0f, 0.0f));
        lowerJaw.addOrReplaceChild("fang_lower_left",
                CubeListBuilder.create().texOffs(0, 44).addBox(-0.65f, -2.6f, -0.65f, 1.3f, 2.6f, 1.3f),
                PartPose.offsetAndRotation(1.4f, -1.1f, -3.4f, -0.10f, 0.0f, 0.0f));
        lowerJaw.addOrReplaceChild("fang_lower_right",
                CubeListBuilder.create().texOffs(7, 44).addBox(-0.65f, -2.6f, -0.65f, 1.3f, 2.6f, 1.3f),
                PartPose.offsetAndRotation(-1.4f, -1.1f, -3.4f, -0.10f, 0.0f, 0.0f));

        return LayerDefinition.create(mesh, 128, 128);
    }

    @Override
    public void setupAnim(LivingEntityRenderState state) {
        super.setupAnim(state);

        float t = state.ageInTicks;
        // The sac rides its own slow cycle, and the head counter-rotates a fraction of the body yaw
        // so it keeps looking roughly where the unit is going rather than being carried rigidly.
        this.body.y += Mth.sin(t * DRIFT_RATE) * 1.4f;
        this.head.yRot += state.yRot * ((float) Math.PI / 180f) * 0.3f;

        // Each tentacle lags the one before it, so the fringe ripples front-to-back instead of
        // swinging as one slab. The x/z split gives the sway a diagonal so it never looks like a
        // metronome.
        for (int i = 0; i < TENTACLES; i++) {
            float phase = t * DRIFT_RATE - i * TENTACLE_LAG;
            this.tentacles[i].xRot = Mth.sin(phase) * SWAY;
            this.tentacles[i].zRot = Mth.cos(phase * 0.7f) * SWAY * 0.6f;
        }
    }
}
