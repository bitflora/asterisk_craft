package net.bitflora.asteriskcraft.client.protoss;

import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;

/**
 * Original blocky Probe model authored for AsteriskCraft — a small, hovering, vanilla-style Protoss
 * worker built from a handful of boxes (no ported geometry). Silhouette cues: a compact gold pod with a
 * domed top, a glowing cyan sensor eye (the emissive zone lit by
 * {@link net.bitflora.asteriskcraft.client.UnitGlowLayer}), and three little downward leg-prongs.
 * Textured via flat colour zones (see tools/gen_probe_texture.py). It hovers and is fully static — no
 * animated parts.
 */
public class ProbeModel extends EntityModel<LivingEntityRenderState> {

    public ProbeModel(ModelPart root) {
        super(root);
    }

    @Override
    public void setupAnim(LivingEntityRenderState state) {
        super.setupAnim(state);
        // no animated parts — the Probe hovers in place
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();

        // Flat colour zones on the atlas: GOLD = texOffs(0,0), DARK = texOffs(0,24),
        // EYE/emissive = texOffs(40,0). The pod floats around y=13 so it reads as hovering.
        PartDefinition body = root.addOrReplaceChild("body",
                CubeListBuilder.create().texOffs(0, 0).addBox(-3.5f, -3.5f, -3.5f, 7.0f, 7.0f, 7.0f),
                PartPose.offset(0.0f, 13.0f, 0.0f));

        // Domed top.
        body.addOrReplaceChild("dome",
                CubeListBuilder.create().texOffs(0, 0).addBox(-2.5f, -2.0f, -2.5f, 5.0f, 2.0f, 5.0f),
                PartPose.offset(0.0f, -3.5f, 0.0f));
        // Lower pod taper.
        body.addOrReplaceChild("underPod",
                CubeListBuilder.create().texOffs(0, 24).addBox(-2.5f, 0.0f, -2.5f, 5.0f, 2.0f, 5.0f),
                PartPose.offset(0.0f, 3.5f, 0.0f));

        // Glowing cyan sensor eye on the front.
        body.addOrReplaceChild("eye",
                CubeListBuilder.create().texOffs(40, 0).addBox(-1.5f, -1.5f, -0.5f, 3.0f, 3.0f, 1.0f),
                PartPose.offset(0.0f, -0.5f, -3.6f));

        // Three downward leg-prongs (two front, one rear).
        body.addOrReplaceChild("prongFL",
                CubeListBuilder.create().texOffs(0, 24).addBox(-0.75f, 0.0f, -0.75f, 1.5f, 5.0f, 1.5f),
                PartPose.offsetAndRotation(2.5f, 3.0f, -2.0f, 0.25f, 0.0f, 0.3f));
        body.addOrReplaceChild("prongFR",
                CubeListBuilder.create().texOffs(0, 24).addBox(-0.75f, 0.0f, -0.75f, 1.5f, 5.0f, 1.5f),
                PartPose.offsetAndRotation(-2.5f, 3.0f, -2.0f, 0.25f, 0.0f, -0.3f));
        body.addOrReplaceChild("prongB",
                CubeListBuilder.create().texOffs(0, 24).addBox(-0.75f, 0.0f, -0.75f, 1.5f, 5.0f, 1.5f),
                PartPose.offsetAndRotation(0.0f, 3.0f, 3.0f, -0.25f, 0.0f, 0.0f));

        // A cluster of gold spikes emerging from the back of the pod, trailing backward and down (each
        // box extends in +z from its pivot; +x rotation tips it downward, y rotation splays it out).
        body.addOrReplaceChild("spikeBackC",
                CubeListBuilder.create().texOffs(0, 0).addBox(-0.75f, -0.75f, 0.0f, 1.5f, 1.5f, 8.0f),
                PartPose.offsetAndRotation(0.0f, -1.5f, 3.0f, 0.35f, 0.0f, 0.0f));
        body.addOrReplaceChild("spikeBackL",
                CubeListBuilder.create().texOffs(0, 0).addBox(-0.75f, -0.75f, 0.0f, 1.5f, 1.5f, 7.0f),
                PartPose.offsetAndRotation(2.0f, 0.5f, 3.0f, 0.5f, -0.4f, 0.0f));
        body.addOrReplaceChild("spikeBackR",
                CubeListBuilder.create().texOffs(0, 0).addBox(-0.75f, -0.75f, 0.0f, 1.5f, 1.5f, 7.0f),
                PartPose.offsetAndRotation(-2.0f, 0.5f, 3.0f, 0.5f, 0.4f, 0.0f));

        return LayerDefinition.create(mesh, 64, 64);
    }
}
