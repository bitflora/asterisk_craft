package net.bitflora.asteriskcraft.client.protoss;

import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.util.Mth;

/**
 * The Archon: a bare Protoss figure hanging inside a ball of light. Two things are being drawn and
 * they come from two different textures, which is the whole trick and the reason this model needed
 * no new render machinery.
 *
 * <p><b>The figure</b> is ordinary geometry painted in {@code archon.png} — a tapering skull with
 * lit eyes and two trailing nerve cords, a bare torso, arms flung wide and down, and legs that hang
 * and trail rather than walk. It is deliberately <em>unarmoured</em>, unlike the Zealot it is built
 * from: an Archon is two Templar burned down to energy, so there is no plate left to paint.
 *
 * <p><b>The ball</b> is the four {@code orb*} parts, and they are drawn <em>only</em> by
 * {@link net.bitflora.asteriskcraft.client.UnitGlowLayer}. Their UV islands must stay <b>fully
 * transparent in {@code archon.png}</b>: {@code MobRenderer}'s own pass is a cutout, which discards
 * those texels outright, so the shells contribute nothing to the body pass. They are then painted in
 * {@code archon_glow.png}, which the glow layer submits with {@code RenderTypes.eyes} — full bright
 * and additive, with no depth write — so the near faces of the shells add light over the figure
 * while the far ones are hidden behind it by the depth test. That is what makes a cluster of cubes
 * read as a glowing sphere with somebody inside it rather than as a box.
 *
 * <p>Painting an {@code orb*} island in {@code archon.png} is the one mistake that ruins the unit:
 * it turns the ball into an opaque crate around the figure. The figure's own islands run the other
 * way — transparent in the glow texture except for the eyes.
 *
 * <p>Three shells rather than one cube because a single box has a silhouette no amount of lighting
 * hides. They are the same volume stretched along each axis in turn, and {@link #setupAnim} turns
 * them against one another, so the outline that catches the eye is always changing.
 *
 * <p>Every cube owns its own UV island; the texture is hand-painted and the layout is assigned by
 * tools/blockbench_export.py. That tooling constrains how this class may be written — one
 * {@code texOffs} literal per cube, builders inlined into their {@code addOrReplaceChild} call, and
 * part names unique within the model. See docs/texturing.md.
 */
public class ArchonModel extends EntityModel<ArchonRenderState> {
    /** Radians per tick each shell turns. Three unrelated rates, so the outline never repeats. */
    private static final float WIDE_SPIN = 0.020f;
    private static final float TALL_SPIN = 0.031f;
    private static final float DEEP_SPIN = 0.026f;
    /** One full turn in ticks, per shell — the age is wrapped to this before it is scaled. */
    private static final float WIDE_PERIOD = (float) (Math.PI * 2.0) / WIDE_SPIN;
    private static final float TALL_PERIOD = (float) (Math.PI * 2.0) / TALL_SPIN;
    private static final float DEEP_PERIOD = (float) (Math.PI * 2.0) / DEEP_SPIN;

    /** Radians per tick of the hover, and its wrap period. Slow: it hangs in the air, it does not throb. */
    private static final float BOB_RATE = 0.08f;
    private static final float BOB_PERIOD = (float) (Math.PI * 2.0) / BOB_RATE;
    /** Pixels the figure rises and falls. */
    private static final float BOB_RISE = 0.8f;

    /** Fraction of the strike spent drawing the arms back before the shockwave leaves. */
    private static final float WINDUP_END = 0.3f;
    /** Fraction of the strike at which the arms have finished travelling forward. */
    private static final float STRIKE_END = 0.6f;
    /** How far back the arms draw during the wind-up, in radians. */
    private static final float WINDUP_ARC = 0.7f;
    /** How far forward they fling at full extension, in radians. Negative drives a limb forward. */
    private static final float STRIKE_ARC = -1.2f;

    private final ModelPart orb;
    private final ModelPart orbWide;
    private final ModelPart orbTall;
    private final ModelPart orbDeep;
    private final ModelPart head;
    private final ModelPart body;
    private final ModelPart armL;
    private final ModelPart armR;
    private final ModelPart foreArmL;
    private final ModelPart foreArmR;
    private final ModelPart legL;
    private final ModelPart legR;

    public ArchonModel(ModelPart root) {
        super(root);
        this.orb = root.getChild("orb");
        this.orbWide = this.orb.getChild("orbWide");
        this.orbTall = this.orb.getChild("orbTall");
        this.orbDeep = this.orb.getChild("orbDeep");
        this.body = root.getChild("body");
        this.head = this.body.getChild("head");
        this.armL = this.body.getChild("armL");
        this.armR = this.body.getChild("armR");
        this.foreArmL = this.armL.getChild("foreArmL");
        this.foreArmR = this.armR.getChild("foreArmR");
        this.legL = this.body.getChild("legL");
        this.legR = this.body.getChild("legR");
    }

    @Override
    public void setupAnim(ArchonRenderState state) {
        // super restores every part to its baked PartPose, so everything below adds a delta onto it.
        super.setupAnim(state);

        this.head.yRot += state.yRot * ((float) Math.PI / 180f);
        this.head.xRot += state.xRot * ((float) Math.PI / 180f);

        // A hover, not a walk. +y is down in model space, so subtracting lifts. The figure and the
        // ball ride the same beat but not the same distance, so the figure drifts within the ball
        // instead of the pair moving as one rigid object. Each age is wrapped to its own cycle
        // before it is scaled: an Archon left standing in a base for a few in-game weeks would
        // otherwise reach an ageInTicks where the raw multiply has lost enough float precision for
        // the motion to judder. Same trap as PhotonCannonModel's bob.
        float bob = Mth.sin((state.ageInTicks % BOB_PERIOD) * BOB_RATE) * BOB_RISE;
        this.body.y -= bob;
        this.orb.y -= bob * 0.5f;

        this.orbWide.yRot += (state.ageInTicks % WIDE_PERIOD) * WIDE_SPIN;
        this.orbTall.yRot -= (state.ageInTicks % TALL_PERIOD) * TALL_SPIN;
        this.orbDeep.xRot += (state.ageInTicks % DEEP_PERIOD) * DEEP_SPIN;

        // The legs trail rather than stride: nothing is bearing weight on them, so walking drags
        // them along behind instead of swinging them under the body.
        float drift = Mth.cos(state.walkAnimationPos * 0.4f) * 0.35f * state.walkAnimationSpeed;
        this.legL.xRot += drift;
        this.legR.xRot -= drift;

        float p = state.attackProgress;
        if (p <= 0.0f) {
            return;
        }
        // A positive xRot swings a limb's down-axis toward +z, i.e. behind the figure; negative
        // drives it forward. So the arc runs positive while the arms draw back, then overshoots
        // negative as they fling out and the shockwave leaves.
        float arc;
        if (p < WINDUP_END) {
            arc = (p / WINDUP_END) * WINDUP_ARC;
        } else if (p < STRIKE_END) {
            // Eased so the arms accelerate out rather than travelling at a constant rate.
            float k = (p - WINDUP_END) / (STRIKE_END - WINDUP_END);
            arc = Mth.lerp(k * k, WINDUP_ARC, STRIKE_ARC);
        } else {
            arc = STRIKE_ARC * (1.0f - (p - STRIKE_END) / (1.0f - STRIKE_END));
        }
        this.armL.xRot += arc;
        this.armR.xRot += arc;
        // The wrists snap through a little after the shoulders, which is what sells the release.
        this.foreArmL.xRot -= arc * 0.4f;
        this.foreArmR.xRot -= arc * 0.4f;
        // The shells lurch as the wave leaves and settle again, peaking mid-strike.
        float lurch = Mth.sin(p * (float) Math.PI);
        this.orbWide.zRot += lurch * 0.45f;
        this.orbTall.zRot -= lurch * 0.45f;
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();

        // Each cube's texOffs points at its own packed UV island — do not hand-edit these; they are
        // assigned by tools/blockbench_export.py and guarded by ModelUvLayoutTest.
        //
        // Vertical layout, y+ pointing down: the ball spans y=2..22 about its centre at 12, and the
        // figure hangs inside it — skull 3..8, torso 8..16, legs trailing to ~23, so the feet just
        // clear the bottom of the ball. Everything stays inside y=0..24, which the renderer then
        // blows up past the hitbox: at its 1.95 scale those 24 units are ~2.9 blocks against a
        // 1.99-block box. Deliberate, and argued at ArchonRenderer.scale — grow the figure here
        // rather than the scale there if that ever needs pulling back.

        // --- The ball of light ------------------------------------------------------------
        // TRANSPARENT in archon.png, painted in archon_glow.png. See the class doc: these four are
        // drawn only by UnitGlowLayer, and painting one into the body texture turns the ball into a
        // crate. Three shells of one volume stretched along each axis in turn, so the union reads
        // round and no single face dominates the silhouette.
        PartDefinition orb = root.addOrReplaceChild("orb",
                CubeListBuilder.create().texOffs(0, 0).addBox(-8.0f, -8.0f, -8.0f, 16.0f, 16.0f, 16.0f),
                PartPose.offset(0.0f, 12.0f, 0.0f));
        orb.addOrReplaceChild("orbWide",
                CubeListBuilder.create().texOffs(0, 34).addBox(-10.0f, -6.0f, -6.0f, 20.0f, 12.0f, 12.0f),
                PartPose.ZERO);
        orb.addOrReplaceChild("orbTall",
                CubeListBuilder.create().texOffs(0, 60).addBox(-6.0f, -10.0f, -6.0f, 12.0f, 20.0f, 12.0f),
                PartPose.ZERO);
        orb.addOrReplaceChild("orbDeep",
                CubeListBuilder.create().texOffs(0, 94).addBox(-6.0f, -6.0f, -10.0f, 12.0f, 12.0f, 20.0f),
                PartPose.ZERO);

        // --- The figure (y 3..23) ---------------------------------------------------------
        // Narrow and bare. Everything hangs off the torso, legs included, so the hover bob moves the
        // whole figure as one and the ball can drift against it.
        PartDefinition body = root.addOrReplaceChild("body",
                CubeListBuilder.create().texOffs(66, 0).addBox(-3.0f, 0.0f, -2.0f, 6.0f, 8.0f, 4.0f),
                PartPose.offset(0.0f, 8.0f, 0.0f));

        // A bare Protoss head, the Zealot's silhouette without the helmet gold: a broad cranium plus
        // a narrower forward face, so the skull tapers toward the eyes rather than reading as a box.
        PartDefinition head = body.addOrReplaceChild("head",
                CubeListBuilder.create().texOffs(88, 0).addBox(-2.5f, -5.0f, -2.5f, 5.0f, 5.0f, 5.0f),
                PartPose.offsetAndRotation(0.0f, 0.0f, 0.0f, 0.2f, 0.0f, 0.0f));
        head.addOrReplaceChild("face",
                CubeListBuilder.create().texOffs(66, 14).addBox(-1.5f, -4.0f, -4.5f, 3.0f, 3.0f, 2.0f),
                PartPose.ZERO);
        // Two lit apertures set into the face, proud of it by a fifth of a texel so they never
        // z-fight with the skin behind them. On the figure, these and nothing else glow.
        head.addOrReplaceChild("eyeL",
                CubeListBuilder.create().texOffs(78, 14).addBox(0.4f, -3.4f, -4.7f, 1.0f, 1.0f, 1.0f),
                PartPose.ZERO);
        head.addOrReplaceChild("eyeR",
                CubeListBuilder.create().texOffs(84, 14).addBox(-1.4f, -3.4f, -4.7f, 1.0f, 1.0f, 1.0f),
                PartPose.ZERO);
        // Nerve cords trailing off the back of the skull, as on the Zealot.
        head.addOrReplaceChild("braidL",
                CubeListBuilder.create().texOffs(88, 12).addBox(-1.0f, 0.0f, -1.0f, 2.0f, 7.0f, 2.0f),
                PartPose.offsetAndRotation(1.2f, -2.0f, 2.0f, 0.4f, 0.0f, -0.15f));
        head.addOrReplaceChild("braidR",
                CubeListBuilder.create().texOffs(98, 12).addBox(-1.0f, 0.0f, -1.0f, 2.0f, 7.0f, 2.0f),
                PartPose.offsetAndRotation(-1.2f, -2.0f, 2.0f, 0.4f, 0.0f, 0.15f));

        // Arms flung wide and down, the pose the reference art holds them in — an Archon carries
        // nothing, so there is no reason to fold them the way a Terran's are. A part's up-axis
        // swings toward +x only for a positive net zRot, so the left arm needs a negative one to
        // lean outward; getting the sign backwards tucks both arms into the torso.
        PartDefinition armL = body.addOrReplaceChild("armL",
                CubeListBuilder.create().texOffs(66, 22).addBox(-1.5f, -1.0f, -1.5f, 3.0f, 6.0f, 3.0f),
                PartPose.offsetAndRotation(3.5f, 1.0f, 0.0f, 0.0f, 0.0f, -0.9f));
        armL.addOrReplaceChild("foreArmL",
                CubeListBuilder.create().texOffs(94, 22).addBox(-1.5f, 0.0f, -1.5f, 3.0f, 6.0f, 3.0f),
                PartPose.offsetAndRotation(0.0f, 5.0f, 0.0f, -0.2f, 0.0f, -0.3f));
        PartDefinition armR = body.addOrReplaceChild("armR",
                CubeListBuilder.create().texOffs(80, 22).addBox(-1.5f, -1.0f, -1.5f, 3.0f, 6.0f, 3.0f),
                PartPose.offsetAndRotation(-3.5f, 1.0f, 0.0f, 0.0f, 0.0f, 0.9f));
        armR.addOrReplaceChild("foreArmR",
                CubeListBuilder.create().texOffs(66, 33).addBox(-1.5f, 0.0f, -1.5f, 3.0f, 6.0f, 3.0f),
                PartPose.offsetAndRotation(0.0f, 5.0f, 0.0f, -0.2f, 0.0f, 0.3f));

        // Legs hanging and raked back rather than planted: the unit floats, and the feet ending just
        // below the ball is what shows it. The baked rake is what the walk drift stacks onto.
        PartDefinition legL = body.addOrReplaceChild("legL",
                CubeListBuilder.create().texOffs(80, 33).addBox(-1.5f, 0.0f, -1.5f, 3.0f, 6.0f, 3.0f),
                PartPose.offsetAndRotation(2.0f, 8.0f, 0.0f, 0.6f, 0.0f, 0.0f));
        legL.addOrReplaceChild("shinL",
                CubeListBuilder.create().texOffs(66, 44).addBox(-1.5f, 0.0f, -1.5f, 3.0f, 5.0f, 3.0f),
                PartPose.offsetAndRotation(0.0f, 6.0f, 0.0f, 0.5f, 0.0f, 0.0f));
        PartDefinition legR = body.addOrReplaceChild("legR",
                CubeListBuilder.create().texOffs(94, 33).addBox(-1.5f, 0.0f, -1.5f, 3.0f, 6.0f, 3.0f),
                PartPose.offsetAndRotation(-2.0f, 8.0f, 0.0f, 0.6f, 0.0f, 0.0f));
        legR.addOrReplaceChild("shinR",
                CubeListBuilder.create().texOffs(80, 44).addBox(-1.5f, 0.0f, -1.5f, 3.0f, 5.0f, 3.0f),
                PartPose.offsetAndRotation(0.0f, 6.0f, 0.0f, 0.5f, 0.0f, 0.0f));

        return LayerDefinition.create(mesh, 128, 128);
    }
}
