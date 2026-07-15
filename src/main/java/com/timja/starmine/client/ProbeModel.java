package com.timja.starmine.client;

import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;

/**
 * Simple floating-drone look: a rounded body box with two side vanes and an eye.
 */
public class ProbeModel extends EntityModel<LivingEntityRenderState> {
    public ProbeModel(ModelPart root) {
        super(root);
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        PartDefinition body = root.addOrReplaceChild("body",
                CubeListBuilder.create()
                        .texOffs(0, 0)
                        .addBox(-4.0f, -8.0f, -4.0f, 8.0f, 8.0f, 8.0f),
                PartPose.offset(0.0f, 20.0f, 0.0f));
        body.addOrReplaceChild("left_vane",
                CubeListBuilder.create()
                        .texOffs(32, 0)
                        .addBox(0.0f, -3.0f, -2.0f, 1.0f, 6.0f, 4.0f),
                PartPose.offset(4.0f, -4.0f, 0.0f));
        body.addOrReplaceChild("right_vane",
                CubeListBuilder.create()
                        .texOffs(32, 0)
                        .addBox(-1.0f, -3.0f, -2.0f, 1.0f, 6.0f, 4.0f),
                PartPose.offset(-4.0f, -4.0f, 0.0f));
        return LayerDefinition.create(mesh, 64, 32);
    }
}
