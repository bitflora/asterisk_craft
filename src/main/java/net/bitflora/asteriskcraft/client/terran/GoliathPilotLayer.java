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
 * The villager riding the Goliath's shoulders — <b>vanilla's own adult villager, not a copy of
 * one</b>. It bakes {@code ModelLayers.VILLAGER} and draws it with vanilla's
 * {@code textures/entity/villager/villager.png}, so the pilot is the thing players already recognise
 * and stays correct if vanilla ever retouches it.
 *
 * <p>Same move as {@link ScvPilotLayer}, with the scope widened from a head to a whole person: this
 * is the second time a Goliath borrows a complete vanilla body ({@link GoliathGolemLayer} is the
 * first), which is what "riding on its shoulders" needs — a floating head would not read as a rider.
 *
 * <p><b>He wears vanilla's armorer welding mask</b>, drawn the way {@link GhostHeadLayer} draws the
 * same one: {@code VillagerProfessionLayer} adds no geometry for a profession — it re-submits the
 * <em>same</em> villager model with {@code profession/&lt;name&gt;.png} — so the mask is one extra
 * cutout pass over the head this layer has already baked, and costs no mesh at all. It is the right
 * face for the job twice over: a mech pilot should have his eyes behind something, and a welding
 * visor is what the race's own {@link GhostHeadLayer} and {@code ScvModel} already establish as the
 * Terran look.
 *
 * <p><b>Only the head is given the overlay, and that is a choice rather than a limit.</b> Verified
 * against 26.1.2's {@code armorer.png}: it paints the {@code head} and {@code hat} cubes (the mask),
 * the {@code jacket} (a leather apron) and the {@code arms} (gloves), and nothing else. Widening the
 * second draw from {@link #head} to {@link #pilot} would put the whole armorer's kit on him — most
 * of which sits inside {@link GoliathModel}'s tub anyway, which is why it is not worth the draw.
 *
 * <p><b>The hat therefore stays visible, where the Marine hides it</b>, and the reason is the same
 * one {@code GhostHeadLayer} records: the bulk of the mask lives on the {@code hat} cube — the
 * inflated 0.51 shell over the head — and {@code villager.png} is fully transparent across that
 * cube's whole UV region, so leaving it on costs the base pass nothing and is the only way the mask
 * can be drawn at all. {@code hat_rim}, the 16x16 straw brim nested under it, is turned off: it is
 * transparent in both textures, and a pilot has no reason to carry a hat's brim.
 *
 * <p><b>The legs are switched off</b> because a golem's head sits straight on its body with no neck:
 * there is nothing to straddle, the only seat is the body's top surface behind the head, and any leg
 * dangling forward from there passes through the skull. {@link GoliathModel}'s cockpit tub is what
 * the legless torso rises out of, so the pose reads as seated rather than as amputated. Vanilla's
 * arms are already folded across the belly in the baked pose, which is exactly a pilot's grip on a
 * set of controls.
 *
 * <p>Submitted as a bare {@code ModelPart} rather than through a {@code VillagerModel}, and that is
 * the deliberate mirror image of what {@link GoliathGolemLayer} does. A model submission re-runs
 * {@code setupAnim} at flush, whose first act is {@code resetPose()} — which would wipe exactly the
 * fixed pose this layer sets up. The pilot never animates, so the part's own state is right for
 * every Goliath at once and there is nothing to re-derive.
 */
public class GoliathPilotLayer extends RenderLayer<GoliathRenderState, GoliathModel> {

    private static final Identifier TEXTURE =
            Identifier.withDefaultNamespace("textures/entity/villager/villager.png");
    /** The welding mask, spread across the head and hat cubes. Vanilla's armorer, unmodified. */
    private static final Identifier MASK =
            Identifier.withDefaultNamespace("textures/entity/villager/profession/armorer.png");

    /**
     * How far the pilot is shrunk to fit the cockpit. A villager is 8 texels across the shoulders
     * and 10 from chin to crown; at 0.7 those become 5.6 and 7, so the torso still sits inside the
     * 8-wide tub while the head is big enough to read as a face at the distance a unit is usually
     * looked at.
     *
     * <p>This and {@code GoliathModel}'s {@code pilot_mount} offset are one decision, and what they
     * decide together is <b>where the eyes land</b>. The mount puts the villager's neck at
     * {@code y = -19}, which is the golem's own crown, so the whole 7-pixel head stands clear above
     * the skull and the eyes — three to five texels down a villager's face — sit four to five pixels
     * proud of it. Shrink the pilot or drop the mount and the face goes back behind the golem's
     * head, which is what this pair was raised from.
     */
    private static final float SCALE = 0.7f;

    private final ModelPart pilot;
    private final ModelPart head;

    public GoliathPilotLayer(RenderLayerParent<GoliathRenderState, GoliathModel> parent,
            EntityRendererProvider.Context context) {
        super(parent);
        this.pilot = context.bakeLayer(ModelLayers.VILLAGER);
        this.head = this.pilot.getChild("head");
        // Safe to mutate: this ModelPart instance is baked for this layer and nothing else ever
        // touches it, and nothing here ever calls setupAnim on it (see the class doc).
        // The hat stays on — it carries most of the mask — but its straw brim does not.
        this.head.getChild("hat").getChild("hat_rim").visible = false;
        this.pilot.getChild("right_leg").visible = false;
        this.pilot.getChild("left_leg").visible = false;
    }

    @Override
    public void submit(PoseStack poseStack, SubmitNodeCollector collector, int lightCoords,
            GoliathRenderState state, float yRot, float xRot) {
        if (state.isInvisible) {
            return;
        }
        poseStack.pushPose();
        this.getParentModel().translateToPilot(poseStack);
        poseStack.scale(SCALE, SCALE, SCALE);
        int overlay = LivingEntityRenderer.getOverlayCoords(state, 0.0f);
        // Order is the order they stack on the face: the villager, then the mask over him. The
        // second draw is the head alone, which carries its own hat, so it lands exactly where the
        // first draw put it.
        collector.submitModelPart(this.pilot, poseStack, RenderTypes.entityCutout(TEXTURE),
                lightCoords, overlay, null);
        collector.submitModelPart(this.head, poseStack, RenderTypes.entityCutout(MASK),
                lightCoords, overlay, null);
        poseStack.popPose();
    }
}
