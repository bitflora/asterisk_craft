package net.bitflora.asteriskcraft.client.zerg;

import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.util.Mth;

/**
 * Original blocky Spore Colony model authored for AsteriskCraft, built to the reference art: a squat
 * purple blob of a body hunkered on the ground, a near-black carapace arcing over its back, four dark
 * stone claws splayed out from underneath it, and — the whole silhouette, really — a stout orange
 * chimney rising out of the front of the body and flaring open at the top. One white eye sits on the
 * front of that chimney. The dark mouth at the top of the flare is the emissive zone lit by
 * {@link net.bitflora.asteriskcraft.client.UnitGlowLayer}, since that is where the spores come out.
 *
 * <p>The art is drawn almost entirely in <b>rounded, inflated shapes</b> — the body is an ellipsoid,
 * the carapace a crescent, the chimney a trumpet. Every one of them is a <b>stepped stack of boxes</b>
 * here: a wide band, a narrower one above it, and so on, which is how a cube model reads as round at
 * Minecraft's resolution. Nothing is a sphere and nothing is scaled at runtime.
 *
 * <p>Each cube owns its own UV island; the texture is hand-painted in Blockbench via
 * tools/blockbench_export.py.
 *
 * <p>Two animations, both up the chimney: a slow idle sway so a rooted structure still reads as alive,
 * and the shot — the chimney kicks back and settles, driven by
 * {@link SporeColonyRenderState#attackProgress}. The body never moves; the colony is rooted and only
 * its body yaw turns to face a target.
 */
public class SporeColonyModel extends EntityModel<SporeColonyRenderState> {
    private final ModelPart chimney;
    private final ModelPart chimneyMid;
    private final ModelPart chimneyRim;
    private final float chimneyX0;
    private final float chimneyMidX0;
    private final float chimneyRimX0;

    public SporeColonyModel(ModelPart root) {
        super(root);
        this.chimney = root.getChild("body").getChild("chimney");
        this.chimneyMid = this.chimney.getChild("chimneyMid");
        this.chimneyRim = this.chimneyMid.getChild("chimneyRim");
        this.chimneyX0 = this.chimney.xRot;
        this.chimneyMidX0 = this.chimneyMid.xRot;
        this.chimneyRimX0 = this.chimneyRim.xRot;
    }

    @Override
    public void setupAnim(SporeColonyRenderState state) {
        super.setupAnim(state);

        // Idle: a slow breathing sway, the same idiom the Sunken Colony's tentacle uses.
        float sway = Mth.cos(state.ageInTicks * 0.05f) * 0.05f;

        // Shot: sin(2πp) is negative through the first half and positive through the second, which is
        // "kick back, then settle forward" in one term. The flare lags the throat so the recoil
        // travels up the chimney instead of it tipping as one rigid pipe.
        float recoil = Mth.sin(state.attackProgress * Mth.TWO_PI);

        this.chimney.xRot = this.chimneyX0 + sway + recoil * 0.16f;
        this.chimneyMid.xRot = this.chimneyMidX0 + sway * 0.7f + recoil * 0.24f;
        this.chimneyRim.xRot = this.chimneyRimX0 + sway * 0.4f + recoil * 0.32f;
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();

        // Each cube's texOffs points at its own packed UV island — do not hand-edit these; they are
        // assigned by tools/blockbench_export.py and guarded by ModelUvLayoutTest.
        // Everything hangs off "body" so the whole colony reads as one rooted mass sitting on the
        // ground (feet at y=24 in model space).
        PartDefinition body = root.addOrReplaceChild("body",
                CubeListBuilder.create().texOffs(0, 0).addBox(-8.0f, -5.0f, -6.5f, 16.0f, 5.0f, 13.0f),
                PartPose.offset(0.0f, 24.0f, 0.0f));

        // The blob's crown: a narrower band on top, so the body reads as an ellipsoid rather than a
        // brick. The art's body is widest across the middle and tucks in above and below.
        body.addOrReplaceChild("bodyTop",
                CubeListBuilder.create().texOffs(24, 19).addBox(-6.5f, -3.0f, -5.0f, 13.0f, 3.0f, 10.0f),
                PartPose.offset(0.0f, -5.0f, 0.0f));

        // --- Carapace: the near-black crescent arcing over and around the back of the body --------
        body.addOrReplaceChild("shellBack",
                CubeListBuilder.create().texOffs(33, 34).addBox(-7.0f, -5.0f, -2.5f, 14.0f, 5.0f, 5.0f),
                PartPose.offset(0.0f, -3.5f, 5.5f));
        body.addOrReplaceChild("shellL",
                CubeListBuilder.create().texOffs(104, 0).addBox(-2.5f, -5.0f, -4.5f, 2.5f, 5.0f, 9.0f),
                PartPose.offsetAndRotation(8.0f, -2.5f, 1.5f, 0.0f, -0.30f, 0.0f));
        body.addOrReplaceChild("shellR",
                CubeListBuilder.create().texOffs(0, 19).addBox(0.0f, -5.0f, -4.5f, 2.5f, 5.0f, 9.0f),
                PartPose.offsetAndRotation(-8.0f, -2.5f, 1.5f, 0.0f, 0.30f, 0.0f));

        // --- Stone claws splayed out from under the body ------------------------------------------
        body.addOrReplaceChild("clawFL",
                CubeListBuilder.create().texOffs(72, 34).addBox(-2.0f, -3.5f, -3.0f, 4.0f, 3.5f, 6.0f),
                PartPose.offsetAndRotation(5.0f, 0.0f, -6.0f, -0.25f, 0.55f, 0.0f));
        body.addOrReplaceChild("clawFR",
                CubeListBuilder.create().texOffs(93, 34).addBox(-2.0f, -3.5f, -3.0f, 4.0f, 3.5f, 6.0f),
                PartPose.offsetAndRotation(-5.0f, 0.0f, -6.0f, -0.25f, -0.55f, 0.0f));
        body.addOrReplaceChild("clawL",
                CubeListBuilder.create().texOffs(29, 48).addBox(-2.0f, -3.0f, -2.5f, 4.0f, 3.0f, 5.0f),
                PartPose.offsetAndRotation(8.0f, 0.0f, 3.0f, 0.0f, -0.85f, 0.0f));
        body.addOrReplaceChild("clawR",
                CubeListBuilder.create().texOffs(48, 48).addBox(-2.0f, -3.0f, -2.5f, 4.0f, 3.0f, 5.0f),
                PartPose.offsetAndRotation(-8.0f, 0.0f, 3.0f, 0.0f, 0.85f, 0.0f));

        // --- The chimney: a trumpet rising out of the front of the body, widening as it goes -------
        // Sunk into the body's crown rather than sitting on it, so the join reads as one creature.
        PartDefinition chimney = body.addOrReplaceChild("chimney",
                CubeListBuilder.create().texOffs(0, 34).addBox(-4.0f, -5.0f, -4.0f, 8.0f, 5.0f, 8.0f),
                PartPose.offsetAndRotation(0.0f, -6.0f, -2.0f, 0.10f, 0.0f, 0.0f));
        PartDefinition chimneyMid = chimney.addOrReplaceChild("chimneyMid",
                CubeListBuilder.create().texOffs(71, 19).addBox(-4.5f, -4.0f, -4.5f, 9.0f, 4.0f, 9.0f),
                PartPose.offset(0.0f, -5.0f, 0.0f));
        PartDefinition chimneyRim = chimneyMid.addOrReplaceChild("chimneyRim",
                CubeListBuilder.create().texOffs(59, 0).addBox(-5.5f, -3.0f, -5.5f, 11.0f, 3.0f, 11.0f),
                PartPose.offset(0.0f, -4.0f, 0.0f));

        // The dark mouth set down inside the flare — the art draws it as a ring with a hole in it, so
        // this cube is inset on every side and sits a texel below the rim's lip.
        chimneyRim.addOrReplaceChild("mouth",
                CubeListBuilder.create().texOffs(0, 48).addBox(-3.5f, -1.5f, -3.5f, 7.0f, 1.5f, 7.0f),
                PartPose.offset(0.0f, -1.5f, 0.0f));

        // The single white eye, on the front of the chimney's throat exactly as in the art.
        chimney.addOrReplaceChild("eye",
                CubeListBuilder.create().texOffs(67, 48).addBox(-1.0f, -1.0f, -0.5f, 2.0f, 2.0f, 1.0f),
                PartPose.offset(1.5f, -2.5f, -4.2f));

        return LayerDefinition.create(mesh, 128, 64);
    }
}
