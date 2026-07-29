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
 * Hand-authored geometry for the Photon Cannon: a golden <b>square base</b> ringed by eight spikes,
 * with the glowing cyan <b>core</b> set flat into its middle, and a one-block gold <b>cube</b>
 * hovering above the whole thing with <b>nothing connecting the two</b>. The cube bobs; that is the
 * only motion, and it is what makes the empty air under it read as levitation rather than a mistake.
 *
 * <p>The shape is deliberately blocky — square base, cubic float, square plinth — so the unit sits in
 * Minecraft rather than looking like a model imported from somewhere else. The one round thing on it
 * is the core, and it earns that by being the part that glows.
 *
 * <p>The core is authored in the XY plane — a rim, a bezel and a lens, exactly as if it faced the
 * camera — and then laid flat by its part pose alone ({@code xRot = -PI/2}, which maps the part's
 * local -Z to world up). One useful consequence of that rotation: local {@code z} becomes height, so
 * every cube's z range reads directly as "this many pixels above the base". The lens ends up 0.6px
 * below the rim's top, so the glow sits recessed in a shallow well rather than proud of it.
 *
 * <p>The core's rim is the model's one curved shape and it is built from <b>crossed planks</b> — a
 * wide plank plus a deep plank make an axis "plus", and a 45°-rotated copy of that pair fills the
 * diagonals, so the union reads as an inscribed octagon. The lens is a single square plate: buried
 * under that rim, its corners never show, so the disc is simply painted round. That is cheaper and
 * cleaner than the crossed pair it replaced, whose notches at the diagonals made the glow ragged.
 *
 * <p>Each spike is <b>one box</b>, and the eight of them run two per side, straight out of the
 * slab's edges. Not one every 45°: on a square the diagonals already reach further than the edges do,
 * so corner spikes come out as stubs while burying the corner they grow from, and the block stops
 * reading as a square at all.
 *
 * <p>Colour comes from {@code textures/entity/photon_cannon.png}, in which <b>every cube owns its own
 * UV island</b> so it can be hand-painted independently — see {@code tools/blockbench_export.py},
 * which packs those islands and emits the Blockbench project. That tooling constrains how this class
 * may be written: one {@code texOffs} literal per cube (so the eight teeth and the four spike pairs
 * are written out rather than looped), builders inlined into their {@code addOrReplaceChild} call, and
 * globally unique part names. See docs/texturing.md. Only the lens, the eyes and the cheek accents
 * are painted into {@code photon_cannon_glow.png}, so the emissive
 * {@link net.bitflora.asteriskcraft.client.UnitGlowLayer} lights just those; everything else must stay
 * transparent there, since that layer re-submits the whole model.
 *
 * <p>Authored in true pixel space (16px = 1 block), feet at {@code y=24}, "up" is negative y, so the
 * renderer applies no extra scale. The unit fills its 2.6 x 2.5 block hitbox and stays inside it: the
 * spike tips reach 20.5px from the centre line (41 of the 41.6 available, the slab's own
 * corners 18.4) and the cube's cap stops at
 * y=-12.0, i.e. 36px of the 40, with the top of the bob spending 0.7 of the rest.
 *
 * <p>Nothing here aims. The cube's eyes end up pointing wherever the entity's body faces, which for a
 * mob that never walks only swings once the look control has dragged its head more than 75° off the
 * nose. If the cannon ever wants to track what it is shooting, that is
 * {@code orb.yRot += state.yRot * (PI/180)} and nothing else — the render state already carries the
 * head yaw relative to the body.
 */
public class PhotonCannonModel extends EntityModel<LivingEntityRenderState> {
    private static final float DEG45 = 0.7853981633974483f;
    /** Lays the core flat: the part's local -Z, the face the lens is painted on, ends up pointing up. */
    private static final float CORE_FLAT = -(float) (Math.PI / 2.0);

    /** Radians per tick of the hover bob — roughly a five-second cycle, an idling antigrav, not a pulse. */
    private static final float BOB_RATE = 0.06f;
    /** Ticks in one bob cycle, so the age can be wrapped instead of growing without bound. */
    private static final float BOB_PERIOD = (float) (Math.PI * 2.0) / BOB_RATE;
    /** Pixels the cube rises and falls. Small: it is a warp focus holding station, not a balloon. */
    private static final float BOB_RISE = 0.7f;

    private final ModelPart orb;

    public PhotonCannonModel(ModelPart root) {
        super(root);
        this.orb = root.getChild("orb");
    }

    @Override
    public void setupAnim(LivingEntityRenderState state) {
        // super restores every part to its baked PartPose, so the bob below adds a delta onto it.
        super.setupAnim(state);

        // The whole animation: the cube rides up and down over the base. +y is down, so subtracting
        // lifts. The age is wrapped to one cycle before it is scaled — a cannon left standing for a
        // few in-game weeks reaches an ageInTicks where the raw multiply has lost enough float
        // precision for the bob to judder.
        this.orb.y -= Mth.sin((state.ageInTicks % BOB_PERIOD) * BOB_RATE) * BOB_RISE;
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();

        // Each cube's texOffs points at its own packed UV island — do not hand-edit these; they are
        // assigned by tools/blockbench_export.py and guarded by ModelUvLayoutTest.

        // --- Base: a square slab with a smaller square step on top of it. 26 across leaves a 2.5px
        // border of base showing around the core's 21-wide rim, which is what actually sells the
        // square — a base the rim covered edge to edge would just read as part of the core.
        root.addOrReplaceChild("base",
                CubeListBuilder.create()
                        .texOffs(0, 0).addBox(-13.0f, -6.0f, -13.0f, 26.0f, 6.0f, 26.0f),
                PartPose.offset(0.0f, 24.0f, 0.0f));
        root.addOrReplaceChild("base_step",
                CubeListBuilder.create()
                        .texOffs(0, 33).addBox(-11.0f, -8.0f, -11.0f, 22.0f, 2.0f, 22.0f),
                PartPose.offset(0.0f, 24.0f, 0.0f));

        // --- Eight spikes, one box each, two per side rather than one every 45 degrees. On a square
        // the diagonals already stick out further than the edges do, so corner spikes come out as
        // stubs while burying the corner they grow from, and the block stops reading as a square at
        // all. Running them straight out of the edges keeps the corners clean.
        //
        // Each side is a part carrying its two cubes, so a spike is still one box with one editable
        // texOffs literal, which is what the UV packer needs (see tools/blockbench_export.py). They
        // sit at y 18..22, their tops flush with the slab's midline, so they read as blades off the block rather
        // than as a skirt around its foot. The -Z side is the model's front.
        root.addOrReplaceChild("spikes_front",
                CubeListBuilder.create()
                        .texOffs(147, 58).addBox(-9.0f, -6.0f, -20.5f, 6.0f, 4.0f, 8.0f)
                        .texOffs(176, 58).addBox(3.0f, -6.0f, -20.5f, 6.0f, 4.0f, 8.0f),
                PartPose.offset(0.0f, 24.0f, 0.0f));
        root.addOrReplaceChild("spikes_right",
                CubeListBuilder.create()
                        .texOffs(29, 73).addBox(-9.0f, -6.0f, -20.5f, 6.0f, 4.0f, 8.0f)
                        .texOffs(58, 73).addBox(3.0f, -6.0f, -20.5f, 6.0f, 4.0f, 8.0f),
                PartPose.offsetAndRotation(0.0f, 24.0f, 0.0f, 0.0f, 2.0f * DEG45, 0.0f));
        root.addOrReplaceChild("spikes_back",
                CubeListBuilder.create()
                        .texOffs(89, 58).addBox(-9.0f, -6.0f, -20.5f, 6.0f, 4.0f, 8.0f)
                        .texOffs(118, 58).addBox(3.0f, -6.0f, -20.5f, 6.0f, 4.0f, 8.0f),
                PartPose.offsetAndRotation(0.0f, 24.0f, 0.0f, 0.0f, 4.0f * DEG45, 0.0f));
        root.addOrReplaceChild("spikes_left",
                CubeListBuilder.create()
                        .texOffs(205, 58).addBox(-9.0f, -6.0f, -20.5f, 6.0f, 4.0f, 8.0f)
                        .texOffs(0, 73).addBox(3.0f, -6.0f, -20.5f, 6.0f, 4.0f, 8.0f),
                PartPose.offsetAndRotation(0.0f, 24.0f, 0.0f, 0.0f, 6.0f * DEG45, 0.0f));

        // --- Core: the weapon, lying flat in the middle of the base and looking straight up. Authored
        // face-on and laid down by CORE_FLAT, which turns every local z below into height above the
        // step's top (z=0 is y=16, z=-3 is y=13).
        PartDefinition core = root.addOrReplaceChild("core",
                CubeListBuilder.create(),
                PartPose.offsetAndRotation(0.0f, 16.0f, 0.0f, CORE_FLAT, 0.0f, 0.0f));
        core.addOrReplaceChild("rim",
                CubeListBuilder.create()
                        .texOffs(115, 73).addBox(-8.0f, -10.5f, -3.0f, 16.0f, 2.5f, 3.0f)
                        .texOffs(154, 73).addBox(-8.0f, 8.0f, -3.0f, 16.0f, 2.5f, 3.0f)
                        .texOffs(115, 33).addBox(-10.5f, -8.0f, -3.0f, 2.5f, 16.0f, 3.0f)
                        .texOffs(127, 33).addBox(8.0f, -8.0f, -3.0f, 2.5f, 16.0f, 3.0f),
                PartPose.ZERO);
        core.addOrReplaceChild("rim_diag",
                CubeListBuilder.create()
                        .texOffs(193, 73).addBox(-7.6f, -10.1f, -2.8f, 15.2f, 2.5f, 2.8f)
                        .texOffs(0, 86).addBox(-7.6f, 7.6f, -2.8f, 15.2f, 2.5f, 2.8f)
                        .texOffs(139, 33).addBox(-10.1f, -7.6f, -2.8f, 2.5f, 15.2f, 2.8f)
                        .texOffs(151, 33).addBox(7.6f, -7.6f, -2.8f, 2.5f, 15.2f, 2.8f),
                PartPose.rotation(0.0f, 0.0f, DEG45));
        // The bezel stays a crossed pair: it is the violet ring that shows in the gap between the
        // lens plate's edge and the rim's inner edge, and a plus covers that gap with two cubes.
        core.addOrReplaceChild("bezel",
                CubeListBuilder.create()
                        .texOffs(49, 58).addBox(-9.0f, -5.5f, -1.8f, 18.0f, 11.0f, 1.2f)
                        .texOffs(89, 33).addBox(-5.5f, -9.0f, -1.8f, 11.0f, 18.0f, 1.2f),
                PartPose.ZERO);
        core.addOrReplaceChild("lens",
                CubeListBuilder.create()
                        .texOffs(163, 33).addBox(-7.5f, -7.5f, -2.4f, 15.0f, 15.0f, 1.0f),
                PartPose.ZERO);

        // Eight teeth standing on the rim around the glow, written out one by one for the same
        // texOffs reason as the spikes.
        core.addOrReplaceChild("tooth0",
                CubeListBuilder.create()
                        .texOffs(37, 86).addBox(-2.0f, -11.0f, -3.6f, 4.0f, 3.0f, 2.2f),
                PartPose.rotation(0.0f, 0.0f, 0.0f * DEG45));
        core.addOrReplaceChild("tooth1",
                CubeListBuilder.create()
                        .texOffs(51, 86).addBox(-2.0f, -11.0f, -3.6f, 4.0f, 3.0f, 2.2f),
                PartPose.rotation(0.0f, 0.0f, 1.0f * DEG45));
        core.addOrReplaceChild("tooth2",
                CubeListBuilder.create()
                        .texOffs(65, 86).addBox(-2.0f, -11.0f, -3.6f, 4.0f, 3.0f, 2.2f),
                PartPose.rotation(0.0f, 0.0f, 2.0f * DEG45));
        core.addOrReplaceChild("tooth3",
                CubeListBuilder.create()
                        .texOffs(79, 86).addBox(-2.0f, -11.0f, -3.6f, 4.0f, 3.0f, 2.2f),
                PartPose.rotation(0.0f, 0.0f, 3.0f * DEG45));
        core.addOrReplaceChild("tooth4",
                CubeListBuilder.create()
                        .texOffs(93, 86).addBox(-2.0f, -11.0f, -3.6f, 4.0f, 3.0f, 2.2f),
                PartPose.rotation(0.0f, 0.0f, 4.0f * DEG45));
        core.addOrReplaceChild("tooth5",
                CubeListBuilder.create()
                        .texOffs(107, 86).addBox(-2.0f, -11.0f, -3.6f, 4.0f, 3.0f, 2.2f),
                PartPose.rotation(0.0f, 0.0f, 5.0f * DEG45));
        core.addOrReplaceChild("tooth6",
                CubeListBuilder.create()
                        .texOffs(121, 86).addBox(-2.0f, -11.0f, -3.6f, 4.0f, 3.0f, 2.2f),
                PartPose.rotation(0.0f, 0.0f, 6.0f * DEG45));
        core.addOrReplaceChild("tooth7",
                CubeListBuilder.create()
                        .texOffs(135, 86).addBox(-2.0f, -11.0f, -3.6f, 4.0f, 3.0f, 2.2f),
                PartPose.rotation(0.0f, 0.0f, 7.0f * DEG45));

        // --- The floating cube: 16px on every side, i.e. exactly one block, plates included. Its
        // lowest point is y=4.0 and the rim's top is y=13, so 9px of open air sit under it at rest
        // and 8.3 at the bottom of the bob — nothing bridges that gap, by design. The plates are
        // inset 2px a side so the silhouette steps instead of ending on a bare edge; everything else
        // about it is one honest box, which is the point.
        PartDefinition orb = root.addOrReplaceChild("orb",
                CubeListBuilder.create()
                        .texOffs(105, 0).addBox(-8.0f, -6.8f, -8.0f, 16.0f, 13.6f, 16.0f),
                PartPose.offset(0.0f, -4.0f, 0.0f));
        orb.addOrReplaceChild("orb_cap",
                CubeListBuilder.create()
                        .texOffs(196, 33).addBox(-6.0f, -8.0f, -6.0f, 12.0f, 1.2f, 12.0f),
                PartPose.ZERO);
        orb.addOrReplaceChild("orb_foot",
                CubeListBuilder.create()
                        .texOffs(0, 58).addBox(-6.0f, 6.8f, -6.0f, 12.0f, 1.2f, 12.0f),
                PartPose.ZERO);
        // Eyes, low on the face and well apart, standing 1px proud of it. The dark visor they sit in
        // is painted rather than built — a flat face is exactly where painted detail reads best.
        orb.addOrReplaceChild("eyes",
                CubeListBuilder.create()
                        .texOffs(149, 86).addBox(-5.2f, -0.6f, -9.0f, 3.6f, 3.6f, 1.0f)
                        .texOffs(160, 86).addBox(1.6f, -0.6f, -9.0f, 3.6f, 3.6f, 1.0f),
                PartPose.ZERO);
        // Cheek accents: the cyan panels on the flanks.
        orb.addOrReplaceChild("accents",
                CubeListBuilder.create()
                        .texOffs(87, 73).addBox(-9.0f, -1.9f, -2.7f, 1.0f, 3.8f, 5.4f)
                        .texOffs(101, 73).addBox(8.0f, -1.9f, -2.7f, 1.0f, 3.8f, 5.4f),
                PartPose.ZERO);

        return LayerDefinition.create(mesh, 256, 256);
    }
}
