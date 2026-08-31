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
 * The Goliath: <b>vanilla's iron golem with an autocannon where each arm was, and a villager riding
 * on its shoulders.</b> Very little of it is painted here — the golem is drawn by
 * {@link GoliathGolemLayer} off vanilla's {@code iron_golem.png}, the pilot by
 * {@link GoliathPilotLayer} off vanilla's {@code villager.png} and its armorer welding mask, and
 * even the two <b>cannon pods</b>
 * come off the golem's own arm pixels. All {@code goliath.png} covers is the muzzle collars, the
 * barrels and the cockpit tub.
 *
 * <p>That split is CLAUDE.md's rule rather than a shortcut, and {@link MissileTurretModel} is the
 * worked example this follows almost line for line: a replacement for a vanilla part is still that
 * part and should still be painted like one, so the pods carry vanilla's arm {@code texOffs} and are
 * sized to land exactly on its island; only the genuinely new geometry is the mod's to paint.
 *
 * <p><b>Where it parts company with the turret is that this one walks</b>, and that changes how the
 * borrow has to be submitted — see {@link GoliathGolemLayer}, which draws a real
 * {@code IronGolemModel} rather than a bare {@code ModelPart} so vanilla's own gait animates per
 * Goliath instead of every Goliath on screen sharing one pose.
 *
 * <p><b>The pods carry vanilla's UVs, not this model's, and that is load-bearing.</b> They are
 * {@code 4 x 20 x 6} — <b>vanilla's own arm cross-section, ten pixels shorter</b> — at
 * {@code texOffs(60, 21)} and {@code texOffs(60, 58)}, which are vanilla's own arm offsets.
 *
 * <p>The turret had to match its island's {@code 20 x 36} footprint <em>exactly</em>, and that fixed
 * a trade between depth and length it could not get out of. It did not have to: a footprint only has
 * to <b>fit inside</b> the island for every face to land in real iron plating, and
 * {@code 2*(4+6) = 20} by {@code 6+20 = 26} fits. Keeping vanilla's own depth of 6 then buys
 * something better than merely landing inside — each face lands in the <em>same column</em> vanilla
 * put it in ({@code top} at {@code u 66}, {@code front} at {@code 66}, {@code back} at {@code 76},
 * and so on), so a pod is vanilla's arm truncated rather than an arbitrary crop of it. That is what
 * lets a shorter arm still be a borrowed one. <b>Do not let {@code tools/blockbench_export.py}
 * repack those two.</b> Every other island here is hand-placed in the top-left band, well clear of
 * {@code x 60..80}, so the packer has no overlap to fix; if a cube is ever added, check those two
 * {@code texOffs} literals survived.
 *
 * <p><b>The pods stay visible, and {@code goliath.png} is transparent where they land.</b> They are
 * drawn twice — once in this model's own pass, where the mod texture has nothing at {@code (60,21)}
 * or {@code (60,58)} and the cutout render type discards them, and once by the golem layer against
 * vanilla's texture. Hiding them between the two draws does <em>not</em> work:
 * {@code SubmitNodeCollector.submitModelPart} stores a <em>reference</em> to the {@code ModelPart}
 * and copies only the pose, so {@code visible} is read long after {@code submit} returns.
 * {@code tools/gen_goliath_texture.py} skips these two parts to keep those texels clear.
 *
 * <p><b>The pods are pitched forward rather than hanging</b>, which is the single strongest cue that
 * this is a mech and not a golem — that, and the person sitting on it. Held about 27 degrees below
 * horizontal off the golem's own shoulder pivot {@code (0, -7, 0)} at {@code x = +-11}, where
 * vanilla's arm boxes sit.
 *
 * <p><b>Pitching them forward is also why they are short.</b> A hanging arm's length costs only
 * height, which vanilla has already spent; a pitched one spends it straight out in front, where it
 * becomes the unit's whole footprint on screen. Twenty pixels of pod plus a collar and barrels puts
 * the muzzles about 21 pixels ahead of the chassis at belly height — half what a full 30-pixel arm
 * reached, and about the overhang a Marine's rifle carries.
 *
 * <p><b>The pilot has no legs, and that is deliberate.</b> A golem's head sits straight on its body
 * with no neck, so there is nothing to straddle and the only seat is the body's top surface behind
 * the head — from which any forward-dangling leg goes straight through the skull. So
 * {@link GoliathPilotLayer} switches vanilla's legs off the way the golem layer switches its arms
 * off, and the {@code tub} below is the mod's own cockpit the torso rises out of. The villager's
 * arms are already folded across its belly in vanilla's baked pose, which is exactly a pilot's grip
 * on a set of controls.
 *
 * <p><b>{@code pilot_mount} sits at the golem's crown, not on its shoulders</b>, and it is the
 * number to change if the pilot ever needs moving. Seated on the body top he was behind the skull
 * from every angle a player actually looks from, with only his scalp clear of it; at {@code y = -19}
 * his neck is level with the crown, so the whole head — visor included — stands over it. The tub was
 * deepened to six pixels to keep a raised torso reading as seated rather than as perched.
 *
 * <p>Authored in true pixel space (16px = 1 block), feet at {@code y=24}, "up" is negative y, -z is
 * forward — the space vanilla's golem is already in, so it drops straight into {@code golem}.
 * {@link GoliathRenderer} then scales the whole assembly down, because the golem is 2.69 blocks tall
 * and no ground unit in this mod may be over 2.0 (see {@code entity.UnitFootprintTest}).
 *
 * <p>Only the barrels are painted into {@code goliath_glow.png}; everything else must stay
 * transparent there, since {@link net.bitflora.asteriskcraft.client.UnitGlowLayer} re-submits the
 * whole model. The borrowed golem and the borrowed pilot are separate draws and so are never glowed.
 */
public class GoliathModel extends EntityModel<GoliathRenderState> {
    /**
     * How far the cannon pods are swung forward, as an {@code xRot} off hanging. The pods grow
     * <em>down</em> from the shoulder, and a down-growing box swings forward on a <em>negative</em>
     * xRot (see docs/neoforge-api-notes.md) — 1.1 radians is 63 degrees forward of straight down,
     * i.e. 27 degrees below horizontal.
     */
    private static final float GUN_PITCH = -1.1f;

    /** How far the pods rock back up at the top of a burst, in radians. */
    private static final float RECOIL_PITCH = 0.18f;

    /** How far the pods shove backwards at the top of a burst, in pixels. Positive z is back. */
    private static final float RECOIL_PUSH = 1.5f;

    private final ModelPart golem;
    private final ModelPart gunLeft;
    private final ModelPart gunRight;
    private final ModelPart podLeft;
    private final ModelPart podRight;
    private final ModelPart pilotMount;

    public GoliathModel(ModelPart root) {
        super(root);
        this.golem = root.getChild("golem");
        this.gunLeft = root.getChild("gun_left");
        this.gunRight = root.getChild("gun_right");
        this.podLeft = this.gunLeft.getChild("pod_left");
        this.podRight = this.gunRight.getChild("pod_right");
        this.pilotMount = root.getChild("pilot_mount");
    }

    @Override
    public void setupAnim(GoliathRenderState state) {
        super.setupAnim(state);

        // The walk swing is vanilla's own arm formula, reproduced rather than borrowed: the pods
        // replaced the golem's arms, so they have to move like them or the unit walks with two
        // frozen limbs beside two swinging legs. IronGolemModel.setupAnim is where these numbers
        // come from, and the golem layer is running that same method on the legs at the same time.
        float wave = Mth.triangleWave(state.walkAnimationPos, 13.0f);
        float speed = state.walkAnimationSpeed;
        this.gunRight.xRot += (-0.2f + 1.5f * wave) * speed;
        this.gunLeft.xRot += (-0.2f - 1.5f * wave) * speed;

        // One out-and-back pulse across the burst: 0 at both ends, peak in the middle. The muzzles
        // kick up (back towards hanging, so xRot rises) and the whole pod shoves backwards.
        float kick = Mth.sin(Mth.clamp(state.attackProgress, 0.0f, 1.0f) * Mth.PI);
        this.gunLeft.xRot += kick * RECOIL_PITCH;
        this.gunRight.xRot += kick * RECOIL_PITCH;
        this.gunLeft.z += kick * RECOIL_PUSH;
        this.gunRight.z += kick * RECOIL_PUSH;
    }

    /**
     * Walks the part chain down to the empty {@code golem} container, so {@link GoliathGolemLayer}
     * can drop vanilla's golem in exactly where this model says the chassis goes. Same contract as
     * {@code MissileTurretModel.translateToGolem}.
     */
    public void translateToGolem(PoseStack poseStack) {
        this.root.translateAndRotate(poseStack);
        this.golem.translateAndRotate(poseStack);
    }

    /**
     * Walks the part chain down to one cannon's pivot, so the golem layer can submit that cannon's
     * {@linkplain #pod(boolean) pod} — which hangs off it with a zero pose — against vanilla's
     * texture, swing and recoil and all.
     */
    public void translateToGun(PoseStack poseStack, boolean left) {
        this.root.translateAndRotate(poseStack);
        (left ? this.gunLeft : this.gunRight).translateAndRotate(poseStack);
    }

    /** Walks the part chain down to the pilot's seat, for {@link GoliathPilotLayer}. */
    public void translateToPilot(PoseStack poseStack) {
        this.root.translateAndRotate(poseStack);
        this.pilotMount.translateAndRotate(poseStack);
    }

    /** One cannon's pod: the cube carrying vanilla's arm UVs, painted by the golem layer. */
    public ModelPart pod(boolean left) {
        return left ? this.podLeft : this.podRight;
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();

        // --- The borrowed chassis. No cubes: GoliathGolemLayer bakes ModelLayers.IRON_GOLEM and
        // draws it here, off vanilla's iron_golem.png. Vanilla's root already stands its own head,
        // body and legs relative to the origin (feet at y=24), so this container sits at zero and
        // exists only to give the layer something to hang off and turn with.
        root.addOrReplaceChild("golem", CubeListBuilder.create(), PartPose.ZERO);

        // --- The cannons, at the golem's own shoulder pivot and at the x its arm boxes occupy,
        // swung forward. The containers carry the pose, the walk swing and the recoil; the geometry
        // hangs off them, split by which texture paints it.
        PartDefinition gunLeft = root.addOrReplaceChild("gun_left", CubeListBuilder.create(),
                PartPose.offsetAndRotation(11.0f, -7.0f, 0.0f, GUN_PITCH, 0.0f, 0.0f));
        PartDefinition gunRight = root.addOrReplaceChild("gun_right", CubeListBuilder.create(),
                PartPose.offsetAndRotation(-11.0f, -7.0f, 0.0f, GUN_PITCH, 0.0f, 0.0f));

        // The pods: vanilla's arm, ten pixels shorter. 4x20x6 keeps vanilla's own 4x6 section, so
        // the footprint fits inside the 20x36 arm island AND every face lands in the column vanilla
        // put it in. These two texOffs are hand-set to vanilla's arm offsets and must not be
        // repacked. See the class docs.
        gunLeft.addOrReplaceChild("pod_left",
                CubeListBuilder.create()
                        .texOffs(60, 21).addBox(-2.0f, -2.5f, -3.0f, 4.0f, 20.0f, 6.0f),
                PartPose.ZERO);
        gunRight.addOrReplaceChild("pod_right",
                CubeListBuilder.create()
                        .texOffs(60, 58).addBox(-2.0f, -2.5f, -3.0f, 4.0f, 20.0f, 6.0f),
                PartPose.ZERO);

        // The muzzle collar and barrels: the mod's own, and most of what goliath.png covers. Written
        // out rather than looped so each cube owns one editable texOffs literal
        // (tools/blockbench_export.py), and hand-placed in the top-left band well clear of the pods'
        // vanilla islands at x 60..80. Three abutting barrels with the centre one longer, so the
        // cluster still reads as three at the scale this unit renders at — the Missile Turret's
        // missiles solved the same legibility problem the same way.
        gunLeft.addOrReplaceChild("muzzle_left",
                CubeListBuilder.create()
                        .texOffs(0, 14).addBox(-3.5f, 14.5f, -3.5f, 7.0f, 3.0f, 7.0f)
                        .texOffs(0, 38).addBox(-3.0f, 17.5f, -1.0f, 2.0f, 5.0f, 2.0f)
                        .texOffs(10, 38).addBox(-1.0f, 17.5f, -1.0f, 2.0f, 6.0f, 2.0f)
                        .texOffs(20, 38).addBox(1.0f, 17.5f, -1.0f, 2.0f, 5.0f, 2.0f),
                PartPose.ZERO);
        gunRight.addOrReplaceChild("muzzle_right",
                CubeListBuilder.create()
                        .texOffs(0, 26).addBox(-3.5f, 14.5f, -3.5f, 7.0f, 3.0f, 7.0f)
                        .texOffs(0, 50).addBox(-3.0f, 17.5f, -1.0f, 2.0f, 5.0f, 2.0f)
                        .texOffs(10, 50).addBox(-1.0f, 17.5f, -1.0f, 2.0f, 6.0f, 2.0f)
                        .texOffs(20, 50).addBox(1.0f, 17.5f, -1.0f, 2.0f, 5.0f, 2.0f),
                PartPose.ZERO);

        // --- The cockpit. The tub is the mod's, and it is what makes a legless pilot read as a
        // seated one: it sits on the golem's body top (y=-9) behind the head (z 0.5..5), and the
        // borrowed torso rises out of it. Six deep rather than five, so it still swallows the bottom
        // of a torso that was raised to put the pilot's eyes over the golem's head.
        root.addOrReplaceChild("tub",
                CubeListBuilder.create()
                        .texOffs(0, 0).addBox(-4.0f, -15.0f, -1.0f, 8.0f, 6.0f, 7.0f),
                PartPose.ZERO);

        // The seat itself: another empty container, positioned where the borrowed villager's own
        // origin — its neck — has to land for its torso to sit in the tub at GoliathPilotLayer's
        // scale. Change one and the other moves.
        root.addOrReplaceChild("pilot_mount", CubeListBuilder.create(),
                PartPose.offset(0.0f, -19.0f, 2.5f));

        return LayerDefinition.create(mesh, 128, 128);
    }
}
