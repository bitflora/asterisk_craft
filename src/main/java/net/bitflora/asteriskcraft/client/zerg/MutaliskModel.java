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
 * Original blocky Mutalisk model authored for AsteriskCraft, from the reference art: an upright,
 * curled-worm silhouette rather than a horizontal flyer. A rounded head sits at the <em>top</em> with
 * one big glowing eye (the emissive zone lit by {@link net.bitflora.asteriskcraft.client.UnitGlowLayer})
 * and a smaller second eye; two broad ear-like wings splay up and out from the shoulders; the body
 * coils downward and forward — a chain of tapering segments each rotated a little more than the last,
 * so it sweeps round toward the front into a hook — and the coil ends in a cluster of bone spikes at
 * the bottom.
 *
 * <p>The coil curls with {@code xRot} increments (rotation in the Y-Z plane) so the tail hooks
 * <em>forward</em> (toward -z), not out to the side: the wings still spread left/right along X, but the
 * body loops in the plane running front-to-back, as a curled worm seen three-quarters would.
 *
 * <p>Unlike the ground units the body is centred in the air rather than standing on y=24 — the entity
 * itself is what hovers (see {@code MutaliskEntity}), so the model just floats around its own origin.
 *
 * <p>Everything animates off a single wing beat: the wings flap, the outer panels lag behind the roots
 * so each beat cracks outward instead of pivoting rigidly, and the whole body bobs on the same phase so
 * the flap reads as what's holding it up. The lower coil undulates on a slower cycle, each segment
 * lagging the one above it. There is no walk cycle — {@code walkAnimationSpeed} stays near zero on a
 * flyer, which would freeze a limb-swing model solid.
 */
public class MutaliskModel extends EntityModel<LivingEntityRenderState> {
    /** Ticks per radian of wing beat — about one full flap every 12 ticks. */
    private static final float FLAP_RATE = 0.5f;
    /** How far behind the roots the wing tips trail, in radians of the beat cycle. */
    private static final float TIP_LAG = 0.7f;

    private final ModelPart body;
    private final ModelPart head;
    private final ModelPart wingRootL;
    private final ModelPart wingRootR;
    private final ModelPart wingTipL;
    private final ModelPart wingTipR;
    private final ModelPart coil4;
    private final ModelPart coil5;
    private final ModelPart coil6;

    public MutaliskModel(ModelPart root) {
        super(root);
        this.body = root.getChild("body");
        this.head = this.body.getChild("head");
        this.wingRootL = this.body.getChild("wingRootL");
        this.wingRootR = this.body.getChild("wingRootR");
        this.wingTipL = this.wingRootL.getChild("wingTipL");
        this.wingTipR = this.wingRootR.getChild("wingTipR");
        ModelPart coil1 = this.body.getChild("coil1");
        ModelPart coil2 = coil1.getChild("coil2");
        ModelPart coil3 = coil2.getChild("coil3");
        this.coil4 = coil3.getChild("coil4");
        this.coil5 = this.coil4.getChild("coil5");
        this.coil6 = this.coil5.getChild("coil6");
    }

    @Override
    public void setupAnim(LivingEntityRenderState state) {
        // super restores every part to its baked PartPose, so everything below adds a delta onto it.
        super.setupAnim(state);

        float t = state.ageInTicks;
        float beat = Mth.sin(t * FLAP_RATE);
        float trail = Mth.sin(t * FLAP_RATE - TIP_LAG);

        this.head.yRot += state.yRot * ((float) Math.PI / 180f);
        this.head.xRot += state.xRot * ((float) Math.PI / 180f);

        // The wings mirror each other: the left root extends along +x, so a negative zRot raises its
        // far tip; the right root takes the opposite sign to beat in sync.
        this.wingRootL.zRot -= beat * 0.6f;
        this.wingRootR.zRot += beat * 0.6f;
        this.wingTipL.zRot -= trail * 0.8f;
        this.wingTipR.zRot += trail * 0.8f;

        // Bob on the downbeat: +y is down in model space, so the trailing term is subtracted.
        this.body.y -= trail * 0.5f;

        // Lazy undulation running down the coil, each segment a quarter-cycle behind the last. On
        // xRot, so the tail flexes within its forward curl rather than swinging sideways out of it.
        this.coil4.xRot += Mth.cos(t * 0.08f) * 0.05f;
        this.coil5.xRot += Mth.cos(t * 0.08f - 0.6f) * 0.07f;
        this.coil6.xRot += Mth.cos(t * 0.08f - 1.2f) * 0.09f;
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();

        // Each cube's texOffs points at its own packed UV island — do not hand-edit these; they are
        // assigned by tools/blockbench_export.py and guarded by ModelUvLayoutTest.
        // "body" is the upper trunk just below the head, where the wings mount and the coil hangs.
        PartDefinition body = root.addOrReplaceChild("body",
                CubeListBuilder.create().texOffs(27, 0).addBox(-3.0f, -3.0f, -2.5f, 6.0f, 6.0f, 5.0f),
                PartPose.offset(0.0f, 9.0f, 0.0f));

        // --- Head at the top: a rounded block with one big glowing eye and a smaller one ---------
        PartDefinition head = body.addOrReplaceChild("head",
                CubeListBuilder.create().texOffs(0, 0).addBox(-3.5f, -4.0f, -3.0f, 7.0f, 7.0f, 6.0f),
                PartPose.offset(0.0f, -5.0f, 0.0f));
        head.addOrReplaceChild("eyeL",
                CubeListBuilder.create().texOffs(77, 24).addBox(-1.5f, -1.5f, -0.4f, 3.0f, 3.0f, 0.8f),
                PartPose.offset(1.0f, -0.5f, -3.2f));
        head.addOrReplaceChild("eyeR",
                CubeListBuilder.create().texOffs(98, 24).addBox(-0.9f, -0.9f, -0.4f, 1.8f, 1.8f, 0.8f),
                PartPose.offset(-1.7f, 0.3f, -3.2f));

        // --- Wings: broad ear-like panels splayed up and out; two segments so the flap bends -----
        // Written out per side rather than through a helper: the texture tooling needs one editable
        // texOffs literal per cube. See docs/texturing.md.
        PartDefinition wingRootL = body.addOrReplaceChild("wingRootL",
                CubeListBuilder.create().texOffs(0, 14).addBox(0.0f, -1.5f, -2.5f, 9.0f, 3.0f, 6.0f),
                PartPose.offsetAndRotation(2.5f, -3.0f, -0.5f, 0.0f, 0.25f, -1.0f));
        wingRootL.addOrReplaceChild("wingTipL",
                CubeListBuilder.create().texOffs(79, 14).addBox(0.0f, -1.25f, -2.0f, 8.0f, 2.5f, 5.0f),
                PartPose.offsetAndRotation(9.0f, 0.0f, 0.0f, 0.0f, 0.0f, -0.35f));

        PartDefinition wingRootR = body.addOrReplaceChild("wingRootR",
                CubeListBuilder.create().texOffs(31, 14).addBox(-9.0f, -1.5f, -2.5f, 9.0f, 3.0f, 6.0f),
                PartPose.offsetAndRotation(-2.5f, -3.0f, -0.5f, 0.0f, -0.25f, 1.0f));
        wingRootR.addOrReplaceChild("wingTipR",
                CubeListBuilder.create().texOffs(0, 24).addBox(-8.0f, -1.25f, -2.0f, 8.0f, 2.5f, 5.0f),
                PartPose.offsetAndRotation(-9.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.35f));

        // --- Coil: tapering segments chaining downward, each pivoting a little further *forward* so
        // the body sweeps round toward the front (-z). Every segment hangs off the bottom (local +y)
        // of the one above. The curl is on xRot (the Y-Z plane): front is -z, and a negative xRot tips
        // a downward-pointing segment toward the front, so the tail curls forward rather than sideways.
        PartDefinition coil1 = body.addOrReplaceChild("coil1",
                CubeListBuilder.create().texOffs(50, 0).addBox(-2.5f, 0.0f, -2.5f, 5.0f, 5.0f, 5.0f),
                PartPose.offset(0.0f, 3.0f, 0.0f));
        PartDefinition coil2 = coil1.addOrReplaceChild("coil2",
                CubeListBuilder.create().texOffs(71, 0).addBox(-2.5f, 0.0f, -2.5f, 5.0f, 5.0f, 5.0f),
                PartPose.offsetAndRotation(0.0f, 5.0f, 0.0f, -0.7f, 0.0f, 0.0f));
        PartDefinition coil3 = coil2.addOrReplaceChild("coil3",
                CubeListBuilder.create().texOffs(92, 0).addBox(-2.25f, 0.0f, -2.25f, 4.5f, 5.0f, 4.5f),
                PartPose.offsetAndRotation(0.0f, 5.0f, 0.0f, -0.75f, 0.0f, 0.0f));
        PartDefinition coil4 = coil3.addOrReplaceChild("coil4",
                CubeListBuilder.create().texOffs(62, 14).addBox(-2.0f, 0.0f, -2.0f, 4.0f, 5.0f, 4.0f),
                PartPose.offsetAndRotation(0.0f, 4.5f, 0.0f, -0.8f, 0.0f, 0.0f));
        PartDefinition coil5 = coil4.addOrReplaceChild("coil5",
                CubeListBuilder.create().texOffs(27, 24).addBox(-1.75f, 0.0f, -1.75f, 3.5f, 4.5f, 3.5f),
                PartPose.offsetAndRotation(0.0f, 4.0f, 0.0f, -0.85f, 0.0f, 0.0f));
        PartDefinition coil6 = coil5.addOrReplaceChild("coil6",
                CubeListBuilder.create().texOffs(42, 24).addBox(-1.5f, 0.0f, -1.5f, 3.0f, 4.0f, 3.0f),
                PartPose.offsetAndRotation(0.0f, 4.0f, 0.0f, -0.9f, 0.0f, 0.0f));

        // --- Bone spikes clustered at the bottom of the coil ------------------------------------
        // Two bristle out to the sides of the lower coil; one caps the tail tip, continuing the
        // forward curl.
        coil4.addOrReplaceChild("spikeA",
                CubeListBuilder.create().texOffs(64, 24).addBox(-4.0f, -0.75f, -1.0f, 4.0f, 1.5f, 2.0f),
                PartPose.offsetAndRotation(-2.0f, 3.0f, 0.0f, 0.0f, 0.0f, 0.55f));
        coil5.addOrReplaceChild("spikeB",
                CubeListBuilder.create().texOffs(86, 24).addBox(0.0f, -0.6f, -0.8f, 3.5f, 1.2f, 1.6f),
                PartPose.offsetAndRotation(1.75f, 3.0f, 0.0f, 0.0f, 0.0f, -0.55f));
        coil6.addOrReplaceChild("spikeTip",
                CubeListBuilder.create().texOffs(55, 24).addBox(-0.9f, 0.0f, -1.0f, 1.8f, 4.0f, 2.0f),
                PartPose.offsetAndRotation(0.0f, 3.5f, 0.0f, -0.3f, 0.0f, 0.0f));

        return LayerDefinition.create(mesh, 128, 128);
    }
}
