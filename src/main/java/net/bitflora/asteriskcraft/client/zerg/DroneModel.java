package net.bitflora.asteriskcraft.client.zerg;

import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;

/**
 * Original blocky Drone model authored for AsteriskCraft — a small, hovering, vanilla-style Zerg worker
 * built from a handful of boxes (no ported geometry). Silhouette cues: a hunched segmented carapace body
 * floating above the ground, a dorsal hump with orange-glowing spikes (the emissive zone lit by
 * {@link net.bitflora.asteriskcraft.client.UnitGlowLayer}), a fanged head with a glowing eye, and two
 * small grasping fore-claws. Each cube owns its own UV island; the texture is hand-painted in Blockbench via tools/blockbench_export.py. It hovers
 * (no legs); only head-look is driven in setupAnim.
 */
public class DroneModel extends EntityModel<LivingEntityRenderState> {
    private final ModelPart head;
    private final float headX0;

    public DroneModel(ModelPart root) {
        super(root);
        this.head = root.getChild("segment1").getChild("head");
        this.headX0 = this.head.xRot;
    }

    @Override
    public void setupAnim(LivingEntityRenderState state) {
        super.setupAnim(state);
        this.head.yRot = state.yRot * ((float) Math.PI / 180f);
        this.head.xRot = this.headX0 + state.xRot * ((float) Math.PI / 180f);
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();

        // Each cube's texOffs points at its own packed UV island — do not hand-edit these; they are
        // assigned by tools/blockbench_export.py and guarded by ModelUvLayoutTest.
        // The whole worker floats around y=15 so it reads as hovering.
        PartDefinition segment1 = root.addOrReplaceChild("segment1",
                CubeListBuilder.create().texOffs(0, 0).addBox(-3.5f, -3.0f, -4.0f, 7.0f, 6.0f, 9.0f),
                PartPose.offsetAndRotation(0.0f, 15.0f, 0.0f, 0.1f, 0.0f, 0.0f));

        // Dorsal hump rising over the back.
        segment1.addOrReplaceChild("hump",
                CubeListBuilder.create().texOffs(33, 0).addBox(-2.5f, -3.0f, -2.0f, 5.0f, 4.0f, 6.0f),
                PartPose.offsetAndRotation(0.0f, -2.5f, 1.5f, -0.2f, 0.0f, 0.0f));
        // Two orange-glowing back spikes.
        segment1.addOrReplaceChild("spikeL",
                CubeListBuilder.create().texOffs(108, 0).addBox(-0.75f, -5.0f, -0.75f, 1.5f, 5.0f, 1.5f),
                PartPose.offsetAndRotation(1.5f, -4.0f, 2.0f, -0.5f, 0.0f, -0.2f));
        segment1.addOrReplaceChild("spikeR",
                CubeListBuilder.create().texOffs(115, 0).addBox(-0.75f, -5.0f, -0.75f, 1.5f, 5.0f, 1.5f),
                PartPose.offsetAndRotation(-1.5f, -4.0f, 2.0f, -0.5f, 0.0f, 0.2f));

        // --- Head at the front (pivot for head-look) --------------------------------------
        PartDefinition head = segment1.addOrReplaceChild("head",
                CubeListBuilder.create().texOffs(56, 0).addBox(-2.5f, -2.0f, -4.0f, 5.0f, 4.0f, 4.0f),
                PartPose.offset(0.0f, 0.5f, -4.0f));
        head.addOrReplaceChild("mandibleL",
                CubeListBuilder.create().texOffs(0, 16).addBox(-0.5f, -0.5f, -3.0f, 1.0f, 1.0f, 3.0f),
                PartPose.offsetAndRotation(1.5f, 1.5f, -3.5f, 0.0f, -0.25f, 0.0f));
        head.addOrReplaceChild("mandibleR",
                CubeListBuilder.create().texOffs(9, 16).addBox(-0.5f, -0.5f, -3.0f, 1.0f, 1.0f, 3.0f),
                PartPose.offsetAndRotation(-1.5f, 1.5f, -3.5f, 0.0f, 0.25f, 0.0f));
        head.addOrReplaceChild("eye",
                CubeListBuilder.create().texOffs(18, 16).addBox(-1.5f, -0.75f, -0.5f, 3.0f, 1.5f, 1.0f),
                PartPose.offset(0.0f, -0.5f, -4.1f));

        // --- Grasping fore-claws (hang down under the front) ------------------------------
        segment1.addOrReplaceChild("clawL",
                CubeListBuilder.create().texOffs(92, 0).addBox(-0.75f, 0.0f, -1.0f, 1.5f, 5.0f, 2.0f),
                PartPose.offsetAndRotation(2.5f, 2.0f, -2.5f, 0.4f, 0.0f, 0.2f));
        segment1.addOrReplaceChild("clawR",
                CubeListBuilder.create().texOffs(100, 0).addBox(-0.75f, 0.0f, -1.0f, 1.5f, 5.0f, 2.0f),
                PartPose.offsetAndRotation(-2.5f, 2.0f, -2.5f, 0.4f, 0.0f, -0.2f));

        // --- Tail stub --------------------------------------------------------------------
        segment1.addOrReplaceChild("tail",
                CubeListBuilder.create().texOffs(75, 0).addBox(-1.5f, -1.5f, 0.0f, 3.0f, 3.0f, 5.0f),
                PartPose.offsetAndRotation(0.0f, 0.5f, 4.5f, 0.3f, 0.0f, 0.0f));

        return LayerDefinition.create(mesh, 128, 64);
    }
}
