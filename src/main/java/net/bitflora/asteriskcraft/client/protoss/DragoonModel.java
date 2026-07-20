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
 * (16px = 1 block), "up" is negative y, and colour comes from flat single-colour <b>material zones</b>
 * of {@code textures/entity/dragoon.png} (see {@code tools/gen_dragoon_texture.py}). Every cube points
 * its {@code texOffs} at the top-left of its material's zone, so cubes of the same material freely
 * share offsets. Only the EYE zone is painted in {@code dragoon_glow.png}, so the emissive
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

    // Material zone offsets — match the zones painted by gen_dragoon_texture.py.
    private static final int GOLD_U = 0, GOLD_V = 0;
    private static final int BLUE_U = 0, BLUE_V = 64;
    private static final int DARK_U = 60, DARK_V = 64;
    private static final int EYE_U = 104, EYE_V = 64;

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
                        .texOffs(GOLD_U, GOLD_V).addBox(-6.0f, -5.0f, -7.0f, 12.0f, 9.0f, 14.0f)
                        .texOffs(GOLD_U, GOLD_V).addBox(-7.0f, -4.0f, -6.0f, 14.0f, 7.0f, 12.0f)
                        // Raised back hump.
                        .texOffs(GOLD_U, GOLD_V).addBox(-4.0f, -8.0f, -1.0f, 8.0f, 4.0f, 8.0f),
                PartPose.offset(0.0f, 8.0f, 0.0f));
        // Blue energy core underbelly.
        body.addOrReplaceChild("core",
                CubeListBuilder.create()
                        .texOffs(BLUE_U, BLUE_V).addBox(-5.0f, 3.0f, -5.0f, 10.0f, 3.0f, 10.0f),
                PartPose.ZERO);

        // Domed cockpit at the front-top, with a dark socket and a glowing eye.
        PartDefinition head = body.addOrReplaceChild("head",
                CubeListBuilder.create()
                        .texOffs(GOLD_U, GOLD_V).addBox(-5.0f, -4.0f, -4.0f, 10.0f, 7.0f, 8.0f)
                        .texOffs(GOLD_U, GOLD_V).addBox(-4.0f, -6.0f, -3.0f, 8.0f, 3.0f, 6.0f),
                PartPose.offset(0.0f, -3.0f, -7.0f));
        // Dark socket recessed into the front face so the eye reads against black.
        head.addOrReplaceChild("socket",
                CubeListBuilder.create()
                        .texOffs(DARK_U, DARK_V).addBox(-4.0f, -2.0f, -5.0f, 8.0f, 5.0f, 2.0f),
                PartPose.ZERO);
        // Glowing orange-red eye, protruding slightly from the socket.
        head.addOrReplaceChild("eye",
                CubeListBuilder.create()
                        .texOffs(EYE_U, EYE_V).addBox(-2.5f, -1.0f, -6.0f, 5.0f, 3.0f, 2.0f),
                PartPose.ZERO);

        // Four legs at the pod's corners. Front legs splay forward-out, back legs back-out.
        addLeg(body, "leg_front_right", -6.0f, -5.0f, -QUARTER, -QUARTER, false);
        addLeg(body, "leg_front_left", 6.0f, -5.0f, QUARTER, QUARTER, true);
        addLeg(body, "leg_back_right", -6.0f, 5.0f, QUARTER, -QUARTER, false);
        addLeg(body, "leg_back_left", 6.0f, 5.0f, -QUARTER, QUARTER, true);

        return LayerDefinition.create(mesh, 128, 128);
    }

    /**
     * Builds one arched spider leg pivoting at ({@code pivotX}, -1, {@code pivotZ}) on the body: a
     * thigh angled up-and-out by {@code zRot} and splayed fore/aft by {@code yRot}, then a knee-bent
     * shin dropping to a small foot. {@code left} legs extend along +x and bend the opposite way.
     */
    private static void addLeg(PartDefinition body, String name, float pivotX, float pivotZ,
                               float yRot, float zRot, boolean left) {
        int s = left ? 1 : -1;
        float thighLen = 11.0f;
        // Thigh extends outward from the pivot (−x for right, +x for left).
        float thighMinX = left ? 0.0f : -thighLen;
        PartDefinition leg = body.addOrReplaceChild(name,
                CubeListBuilder.create()
                        .texOffs(DARK_U, DARK_V).addBox(thighMinX, -1.5f, -1.5f, thighLen, 3.0f, 3.0f),
                PartPose.offsetAndRotation(pivotX, -1.0f, pivotZ, 0.0f, yRot, zRot));
        // Knee at the thigh's outer end; bend the shin back toward vertical (opposite the thigh lift).
        PartDefinition shin = leg.addOrReplaceChild(name + "_shin",
                CubeListBuilder.create()
                        .texOffs(DARK_U, DARK_V).addBox(-1.5f, 0.0f, -1.5f, 3.0f, 12.0f, 3.0f),
                PartPose.offsetAndRotation(s * thighLen, 0.0f, 0.0f, 0.0f, 0.0f, s * 1.15f));
        // Foot pad at the bottom of the shin.
        shin.addOrReplaceChild(name + "_foot",
                CubeListBuilder.create()
                        .texOffs(DARK_U, DARK_V).addBox(-2.0f, 11.0f, -2.0f, 4.0f, 3.0f, 4.0f),
                PartPose.ZERO);
    }
}
