package net.bitflora.asteriskcraft.client.protoss;

import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.util.Mth;

/**
 * Hand-authored geometry for the Dragoon, shaped after the StarCraft sprite: a squat, armoured
 * quadruped walker. A wide gold <b>body pod</b> with a blue energy core rides high on <b>four arched,
 * spider-like legs</b>, and a domed front <b>cockpit</b> carries a single glowing orange-red eye.
 *
 * <p>There is no 1.12 model to port (the old mod's {@code ModelDragoon} is an empty stub), so this is
 * net-new. It follows {@link PhotonCannonModel}'s conventions: authored in true pixel space
 * (16px = 1 block), "up" is negative y, and colour comes from {@code textures/entity/dragoon.png}, in
 * which <b>every cube owns its own UV island</b> so it can be hand-painted independently — see
 * {@code tools/blockbench_export.py}, which packs those islands and emits the Blockbench project.
 * Only the eye is painted in {@code dragoon_glow.png}, so the emissive
 * {@link UnitGlowLayer} lights just the eye.
 *
 * <p>Each leg is a single animated {@link PartDefinition} pivoting at the body (thigh angled up-and-out,
 * a knee-bent shin dropping to a foot), mirroring the vanilla {@code SpiderModel} splay reduced from
 * eight legs to four corners. {@link #setupAnim} adds a diagonal-gait walk cycle onto that baked splay:
 * the two diagonal pairs (front-right + back-left, front-left + back-right) swing and step in
 * opposition, so the walker moves its legs like a spider. Head-look tracks the look direction.
 */
public class DragoonModel extends EntityModel<LivingEntityRenderState> {
    private static final float DEG = (float) (Math.PI / 180.0);
    private static final float QUARTER = (float) (Math.PI / 4.0); // 45°: leg splay yaw + thigh lift

    private final ModelPart head;
    private final ModelPart legFrontRight;
    private final ModelPart legFrontLeft;
    private final ModelPart legBackRight;
    private final ModelPart legBackLeft;

    // Baked-in splay so setupAnim can add swing/step deltas on top.
    private final float legFRz0, legFLz0, legBRz0, legBLz0;

    public DragoonModel(ModelPart root) {
        super(root);
        ModelPart body = root.getChild("body");
        this.head = body.getChild("head");
        this.legFrontRight = body.getChild("leg_front_right");
        this.legFrontLeft = body.getChild("leg_front_left");
        this.legBackRight = body.getChild("leg_back_right");
        this.legBackLeft = body.getChild("leg_back_left");
        this.legFRz0 = this.legFrontRight.zRot;
        this.legFLz0 = this.legFrontLeft.zRot;
        this.legBRz0 = this.legBackRight.zRot;
        this.legBLz0 = this.legBackLeft.zRot;
    }

    @Override
    public void setupAnim(LivingEntityRenderState state) {
        super.setupAnim(state);

        // Head-look: the cockpit tracks the look direction.
        this.head.yRot = state.yRot * DEG;
        this.head.xRot += state.xRot * DEG;

        // Diagonal-gait walk. pos advances the cycle; speed scales it to zero when standing still.
        float pos = state.walkAnimationPos * 0.6662f;
        float speed = state.walkAnimationSpeed;

        // Two diagonal pairs, half a cycle apart: (FR, BL) at phase 0, (FL, BR) at phase PI.
        animateLeg(this.legFrontRight, this.legFRz0, pos, speed, false, 0.0f);
        animateLeg(this.legBackLeft, this.legBLz0, pos, speed, true, 0.0f);
        animateLeg(this.legFrontLeft, this.legFLz0, pos, speed, true, Mth.PI);
        animateLeg(this.legBackRight, this.legBRz0, pos, speed, false, Mth.PI);
    }

    /**
     * Applies one leg's walk delta on top of its baked splay: a fore/aft swing about the body pivot
     * (yRot) plus an upward step during the forward half of the swing (zRot). {@code left} legs mirror
     * the right side's signs.
     */
    private static void animateLeg(ModelPart leg, float baseZ, float pos, float speed, boolean left, float phase) {
        float swing = Mth.cos(pos + phase) * 0.45f * speed;
        float step = Math.max(0.0f, Mth.sin(pos + phase)) * 0.5f * speed;
        leg.yRot += left ? -swing : swing;
        leg.zRot = baseZ + (left ? -step : step);
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();

        // Body pod rides ~1 block up on the legs; -z is forward (the cockpit faces -z).
        PartDefinition body = root.addOrReplaceChild("body",
                CubeListBuilder.create()
                        // Chunky rounded hull: two crossed gold boxes read as a wide, rounded pod.
                        .texOffs(0, 0).addBox(-6.0f, -5.0f, -7.0f, 12.0f, 9.0f, 14.0f)
                        .texOffs(53, 0).addBox(-7.0f, -4.0f, -6.0f, 14.0f, 7.0f, 12.0f)
                        // Raised back hump.
                        .texOffs(41, 40).addBox(-4.0f, -8.0f, -1.0f, 8.0f, 4.0f, 8.0f),
                PartPose.offset(0.0f, 5.5f, 0.0f));
        // Blue energy core underbelly.
        body.addOrReplaceChild("core",
                CubeListBuilder.create()
                        .texOffs(0, 40).addBox(-5.0f, 3.0f, -5.0f, 10.0f, 3.0f, 10.0f),
                PartPose.ZERO);

        // Domed cockpit at the front-top, with a dark socket and a glowing eye.
        PartDefinition head = body.addOrReplaceChild("head",
                CubeListBuilder.create()
                        .texOffs(0, 24).addBox(-5.0f, -4.0f, -4.0f, 10.0f, 7.0f, 8.0f)
                        .texOffs(74, 40).addBox(-4.0f, -6.0f, -3.0f, 8.0f, 3.0f, 6.0f),
                PartPose.offset(0.0f, -3.0f, -7.0f));
        // Dark socket recessed into the front face so the eye reads against black.
        head.addOrReplaceChild("socket",
                CubeListBuilder.create()
                        .texOffs(103, 40).addBox(-4.0f, -2.0f, -5.0f, 8.0f, 5.0f, 2.0f),
                PartPose.ZERO);
        // Glowing orange-red eye, protruding slightly from the socket.
        head.addOrReplaceChild("eye",
                CubeListBuilder.create()
                        .texOffs(58, 62).addBox(-2.5f, -1.0f, -6.0f, 5.0f, 3.0f, 2.0f),
                PartPose.ZERO);

        // Four legs at the pod's corners. Front legs splay forward-out, back legs back-out. Each leg is
        // a thigh angled up-and-out from the body pivot, then a knee-bent shin that drops straight down
        // to a foot pad planted on the ground. Each shin's rotation is the exact inverse of its thigh's
        // (xRot/yRot/zRot below), so the shin — whatever the thigh's splay — hangs world-vertical and the
        // foot pad lands flat at model y=24 (the ground line; see PhotonCannonModel). With the body pod
        // offset to y=5.5 the knees sit at y=10 and the 14px shins reach exactly to y=24.
        //
        // Written out per leg rather than built by a shared helper: every cube needs its own editable
        // texOffs literal so it can own a UV island and be hand-painted independently (see
        // tools/blockbench_export.py). A helper called four times would force twelve cubes to share
        // three offsets, which is exactly the flat-zone texturing this model moved away from.
        PartDefinition legFR = body.addOrReplaceChild("leg_front_right",
                CubeListBuilder.create()
                        .texOffs(29, 62).addBox(-11.0f, -1.5f, -1.5f, 11.0f, 3.0f, 3.0f),
                PartPose.offsetAndRotation(-6.0f, -1.0f, -5.0f, 0.0f, -QUARTER, -QUARTER));
        PartDefinition legFRShin = legFR.addOrReplaceChild("leg_front_right_shin",
                CubeListBuilder.create()
                        .texOffs(76, 24).addBox(-1.5f, 0.0f, -1.5f, 3.0f, 12.0f, 3.0f),
                PartPose.offsetAndRotation(-11.0f, 0.0f, 0.0f, 0.6155f, 0.5236f, 0.9553f));
        legFRShin.addOrReplaceChild("leg_front_right_foot",
                CubeListBuilder.create()
                        .texOffs(51, 54).addBox(-2.0f, 11.0f, -2.0f, 4.0f, 3.0f, 4.0f),
                PartPose.ZERO);

        PartDefinition legFL = body.addOrReplaceChild("leg_front_left",
                CubeListBuilder.create()
                        .texOffs(0, 62).addBox(0.0f, -1.5f, -1.5f, 11.0f, 3.0f, 3.0f),
                PartPose.offsetAndRotation(6.0f, -1.0f, -5.0f, 0.0f, QUARTER, QUARTER));
        PartDefinition legFLShin = legFL.addOrReplaceChild("leg_front_left_shin",
                CubeListBuilder.create()
                        .texOffs(63, 24).addBox(-1.5f, 0.0f, -1.5f, 3.0f, 12.0f, 3.0f),
                PartPose.offsetAndRotation(11.0f, 0.0f, 0.0f, 0.6155f, -0.5236f, -0.9553f));
        legFLShin.addOrReplaceChild("leg_front_left_foot",
                CubeListBuilder.create()
                        .texOffs(34, 54).addBox(-2.0f, 11.0f, -2.0f, 4.0f, 3.0f, 4.0f),
                PartPose.ZERO);

        PartDefinition legBR = body.addOrReplaceChild("leg_back_right",
                CubeListBuilder.create()
                        .texOffs(97, 54).addBox(-11.0f, -1.5f, -1.5f, 11.0f, 3.0f, 3.0f),
                PartPose.offsetAndRotation(-6.0f, -1.0f, 5.0f, 0.0f, QUARTER, -QUARTER));
        PartDefinition legBRShin = legBR.addOrReplaceChild("leg_back_right_shin",
                CubeListBuilder.create()
                        .texOffs(50, 24).addBox(-1.5f, 0.0f, -1.5f, 3.0f, 12.0f, 3.0f),
                PartPose.offsetAndRotation(-11.0f, 0.0f, 0.0f, -0.6155f, -0.5236f, 0.9553f));
        legBRShin.addOrReplaceChild("leg_back_right_foot",
                CubeListBuilder.create()
                        .texOffs(17, 54).addBox(-2.0f, 11.0f, -2.0f, 4.0f, 3.0f, 4.0f),
                PartPose.ZERO);

        PartDefinition legBL = body.addOrReplaceChild("leg_back_left",
                CubeListBuilder.create()
                        .texOffs(68, 54).addBox(0.0f, -1.5f, -1.5f, 11.0f, 3.0f, 3.0f),
                PartPose.offsetAndRotation(6.0f, -1.0f, 5.0f, 0.0f, -QUARTER, QUARTER));
        PartDefinition legBLShin = legBL.addOrReplaceChild("leg_back_left_shin",
                CubeListBuilder.create()
                        .texOffs(37, 24).addBox(-1.5f, 0.0f, -1.5f, 3.0f, 12.0f, 3.0f),
                PartPose.offsetAndRotation(11.0f, 0.0f, 0.0f, -0.6155f, 0.5236f, -0.9553f));
        legBLShin.addOrReplaceChild("leg_back_left_foot",
                CubeListBuilder.create()
                        .texOffs(0, 54).addBox(-2.0f, 11.0f, -2.0f, 4.0f, 3.0f, 4.0f),
                PartPose.ZERO);

        return LayerDefinition.create(mesh, 128, 128);
    }

}
