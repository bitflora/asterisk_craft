package net.bitflora.asteriskcraft.client.terran;

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
 * Original blocky Science Vessel model authored for AsteriskCraft, from the reference art. It is the
 * mod's third aircraft and, unlike {@code WraithModel} and {@code client.protoss.ScoutModel}, it is
 * deliberately built to read as a <em>ship</em> rather than a fighter — nothing about it points at
 * anything. Four things off the art carry that:
 *
 * <ul>
 *   <li><b>A wide, shallow hull.</b> A four-unit slab with a broader, quarter-thickness rim
 *       through its waist, where both fighters are spindles. At the altitude these things hold the
 *       planform is the whole silhouette, and this one is a disc.</li>
 *   <li><b>The dome is the unit.</b> A stepped sensor blister sits high on the hull toward the rear
 *       and is the only large lit surface on the model — the reference art is essentially a
 *       green-glassed eye on a dark body, and the glow pass is what says so.</li>
 *   <li><b>Two pods on outriggers.</b> The art's spheres, cubified the way {@code ObserverModel}
 *       cubifies its pod, held off each flank on short arms. They are what makes the planform read
 *       as three masses rather than one, and they are what moves.</li>
 *   <li><b>A keel under the prow.</b> A thin blade hanging below the nose, which is the one thing
 *       telling the eye which end is the front on a hull with no cockpit.</li>
 * </ul>
 *
 * <p>Like {@code WraithModel} and {@code ObserverModel} the body is centred in the air rather than
 * standing on y=24: the entity itself is what hovers (see {@code ScienceVesselEntity}), so the model
 * just floats around its own origin.
 *
 * <p>Everything animates off {@code ageInTicks} alone: the hull bobs, and the {@code pods} container
 * — an empty part whose only job is to be rotated once for both outriggers — rolls gently on its own
 * phase, so the ship trims itself while the dome stays level and pointed. There is deliberately no
 * walk cycle ({@code walkAnimationSpeed} stays near zero on a flyer, which would freeze a limb-swing
 * model solid) and no strike animation, since {@link net.bitflora.asteriskcraft.stats.UnitStats}
 * declares this unit no attack at all.
 *
 * <p>The glow pass ({@code client.UnitGlowLayer}) lights the dome and the two pod caps; everything
 * else must be transparent in {@code science_vessel_glow.png}.
 */
public class ScienceVesselModel extends EntityModel<LivingEntityRenderState> {
    /** Radians per tick of the vertical bob. */
    private static final float BOB_RATE = 0.08f;
    /** Radians per tick of the outriggers' trim, deliberately off the bob's phase. */
    private static final float TRIM_RATE = 0.045f;
    /** Peak roll of the outriggers, in radians — a trim, not a bank. */
    private static final float TRIM_AMOUNT = 0.10f;

    private final ModelPart body;
    private final ModelPart pods;

    public ScienceVesselModel(ModelPart root) {
        super(root);
        this.body = root.getChild("body");
        this.pods = this.body.getChild("pods");
    }

    @Override
    public void setupAnim(LivingEntityRenderState state) {
        // super restores every part to its baked PartPose, so everything below adds a delta onto it.
        super.setupAnim(state);

        float t = state.ageInTicks;

        // +y is down in model space, so subtracting lifts the hull on the upbeat.
        this.body.y -= Mth.sin(t * BOB_RATE) * 0.4f;
        // Only the outriggers trim. Rolling the hull would take the dome off level with it, and the
        // dome holding still against a moving frame is what reads as an instrument rather than a
        // decoration.
        this.pods.zRot += Mth.sin(t * TRIM_RATE) * TRIM_AMOUNT;
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();

        // Each cube's texOffs points at its own packed UV island — do not hand-edit these; they are
        // assigned by tools/blockbench_export.py and guarded by ModelUvLayoutTest.
        // "body" is the hull slab; everything else hangs off it. y=16 puts the hull's centre half a
        // block above the entity position, i.e. the middle of its hitbox.
        PartDefinition body = root.addOrReplaceChild("body",
                CubeListBuilder.create().texOffs(0, 0).addBox(-7.0f, -2.0f, -6.0f, 14.0f, 4.0f, 12.0f),
                PartPose.offset(0.0f, 16.0f, 0.0f));

        // --- The rim: a broad, thin plate through the hull's waist --------------------------------
        // This is what makes the planform read as a saucer rather than a brick, and at the altitude
        // these things hold the planform is the whole silhouette. Wider than the hull and a quarter
        // its thickness, so it catches light as one hard horizontal edge.
        body.addOrReplaceChild("rim",
                CubeListBuilder.create().texOffs(0, 46).addBox(-11.0f, -0.75f, -8.0f, 22.0f, 1.5f, 16.0f),
                PartPose.ZERO);

        // --- Prow and stern: the hull tapering fore and aft ---------------------------------------
        body.addOrReplaceChild("nose",
                CubeListBuilder.create().texOffs(52, 0).addBox(-4.0f, -1.5f, -4.0f, 8.0f, 3.0f, 4.0f),
                PartPose.offset(0.0f, 0.0f, -8.0f));
        body.addOrReplaceChild("stern",
                CubeListBuilder.create().texOffs(76, 0).addBox(-5.0f, -1.5f, -2.0f, 10.0f, 3.0f, 4.0f),
                PartPose.offset(0.0f, 0.0f, 7.0f));

        // --- The dome: the art's green-glassed eye, cubified into two steps ------------------------
        // Both are on the glow texture; the base is the bezel the lit cap sits in.
        body.addOrReplaceChild("dome_base",
                CubeListBuilder.create().texOffs(44, 34).addBox(-4.0f, -3.0f, -4.0f, 8.0f, 3.0f, 8.0f),
                PartPose.offset(0.0f, -2.0f, 2.0f));
        body.addOrReplaceChild("dome",
                CubeListBuilder.create().texOffs(0, 20).addBox(-3.0f, -3.0f, -3.0f, 6.0f, 3.0f, 6.0f),
                PartPose.offset(0.0f, -5.0f, 2.0f));

        // --- The keel: the blade under the prow that tells the eye which end is the front ----------
        body.addOrReplaceChild("keel",
                CubeListBuilder.create().texOffs(26, 20).addBox(-0.5f, 0.0f, -3.0f, 1.0f, 6.0f, 6.0f),
                PartPose.offset(0.0f, 2.0f, -5.0f));

        // --- Swept horns at the upper rear corners, canted outward --------------------------------
        // Written out per side rather than through a helper: the texture tooling needs one editable
        // texOffs literal per cube. See docs/texturing.md.
        body.addOrReplaceChild("fin_left",
                CubeListBuilder.create().texOffs(46, 20).addBox(0.0f, -5.0f, -1.0f, 1.0f, 5.0f, 5.0f),
                PartPose.offsetAndRotation(5.0f, -1.5f, 5.0f, 0.0f, 0.0f, -0.35f));
        body.addOrReplaceChild("fin_right",
                CubeListBuilder.create().texOffs(60, 20).addBox(-1.0f, -5.0f, -1.0f, 1.0f, 5.0f, 5.0f),
                PartPose.offsetAndRotation(-5.0f, -1.5f, 5.0f, 0.0f, 0.0f, 0.35f));

        // --- The outriggers: an empty container, so setupAnim trims both from one rotation ---------
        PartDefinition pods = body.addOrReplaceChild("pods",
                CubeListBuilder.create(),
                PartPose.ZERO);

        pods.addOrReplaceChild("arm_left",
                CubeListBuilder.create().texOffs(74, 20).addBox(0.0f, -0.75f, -1.5f, 3.0f, 1.5f, 3.0f),
                PartPose.offset(7.0f, 0.0f, 0.0f));
        pods.addOrReplaceChild("pod_left",
                CubeListBuilder.create().texOffs(90, 20).addBox(-2.0f, -2.0f, -2.0f, 4.0f, 4.0f, 4.0f),
                PartPose.offset(12.0f, 0.0f, 0.0f));
        pods.addOrReplaceChild("cap_left",
                CubeListBuilder.create().texOffs(106, 20).addBox(-1.5f, -1.0f, -1.5f, 3.0f, 1.0f, 3.0f),
                PartPose.offset(12.0f, -2.5f, 0.0f));

        pods.addOrReplaceChild("arm_right",
                CubeListBuilder.create().texOffs(0, 34).addBox(-3.0f, -0.75f, -1.5f, 3.0f, 1.5f, 3.0f),
                PartPose.offset(-7.0f, 0.0f, 0.0f));
        pods.addOrReplaceChild("pod_right",
                CubeListBuilder.create().texOffs(16, 34).addBox(-2.0f, -2.0f, -2.0f, 4.0f, 4.0f, 4.0f),
                PartPose.offset(-12.0f, 0.0f, 0.0f));
        pods.addOrReplaceChild("cap_right",
                CubeListBuilder.create().texOffs(32, 34).addBox(-1.5f, -1.0f, -1.5f, 3.0f, 1.0f, 3.0f),
                PartPose.offset(-12.0f, -2.5f, 0.0f));

        return LayerDefinition.create(mesh, 128, 128);
    }
}
