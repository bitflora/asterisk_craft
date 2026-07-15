package com.timja.starmine.client;

import com.timja.starmine.StarMine;
import com.timja.starmine.entity.ProbeEntity;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.resources.Identifier;

public class ProbeRenderer extends MobRenderer<ProbeEntity, LivingEntityRenderState, ProbeModel> {
    private static final Identifier TEXTURE = StarMine.id("textures/entity/probe.png");

    public ProbeRenderer(EntityRendererProvider.Context context) {
        super(context, new ProbeModel(context.bakeLayer(StarMineClient.PROBE_LAYER)), 0.4f);
    }

    @Override
    public LivingEntityRenderState createRenderState() {
        return new LivingEntityRenderState();
    }

    @Override
    public Identifier getTextureLocation(LivingEntityRenderState state) {
        return TEXTURE;
    }
}
