package net.bitflora.asteriskcraft.client.terran;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.resources.Identifier;

/**
 * The Missile Turret's body — <b>vanilla's own iron golem, not a copy of one</b>. It bakes
 * {@code ModelLayers.IRON_GOLEM} and draws that model with vanilla's
 * {@code textures/entity/iron_golem/iron_golem.png}, so the riveted plating and the vine-cracked
 * face are pixel-for-pixel the ones players already know, and stay correct if vanilla ever retouches
 * them.
 *
 * <p>Same move as {@link MarineHeadLayer} and {@link ScvPilotLayer}, and the <b>default whenever this
 * mod borrows a vanilla model</b>: take vanilla's geometry <em>and</em> vanilla's texture, as a
 * second draw. What is new here is only the scope — this is the first borrow of a whole body rather
 * than a head, so the container part in {@link MissileTurretModel} is the whole golem and the mod's
 * own geometry is what hangs beside it.
 *
 * <p><b>The arms are hidden rather than avoided.</b> They are what the missile racks replace, so
 * vanilla's {@code right_arm} and {@code left_arm} are switched off and {@link MissileTurretModel}
 * puts its own geometry at the same shoulder pivot. Hiding sub-parts is the documented way to drop
 * something out of a borrowed model — re-authoring the golem around the missing arms would be a
 * hand-copy of vanilla pixels that goes stale silently, and is only justified when the mod has to
 * modify the geometry it keeps (which is what {@code client/zerg/InfestedVillagerModel} does).
 *
 * <p><b>The racks that replaced them are drawn here too, and off the same texture.</b> They are new
 * geometry but they are still the golem's arms, so borrowing the model's pixels for them is the same
 * rule applied one step further: their cubes carry vanilla's arm {@code texOffs} and are sized to
 * land exactly on its island (see {@link MissileTurretModel}). Only the missiles standing out of each
 * muzzle belong to {@code missile_turret.png}, and those are separate parts.
 *
 * <p>The shells are drawn twice and that is deliberate: the model's own pass finds nothing at those
 * texels and discards them, and this layer paints them properly. <b>Do not try to hide them between
 * the two draws.</b> {@code submitModelPart} keeps a reference to the {@code ModelPart} and copies
 * only the pose, so anything set on the part after {@code submit} returns is what actually gets
 * rendered — setting {@code visible = false} again here silently drops them from the frame.
 *
 * <p>Vanilla's own root already stands the head, body and legs relative to the origin with the soles
 * at {@code y=24}, which is the space {@link MissileTurretModel} is authored in, so nothing has to be
 * repositioned the way {@code MarineHeadLayer} clears the villager's neck offset.
 */
public class MissileTurretGolemLayer extends RenderLayer<MissileTurretRenderState, MissileTurretModel> {

    private static final Identifier TEXTURE =
            Identifier.withDefaultNamespace("textures/entity/iron_golem/iron_golem.png");

    private final ModelPart golem;

    public MissileTurretGolemLayer(RenderLayerParent<MissileTurretRenderState, MissileTurretModel> parent,
            EntityRendererProvider.Context context) {
        super(parent);
        this.golem = context.bakeLayer(ModelLayers.IRON_GOLEM);
        // Safe to mutate: this ModelPart instance is baked for this layer and nothing else ever
        // touches it. Vanilla's IronGolemModel is never constructed here, so the arms are simply
        // never drawn rather than being posed and then hidden every frame.
        this.golem.getChild("right_arm").visible = false;
        this.golem.getChild("left_arm").visible = false;
    }

    @Override
    public void submit(PoseStack poseStack, SubmitNodeCollector collector, int lightCoords,
            MissileTurretRenderState state, float yRot, float xRot) {
        if (state.isInvisible) {
            return;
        }
        int overlay = LivingEntityRenderer.getOverlayCoords(state, 0.0f);
        MissileTurretModel model = this.getParentModel();

        poseStack.pushPose();
        model.translateToGolem(poseStack);
        collector.submitModelPart(this.golem, poseStack, RenderTypes.entityCutout(TEXTURE), lightCoords,
                overlay, null);
        poseStack.popPose();

        for (boolean left : new boolean[] {true, false}) {
            poseStack.pushPose();
            model.translateToRack(poseStack, left);
            collector.submitModelPart(model.shell(left), poseStack, RenderTypes.entityCutout(TEXTURE),
                    lightCoords, overlay, null);
            poseStack.popPose();
        }
    }
}
