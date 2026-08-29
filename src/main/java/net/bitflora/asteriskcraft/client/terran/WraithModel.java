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
 * Original blocky Wraith model authored for AsteriskCraft, from the reference art. It is the mod's
 * second aircraft and is built to be told apart from {@code client.protoss.ScoutModel} at a glance,
 * since the two share a hitbox, an altitude and a job. Three things carry that, and all three come
 * off the art:
 *
 * <ul>
 *   <li><b>The cannons are in front of the aircraft.</b> Two long barrels slung under the wing roots
 *       run forward <em>past</em> the nose. The Scout's forward prongs flank its nose and stop level
 *       with the tip; the Wraith's overhang it, which is what makes the silhouette read as a gun
 *       being carried rather than a hull being pointed.</li>
 *   <li><b>The wings sweep down, not up.</b> Anhedral rather than the Scout's dihedral, so from the
 *       ground the two aircraft are opposite letters.</li>
 *   <li><b>Twin fins.</b> The Scout closes on a slim boom and one upright blade; the Wraith closes on
 *       a broad tail carrying two canted ones.</li>
 * </ul>
 *
 * <p>The fuselage is wider and flatter than the Scout's for the same reason — a slab rather than a
 * spindle. Like {@code MutaliskModel} and the Scout the body is centred in the air rather than
 * standing on y=24: the entity itself is what hovers (see {@code WraithEntity}), so the model just
 * floats around its own origin.
 *
 * <p>Everything animates off {@code ageInTicks} alone: a slow bank that the wingtips flex into, a
 * gentle bob on its own phase, and a tail sway lagging behind the bank. There is deliberately no walk
 * cycle — {@code walkAnimationSpeed} stays near zero on a flyer, which would freeze a limb-swing
 * model solid — and no strike animation, since {@code UnitStats.WRAITH} declares none.
 *
 * <p>The glow pass ({@code client.UnitGlowLayer}) lights the canopy, the two exhausts and the muzzles
 * of the two cannons; everything else must be transparent in {@code wraith_glow.png}.
 */
public class WraithModel extends EntityModel<LivingEntityRenderState> {
    /** Radians per tick of the lazy roll — a touch quicker than the Scout's, and on its own phase. */
    private static final float BANK_RATE = 0.05f;
    /** Radians per tick of the vertical bob, deliberately off the bank's phase. */
    private static final float BOB_RATE = 0.11f;

    private final ModelPart body;
    private final ModelPart wingTipL;
    private final ModelPart wingTipR;
    private final ModelPart tail;

    public WraithModel(ModelPart root) {
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

        this.body.zRot += bank * 0.12f;
        // +y is down in model space, so subtracting lifts the craft on the upbeat.
        this.body.y -= bob * 0.3f;

        // The panels flex into the roll: the tips mirror each other, so the craft banks as one piece
        // rather than the wings pivoting rigidly with the fuselage.
        this.wingTipL.zRot -= bank * 0.18f;
        this.wingTipR.zRot += bank * 0.18f;

        // The tail trails the bank by about a fifth of a cycle.
        this.tail.yRot += Mth.cos(t * BANK_RATE - 0.8f) * 0.05f;
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();

        // Each cube's texOffs points at its own packed UV island — do not hand-edit these; they are
        // assigned by tools/blockbench_export.py and guarded by ModelUvLayoutTest.
        // "body" is the main fuselage; everything else hangs off it. y=15 puts the craft's centreline
        // half a block above the entity position, i.e. the middle of its shallow hitbox.
        PartDefinition body = root.addOrReplaceChild("body",
                CubeListBuilder.create().texOffs(0, 0).addBox(-3.5f, -2.0f, -6.0f, 7.0f, 4.0f, 12.0f),
                PartPose.offset(0.0f, 15.0f, 0.0f));

        // --- Nose: two tapering steps forward (-z), ending blunt rather than pointed -------------
        PartDefinition nose = body.addOrReplaceChild("nose",
                CubeListBuilder.create().texOffs(38, 0).addBox(-2.5f, -1.5f, -4.0f, 5.0f, 3.0f, 4.0f),
                PartPose.offset(0.0f, 0.25f, -6.0f));
        nose.addOrReplaceChild("noseTip",
                CubeListBuilder.create().texOffs(60, 0).addBox(-1.5f, -1.0f, -3.0f, 3.0f, 2.0f, 3.0f),
                PartPose.offset(0.0f, 0.0f, -4.0f));

        // --- Canopy: a raised cockpit blister well forward on the spine (lit by the glow pass) ----
        body.addOrReplaceChild("canopy",
                CubeListBuilder.create().texOffs(78, 0).addBox(-1.5f, -1.75f, -3.0f, 3.0f, 2.0f, 6.0f),
                PartPose.offset(0.0f, -2.0f, -1.5f));

        // --- Cannons: the silhouette. Slung under the wing roots and overhanging the nose ---------
        // Written out per side rather than through a helper: the texture tooling needs one editable
        // texOffs literal per cube. See docs/texturing.md.
        body.addOrReplaceChild("cannonL",
                CubeListBuilder.create().texOffs(0, 18).addBox(0.0f, -0.75f, -9.0f, 1.5f, 1.5f, 9.0f),
                PartPose.offset(2.75f, 1.25f, -2.0f));
        body.addOrReplaceChild("cannonR",
                CubeListBuilder.create().texOffs(24, 18).addBox(-1.5f, -0.75f, -9.0f, 1.5f, 1.5f, 9.0f),
                PartPose.offset(-2.75f, 1.25f, -2.0f));

        // --- Wings: broad panels canted DOWN, in two segments so the tips can flex into a bank ----
        PartDefinition wingL = body.addOrReplaceChild("wingL",
                CubeListBuilder.create().texOffs(48, 18).addBox(0.0f, -0.75f, -3.0f, 7.0f, 1.5f, 8.0f),
                PartPose.offsetAndRotation(3.5f, -0.5f, 1.5f, 0.0f, 0.35f, 0.25f));
        wingL.addOrReplaceChild("wingTipL",
                CubeListBuilder.create().texOffs(0, 34).addBox(0.0f, -0.6f, -2.5f, 4.0f, 1.2f, 6.0f),
                PartPose.offsetAndRotation(7.0f, 0.0f, 0.5f, 0.0f, 0.2f, 0.3f));

        PartDefinition wingR = body.addOrReplaceChild("wingR",
                CubeListBuilder.create().texOffs(84, 18).addBox(-7.0f, -0.75f, -3.0f, 7.0f, 1.5f, 8.0f),
                PartPose.offsetAndRotation(-3.5f, -0.5f, 1.5f, 0.0f, -0.35f, -0.25f));
        wingR.addOrReplaceChild("wingTipR",
                CubeListBuilder.create().texOffs(22, 34).addBox(-4.0f, -0.6f, -2.5f, 4.0f, 1.2f, 6.0f),
                PartPose.offsetAndRotation(-7.0f, 0.0f, 0.5f, 0.0f, -0.2f, -0.3f));

        // --- Engines: a short pylon carrying a heavy nacelle, exhaust at the back -----------------
        PartDefinition pylonL = body.addOrReplaceChild("pylonL",
                CubeListBuilder.create().texOffs(44, 34).addBox(-1.0f, -1.0f, -2.0f, 2.0f, 2.0f, 4.0f),
                PartPose.offset(3.5f, -1.0f, 3.5f));
        PartDefinition nacelleL = pylonL.addOrReplaceChild("nacelleL",
                CubeListBuilder.create().texOffs(62, 34).addBox(-2.0f, -2.0f, -4.0f, 4.0f, 4.0f, 8.0f),
                PartPose.offset(0.0f, -0.5f, 1.0f));
        nacelleL.addOrReplaceChild("exhaustL",
                CubeListBuilder.create().texOffs(90, 34).addBox(-1.5f, -1.5f, 0.0f, 3.0f, 3.0f, 2.0f),
                PartPose.offset(0.0f, 0.0f, 4.0f));

        PartDefinition pylonR = body.addOrReplaceChild("pylonR",
                CubeListBuilder.create().texOffs(0, 48).addBox(-1.0f, -1.0f, -2.0f, 2.0f, 2.0f, 4.0f),
                PartPose.offset(-3.5f, -1.0f, 3.5f));
        PartDefinition nacelleR = pylonR.addOrReplaceChild("nacelleR",
                CubeListBuilder.create().texOffs(18, 48).addBox(-2.0f, -2.0f, -4.0f, 4.0f, 4.0f, 8.0f),
                PartPose.offset(0.0f, -0.5f, 1.0f));
        nacelleR.addOrReplaceChild("exhaustR",
                CubeListBuilder.create().texOffs(46, 48).addBox(-1.5f, -1.5f, 0.0f, 3.0f, 3.0f, 2.0f),
                PartPose.offset(0.0f, 0.0f, 4.0f));

        // --- Tail: a broad plate rather than the Scout's boom, carrying two canted fins -----------
        PartDefinition tail = body.addOrReplaceChild("tail",
                CubeListBuilder.create().texOffs(64, 48).addBox(-2.0f, -1.5f, 0.0f, 4.0f, 3.0f, 5.0f),
                PartPose.offset(0.0f, 0.0f, 6.0f));
        tail.addOrReplaceChild("finL",
                CubeListBuilder.create().texOffs(90, 48).addBox(-0.5f, -4.5f, 0.0f, 1.0f, 4.5f, 4.0f),
                PartPose.offsetAndRotation(1.5f, -1.0f, 1.0f, 0.0f, 0.0f, -0.25f));
        tail.addOrReplaceChild("finR",
                CubeListBuilder.create().texOffs(104, 48).addBox(-0.5f, -4.5f, 0.0f, 1.0f, 4.5f, 4.0f),
                PartPose.offsetAndRotation(-1.5f, -1.0f, 1.0f, 0.0f, 0.0f, 0.25f));

        return LayerDefinition.create(mesh, 128, 128);
    }
}
