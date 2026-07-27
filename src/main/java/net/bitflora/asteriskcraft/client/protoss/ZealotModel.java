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
 * Original blocky Zealot model authored for AsteriskCraft from the reference art — a hulking armoured
 * warrior rather than the chunky vanilla-style biped this used to be. Silhouette cues, working down:
 * a bare Protoss head, tapering to a narrow face with lit eyes and trailing four nerve cords;
 * enormous pauldrons whose horns sweep up and out past the shoulders; a layered chest around a
 * glowing gem; a waist tasset; armoured thighs with forward knee spikes ending in heavy hooves; and
 * the signature twin psi-blades projecting straight out of the forearms.
 *
 * <p>The head is deliberately <em>not</em> a helmet — Zealots go bare-headed, so the skull is painted
 * in flesh while everything else is gold plate. That colour split is carried entirely by the texture;
 * nothing here distinguishes the two.
 *
 * <p>The emissive zones lit by {@link net.bitflora.asteriskcraft.client.UnitGlowLayer} are the blades,
 * the eyes and the gems — everything else must stay transparent in {@code zealot_glow.png}, since
 * that layer re-submits the whole model.
 *
 * <p>Every cube owns its own UV island; the texture is hand-painted and the layout is assigned by
 * tools/blockbench_export.py. That tooling constrains how this class may be written — one
 * {@code texOffs} literal per cube, builders inlined into their {@code addOrReplaceChild} call, and
 * globally unique part names (the round-trip verifier keys parts by leaf name, so {@code shoulderL}
 * and {@code shoulderR} may not both be called {@code shoulder}). See docs/texturing.md.
 *
 * <p>setupAnim drives head-look, a limb-swing walk, and a two-handed cross-slash off
 * {@link ZealotRenderState#attackProgress}. Everything stacks as a delta onto the baked pose, since
 * {@code super.setupAnim} restores every part first.
 */
public class ZealotModel extends EntityModel<ZealotRenderState> {
    /** Fraction of the strike spent winding the blades back before they come down. */
    private static final float WINDUP_END = 0.3f;
    /** Fraction of the strike at which the blades have finished travelling forward. */
    private static final float STRIKE_END = 0.65f;
    /**
     * How far back the blades tuck during the wind-up, in radians. Deliberately shallow: the arms
     * pivot inside the pauldrons' volume, so anything like a full overhead cock (~2.2, which is what
     * it would take to raise the blades behind the shoulders) drags them straight through the
     * shoulder plates. This is a rising cross-slash, not a chop, for that reason.
     */
    private static final float WINDUP_ARC = 0.6f;
    /** How far forward and up they carry at full extension, in radians. */
    private static final float STRIKE_ARC = -1.5f;

    private final ModelPart head;
    private final ModelPart body;
    private final ModelPart legL;
    private final ModelPart legR;
    private final ModelPart armL;
    private final ModelPart armR;
    private final ModelPart foreArmL;
    private final ModelPart foreArmR;

    public ZealotModel(ModelPart root) {
        super(root);
        this.head = root.getChild("head");
        this.body = root.getChild("body");
        this.armL = this.body.getChild("armL");
        this.armR = this.body.getChild("armR");
        this.foreArmL = this.armL.getChild("foreArmL");
        this.foreArmR = this.armR.getChild("foreArmR");
        this.legL = root.getChild("legL");
        this.legR = root.getChild("legR");
    }

    @Override
    public void setupAnim(ZealotRenderState state) {
        // super restores every part to its baked PartPose, so everything below adds a delta onto it.
        super.setupAnim(state);

        this.head.yRot += state.yRot * ((float) Math.PI / 180f);
        this.head.xRot += state.xRot * ((float) Math.PI / 180f);

        float swing = Mth.cos(state.walkAnimationPos * 0.6662f) * 1.4f * state.walkAnimationSpeed;
        this.legL.xRot += swing;
        this.legR.xRot -= swing;
        this.armL.xRot -= swing;
        this.armR.xRot += swing;

        float p = state.attackProgress;
        if (p <= 0.0f) {
            return;
        }
        // A positive xRot swings a limb's down-axis toward +z, i.e. behind the Zealot; negative drives
        // it forward and up. So the arc runs positive while the blades tuck back, crosses zero as
        // they come through, and overshoots negative as they sweep up and across the target.
        float arc;
        float cross;
        if (p < WINDUP_END) {
            float k = p / WINDUP_END;
            arc = k * WINDUP_ARC;
            cross = k;
        } else if (p < STRIKE_END) {
            // Eased so the blades accelerate into the target rather than travelling at a constant rate.
            float k = (p - WINDUP_END) / (STRIKE_END - WINDUP_END);
            arc = Mth.lerp(k * k, WINDUP_ARC, STRIKE_ARC);
            cross = 1.0f - k;
        } else {
            float k = (p - STRIKE_END) / (1.0f - STRIKE_END);
            arc = STRIKE_ARC * (1.0f - k);
            cross = -k * 0.35f;
        }

        this.armL.xRot += arc;
        this.armR.xRot += arc;
        // The blades scissor inward across the body as they come through, rather than the two arms
        // swinging in parallel — that crossing sweep is the signature twin-blade strike.
        this.armL.zRot += cross * 0.6f;
        this.armR.zRot -= cross * 0.6f;
        // The wrists snap through a little after the shoulders, which is what sells the strike.
        this.foreArmL.xRot -= arc * 0.35f;
        this.foreArmR.xRot -= arc * 0.35f;
        // A slight shoulder twist into the swing.
        this.body.yRot += cross * 0.15f;
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();

        // Each cube's texOffs points at its own packed UV island — do not hand-edit these; they are
        // assigned by tools/blockbench_export.py and guarded by ModelUvLayoutTest.
        //
        // Vertical layout, y+ pointing down: horn tips ~-9, skull -6.5..-0.5, torso 0..10, hips at 10,
        // thigh 10..16, shin 16..21, hoof 21..24. Splitting the figure roughly a third torso to a half
        // legs is what stops it reading as the squat chibi it used to be, and the horns are the tallest
        // thing on the model, which is what the entity's 2.0-block height is sized against.

        // --- Head (pivot at the neck) ------------------------------------------------------
        // A bare Protoss head, not a helmet: the skull itself is the silhouette, painted in flesh
        // rather than the gold used everywhere else on the model. Built as a broad cranium plus a
        // narrower forward face so the skull tapers toward the eyes instead of reading as one box,
        // and tilted forward so it leans over the chest. setupAnim's head-look pitch stacks on top
        // of this baked tilt.
        // Sat a little proud of the torso so the skull clears the pauldrons instead of being wedged
        // between them; the neck below covers the gap that opens up.
        PartDefinition head = root.addOrReplaceChild("head",
                CubeListBuilder.create().texOffs(0, 17).addBox(-3.5f, -6.0f, -3.5f, 7.0f, 6.0f, 7.0f),
                PartPose.offsetAndRotation(0.0f, -1.5f, -1.0f, 0.35f, 0.0f, 0.0f));
        head.addOrReplaceChild("face",
                CubeListBuilder.create().texOffs(110, 43).addBox(-2.5f, -4.5f, -6.5f, 5.0f, 4.0f, 3.0f),
                PartPose.ZERO);
        // Eyes, not a visor slit — two lit apertures set into the face, proud of it by a fifth of a
        // texel so they never z-fight with the skin behind them.
        head.addOrReplaceChild("eyeL",
                CubeListBuilder.create().texOffs(44, 54).addBox(0.6f, -3.8f, -6.7f, 2.0f, 2.0f, 1.0f),
                PartPose.ZERO);
        head.addOrReplaceChild("eyeR",
                CubeListBuilder.create().texOffs(51, 54).addBox(-2.6f, -3.8f, -6.7f, 2.0f, 2.0f, 1.0f),
                PartPose.ZERO);
        // Nerve cords trailing off the back of the skull — four of them, an inner pair and a shorter
        // outer pair splayed wider, so the back of the head isn't two lonely rods.
        head.addOrReplaceChild("braidL",
                CubeListBuilder.create().texOffs(42, 31).addBox(-1.0f, 0.0f, -1.0f, 2.0f, 9.0f, 2.0f),
                PartPose.offsetAndRotation(1.6f, -2.0f, 3.0f, 0.35f, 0.0f, -0.12f));
        head.addOrReplaceChild("braidR",
                CubeListBuilder.create().texOffs(51, 31).addBox(-1.0f, 0.0f, -1.0f, 2.0f, 9.0f, 2.0f),
                PartPose.offsetAndRotation(-1.6f, -2.0f, 3.0f, 0.35f, 0.0f, 0.12f));
        head.addOrReplaceChild("braidL2",
                CubeListBuilder.create().texOffs(73, 43).addBox(-1.0f, 0.0f, -1.0f, 2.0f, 7.0f, 2.0f),
                PartPose.offsetAndRotation(3.4f, -3.0f, 2.4f, 0.5f, 0.0f, -0.32f));
        head.addOrReplaceChild("braidR2",
                CubeListBuilder.create().texOffs(82, 43).addBox(-1.0f, 0.0f, -1.0f, 2.0f, 7.0f, 2.0f),
                PartPose.offsetAndRotation(-3.4f, -3.0f, 2.4f, 0.5f, 0.0f, 0.32f));

        // --- Body / torso (y 0..10) --------------------------------------------------------
        // Deliberately narrow at 8 wide: the chest plate flares wider than the waist does, which is
        // what gives the broad-shouldered, tapered profile the art has.
        PartDefinition body = root.addOrReplaceChild("body",
                CubeListBuilder.create().texOffs(0, 0).addBox(-4.0f, 0.0f, -3.0f, 8.0f, 10.0f, 6.0f),
                PartPose.offset(0.0f, 0.0f, 0.0f));
        // Bare neck bridging the torso to the raised skull. Painted flesh, like the head — it is what
        // stops a gap opening under the jaw. Hung off the body rather than the head so it doesn't
        // swing with head-look.
        body.addOrReplaceChild("neck",
                CubeListBuilder.create().texOffs(91, 43).addBox(-2.5f, -2.0f, -2.0f, 5.0f, 3.0f, 4.0f),
                PartPose.offset(0.0f, 0.0f, -0.5f));
        // A wider, deeper box crossed over the upper torso, so the chest reads as a rounded armoured
        // mass overhanging the waist instead of one flat slab. Same trick as the Dragoon's body pod.
        body.addOrReplaceChild("chestPlate",
                CubeListBuilder.create().texOffs(29, 0).addBox(-5.5f, 0.0f, -4.0f, 11.0f, 6.0f, 8.0f),
                PartPose.offset(0.0f, 0.5f, 0.0f));
        // Chest diamond, proud of the front of the plate. Glows.
        body.addOrReplaceChild("gem",
                CubeListBuilder.create().texOffs(26, 54).addBox(-1.5f, 0.0f, -4.6f, 3.0f, 3.0f, 1.0f),
                PartPose.offset(0.0f, 2.5f, 0.0f));
        // Waist tasset, hung over the tops of the thighs.
        body.addOrReplaceChild("skirt",
                CubeListBuilder.create().texOffs(51, 17).addBox(-5.0f, 8.0f, -3.5f, 10.0f, 4.0f, 7.0f),
                PartPose.offset(0.0f, 0.0f, 0.0f));
        body.addOrReplaceChild("skirtGem",
                CubeListBuilder.create().texOffs(35, 54).addBox(-1.5f, 0.0f, -4.1f, 3.0f, 2.0f, 1.0f),
                PartPose.offset(0.0f, 8.5f, 0.0f));

        // --- Pauldrons: heavy plates cocked up and out, each carrying an upswept horn -------
        // Written out per side rather than through a helper: the texture tooling needs one editable
        // texOffs literal per cube, and the round-trip verifier needs unique part names. See
        // docs/texturing.md.
        PartDefinition shoulderL = body.addOrReplaceChild("shoulderL",
                CubeListBuilder.create().texOffs(68, 0).addBox(-1.0f, -4.0f, -3.5f, 6.0f, 7.0f, 7.0f),
                PartPose.offsetAndRotation(4.5f, 1.0f, 0.0f, 0.0f, 0.0f, -0.45f));
        // A part's up-axis swings toward +x only for a *positive* net zRot, and the horn inherits the
        // pauldron's -0.45 — so the left horn needs a local +1.05 to end up leaning up and outward.
        // Getting this sign wrong sweeps the horns inward across the head, where they vanish behind
        // it. Length is capped at 6 so the tips top out near model y=-8.8, which keeps the rendered
        // figure inside the 1.95-block hitbox.
        shoulderL.addOrReplaceChild("hornL",
                CubeListBuilder.create().texOffs(51, 43).addBox(-1.0f, -6.0f, -1.5f, 2.0f, 6.0f, 3.0f),
                PartPose.offsetAndRotation(4.0f, -3.5f, -0.5f, 0.0f, 0.0f, 1.05f));
        PartDefinition shoulderR = body.addOrReplaceChild("shoulderR",
                CubeListBuilder.create().texOffs(95, 0).addBox(-5.0f, -4.0f, -3.5f, 6.0f, 7.0f, 7.0f),
                PartPose.offsetAndRotation(-4.5f, 1.0f, 0.0f, 0.0f, 0.0f, 0.45f));
        shoulderR.addOrReplaceChild("hornR",
                CubeListBuilder.create().texOffs(62, 43).addBox(-1.0f, -6.0f, -1.5f, 2.0f, 6.0f, 3.0f),
                PartPose.offsetAndRotation(-4.0f, -3.5f, -0.5f, 0.0f, 0.0f, -1.05f));

        // --- Arms (pivot under the pauldrons) ----------------------------------------------
        PartDefinition armL = body.addOrReplaceChild("armL",
                CubeListBuilder.create().texOffs(106, 31).addBox(-2.0f, -1.0f, -2.0f, 4.0f, 6.0f, 4.0f),
                PartPose.offset(5.5f, 2.0f, 0.0f));
        // Heavier gauntlet below the elbow, cocked slightly forward.
        PartDefinition foreArmL = armL.addOrReplaceChild("foreArmL",
                CubeListBuilder.create().texOffs(86, 17).addBox(-2.5f, 0.0f, -2.5f, 5.0f, 6.0f, 5.0f),
                PartPose.offsetAndRotation(0.0f, 5.0f, 0.0f, -0.15f, 0.0f, 0.0f));
        // Psi-blade: one short segment projecting straight out of the gauntlet, in line with the
        // forearm rather than hinged off at an angle. Only a slight forward rake, just enough that
        // the blade leads the arm. It is 1 thick across the body by 4 deep, so it reads as a flat
        // blade seen edge-on, the way the art draws it.
        foreArmL.addOrReplaceChild("bladeL",
                CubeListBuilder.create().texOffs(29, 17).addBox(-0.5f, 0.0f, -3.0f, 1.0f, 9.0f, 4.0f),
                PartPose.offsetAndRotation(0.0f, 5.0f, -0.5f, -0.25f, 0.0f, -0.12f));

        PartDefinition armR = body.addOrReplaceChild("armR",
                CubeListBuilder.create().texOffs(0, 43).addBox(-2.0f, -1.0f, -2.0f, 4.0f, 6.0f, 4.0f),
                PartPose.offset(-5.5f, 2.0f, 0.0f));
        PartDefinition foreArmR = armR.addOrReplaceChild("foreArmR",
                CubeListBuilder.create().texOffs(107, 17).addBox(-2.5f, 0.0f, -2.5f, 5.0f, 6.0f, 5.0f),
                PartPose.offsetAndRotation(0.0f, 5.0f, 0.0f, -0.15f, 0.0f, 0.0f));
        foreArmR.addOrReplaceChild("bladeR",
                CubeListBuilder.create().texOffs(40, 17).addBox(-0.5f, 0.0f, -3.0f, 1.0f, 9.0f, 4.0f),
                PartPose.offsetAndRotation(0.0f, 5.0f, -0.5f, -0.25f, 0.0f, 0.12f));

        // --- Legs (hips at y=10; thigh 6 + shin 5 + hoof 3 lands the feet on y=24) ----------
        // Splayed to x=+-3 with 4-wide hooves, so the feet read as two boots with a gap between them
        // rather than one merged slab.
        PartDefinition legL = root.addOrReplaceChild("legL",
                CubeListBuilder.create().texOffs(0, 31).addBox(-2.5f, 0.0f, -2.5f, 5.0f, 6.0f, 5.0f),
                PartPose.offset(3.0f, 10.0f, 0.0f));
        legL.addOrReplaceChild("kneeL",
                CubeListBuilder.create().texOffs(0, 54).addBox(-1.5f, 0.0f, -3.0f, 3.0f, 3.0f, 3.0f),
                PartPose.offsetAndRotation(0.0f, 4.5f, -2.0f, -0.35f, 0.0f, 0.0f));
        PartDefinition shinL = legL.addOrReplaceChild("shinL",
                CubeListBuilder.create().texOffs(17, 43).addBox(-2.0f, 0.0f, -2.0f, 4.0f, 5.0f, 4.0f),
                PartPose.offset(0.0f, 6.0f, 0.0f));
        shinL.addOrReplaceChild("footL",
                CubeListBuilder.create().texOffs(60, 31).addBox(-2.0f, 0.0f, -4.5f, 4.0f, 3.0f, 7.0f),
                PartPose.offset(0.0f, 5.0f, 0.0f));

        PartDefinition legR = root.addOrReplaceChild("legR",
                CubeListBuilder.create().texOffs(21, 31).addBox(-2.5f, 0.0f, -2.5f, 5.0f, 6.0f, 5.0f),
                PartPose.offset(-3.0f, 10.0f, 0.0f));
        legR.addOrReplaceChild("kneeR",
                CubeListBuilder.create().texOffs(13, 54).addBox(-1.5f, 0.0f, -3.0f, 3.0f, 3.0f, 3.0f),
                PartPose.offsetAndRotation(0.0f, 4.5f, -2.0f, -0.35f, 0.0f, 0.0f));
        PartDefinition shinR = legR.addOrReplaceChild("shinR",
                CubeListBuilder.create().texOffs(34, 43).addBox(-2.0f, 0.0f, -2.0f, 4.0f, 5.0f, 4.0f),
                PartPose.offset(0.0f, 6.0f, 0.0f));
        shinR.addOrReplaceChild("footR",
                CubeListBuilder.create().texOffs(83, 31).addBox(-2.0f, 0.0f, -4.5f, 4.0f, 3.0f, 7.0f),
                PartPose.offset(0.0f, 5.0f, 0.0f));

        return LayerDefinition.create(mesh, 128, 128);
    }
}

