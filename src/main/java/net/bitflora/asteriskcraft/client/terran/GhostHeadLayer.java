package net.bitflora.asteriskcraft.client.terran;

import com.mojang.blaze3d.vertex.PoseStack;
import net.bitflora.asteriskcraft.AsteriskCraft;
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
 * The Ghost's face — <b>vanilla's villager head wearing vanilla's armorer mask and vanilla's
 * cartographer eyepiece</b>, all three painted from vanilla's own textures, plus one small mod-owned
 * pass that lights the eyeholes green.
 *
 * <p>This is {@link MarineHeadLayer} taken one step further, and the step is free. Verified against
 * the decompiled 26.1.2 source: {@code VillagerProfessionLayer} does not add geometry for a
 * profession — it re-submits the <em>same</em> villager model with {@code profession/&lt;name&gt;.png},
 * so the welding mask and the monocle are texture overlays on the head's own {@code hat} and
 * {@code head} cubes. Drawing two of them on one head is therefore three draws of one baked part,
 * and something a real villager could never be (it has one profession) while costing no new mesh.
 *
 * <p><b>The hat stays visible here, where the Marine hides it.</b> The mask lives almost entirely on
 * the {@code hat} cube — the inflated 0.51 shell over the head — and {@code villager.png} is fully
 * transparent across that cube's whole UV region, so leaving it on costs nothing and is the only way
 * the mask can be drawn at all. {@code hat_rim}, the 16x16 straw brim nested under it, is turned off:
 * it is transparent in all three textures, and a Ghost has no reason to carry a hat's brim.
 *
 * <p>{@code ModelLayers.VILLAGER_NO_HAT} is still not the answer to anything here —
 * {@code createNoHatModel} clears the whole head recursively and leaves no face at all. See
 * docs/neoforge-api-notes.md.
 *
 * <h2>The three green eyeholes</h2>
 *
 * The mask has two eye slits and the eyepiece has one lens, and all three are lit from
 * {@code ghost_visor.png} — a mod-owned 64x64 PNG laid out on <em>vanilla's</em> villager UVs and
 * transparent everywhere except those six texels. It is drawn <b>twice</b>, and both draws matter:
 * a cutout pass makes the pixels solid green (the cartographer lens underneath them is opaque blue,
 * so an additive pass alone would come out cyan), then an {@code eyes} pass makes them full-bright
 * regardless of world light. One file, because they are the same six pixels.
 *
 * <p>This is the one place the mod paints over borrowed vanilla pixels, and it is deliberate: the
 * geometry, the face and both pieces of headgear still come from vanilla and still track vanilla's
 * own art. Only the glow is the mod's.
 *
 * <p>Always returns early on {@code state.isInvisible} — otherwise a cloaked Ghost, which is most of
 * them, would be a floating face.
 */
public class GhostHeadLayer extends RenderLayer<GhostRenderState, GhostModel> {

    private static final Identifier FACE =
            Identifier.withDefaultNamespace("textures/entity/villager/villager.png");
    /** The welding mask, on the hat cube. Vanilla's armorer, unmodified. */
    private static final Identifier MASK =
            Identifier.withDefaultNamespace("textures/entity/villager/profession/armorer.png");
    /** The eyepiece, low on the right of the face. Vanilla's cartographer, unmodified. */
    private static final Identifier EYEPIECE =
            Identifier.withDefaultNamespace("textures/entity/villager/profession/cartographer.png");
    /** Six texels of green: the mask's two slits and the eyepiece's lens. */
    private static final Identifier VISOR = AsteriskCraft.id("textures/entity/ghost_visor.png");

    private final ModelPart head;

    public GhostHeadLayer(RenderLayerParent<GhostRenderState, GhostModel> parent,
            EntityRendererProvider.Context context) {
        super(parent);
        this.head = context.bakeLayer(ModelLayers.VILLAGER).getChild("head");
        // Vanilla hangs this head off a standing villager's neck; GhostModel's head container
        // already says where it goes, so that offset is cleared rather than cancelled out on the
        // PoseStack every frame. Safe because this ModelPart instance is baked for this layer and
        // nothing else ever touches it.
        this.head.setPos(0.0f, 0.0f, 0.0f);
        this.head.getChild("hat").getChild("hat_rim").visible = false;
    }

    @Override
    public void submit(PoseStack poseStack, SubmitNodeCollector collector, int lightCoords,
            GhostRenderState state, float yRot, float xRot) {
        if (state.isInvisible) {
            return;
        }
        poseStack.pushPose();
        this.getParentModel().translateToHead(poseStack);
        int overlay = LivingEntityRenderer.getOverlayCoords(state, 0.0f);
        // Order is the order they stack on the face: skin, then the mask over it, then the eyepiece
        // below the mask, then the glow through all of it.
        collector.submitModelPart(this.head, poseStack, RenderTypes.entityCutout(FACE), lightCoords,
                overlay, null);
        collector.submitModelPart(this.head, poseStack, RenderTypes.entityCutout(MASK), lightCoords,
                overlay, null);
        collector.submitModelPart(this.head, poseStack, RenderTypes.entityCutout(EYEPIECE), lightCoords,
                overlay, null);
        collector.submitModelPart(this.head, poseStack, RenderTypes.entityCutout(VISOR), lightCoords,
                overlay, null);
        collector.submitModelPart(this.head, poseStack, RenderTypes.eyes(VISOR), lightCoords,
                overlay, null);
        poseStack.popPose();
    }
}
