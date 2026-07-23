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
 * Original blocky Sunken Colony model authored for AsteriskCraft. Silhouette cues from the reference
 * art: a squat, near-black root mound hugging the ground, bristling with pale bone spines and low
 * tendrils, out of which a heavy segmented tentacle arches up and forward to a claw tip, with a
 * glowing red maw at the front of the mound (the emissive zone lit by
 * {@link net.bitflora.asteriskcraft.client.UnitGlowLayer}). Each cube owns its own UV island; the texture is hand-painted in Blockbench via tools/blockbench_export.py.
 *
 * <p>Two animations, both on the tentacle: a slow idle sway, and the strike — a rear-back followed by
 * a whip forward, driven by {@link SunkenColonyRenderState#attackProgress}. Nothing else moves; the
 * colony is rooted and only its body yaw turns to face a target.
 */
public class SunkenColonyModel extends EntityModel<SunkenColonyRenderState> {
    private final ModelPart tentacle1;
    private final ModelPart tentacle2;
    private final ModelPart tentacle3;
    private final float tentacle1X0;
    private final float tentacle2X0;
    private final float tentacle3X0;

    public SunkenColonyModel(ModelPart root) {
        super(root);
        this.tentacle1 = root.getChild("base").getChild("tentacle1");
        this.tentacle2 = this.tentacle1.getChild("tentacle2");
        this.tentacle3 = this.tentacle2.getChild("tentacle3");
        this.tentacle1X0 = this.tentacle1.xRot;
        this.tentacle2X0 = this.tentacle2.xRot;
        this.tentacle3X0 = this.tentacle3.xRot;
    }

    @Override
    public void setupAnim(SunkenColonyRenderState state) {
        super.setupAnim(state);

        // Idle: a slow breathing sway so a rooted structure still reads as alive.
        float sway = Mth.cos(state.ageInTicks * 0.06f) * 0.08f;

        // Strike: wind back through the first half, snap forward through the second. sin(2πp) is
        // negative then positive across p in [0,1], which is exactly that shape in one term; the
        // outer segments lag progressively so the tentacle cracks like a whip rather than swinging
        // as one rigid arm.
        float whip = Mth.sin(state.attackProgress * Mth.TWO_PI);

        this.tentacle1.xRot = this.tentacle1X0 + sway + whip * 0.55f;
        this.tentacle2.xRot = this.tentacle2X0 + sway * 0.7f + whip * 0.75f;
        this.tentacle3.xRot = this.tentacle3X0 + sway * 0.4f + whip * 0.95f;
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();

        // Each cube's texOffs points at its own packed UV island — do not hand-edit these; they are
        // assigned by tools/blockbench_export.py and guarded by ModelUvLayoutTest.
        // Everything hangs off "base" so the whole colony reads as one rooted mass sitting on the
        // ground (feet at y=24 in model space).
        PartDefinition base = root.addOrReplaceChild("base",
                CubeListBuilder.create().texOffs(0, 0).addBox(-7.0f, -8.0f, -7.0f, 14.0f, 8.0f, 14.0f),
                PartPose.offset(0.0f, 24.0f, 0.0f));

        // Splayed skirt of root matter where the mound meets the ground.
        base.addOrReplaceChild("skirt",
                CubeListBuilder.create().texOffs(57, 0).addBox(-8.0f, -3.0f, -8.0f, 16.0f, 3.0f, 16.0f),
                PartPose.offset(0.0f, 0.0f, 0.0f));

        // --- Tentacle: three tapering segments arching up and forward, ending in a claw ---------
        PartDefinition tentacle1 = base.addOrReplaceChild("tentacle1",
                CubeListBuilder.create().texOffs(0, 23).addBox(-2.5f, -8.0f, -2.5f, 5.0f, 8.0f, 5.0f),
                PartPose.offsetAndRotation(0.0f, -7.0f, -1.0f, -0.25f, 0.0f, 0.0f));
        PartDefinition tentacle2 = tentacle1.addOrReplaceChild("tentacle2",
                CubeListBuilder.create().texOffs(67, 23).addBox(-2.0f, -7.0f, -2.0f, 4.0f, 7.0f, 4.0f),
                PartPose.offsetAndRotation(0.0f, -8.0f, 0.0f, -0.30f, 0.0f, 0.0f));
        PartDefinition tentacle3 = tentacle2.addOrReplaceChild("tentacle3",
                CubeListBuilder.create().texOffs(84, 23).addBox(-1.5f, -6.0f, -1.5f, 3.0f, 6.0f, 3.0f),
                PartPose.offsetAndRotation(0.0f, -7.0f, 0.0f, -0.35f, 0.0f, 0.0f));
        tentacle3.addOrReplaceChild("claw",
                CubeListBuilder.create().texOffs(9, 37).addBox(-1.0f, -6.0f, -1.0f, 2.0f, 6.0f, 2.0f),
                PartPose.offsetAndRotation(0.0f, -6.0f, 0.0f, -0.30f, 0.0f, 0.0f));

        // --- Bone spines splayed off the rim of the mound (silhouette only) --------------------
        base.addOrReplaceChild("spineFL",
                CubeListBuilder.create().texOffs(115, 23).addBox(-1.0f, -7.0f, -1.0f, 2.0f, 7.0f, 2.0f),
                PartPose.offsetAndRotation(5.0f, -6.5f, -5.0f, -0.55f, 0.0f, 0.55f));
        base.addOrReplaceChild("spineFR",
                CubeListBuilder.create().texOffs(0, 37).addBox(-1.0f, -7.0f, -1.0f, 2.0f, 7.0f, 2.0f),
                PartPose.offsetAndRotation(-5.0f, -6.5f, -5.0f, -0.55f, 0.0f, -0.55f));
        base.addOrReplaceChild("spineBL",
                CubeListBuilder.create().texOffs(97, 23).addBox(-1.0f, -7.0f, -1.0f, 2.0f, 7.0f, 2.0f),
                PartPose.offsetAndRotation(5.0f, -6.5f, 5.0f, 0.55f, 0.0f, 0.55f));
        base.addOrReplaceChild("spineBR",
                CubeListBuilder.create().texOffs(106, 23).addBox(-1.0f, -7.0f, -1.0f, 2.0f, 7.0f, 2.0f),
                PartPose.offsetAndRotation(-5.0f, -6.5f, 5.0f, 0.55f, 0.0f, -0.55f));

        // --- Low tendrils crawling forward along the ground ------------------------------------
        base.addOrReplaceChild("rootL",
                CubeListBuilder.create().texOffs(21, 23).addBox(-1.5f, -3.0f, -8.0f, 3.0f, 3.0f, 8.0f),
                PartPose.offsetAndRotation(5.0f, -1.0f, -6.0f, 0.0f, 0.45f, 0.0f));
        base.addOrReplaceChild("rootR",
                CubeListBuilder.create().texOffs(44, 23).addBox(-1.5f, -3.0f, -8.0f, 3.0f, 3.0f, 8.0f),
                PartPose.offsetAndRotation(-5.0f, -1.0f, -6.0f, 0.0f, -0.45f, 0.0f));

        // --- Glowing maw at the front of the mound ---------------------------------------------
        base.addOrReplaceChild("maw",
                CubeListBuilder.create().texOffs(18, 37).addBox(-3.0f, -1.0f, -1.0f, 6.0f, 2.0f, 2.0f),
                PartPose.offset(0.0f, -4.5f, -7.2f));

        return LayerDefinition.create(mesh, 128, 64);
    }
}
