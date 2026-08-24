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
 * The Marine's face — <b>vanilla's own villager head, not a copy of one</b>. It bakes
 * {@code ModelLayers.VILLAGER} and draws that model's {@code head} with vanilla's
 * {@code textures/entity/villager/villager.png}, so the unibrow, the eyes and the big nose are
 * pixel-for-pixel the ones players already know, and stay correct if vanilla ever retouches them.
 *
 * <p>This is the same move {@link ScvPilotLayer} makes for the pilot in the SCV's cab, and it is the
 * <b>default whenever this mod borrows a vanilla model</b>: take vanilla's geometry <em>and</em>
 * vanilla's texture, as a second draw. The reason is that a model part can only be painted from its
 * own model's texture — owning the head in {@link MarineModel} would mean hand-copying villager
 * pixels into {@code marine.png} and keeping them in sync by hand, which is a copy that goes stale
 * silently. {@code client/zerg/InfestedVillagerModel} copies villager <em>proportions</em> for the
 * opposite reason: it needs a head it can then replace with a Zergling's. Nothing here modifies the
 * face, so nothing here should own one.
 *
 * <p><b>The straw hat is hidden rather than avoided.</b> Vanilla's {@code head} carries {@code hat}
 * (and its 16x16 {@code hat_rim}) as children, and a Marine wears a helmet instead — but
 * {@code ModelLayers.VILLAGER_NO_HAT} is not the answer, because {@code createNoHatModel} clears the
 * whole head recursively rather than just the hat, leaving no face at all. So the adult layer is
 * baked and {@code hat.visible} is turned off, which takes {@code hat_rim} with it.
 *
 * <p>{@link MarineModel}'s own {@code head} part carries no cubes: it is an empty container that the
 * helmet plates hang off and that this layer draws into, so the borrowed face and the mod's own
 * helmet turn together as one head.
 */
public class MarineHeadLayer extends RenderLayer<MarineRenderState, MarineModel> {

    private static final Identifier TEXTURE =
            Identifier.withDefaultNamespace("textures/entity/villager/villager.png");

    private final ModelPart head;

    public MarineHeadLayer(RenderLayerParent<MarineRenderState, MarineModel> parent,
            EntityRendererProvider.Context context) {
        super(parent);
        this.head = context.bakeLayer(ModelLayers.VILLAGER).getChild("head");
        // Vanilla hangs this head off a standing villager's neck; MarineModel's head container
        // already says where it goes, so that offset is cleared rather than cancelled out on the
        // PoseStack every frame. Safe because this ModelPart instance is baked for this layer and
        // nothing else ever touches it.
        this.head.setPos(0.0f, 0.0f, 0.0f);
        this.head.getChild("hat").visible = false;
    }

    @Override
    public void submit(PoseStack poseStack, SubmitNodeCollector collector, int lightCoords,
            MarineRenderState state, float yRot, float xRot) {
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
