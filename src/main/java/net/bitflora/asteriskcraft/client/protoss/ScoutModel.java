package net.bitflora.asteriskcraft.client.protoss;

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
 * Original blocky Scout model authored for AsteriskCraft, from the reference art: a horizontal
 * fighter craft rather than a creature. A long gold fuselage tapers forward into a pointed nose,
 * flanked by two forward-swept prongs; a low canopy sits on the spine; two blades sweep back and out
 * from the flanks; and two heavy engine nacelles ride short pylons at the rear, ending in exhausts
 * that the glow pass lights (see {@link net.bitflora.asteriskcraft.client.UnitGlowLayer}). A slim tail
 * boom with an upright fin closes the silhouette.
 *
 * <p>Like {@code MutaliskModel} the body is centred in the air rather than standing on y=24 — the
 * entity itself is what hovers (see {@code ScoutEntity}), so the model just floats around its own
 * origin.
 *
 * <p>Everything animates off {@code ageInTicks} alone: a slow bank that the wingtips flex into, a
 * gentle bob on its own phase, and a tail-boom sway lagging behind the bank. There is deliberately no
 * walk cycle — {@code walkAnimationSpeed} stays near zero on a flyer, which would freeze a
 * limb-swing model solid.
 */
public class ScoutModel extends EntityModel<LivingEntityRenderState> {
    /** Radians per tick of the lazy roll — one full bank cycle roughly every 2.5 minutes. */
    private static final float BANK_RATE = 0.04f;
    /** Radians per tick of the vertical bob, deliberately off the bank's phase. */
    private static final float BOB_RATE = 0.09f;

    private final ModelPart body;
    private final ModelPart wingTipL;
    private final ModelPart wingTipR;
    private final ModelPart tail;

    public ScoutModel(ModelPart root) {
        super(root);
        this.body = root.getChild("body");
        this.wingTipL = this.body.getChild("wingL").getChild("wingTipL");
        this.wingTipR = this.body.getChild("wingR").getChild("wingTipR");
        this.tail = this.body.getChild("tail");
    }

    @Override
    public void setupAnim(LivingEntityRenderState state) {
        // super restores every part to its baked PartPose, so everything below adds a delta onto it.
        super.setupAnim(state);

        float t = state.ageInTicks;
        float bank = Mth.sin(t * BANK_RATE);
        float bob = Mth.sin(t * BOB_RATE);

        this.body.zRot += bank * 0.10f;
        // +y is down in model space, so subtracting lifts the craft on the upbeat.
        this.body.y -= bob * 0.35f;

        // The blades flex into the roll: the tips mirror each other, so the craft banks as one piece
        // rather than the wings pivoting rigidly with the fuselage.
        this.wingTipL.zRot -= bank * 0.15f;
        this.wingTipR.zRot += bank * 0.15f;

        // The boom trails the bank by about a fifth of a cycle.
        this.tail.yRot += Mth.cos(t * BANK_RATE - 0.8f) * 0.06f;
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();

        // Each cube's texOffs points at its own packed UV island — do not hand-edit these; they are
        // assigned by tools/blockbench_export.py and guarded by ModelUvLayoutTest.
        // "body" is the main fuselage; everything else hangs off it. y=15 puts the craft's centreline
        // half a block above the entity position, i.e. the middle of its shallow hitbox.
        PartDefinition body = root.addOrReplaceChild("body",
                CubeListBuilder.create().texOffs(0, 0).addBox(-3.0f, -2.5f, -5.0f, 6.0f, 5.0f, 11.0f),
                PartPose.offset(0.0f, 15.0f, 0.0f));

        // --- Nose: two tapering steps forward (-z), ending in a point --------------------------
        PartDefinition nose = body.addOrReplaceChild("nose",
                CubeListBuilder.create().texOffs(31, 17).addBox(-2.0f, -1.75f, -5.0f, 4.0f, 3.5f, 5.0f),
                PartPose.offset(0.0f, 0.25f, -5.0f));
        nose.addOrReplaceChild("noseTip",
                CubeListBuilder.create().texOffs(89, 27).addBox(-1.0f, -1.0f, -4.0f, 2.0f, 2.0f, 4.0f),
                PartPose.offset(0.0f, 0.0f, -5.0f));

        // --- Forward-swept prongs flanking the nose ---------------------------------------------
        // Written out per side rather than through a helper: the texture tooling needs one editable
        // texOffs literal per cube. See docs/texturing.md.
        body.addOrReplaceChild("prongL",
                CubeListBuilder.create().texOffs(69, 17).addBox(0.0f, -0.75f, -7.0f, 1.5f, 1.5f, 7.0f),
                PartPose.offsetAndRotation(2.0f, 0.0f, -3.0f, 0.0f, -0.35f, 0.0f));
        body.addOrReplaceChild("prongR",
                CubeListBuilder.create().texOffs(87, 17).addBox(-1.5f, -0.75f, -7.0f, 1.5f, 1.5f, 7.0f),
                PartPose.offsetAndRotation(-2.0f, 0.0f, -3.0f, 0.0f, 0.35f, 0.0f));

        // --- Canopy: a low blister on the spine (lit by the glow pass) --------------------------
        body.addOrReplaceChild("canopy",
                CubeListBuilder.create().texOffs(42, 27).addBox(-1.5f, -2.0f, -2.5f, 3.0f, 2.0f, 5.0f),
                PartPose.offset(0.0f, -2.5f, -1.5f));

        // --- Wings: swept blades in two segments so the tips can flex into a bank ---------------
        PartDefinition wingL = body.addOrReplaceChild("wingL",
                CubeListBuilder.create().texOffs(89, 0).addBox(0.0f, -0.75f, -3.5f, 8.0f, 1.5f, 7.0f),
                PartPose.offsetAndRotation(3.0f, -0.5f, 1.0f, 0.0f, 0.3f, -0.12f));
        wingL.addOrReplaceChild("wingTipL",
                CubeListBuilder.create().texOffs(0, 27).addBox(0.0f, -0.6f, -2.5f, 5.0f, 1.2f, 5.0f),
                PartPose.offsetAndRotation(8.0f, 0.0f, 0.5f, 0.0f, 0.25f, -0.15f));

        PartDefinition wingR = body.addOrReplaceChild("wingR",
                CubeListBuilder.create().texOffs(0, 17).addBox(-8.0f, -0.75f, -3.5f, 8.0f, 1.5f, 7.0f),
                PartPose.offsetAndRotation(-3.0f, -0.5f, 1.0f, 0.0f, -0.3f, 0.12f));
        wingR.addOrReplaceChild("wingTipR",
                CubeListBuilder.create().texOffs(21, 27).addBox(-5.0f, -0.6f, -2.5f, 5.0f, 1.2f, 5.0f),
                PartPose.offsetAndRotation(-8.0f, 0.0f, 0.5f, 0.0f, -0.25f, 0.15f));

        // --- Engines: a short pylon carrying a heavy nacelle, exhaust at the back ---------------
        PartDefinition pylonL = body.addOrReplaceChild("pylonL",
                CubeListBuilder.create().texOffs(59, 27).addBox(-1.0f, -1.0f, -2.0f, 2.0f, 2.0f, 5.0f),
                PartPose.offset(4.5f, -1.5f, 2.5f));
        PartDefinition nacelleL = pylonL.addOrReplaceChild("nacelleL",
                CubeListBuilder.create().texOffs(35, 0).addBox(-2.0f, -2.0f, -4.5f, 4.0f, 4.0f, 9.0f),
                PartPose.offset(0.0f, -1.0f, 1.0f));
        nacelleL.addOrReplaceChild("exhaustL",
                CubeListBuilder.create().texOffs(102, 27).addBox(-1.5f, -1.5f, 0.0f, 3.0f, 3.0f, 2.0f),
                PartPose.offset(0.0f, 0.0f, 4.5f));

        PartDefinition pylonR = body.addOrReplaceChild("pylonR",
                CubeListBuilder.create().texOffs(74, 27).addBox(-1.0f, -1.0f, -2.0f, 2.0f, 2.0f, 5.0f),
                PartPose.offset(-4.5f, -1.5f, 2.5f));
        PartDefinition nacelleR = pylonR.addOrReplaceChild("nacelleR",
                CubeListBuilder.create().texOffs(62, 0).addBox(-2.0f, -2.0f, -4.5f, 4.0f, 4.0f, 9.0f),
                PartPose.offset(0.0f, -1.0f, 1.0f));
        nacelleR.addOrReplaceChild("exhaustR",
                CubeListBuilder.create().texOffs(113, 27).addBox(-1.5f, -1.5f, 0.0f, 3.0f, 3.0f, 2.0f),
                PartPose.offset(0.0f, 0.0f, 4.5f));

        // --- Tail boom and upright fin ----------------------------------------------------------
        PartDefinition tail = body.addOrReplaceChild("tail",
                CubeListBuilder.create().texOffs(50, 17).addBox(-1.5f, -1.5f, 0.0f, 3.0f, 3.0f, 6.0f),
                PartPose.offset(0.0f, 0.0f, 6.0f));
        tail.addOrReplaceChild("tailFin",
                CubeListBuilder.create().texOffs(105, 17).addBox(-0.5f, -4.0f, 0.0f, 1.0f, 4.0f, 4.0f),
                PartPose.offset(0.0f, -1.0f, 1.5f));

        return LayerDefinition.create(mesh, 128, 128);
    }
}
