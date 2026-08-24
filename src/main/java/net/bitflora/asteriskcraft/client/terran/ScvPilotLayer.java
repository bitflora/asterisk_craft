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
 * The villager riding in the SCV's cab — <b>vanilla's own baby villager head, not a copy of one</b>.
 * It bakes {@code ModelLayers.VILLAGER_BABY} and draws that model's {@code head} (its cranium, hat,
 * wide straw brim and stub nose) with vanilla's {@code textures/entity/villager/villager_baby.png},
 * so the pilot is pixel-for-pixel the thing players already recognise and stays correct if vanilla
 * ever retouches it.
 *
 * <p>It is a layer rather than geometry on {@link ScvModel} because <b>the texture is the reason</b>:
 * a model part can only be painted from its own model's texture, so putting the head in
 * {@code ScvModel} would have meant hand-copying villager pixels into {@code scv.png} and keeping
 * them in sync by hand. A second draw with a second texture is what buys the real thing.
 *
 * <p>Note the baby villager is a genuinely different head from the adult, not a scaled one — 8x8x7
 * against the adult's 8x10x8, with its own hat, a 14x1x12 brim the adult doesn't have, and its own
 * texture layout — which is why {@code ModelLayers.VILLAGER_BABY} is baked here rather than
 * {@code VILLAGER} with a scale on it.
 */
public class ScvPilotLayer extends RenderLayer<ScvRenderState, ScvModel> {

    private static final Identifier TEXTURE =
            Identifier.withDefaultNamespace("textures/entity/villager/villager_baby.png");

    /**
     * How far the pilot is shrunk to fit the cab. A baby villager's head is 8 texels across and its
     * hat brim 14; at 0.6 those become 4.8 and 8.4, so the head sits comfortably inside the 9-wide
     * opening and the brim fills it out to just short of the canopy posts at x=+-4.5.
     * {@code ScvModel}'s {@code pilot_mount} offsets are chosen against this number — change one and
     * the other moves.
     */
    private static final float SCALE = 0.6f;

    private final ModelPart head;

    public ScvPilotLayer(RenderLayerParent<ScvRenderState, ScvModel> parent,
            EntityRendererProvider.Context context) {
        super(parent);
        this.head = context.bakeLayer(ModelLayers.VILLAGER_BABY).getChild("head");
        // Vanilla hangs this head off a standing villager's neck at y=16. Here the mount already
        // says where it goes, so that offset is cleared rather than cancelled out on the PoseStack
        // every frame. Safe because this ModelPart instance is baked for this layer and nothing
        // else ever touches it.
        this.head.setPos(0.0f, 0.0f, 0.0f);
    }

    @Override
    public void submit(PoseStack poseStack, SubmitNodeCollector collector, int lightCoords,
            ScvRenderState state, float yRot, float xRot) {
        if (state.isInvisible) {
            return;
        }
        poseStack.pushPose();
        this.getParentModel().translateToPilot(poseStack);
        poseStack.scale(SCALE, SCALE, SCALE);
        collector.submitModelPart(this.head, poseStack, RenderTypes.entityCutout(TEXTURE), lightCoords,
                LivingEntityRenderer.getOverlayCoords(state, 0.0f), null);
        poseStack.popPose();
    }
}
