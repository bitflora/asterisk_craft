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
 * The Firebat's face — <b>vanilla's own pillager head, not a copy of one</b>. It bakes
 * {@code ModelLayers.PILLAGER} and draws that model's {@code head} with vanilla's
 * {@code textures/entity/illager/pillager.png}, so the grey crown, the heavy brow and the long nose
 * are pixel-for-pixel the ones players already know, and stay correct if vanilla ever retouches them.
 *
 * <p>This is the third instance of the same move — {@link ScvPilotLayer} for the pilot in the SCV's
 * cab, {@link MarineHeadLayer} for the Marine's villager face — and it is the <b>default whenever
 * this mod borrows a vanilla model</b>: take vanilla's geometry <em>and</em> vanilla's texture, as a
 * second draw. The reason is that a model part can only be painted from its own model's texture, so
 * owning the head in {@link FirebatModel} would mean hand-copying pillager pixels into
 * {@code firebat.png} and keeping them in sync by hand, which is a copy that goes stale silently.
 *
 * <p><b>The nose is kept and the hat is hidden.</b> Vanilla's illager {@code head} carries two
 * children: {@code nose} (a 2x4x2 snout reaching to z=-6), which is half of what makes the head read
 * as a pillager and is therefore the whole reason to borrow this layer rather than the villager one;
 * and {@code hat}, an 8x12x8 inflated shell that would swallow the face entirely. Vanilla's own
 * {@code IllagerModel} constructor turns the hat off for exactly the same reason, so this is not a
 * mod-specific correction — it is what a pillager looks like.
 *
 * <p><b>And unlike the Marine, nothing is drawn over it.</b> The Marine hangs four helmet plates off
 * its own {@code head} container; the Firebat hangs none, because this face <em>is</em> the unit's
 * identity — see {@link FirebatModel}.
 */
public class FirebatHeadLayer extends RenderLayer<FirebatRenderState, FirebatModel> {

    private static final Identifier TEXTURE =
            Identifier.withDefaultNamespace("textures/entity/illager/pillager.png");

    private final ModelPart head;

    public FirebatHeadLayer(RenderLayerParent<FirebatRenderState, FirebatModel> parent,
            EntityRendererProvider.Context context) {
        super(parent);
        this.head = context.bakeLayer(ModelLayers.PILLAGER).getChild("head");
        // Vanilla hangs this head off a standing illager's neck; FirebatModel's head container
        // already says where it goes, so that offset is cleared rather than cancelled out on the
        // PoseStack every frame. Safe because this ModelPart instance is baked for this layer and
        // nothing else ever touches it.
        this.head.setPos(0.0f, 0.0f, 0.0f);
        this.head.getChild("hat").visible = false;
    }

    @Override
    public void submit(PoseStack poseStack, SubmitNodeCollector collector, int lightCoords,
            FirebatRenderState state, float yRot, float xRot) {
        if (state.isInvisible) {
            return;
        }
        poseStack.pushPose();
        this.getParentModel().translateToHead(poseStack);
        collector.submitModelPart(this.head, poseStack, RenderTypes.entityCutout(TEXTURE), lightCoords,
                LivingEntityRenderer.getOverlayCoords(state, 0.0f), null);
        poseStack.popPose();
    }
}
