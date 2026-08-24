package net.bitflora.asteriskcraft.client.terran;

import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;

/**
 * Hand-authored geometry for the Terran Bunker: a squat concrete blockhouse — a wide <b>foundation</b>
 * slab, a shorter <b>hull</b> stepped in from it, an overhanging <b>roof</b> slab wider than the hull
 * again, and four corner <b>posts</b> running the full height and standing proud of every face. A dark
 * <b>slit</b> band runs round it at chest height, and a <b>barrel</b> pokes out of each slit — one per
 * unit inside.
 *
 * <p>Nothing here is rounded and nothing is decorative. The Marine and the SCV are the models that
 * carry the race's character; a Bunker is the thing they hide in, and the CLAUDE.md villager pose is
 * a rule about Terran <em>people</em>. The right precedent is
 * {@code client.protoss.PhotonCannonModel}'s reasoning about the base: square slab, square step,
 * stepped silhouette, so the unit sits in Minecraft rather than looking imported.
 *
 * <p><b>The four barrels are the whole occupancy display</b>, and the reason this model has an
 * animation at all. A Bunker's numbers say nothing about how dangerous it is — an empty one is a wall
 * — so a player has to be able to read at a glance whether the thing they are walking at is loaded.
 * One barrel per garrisoned unit, shown from {@link #setupAnim}; four out is a full house.
 * {@code Model.setupAnim} restores every part's baked pose but never touches {@code visible}, so
 * setting it every frame is the whole mechanism.
 *
 * <p>Colour comes from {@code textures/entity/bunker.png}, in which <b>every cube owns its own UV
 * island</b> so it can be hand-painted independently — see {@code tools/blockbench_export.py}, which
 * packs those islands and emits the Blockbench project. That tooling constrains how this class may be
 * written: one {@code texOffs} literal per cube (so the four posts, the four slits and the four
 * barrels are written out rather than looped), builders inlined into their {@code addOrReplaceChild}
 * call, and globally unique part names. See docs/texturing.md. Only the barrel muzzles are painted
 * into {@code bunker_glow.png}, so the emissive {@code client.UnitGlowLayer} lights just those;
 * everything else must stay transparent there, since that layer re-submits the whole model.
 *
 * <p>Authored in true pixel space (16px = 1 block), feet at {@code y=24}, "up" is negative y, so the
 * renderer applies no extra scale. It fills its 2.6 x 2.0 block hitbox and stays inside it: the
 * foundation and the posts reach 20 px from the centre line and the barrels 20.5 of the 20.8
 * available, and the roof caps 26 px up of the 32.
 */
public class BunkerModel extends EntityModel<BunkerRenderState> {
    /** How many barrels there are, which is also the garrison the model can display. */
    private static final int BARRELS = 4;

    private final ModelPart[] barrels;

    public BunkerModel(ModelPart root) {
        super(root);
        this.barrels = new ModelPart[] {
                root.getChild("barrel_north"),
                root.getChild("barrel_east"),
                root.getChild("barrel_south"),
                root.getChild("barrel_west"),
        };
    }

    @Override
    public void setupAnim(BunkerRenderState state) {
        // super restores every part to its baked PartPose; visibility is not part of a PartPose, so
        // it survives and has to be written every frame rather than only on a change.
        super.setupAnim(state);
        for (int i = 0; i < BARRELS; i++) {
            // Filled in a fixed order rather than one barrel per seat index: which passenger sits
            // where is vanilla's business and changes as units board and leave, and a barrel that
            // jumped from one wall to another when a Marine died would read as a bug.
            this.barrels[i].visible = state.garrison > i;
        }
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();

        // Each cube's texOffs points at its own packed UV island — do not hand-edit these; they are
        // assigned by tools/blockbench_export.py and guarded by ModelUvLayoutTest.

        // --- The three slabs. Wide, narrow, wide again: the hull is stepped in from the foundation
        // and the roof overhangs it, which is what gives a plain box a silhouette at all. The roof
        // is deliberately wider than the hull and narrower than nothing else, so from a distance the
        // building reads as a lid on a base rather than as one solid cube.
        root.addOrReplaceChild("foundation",
                CubeListBuilder.create()
                        .texOffs(0, 0).addBox(-20.0f, -5.0f, -20.0f, 40.0f, 5.0f, 40.0f),
                PartPose.offset(0.0f, 24.0f, 0.0f));
        root.addOrReplaceChild("hull",
                CubeListBuilder.create()
                        .texOffs(0, 46).addBox(-17.0f, -22.0f, -17.0f, 34.0f, 17.0f, 34.0f),
                PartPose.offset(0.0f, 24.0f, 0.0f));
        root.addOrReplaceChild("roof",
                CubeListBuilder.create()
                        .texOffs(0, 98).addBox(-19.0f, -26.0f, -19.0f, 38.0f, 4.0f, 38.0f),
                PartPose.offset(0.0f, 24.0f, 0.0f));

        // --- Four corner posts, running the full height and standing 3px proud of the hull on both
        // of the faces they touch. They are what stop the slit band reading as a stripe painted round
        // a box: it now terminates against something on each side. One part carrying four cubes, so
        // each post still owns one editable texOffs literal (see tools/blockbench_export.py).
        root.addOrReplaceChild("posts",
                CubeListBuilder.create()
                        .texOffs(0, 141).addBox(-20.0f, -26.0f, -20.0f, 6.0f, 24.0f, 6.0f)
                        .texOffs(26, 141).addBox(14.0f, -26.0f, -20.0f, 6.0f, 24.0f, 6.0f)
                        .texOffs(52, 141).addBox(-20.0f, -26.0f, 14.0f, 6.0f, 24.0f, 6.0f)
                        .texOffs(78, 141).addBox(14.0f, -26.0f, 14.0f, 6.0f, 24.0f, 6.0f),
                PartPose.offset(0.0f, 24.0f, 0.0f));

        // --- The firing slits: a dark band a hair proud of each hull face, so it is a real surface
        // the painter can blacken rather than a coplanar decal fighting the hull for depth. Chest
        // height on the hull, which is where the barrels come out.
        root.addOrReplaceChild("slits",
                CubeListBuilder.create()
                        .texOffs(0, 172).addBox(-8.0f, -18.0f, -17.6f, 16.0f, 4.0f, 1.0f)
                        .texOffs(36, 172).addBox(-8.0f, -18.0f, 16.6f, 16.0f, 4.0f, 1.0f)
                        .texOffs(72, 172).addBox(-17.6f, -18.0f, -8.0f, 1.0f, 4.0f, 16.0f)
                        .texOffs(108, 172).addBox(16.6f, -18.0f, -8.0f, 1.0f, 4.0f, 16.0f),
                PartPose.offset(0.0f, 24.0f, 0.0f));

        // --- One barrel per slit, and one shown per unit inside (see setupAnim). Each is its own
        // part rather than four cubes in one, because visibility is per-part: a shared part could
        // only ever be all four or none, which is exactly the distinction this is here to draw.
        root.addOrReplaceChild("barrel_north",
                CubeListBuilder.create()
                        .texOffs(0, 194).addBox(-1.5f, -17.5f, -20.5f, 3.0f, 3.0f, 4.0f),
                PartPose.offset(0.0f, 24.0f, 0.0f));
        root.addOrReplaceChild("barrel_south",
                CubeListBuilder.create()
                        .texOffs(16, 194).addBox(-1.5f, -17.5f, 16.5f, 3.0f, 3.0f, 4.0f),
                PartPose.offset(0.0f, 24.0f, 0.0f));
        root.addOrReplaceChild("barrel_west",
                CubeListBuilder.create()
                        .texOffs(32, 194).addBox(-20.5f, -17.5f, -1.5f, 4.0f, 3.0f, 3.0f),
                PartPose.offset(0.0f, 24.0f, 0.0f));
        root.addOrReplaceChild("barrel_east",
                CubeListBuilder.create()
                        .texOffs(48, 194).addBox(16.5f, -17.5f, -1.5f, 4.0f, 3.0f, 3.0f),
                PartPose.offset(0.0f, 24.0f, 0.0f));

        return LayerDefinition.create(mesh, 256, 256);
    }
}
