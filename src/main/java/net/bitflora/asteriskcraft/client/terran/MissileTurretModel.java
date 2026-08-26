package net.bitflora.asteriskcraft.client.terran;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.util.Mth;

/**
 * The Missile Turret: <b>vanilla's iron golem with a missile rack where each arm was.</b> Almost
 * none of it is painted here — the head, the body, the legs <em>and the racks themselves</em> are
 * drawn by {@link MissileTurretGolemLayer} off vanilla's own {@code iron_golem.png}, and the only
 * thing {@code missile_turret.png} covers is the six missiles.
 *
 * <p>That split is CLAUDE.md's rule rather than a shortcut: borrowing a vanilla model means
 * borrowing its texture too, because a model part can only be painted from its own model's texture.
 * The racks are new geometry but they are still the golem's arms, so they are painted from the
 * golem's arm pixels; only the missiles are genuinely the mod's and so only the missiles are the
 * mod's to paint. It is also the first time the mod borrows something other than a head — the
 * mechanism is unchanged from {@link MarineHeadLayer}, with {@code golem} an <b>empty container
 * part</b> the layer draws into and {@link #translateToGolem} walking the chain to it.
 *
 * <p><b>The racks are held 30 degrees above horizontal</b>, hung at the golem's own shoulder pivot
 * {@code (0, -7, 0)} at {@code x = ±11} where vanilla's arm boxes sit. Shallow rather than upright,
 * which is what the reference art has and what keeps the whole unit inside the golem's own 2.7-block
 * height instead of towering over it. They are pivoted two thirds of the way along, so the breech
 * sits behind the shoulder as a counterweight and the muzzle reaches forward without the hitbox
 * having to grow to a launcher's full length.
 *
 * <p><b>That pitch is what decides which of the box's dimensions is the tall one, and it is not the
 * long one.</b> Under an {@code xRot} of 60 degrees the box's own length swings to within 30 degrees
 * of horizontal — that is the direction it points — and it is the box's <em>depth</em> that swings
 * up to within 30 degrees of vertical. So a rack that should read as much taller than it is wide has
 * to be narrow in x and deep in z: {@code 3 x 29 x 7} presents 3 texels across and
 * {@code 7 * cos 30 = 6.1} tall, a two-to-one upright plate. Widening it front-to-back would lay it
 * flat however long it is.
 *
 * <p><b>The two shell cubes carry vanilla's UVs, not this model's, and that is load-bearing.</b>
 * {@code 3 x 29 x 7} is not only the shape above, it is a shape whose box-UV footprint is exactly
 * {@code 2*(3+7) = 20} by {@code 7+29 = 36} texels — precisely vanilla's arm island — so
 * {@code texOffs(60, 21)} and {@code texOffs(60, 58)} land <em>every</em> face inside real iron
 * plating, with not one texel sampled off the edge of it. The length is 29 rather than anything
 * longer because that footprint fixes it: depth and length trade against each other inside the same
 * island, and an upright plate is worth more here than a longer boom. <b>Do not let
 * {@code tools/blockbench_export.py} repack those two.</b> It only repacks a model whose islands
 * overlap, and the missiles below are placed well clear of them for that reason; if a cube is ever
 * added here, check those two {@code texOffs} literals survived.
 *
 * <p><b>The shells stay visible, and {@code missile_turret.png} is transparent where they land.</b>
 * They are drawn twice — once in this model's own pass, where the mod texture has nothing at
 * {@code (60,21)} or {@code (60,58)} and the cutout render type discards them, and once by the golem
 * layer against vanilla's texture. Hiding them between the two draws instead does <em>not</em> work
 * and is worth recording: {@code SubmitNodeCollector.submitModelPart} stores a <em>reference</em> to
 * the {@code ModelPart} and copies only the pose, so {@code visible} is read long after {@code submit}
 * returns — flipping it back makes the part vanish from the frame it was just submitted to.
 * {@code tools/gen_missile_turret_texture.py} skips these two parts to keep those texels clear.
 *
 * <p>Three missiles stand proud of each muzzle, <b>stacked up the plate rather than laid across
 * it</b>, since the plate's tall axis is the one the pitch raised; the centre one is longer, so an
 * abutting row still reads as three.
 *
 * <p>Nothing here aims: the turret hits with a hitscan salvo and swinging the racks at a target
 * would promise a tracking weapon the goal does not implement. The only animation is the recoil —
 * both racks rock back and drop a little on each salvo, a single out-and-back pulse driven by
 * {@code attackProgress}.
 *
 * <p>Authored in true pixel space (16px = 1 block), feet at {@code y=24}, "up" is negative y, -z is
 * forward, so the renderer applies no extra scale and the borrowed golem drops straight in.
 *
 * <p>Only the missiles are painted into {@code missile_turret_glow.png}; everything else must stay
 * transparent there, since {@link net.bitflora.asteriskcraft.client.UnitGlowLayer} re-submits the
 * whole model. The borrowed golem and the shells are separate draws and so are never glowed.
 */
public class MissileTurretModel extends EntityModel<MissileTurretRenderState> {
    /**
     * How far above horizontal the racks are held, as an {@code xRot} off vertical: 60 degrees off
     * upright is 30 degrees off flat.
     */
    private static final float RACK_PITCH = (float) (Math.PI / 3.0);

    /** How far the racks rock back at the top of a salvo, in radians. */
    private static final float RECOIL_PITCH = 0.2f;

    /** How far the racks drop at the top of a salvo, in pixels. Positive y is down. */
    private static final float RECOIL_DROP = 2.0f;

    private final ModelPart golem;
    private final ModelPart rackLeft;
    private final ModelPart rackRight;
    private final ModelPart shellLeft;
    private final ModelPart shellRight;

    public MissileTurretModel(ModelPart root) {
        super(root);
        this.golem = root.getChild("golem");
        this.rackLeft = root.getChild("rack_left");
        this.rackRight = root.getChild("rack_right");
        this.shellLeft = this.rackLeft.getChild("shell_left");
        this.shellRight = this.rackRight.getChild("shell_right");
    }

    @Override
    public void setupAnim(MissileTurretRenderState state) {
        super.setupAnim(state);
        // One out-and-back pulse across the strike animation: 0 at both ends, peak in the middle.
        float kick = Mth.sin(Mth.clamp(state.attackProgress, 0.0f, 1.0f) * Mth.PI);
        this.rackLeft.xRot -= kick * RECOIL_PITCH;
        this.rackRight.xRot -= kick * RECOIL_PITCH;
        this.rackLeft.y += kick * RECOIL_DROP;
        this.rackRight.y += kick * RECOIL_DROP;
    }

    /**
     * Walks the part chain down to the empty {@code golem} container, so
     * {@link MissileTurretGolemLayer} can drop vanilla's baked golem in exactly where this model says
     * the body goes. Same contract as {@code MarineModel.translateToHead}.
     */
    public void translateToGolem(PoseStack poseStack) {
        this.root.translateAndRotate(poseStack);
        this.golem.translateAndRotate(poseStack);
    }

    /**
     * Walks the part chain down to one rack's pivot, so the golem layer can submit that rack's
     * {@linkplain #shell(boolean) shell} — which hangs off it with a zero pose — against vanilla's
     * texture, recoil and all.
     */
    public void translateToRack(PoseStack poseStack, boolean left) {
        this.root.translateAndRotate(poseStack);
        (left ? this.rackLeft : this.rackRight).translateAndRotate(poseStack);
    }

    /** One rack's shell: the cube carrying vanilla's arm UVs, hidden in this model's own draw. */
    public ModelPart shell(boolean left) {
        return left ? this.shellLeft : this.shellRight;
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();

        // --- The borrowed body. No cubes: MissileTurretGolemLayer bakes ModelLayers.IRON_GOLEM and
        // draws it here, off vanilla's iron_golem.png. Vanilla's root already stands its own head,
        // body and legs relative to the origin (feet at y=24), so this container sits at zero and
        // exists only to give the layer something to hang off and turn with.
        root.addOrReplaceChild("golem", CubeListBuilder.create(), PartPose.ZERO);

        // --- The racks, at the golem's own shoulder pivot and at the x its arm boxes occupy, held
        // 30 degrees above horizontal. The containers carry the pose and the recoil; the geometry
        // hangs off them, split by which texture paints it.
        PartDefinition rackLeft = root.addOrReplaceChild("rack_left", CubeListBuilder.create(),
                PartPose.offsetAndRotation(11.0f, -7.0f, 0.0f, RACK_PITCH, 0.0f, 0.0f));
        PartDefinition rackRight = root.addOrReplaceChild("rack_right", CubeListBuilder.create(),
                PartPose.offsetAndRotation(-11.0f, -7.0f, 0.0f, RACK_PITCH, 0.0f, 0.0f));

        // The shells: vanilla's arm island, verbatim. 3x29x7 packs to exactly the 20x36 texels
        // vanilla's 4x30x6 arm occupies, which is why these two texOffs are hand-set to vanilla's
        // arm offsets and must not be repacked. Narrow in x and deep in z because the pitch below
        // raises z, not the length. See the class docs for both.
        rackLeft.addOrReplaceChild("shell_left",
                CubeListBuilder.create()
                        .texOffs(60, 21).addBox(-1.5f, -20.0f, -3.5f, 3.0f, 29.0f, 7.0f),
                PartPose.ZERO);
        rackRight.addOrReplaceChild("shell_right",
                CubeListBuilder.create()
                        .texOffs(60, 58).addBox(-1.5f, -20.0f, -3.5f, 3.0f, 29.0f, 7.0f),
                PartPose.ZERO);

        // The missiles: the mod's own, and the only thing missile_turret.png covers. Stacked up the
        // plate's tall axis rather than across its narrow one, standing out of the muzzle. Written
        // out rather than looped so each owns one editable texOffs literal
        // (tools/blockbench_export.py), and parked in the bottom band of the texture, well clear of
        // the shells' vanilla islands. The centre one is a pixel longer, so three abutting tips
        // still read as three.
        rackLeft.addOrReplaceChild("missiles_left",
                CubeListBuilder.create()
                        .texOffs(0, 100).addBox(-1.0f, -25.0f, -3.0f, 2.0f, 5.0f, 2.0f)
                        .texOffs(10, 100).addBox(-1.0f, -26.0f, -1.0f, 2.0f, 6.0f, 2.0f)
                        .texOffs(20, 100).addBox(-1.0f, -25.0f, 1.0f, 2.0f, 5.0f, 2.0f),
                PartPose.ZERO);
        rackRight.addOrReplaceChild("missiles_right",
                CubeListBuilder.create()
                        .texOffs(30, 100).addBox(-1.0f, -25.0f, -3.0f, 2.0f, 5.0f, 2.0f)
                        .texOffs(40, 100).addBox(-1.0f, -26.0f, -1.0f, 2.0f, 6.0f, 2.0f)
                        .texOffs(50, 100).addBox(-1.0f, -25.0f, 1.0f, 2.0f, 5.0f, 2.0f),
                PartPose.ZERO);

        return LayerDefinition.create(mesh, 128, 128);
    }
}
