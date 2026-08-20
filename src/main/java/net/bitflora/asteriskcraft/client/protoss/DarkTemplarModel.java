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
 * The Dark Templar: a {@link ZealotModel} silhouette rebuilt as an assassin. Same biped proportions
 * and the same limb skeleton — this is a Zealot's frame, deliberately, so the two read as the same
 * species — but three things replace the Zealot's cues, working down: a deep <b>hood</b> over the
 * face where the Zealot goes bare-headed, a <b>cloak</b> hanging from the shoulders to the calves,
 * and <b>one</b> long warp blade on the right forearm in place of the twin psi-blades.
 *
 * <p>The single blade is the whole point of the pose. It is nearly half again the Zealot's, hangs
 * down and rakes forward past the knee, and the left arm carries nothing at all — so the figure is
 * lopsided in a way the Zealot never is, and the asymmetry is what identifies it at a glance even
 * through the cloak's transparency.
 *
 * <p><b>It is always seen through that transparency.</b> A Dark Templar is permanently
 * {@code faction.Cloaked}, so it never renders at full opacity for anyone — its own side sees a
 * 15%-alpha ghost and an enemy without detection sees nothing. That makes the emissive pass do most
 * of the visual work, and it is why the lit zones here are kept few and bright: only the
 * <b>blade</b> and the <b>eyes</b> may be opaque in {@code dark_templar_glow.png}. Everything else
 * must stay transparent there, since {@link net.bitflora.asteriskcraft.client.UnitGlowLayer}
 * re-submits the whole model. There is deliberately no chest gem — the cloak covers where the
 * Zealot wears one, and a third glow source would muddy the read.
 *
 * <p>Every cube owns its own UV island; the texture is hand-painted and the layout is assigned by
 * tools/blockbench_export.py. That tooling constrains how this class may be written — one
 * {@code texOffs} literal per cube, builders inlined into their {@code addOrReplaceChild} call, and
 * globally unique part names (the round-trip verifier keys parts by leaf name). See
 * docs/texturing.md.
 *
 * <p>setupAnim drives head-look, a limb-swing walk, a trailing cloak, and a one-handed diagonal
 * chop off {@link DarkTemplarRenderState#attackProgress}. Everything stacks as a delta onto the
 * baked pose, since {@code super.setupAnim} restores every part first.
 */
public class DarkTemplarModel extends EntityModel<DarkTemplarRenderState> {
    /** Fraction of the strike spent raising the blade before it comes down. */
    private static final float WINDUP_END = 0.3f;
    /** Fraction of the strike at which the blade has finished travelling. */
    private static final float STRIKE_END = 0.6f;
    /**
     * How far back the blade cocks during the wind-up, in radians. Deeper than the Zealot's 0.6:
     * only one arm moves here, and it swings clear of the right pauldron rather than through both.
     */
    private static final float WINDUP_ARC = 1.0f;
    /** How far forward the arm carries at full extension, in radians. */
    private static final float STRIKE_ARC = -1.3f;
    /** How much of the driving arm's motion the empty left arm mirrors back, as counterbalance. */
    private static final float COUNTERSWING = 0.3f;

    private final ModelPart head;
    private final ModelPart body;
    private final ModelPart legL;
    private final ModelPart legR;
    private final ModelPart armL;
    private final ModelPart armR;
    private final ModelPart foreArmR;
    private final ModelPart capeUpper;
    private final ModelPart capeLower;

    public DarkTemplarModel(ModelPart root) {
        super(root);
        this.head = root.getChild("head");
        this.body = root.getChild("body");
        this.armL = this.body.getChild("armL");
        this.armR = this.body.getChild("armR");
        this.foreArmR = this.armR.getChild("foreArmR");
        this.capeUpper = this.body.getChild("capeUpper");
        this.capeLower = this.capeUpper.getChild("capeLower");
        this.legL = root.getChild("legL");
        this.legR = root.getChild("legR");
    }

    @Override
    public void setupAnim(DarkTemplarRenderState state) {
        // super restores every part to its baked PartPose, so everything below adds a delta onto it.
        super.setupAnim(state);

        this.head.yRot += state.yRot * ((float) Math.PI / 180f);
        this.head.xRot += state.xRot * ((float) Math.PI / 180f);

        float swing = Mth.cos(state.walkAnimationPos * 0.6662f) * 1.4f * state.walkAnimationSpeed;
        this.legL.xRot += swing;
        this.legR.xRot -= swing;
        this.armL.xRot -= swing;
        this.armR.xRot += swing;

        // The cloak trails: it lifts away from the legs with speed rather than with stride phase, so
        // it streams behind a walking Templar instead of flapping once per step. The lower panel
        // lags the upper by half again, which is what keeps it reading as cloth rather than a board.
        float trail = state.walkAnimationSpeed * 0.5f;
        this.capeUpper.xRot += trail;
        this.capeLower.xRot += trail * 0.6f;

        float p = state.attackProgress;
        if (p <= 0.0f) {
            return;
        }
        // A positive xRot swings a limb's down-axis toward +z, i.e. behind the unit; negative drives
        // it forward. So the arc runs positive while the blade cocks back over the shoulder, then
        // crosses hard negative as it comes down and through.
        float arc;
        float roll;
        if (p < WINDUP_END) {
            float k = p / WINDUP_END;
            arc = k * WINDUP_ARC;
            roll = k;
        } else if (p < STRIKE_END) {
            // Eased so the blade accelerates into the target rather than travelling at a constant rate.
            float k = (p - WINDUP_END) / (STRIKE_END - WINDUP_END);
            arc = Mth.lerp(k * k, WINDUP_ARC, STRIKE_ARC);
            roll = 1.0f - k;
        } else {
            float k = (p - STRIKE_END) / (1.0f - STRIKE_END);
            arc = STRIKE_ARC * (1.0f - k);
            roll = -k * 0.3f;
        }

        // One arm does the work. The right shoulder drives the whole arc; the left only leans back
        // against it, which is what makes the strike read as a one-handed chop rather than half of
        // the Zealot's symmetric scissor.
        this.armR.xRot += arc;
        this.armR.zRot -= roll * 0.7f;
        this.armL.xRot -= arc * COUNTERSWING;
        // The wrist snaps through a little after the shoulder, which is what sells the strike.
        this.foreArmR.xRot -= arc * 0.4f;
        // The whole body turns into the swing, and the cloak swings with it because it hangs off the
        // body — no separate bookkeeping needed. Twice the Zealot's twist, since only one arm is
        // contributing reach.
        this.body.yRot += roll * 0.3f;
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();

        // Each cube's texOffs points at its own packed UV island — do not hand-edit these; they are
        // assigned by tools/blockbench_export.py and guarded by ModelUvLayoutTest.
        //
        // Vertical layout, y+ pointing down, matched to the Zealot so the two stand the same height:
        // hood crown ~-7, torso 0..10, hips at 10, thigh 10..16, shin 16..21, hoof 21..24. The blade
        // hangs below the right hand and is the lowest thing on the model, which is intended — it
        // rakes past the knee the way the reference art carries it.

        // --- Head: a hood, where the Zealot has a bare skull ---------------------------------
        // The hood *is* the head cube rather than a shell over one: a Dark Templar's face is never
        // seen, so a skull underneath would be geometry nobody can look at. Tilted forward like the
        // Zealot's, and setupAnim's head-look pitch stacks on top of that baked tilt.
        PartDefinition head = root.addOrReplaceChild("head",
                CubeListBuilder.create().texOffs(42, 0).addBox(-4.0f, -7.0f, -4.0f, 8.0f, 7.0f, 8.0f),
                PartPose.offsetAndRotation(0.0f, -1.5f, -1.0f, 0.35f, 0.0f, 0.0f));
        // The hood's rim, flared wider than the crown so the head reads as cowled rather than boxed.
        head.addOrReplaceChild("cowl",
                CubeListBuilder.create().texOffs(0, 19).addBox(-4.5f, -2.0f, -4.5f, 9.0f, 3.0f, 9.0f),
                PartPose.ZERO);
        // The one bright plate on the unit: the gold mask set back inside the hood's mouth, which is
        // the only part of the reference art that isn't cloth or shadow.
        head.addOrReplaceChild("face",
                CubeListBuilder.create().texOffs(79, 56).addBox(-2.5f, -5.0f, -5.0f, 5.0f, 4.0f, 1.0f),
                PartPose.ZERO);
        // Eyes, proud of the mask by a fifth of a texel so they never z-fight with it. Glows.
        head.addOrReplaceChild("eyeL",
                CubeListBuilder.create().texOffs(92, 56).addBox(0.6f, -4.2f, -5.2f, 2.0f, 1.0f, 1.0f),
                PartPose.ZERO);
        head.addOrReplaceChild("eyeR",
                CubeListBuilder.create().texOffs(99, 56).addBox(-2.6f, -4.2f, -5.2f, 2.0f, 1.0f, 1.0f),
                PartPose.ZERO);
        // Nerve cords, swept back and down rather than the Zealot's upright pair — in the reference
        // they trail behind the hood almost horizontally, which is the strongest read on the whole
        // silhouette after the blade. Inner pair long, outer pair shorter and splayed wider.
        head.addOrReplaceChild("tendrilL1",
                CubeListBuilder.create().texOffs(110, 0).addBox(-1.0f, 0.0f, -1.0f, 2.0f, 11.0f, 2.0f),
                PartPose.offsetAndRotation(1.8f, -3.0f, 3.4f, 1.05f, 0.0f, -0.10f));
        head.addOrReplaceChild("tendrilR1",
                CubeListBuilder.create().texOffs(119, 0).addBox(-1.0f, 0.0f, -1.0f, 2.0f, 11.0f, 2.0f),
                PartPose.offsetAndRotation(-1.8f, -3.0f, 3.4f, 1.05f, 0.0f, 0.10f));
        head.addOrReplaceChild("tendrilL2",
                CubeListBuilder.create().texOffs(101, 44).addBox(-1.0f, 0.0f, -1.0f, 2.0f, 8.0f, 2.0f),
                PartPose.offsetAndRotation(3.6f, -4.0f, 3.0f, 0.85f, 0.0f, -0.34f));
        head.addOrReplaceChild("tendrilR2",
                CubeListBuilder.create().texOffs(110, 44).addBox(-1.0f, 0.0f, -1.0f, 2.0f, 8.0f, 2.0f),
                PartPose.offsetAndRotation(-3.6f, -4.0f, 3.0f, 0.85f, 0.0f, 0.34f));

        // --- Body / torso (y 0..10) ----------------------------------------------------------
        PartDefinition body = root.addOrReplaceChild("body",
                CubeListBuilder.create().texOffs(13, 0).addBox(-4.0f, 0.0f, -3.0f, 8.0f, 10.0f, 6.0f),
                PartPose.offset(0.0f, 0.0f, 0.0f));
        body.addOrReplaceChild("neck",
                CubeListBuilder.create().texOffs(34, 56).addBox(-2.5f, -2.0f, -2.0f, 5.0f, 3.0f, 4.0f),
                PartPose.offset(0.0f, 0.0f, -0.5f));
        // Narrower and shallower than the Zealot's chest plate: this is a lean figure under cloth,
        // not a slab of gold armour.
        body.addOrReplaceChild("chestPlate",
                CubeListBuilder.create().texOffs(75, 0).addBox(-5.0f, 0.0f, -3.5f, 10.0f, 6.0f, 7.0f),
                PartPose.offset(0.0f, 0.5f, 0.0f));
        body.addOrReplaceChild("skirt",
                CubeListBuilder.create().texOffs(0, 32).addBox(-5.0f, 8.0f, -3.5f, 10.0f, 4.0f, 7.0f),
                PartPose.offset(0.0f, 0.0f, 0.0f));

        // --- Cloak: two flat panels down the back, hinged so the lower one can lag -------------
        // Hung off the body rather than off root, so the twist that setupAnim puts into the strike
        // carries the cloak around with it for free. Split in two because a single panel pivoting at
        // the shoulders swings like a plank; the second hinge is what lets it trail.
        PartDefinition capeUpper = body.addOrReplaceChild("capeUpper",
                CubeListBuilder.create().texOffs(37, 19).addBox(-6.0f, 0.0f, 0.0f, 12.0f, 11.0f, 1.0f),
                PartPose.offsetAndRotation(0.0f, 0.5f, 3.2f, 0.08f, 0.0f, 0.0f));
        capeUpper.addOrReplaceChild("capeLower",
                CubeListBuilder.create().texOffs(35, 32).addBox(-5.5f, 0.0f, 0.0f, 11.0f, 10.0f, 1.0f),
                PartPose.offsetAndRotation(0.0f, 11.0f, 0.0f, 0.06f, 0.0f, 0.0f));

        // --- Pauldrons: present but modest, and hornless ---------------------------------------
        // The Zealot's upswept horns are the loudest thing on its silhouette; leaving them off is
        // most of what stops this reading as a recoloured Zealot. Written out per side rather than
        // through a helper — the texture tooling needs one editable texOffs literal per cube, and
        // the round-trip verifier needs unique part names. See docs/texturing.md.
        body.addOrReplaceChild("shoulderL",
                CubeListBuilder.create().texOffs(64, 19).addBox(-1.0f, -3.0f, -3.0f, 5.0f, 6.0f, 6.0f),
                PartPose.offsetAndRotation(4.0f, 1.0f, 0.0f, 0.0f, 0.0f, -0.35f));
        body.addOrReplaceChild("shoulderR",
                CubeListBuilder.create().texOffs(87, 19).addBox(-4.0f, -3.0f, -3.0f, 5.0f, 6.0f, 6.0f),
                PartPose.offsetAndRotation(-4.0f, 1.0f, 0.0f, 0.0f, 0.0f, 0.35f));

        // --- Arms. The left one is empty; that asymmetry is the unit's signature ---------------
        PartDefinition armL = body.addOrReplaceChild("armL",
                CubeListBuilder.create().texOffs(67, 44).addBox(-2.0f, -1.0f, -2.0f, 4.0f, 6.0f, 4.0f),
                PartPose.offset(5.0f, 2.0f, 0.0f));
        armL.addOrReplaceChild("foreArmL",
                CubeListBuilder.create().texOffs(60, 32).addBox(-2.5f, 0.0f, -2.5f, 5.0f, 6.0f, 5.0f),
                PartPose.offsetAndRotation(0.0f, 5.0f, 0.0f, -0.15f, 0.0f, 0.0f));

        PartDefinition armR = body.addOrReplaceChild("armR",
                CubeListBuilder.create().texOffs(84, 44).addBox(-2.0f, -1.0f, -2.0f, 4.0f, 6.0f, 4.0f),
                PartPose.offset(-5.0f, 2.0f, 0.0f));
        PartDefinition foreArmR = armR.addOrReplaceChild("foreArmR",
                CubeListBuilder.create().texOffs(81, 32).addBox(-2.5f, 0.0f, -2.5f, 5.0f, 6.0f, 5.0f),
                PartPose.offsetAndRotation(0.0f, 5.0f, 0.0f, -0.15f, 0.0f, 0.0f));
        // The warp blade: 13 long against the Zealot's 9, and raked further forward so it leads the
        // arm and clears the leg rather than hanging straight down through it. One thick across the
        // body by five deep, so it reads as a flat blade seen edge-on. Glows.
        foreArmR.addOrReplaceChild("bladeR",
                CubeListBuilder.create().texOffs(0, 0).addBox(-0.5f, 0.0f, -3.5f, 1.0f, 13.0f, 5.0f),
                PartPose.offsetAndRotation(0.0f, 5.0f, -0.5f, -0.45f, 0.0f, 0.10f));

        // --- Legs (hips at y=10; thigh 6 + shin 5 + hoof 3 lands the feet on y=24) -------------
        PartDefinition legL = root.addOrReplaceChild("legL",
                CubeListBuilder.create().texOffs(102, 32).addBox(-2.5f, 0.0f, -2.5f, 5.0f, 6.0f, 5.0f),
                PartPose.offset(3.0f, 10.0f, 0.0f));
        legL.addOrReplaceChild("kneeL",
                CubeListBuilder.create().texOffs(53, 56).addBox(-1.5f, 0.0f, -3.0f, 3.0f, 3.0f, 3.0f),
                PartPose.offsetAndRotation(0.0f, 4.5f, -2.0f, -0.35f, 0.0f, 0.0f));
        PartDefinition shinL = legL.addOrReplaceChild("shinL",
                CubeListBuilder.create().texOffs(0, 56).addBox(-2.0f, 0.0f, -2.0f, 4.0f, 5.0f, 4.0f),
                PartPose.offset(0.0f, 6.0f, 0.0f));
        shinL.addOrReplaceChild("footL",
                CubeListBuilder.create().texOffs(21, 44).addBox(-2.0f, 0.0f, -4.5f, 4.0f, 3.0f, 7.0f),
                PartPose.offset(0.0f, 5.0f, 0.0f));

        PartDefinition legR = root.addOrReplaceChild("legR",
                CubeListBuilder.create().texOffs(0, 44).addBox(-2.5f, 0.0f, -2.5f, 5.0f, 6.0f, 5.0f),
                PartPose.offset(-3.0f, 10.0f, 0.0f));
        legR.addOrReplaceChild("kneeR",
                CubeListBuilder.create().texOffs(66, 56).addBox(-1.5f, 0.0f, -3.0f, 3.0f, 3.0f, 3.0f),
                PartPose.offsetAndRotation(0.0f, 4.5f, -2.0f, -0.35f, 0.0f, 0.0f));
        PartDefinition shinR = legR.addOrReplaceChild("shinR",
                CubeListBuilder.create().texOffs(17, 56).addBox(-2.0f, 0.0f, -2.0f, 4.0f, 5.0f, 4.0f),
                PartPose.offset(0.0f, 6.0f, 0.0f));
        shinR.addOrReplaceChild("footR",
                CubeListBuilder.create().texOffs(44, 44).addBox(-2.0f, 0.0f, -4.5f, 4.0f, 3.0f, 7.0f),
                PartPose.offset(0.0f, 5.0f, 0.0f));

        return LayerDefinition.create(mesh, 128, 128);
    }
}
