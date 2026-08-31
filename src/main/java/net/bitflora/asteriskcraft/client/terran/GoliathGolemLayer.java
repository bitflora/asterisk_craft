package net.bitflora.asteriskcraft.client.terran;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.animal.golem.IronGolemModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.IronGolemRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.resources.Identifier;

/**
 * The Goliath's chassis — <b>vanilla's own iron golem, not a copy of one</b> — and the two cannon
 * pods that replaced its arms, drawn off vanilla's
 * {@code textures/entity/iron_golem/iron_golem.png} so the riveted plating is pixel-for-pixel the
 * one players already know.
 *
 * <p>Same move as {@link MissileTurretGolemLayer}, whose class doc carries the reasoning: hide the
 * arms rather than re-authoring the mesh around them, and paint their replacements from their own
 * pixels because a replacement for a vanilla part is still that part.
 *
 * <p><b>What is new here is that this golem walks, and that changes how it has to be submitted.</b>
 * The turret is rooted, so it gets away with handing the collector a bare {@code ModelPart}. A
 * Goliath cannot, and the reason is worth stating precisely (verified in the 26.1.2 decompile, and
 * recorded in docs/neoforge-api-notes.md):
 *
 * <ul>
 *   <li>{@code submitModelPart} stores a <em>reference</em> to the {@code ModelPart} and a copy of
 *       the pose, and <b>no render state</b>. Whatever that part holds when the frame is flushed is
 *       what <em>every</em> submission of it draws — so posing a shared borrowed part per entity
 *       would give every Goliath on screen the last one's legs.</li>
 *   <li>{@code submitModel} stores the model <em>and the state</em>, and
 *       {@code ModelFeatureRenderer} calls {@code model.setupAnim(submit.state())} at flush. That is
 *       what makes per-entity animation work at all, for vanilla's own mobs as much as for this.</li>
 * </ul>
 *
 * <p>So this layer constructs a real {@link IronGolemModel} and submits it with a state of its own,
 * which buys vanilla's gait <em>and</em> its head-tracking with nothing written for either. The
 * state is <b>allocated per submission</b> rather than kept as a field, for exactly the reason
 * above: every Goliath drawn this frame is still holding its own until the flush.
 *
 * <p>The hidden arms survive that. {@code Model.setupAnim} calls {@code resetPose()}, which restores
 * each part's {@code PartPose} and <em>not</em> its {@code visible} flag, so switching them off once
 * in the constructor is permanent.
 *
 * <p>The pods are the one thing here still submitted as bare parts, and correctly: their own pose is
 * {@code PartPose.ZERO} and never varies, so all of the per-entity motion is already baked into the
 * pose {@link GoliathModel#translateToGun} walks. They are drawn twice on purpose — the model's own
 * pass finds nothing at those texels and the cutout render type discards them. <b>Do not try to hide
 * them between the two draws</b>; see {@link GoliathModel}.
 */
public class GoliathGolemLayer extends RenderLayer<GoliathRenderState, GoliathModel> {

    private static final Identifier TEXTURE =
            Identifier.withDefaultNamespace("textures/entity/iron_golem/iron_golem.png");

    private final IronGolemModel golem;

    public GoliathGolemLayer(RenderLayerParent<GoliathRenderState, GoliathModel> parent,
            EntityRendererProvider.Context context) {
        super(parent);
        this.golem = new IronGolemModel(context.bakeLayer(ModelLayers.IRON_GOLEM));
        // Safe to mutate: this model is baked for this layer and nothing else ever touches it. The
        // arms are what the cannon pods replace, and resetPose() never turns them back on.
        this.golem.root().getChild("right_arm").visible = false;
        this.golem.root().getChild("left_arm").visible = false;
    }

    @Override
    public void submit(PoseStack poseStack, SubmitNodeCollector collector, int lightCoords,
            GoliathRenderState state, float yRot, float xRot) {
        if (state.isInvisible) {
            return;
        }
        int overlay = LivingEntityRenderer.getOverlayCoords(state, 0.0f);
        GoliathModel model = this.getParentModel();

        poseStack.pushPose();
        model.translateToGolem(poseStack);
        collector.submitModel(this.golem, golemState(state), poseStack,
                RenderTypes.entityCutout(TEXTURE), lightCoords, overlay, 0, null);
        poseStack.popPose();

        for (boolean left : new boolean[] {true, false}) {
            poseStack.pushPose();
            model.translateToGun(poseStack, left);
            collector.submitModelPart(model.pod(left), poseStack, RenderTypes.entityCutout(TEXTURE),
                    lightCoords, overlay, null);
            poseStack.popPose();
        }
    }

    /**
     * The four fields {@code IronGolemModel.setupAnim} actually reads, copied across. A fresh
     * instance every call — see the class doc; the collector holds it until the frame is flushed.
     * {@code attackTicksRemaining} and {@code offerFlowerTick} stay zero: a Goliath never swings a
     * fist and never offers a poppy, so the walk branch is the one that should run.
     */
    private static IronGolemRenderState golemState(GoliathRenderState state) {
        IronGolemRenderState golem = new IronGolemRenderState();
        golem.walkAnimationPos = state.walkAnimationPos;
        golem.walkAnimationSpeed = state.walkAnimationSpeed;
        golem.yRot = state.yRot;
        golem.xRot = state.xRot;
        return golem;
    }
}
