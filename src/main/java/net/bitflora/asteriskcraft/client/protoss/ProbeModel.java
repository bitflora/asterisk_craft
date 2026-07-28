package net.bitflora.asteriskcraft.client.protoss;

import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.util.Mth;

/**
 * Hand-authored geometry for the Probe, shaped after the reference art rather than the seven boxes
 * this used to be. It is a <b>hovering gold pod with nothing under it</b>: a flattened disc hull
 * banded by a raised crown and a keel, carrying a violet visor ring on top whose glowing blue lens is
 * the unit's single eye. Off the upper rear flanks two long gold spikes sweep <b>back and up</b>; off
 * the lower front two more reach <b>forward and down</b>, and a swept violet fin rides each side, so
 * the silhouette is a four-pointed star around a disc — the shape the art reads as from any angle.
 * Slung beneath the nose are its <b>three warp emitters</b>: short nacelles tipped with green lenses,
 * which are what the Probe actually mines with, and the only reason a worker with no arms looks like
 * it is working.
 *
 * <p>Authored in true pixel space (16px = 1 block), "up" is negative y, ground is y=24, and colour
 * comes from {@code textures/entity/probe.png}, in which <b>every cube owns its own UV island</b> so
 * it can be hand-painted independently — see {@code tools/blockbench_export.py}, which packs those
 * islands and emits the Blockbench project. That tooling also constrains how this class may be
 * written: one {@code texOffs} literal per cube, builders inlined into their
 * {@code addOrReplaceChild} call, and globally unique part names (the round-trip verifier keys parts
 * by leaf name, so the two fins may not both be called {@code fin}). See docs/texturing.md.
 *
 * <p>Only the visor lens and the three emitter tips are painted into {@code probe_glow.png}, so the
 * emissive {@link net.bitflora.asteriskcraft.client.UnitGlowLayer} lights just those; everything else
 * must stay transparent there, since that layer re-submits the whole model.
 *
 * <p>{@link #setupAnim} drives a hover bob, a lean toward whatever the Probe is looking at, a bank
 * into travel, and a mining stroke off {@link ProbeRenderState#miningProgress}. Everything stacks as a
 * delta onto the baked pose, since {@code super.setupAnim} restores every part first.
 */
public class ProbeModel extends EntityModel<ProbeRenderState> {
    private static final float DEG = (float) (Math.PI / 180.0);

    /** Radians per tick of the hover bob — an antigrav pod idling, not a breath. */
    private static final float HOVER_RATE = 0.10f;
    /** Cap on how far the look may pitch the hull, in radians. See {@link #setupAnim}. */
    private static final float LOOK_PITCH_LIMIT = 0.16f;

    private final ModelPart hull;
    private final ModelPart visorRing;
    private final ModelPart finLeft;
    private final ModelPart finRight;
    private final ModelPart spikeTopLeft;
    private final ModelPart spikeTopRight;
    private final ModelPart spikeFrontLeft;
    private final ModelPart spikeFrontRight;
    private final ModelPart nacelleMid;
    private final ModelPart nacelleLeft;
    private final ModelPart nacelleRight;

    public ProbeModel(ModelPart root) {
        super(root);
        this.hull = root.getChild("hull");
        this.visorRing = this.hull.getChild("visor_ring");
        this.finLeft = this.hull.getChild("fin_left");
        this.finRight = this.hull.getChild("fin_right");
        this.spikeTopLeft = this.hull.getChild("pod_left").getChild("spike_top_left");
        this.spikeTopRight = this.hull.getChild("pod_right").getChild("spike_top_right");
        this.spikeFrontLeft = this.hull.getChild("spike_front_left");
        this.spikeFrontRight = this.hull.getChild("spike_front_right");
        this.nacelleMid = this.hull.getChild("nacelle_mid");
        this.nacelleLeft = this.hull.getChild("nacelle_left");
        this.nacelleRight = this.hull.getChild("nacelle_right");
    }

    @Override
    public void setupAnim(ProbeRenderState state) {
        // super restores every part to its baked PartPose, so everything below adds a delta onto it.
        super.setupAnim(state);

        // No neck to turn: the whole pod leans at what it is watching, which is the only way a
        // headless disc can express "looking". Yaw is deliberately a small share of the look — the
        // renderer already yaws the entity itself, so taking the full angle here would double it.
        //
        // The pitch is clamped, and that clamp is load-bearing rather than tidiness. A Probe looks at
        // the block it is mining, so state.xRot regularly runs past 60 degrees; the front spikes hang
        // 9.3px ahead of the hull's pivot and the top ones 11.3px behind it, which turns every radian
        // of lean into that much travel at their tips. Unclamped, the front pair swings through the
        // floor and the back pair out of the top of the hitbox, both on top of the mining bow below.
        float look = Mth.clamp(state.xRot * DEG * 0.30f, -LOOK_PITCH_LIMIT, LOOK_PITCH_LIMIT);
        this.hull.yRot += state.yRot * DEG * 0.25f;
        this.hull.xRot += look;
        // The visor takes a share of its own, so the eye ends up pointed further at the target than
        // the hull it sits on. It hangs over the pivot rather than ahead of it, so it needs no clamp.
        this.visorRing.xRot += state.xRot * DEG * 0.25f;

        // Hover: the pod rises and falls on its antigrav and rolls a little as it does. +y is down, so
        // subtracting lifts. The roll runs at a different rate from the bob on purpose — locked
        // together they read as one mechanical cycle, apart they read as a thing holding station.
        float hover = Mth.sin(state.ageInTicks * HOVER_RATE);
        this.hull.y -= hover * 0.35f;
        this.hull.zRot += Mth.sin(state.ageInTicks * 0.07f) * 0.05f;
        // The fins breathe with the bob, a quarter cycle behind it, which is what sells them as
        // control surfaces trimming the hover rather than decoration bolted to the shell.
        float trim = Mth.sin(state.ageInTicks * HOVER_RATE - 1.57f) * 0.09f;
        this.finLeft.zRot += trim;
        this.finRight.zRot -= trim;

        // Travel: a hovering pod has no legs to cycle, so speed has to be sold by attitude. It noses
        // down into the run and the spikes rake back, exactly like the art's silhouette in motion.
        // walkAnimationSpeed saturates near 1, so these are close to their full values at a sprint.
        // No extra lift here — the nose-down already reads as speed, and stacking a rise on top of
        // the bob is what puts the visor out of the top of the hitbox.
        float speed = state.walkAnimationSpeed;
        this.hull.xRot += speed * 0.16f;
        this.spikeTopLeft.xRot -= speed * 0.18f;
        this.spikeTopRight.xRot -= speed * 0.18f;
        this.spikeFrontLeft.xRot += speed * 0.12f;
        this.spikeFrontRight.xRot += speed * 0.12f;

        float p = state.miningProgress;
        if (p <= 0.0f) {
            return;
        }
        // Mining stroke. ProbeEntity.HarvestGoal swings every tick it is in range of a node and
        // LivingEntity.swing restarts from the half-way mark, so this cycles continuously for as long
        // as the Probe works.
        //
        // The pod bows over the node and the three emitters converge on one point ahead of it: the
        // outer two toe inward, the middle one drops. The beams themselves are particles from the
        // goal, so the geometry's whole job is to aim at where they come out — emitters that stayed
        // splayed while a beam played would read as the model being unaware of it.
        //
        // The bow is small (0.12rad and 0.3px, where a first pass had 0.30 and 0.7) because it stacks
        // on top of a look that is already pitched down at the node: together they leave the middle
        // emitter 0.2px off the floor at the peak of the stroke, and any more buries it.
        float pulse = Mth.sin(p * Mth.PI);
        this.hull.xRot += pulse * 0.12f;
        this.hull.y += pulse * 0.30f;            // dips toward the block it is working
        this.visorRing.xRot += pulse * 0.16f;
        this.nacelleMid.xRot += pulse * 0.26f;
        this.nacelleLeft.xRot += pulse * 0.20f;
        this.nacelleRight.xRot += pulse * 0.20f;
        this.nacelleLeft.yRot += pulse * 0.22f;
        this.nacelleRight.yRot -= pulse * 0.22f;
        // The spikes flare open on the stroke — the one bit of body language a pod this rigid has.
        this.spikeTopLeft.zRot += pulse * 0.10f;
        this.spikeTopRight.zRot -= pulse * 0.10f;
        this.spikeFrontLeft.zRot -= pulse * 0.14f;
        this.spikeFrontRight.zRot += pulse * 0.14f;
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();

        // Each cube's texOffs points at its own packed UV island — do not hand-edit these; they are
        // assigned by tools/blockbench_export.py and guarded by ModelUvLayoutTest.
        //
        // Vertical layout, y+ pointing down, -z forward. Nothing here touches the ground: the belly
        // cap bottoms out at y=21.0, so the Probe floats 3px clear, and the whole model lives between
        // y=10.66 (the visor lens, with the top spikes tuned to stop just under it at 11.29) and
        // there. That is 13.3px of visible unit reaching 0.83 blocks over the ground line, against a
        // 0.9-block hitbox at the renderer's 1.0 scale. The slack is what the hover bob and the
        // mining bow spend: at the worst pose — mid-stroke, pitched down at a node, which swings the
        // back-swept spikes up — the tips reach 0.94, and the look pitch is clamped precisely so they
        // do not go further.
        //
        // Width is the tighter budget and the spikes deliberately break it: the hull and its flank
        // panels stay inside +/-5.5px (the 0.7 hitbox allows 5.6), while the fin and top-spike tips
        // reach 6.41, i.e. 0.80 blocks across. That overhang is the same trade ZealotEntity's
        // pauldrons make — widening the hitbox to cover four thin spikes would change how the unit is
        // hit and pathed for the sake of geometry that is mostly air.
        //
        // All of those are measured off the baked geometry under the animation, not estimated part by
        // part: rotations compound down the chain, and the parts that decide these numbers (the spike
        // tips, hanging ~10px off the pivot) are levered by every degree the hull leans.
        //
        // Sign note, because it decides every rotation below: for a box grown along +z (the top
        // spikes, the fins) a positive xRot *lifts* the tip and a positive yRot swings it toward +x;
        // for one grown along -z (the front spikes, the nacelles) the same numbers push the tip down
        // and toward -x instead. Read each rotation against its box's direction, not against the
        // part's name.

        // --- Hull: a flattened disc, banded ---------------------------------------------------------
        // Four stacked slabs rather than one box, each narrower than the one it sits on, so the
        // profile steps in above and below and reads as a lens rather than a brick. Keeping the whole
        // stack to 8px of height against 9.6 of width is what makes it a disc at all: an earlier pass
        // at 9.8 tall was near enough cubic that the unit read as a bucket with horns.
        PartDefinition hull = root.addOrReplaceChild("hull",
                CubeListBuilder.create().texOffs(0, 0).addBox(-4.8f, -2.1f, -3.8f, 9.6f, 4.2f, 7.6f),
                PartPose.offset(0.0f, 16.4f, 0.0f));
        hull.addOrReplaceChild("crown",
                CubeListBuilder.create().texOffs(0, 25).addBox(-3.9f, -1.3f, -3.0f, 7.8f, 1.3f, 6.0f),
                PartPose.offset(0.0f, -2.1f, 0.0f));
        PartDefinition keel = hull.addOrReplaceChild("keel",
                CubeListBuilder.create().texOffs(29, 25).addBox(-3.9f, 0.0f, -3.0f, 7.8f, 1.4f, 6.0f),
                PartPose.offset(0.0f, 2.1f, 0.0f));
        keel.addOrReplaceChild("belly",
                CubeListBuilder.create().texOffs(23, 51).addBox(-2.6f, 0.0f, -2.4f, 5.2f, 1.1f, 4.8f),
                PartPose.offset(0.0f, 1.4f, 0.0f));

        // Side panels: the flat gold plates that carry the art's blue circuit tracery. They stand
        // proud of the hull's flank so the tracery catches its own edge of shadow, and they are the
        // widest solid part of the unit.
        hull.addOrReplaceChild("flank_left",
                CubeListBuilder.create().texOffs(16, 13).addBox(-0.7f, -1.5f, -2.8f, 1.4f, 3.0f, 5.6f),
                PartPose.offset(4.8f, 0.2f, 0.2f));
        hull.addOrReplaceChild("flank_right",
                CubeListBuilder.create().texOffs(31, 13).addBox(-0.7f, -1.5f, -2.8f, 1.4f, 3.0f, 5.6f),
                PartPose.offset(-4.8f, 0.2f, 0.2f));

        // --- Visor: the violet ring and the eye inside it --------------------------------------------
        // Deliberately large — nearly the width of the crown it sits on — because in the art the blue
        // dome and its emblem are the biggest single feature of the Probe seen from anywhere above,
        // and a lens smaller than 6x5 texels has no room for the chevron painted into it.
        PartDefinition visorRing = hull.addOrReplaceChild("visor_ring",
                CubeListBuilder.create().texOffs(0, 34).addBox(-3.6f, -1.2f, -3.2f, 7.2f, 1.2f, 6.4f),
                PartPose.offsetAndRotation(0.0f, -3.4f, -0.3f, 0.15f, 0.0f, 0.0f));
        visorRing.addOrReplaceChild("visor",
                CubeListBuilder.create().texOffs(0, 51).addBox(-2.8f, -0.8f, -2.6f, 5.6f, 0.8f, 5.2f),
                PartPose.offset(0.0f, -1.2f, -0.2f));

        // --- Top spikes: back and up off two shoulder blisters ----------------------------------------
        // Each spike is two cubes, the second thinner than the first, because a single box of constant
        // section reads as a rod and the art's are tapered lances. The pods they root in are what stop
        // them looking stuck onto a smooth shell.
        //
        // The pitch is the number to be careful with: at the 0.34/+0.04 baked here the tips land just
        // under the visor. Raking them further back looks better in a still and walks them straight
        // out of the top of the hitbox, and the travel lean already rakes them another 0.18 on top.
        PartDefinition podLeft = hull.addOrReplaceChild("pod_left",
                CubeListBuilder.create().texOffs(29, 34).addBox(-1.2f, -1.5f, -1.9f, 2.4f, 3.0f, 3.8f),
                PartPose.offsetAndRotation(3.2f, -1.3f, 1.7f, 0.0f, 0.0f, 0.22f));
        PartDefinition spikeTopLeft = podLeft.addOrReplaceChild("spike_top_left",
                CubeListBuilder.create().texOffs(43, 34).addBox(-0.9f, -0.9f, 0.0f, 1.8f, 1.8f, 4.6f),
                PartPose.offsetAndRotation(0.5f, -0.7f, 1.5f, 0.34f, 0.13f, 0.0f));
        spikeTopLeft.addOrReplaceChild("spike_top_left_tip",
                CubeListBuilder.create().texOffs(22, 58).addBox(-0.55f, -0.55f, 0.0f, 1.1f, 1.1f, 3.8f),
                PartPose.offsetAndRotation(0.0f, 0.0f, 4.6f, 0.04f, 0.05f, 0.0f));

        PartDefinition podRight = hull.addOrReplaceChild("pod_right",
                CubeListBuilder.create().texOffs(0, 43).addBox(-1.2f, -1.5f, -1.9f, 2.4f, 3.0f, 3.8f),
                PartPose.offsetAndRotation(-3.2f, -1.3f, 1.7f, 0.0f, 0.0f, -0.22f));
        PartDefinition spikeTopRight = podRight.addOrReplaceChild("spike_top_right",
                CubeListBuilder.create().texOffs(14, 43).addBox(-0.9f, -0.9f, 0.0f, 1.8f, 1.8f, 4.6f),
                PartPose.offsetAndRotation(-0.5f, -0.7f, 1.5f, 0.34f, -0.13f, 0.0f));
        spikeTopRight.addOrReplaceChild("spike_top_right_tip",
                CubeListBuilder.create().texOffs(33, 58).addBox(-0.55f, -0.55f, 0.0f, 1.1f, 1.1f, 3.8f),
                PartPose.offsetAndRotation(0.0f, 0.0f, 4.6f, 0.04f, -0.05f, 0.0f));

        // --- Front spikes: forward and down off the lower hull ----------------------------------------
        // These grow along -z, so the signs invert: +xRot drops the tip, -yRot swings it outboard.
        // They stop short of the belly cap's level, so the Probe reads as hovering over them rather
        // than standing on them.
        //
        // Their length is capped by the animation rather than by looks: reaching ~9px ahead of the
        // hull pivot, they are the longest lever on the model, and every radian the pod leans moves
        // their tips nearly that far. That is why the look pitch is clamped and the mining bow is
        // small.
        PartDefinition spikeFrontLeft = hull.addOrReplaceChild("spike_front_left",
                CubeListBuilder.create().texOffs(44, 58).addBox(-0.8f, -0.8f, -3.4f, 1.6f, 1.6f, 3.4f),
                PartPose.offsetAndRotation(3.1f, 0.9f, -3.2f, 0.24f, -0.20f, 0.0f));
        spikeFrontLeft.addOrReplaceChild("spike_front_left_tip",
                CubeListBuilder.create().texOffs(11, 64).addBox(-0.5f, -0.5f, -2.8f, 1.0f, 1.0f, 2.8f),
                PartPose.offsetAndRotation(0.0f, 0.0f, -3.4f, 0.08f, -0.07f, 0.0f));

        PartDefinition spikeFrontRight = hull.addOrReplaceChild("spike_front_right",
                CubeListBuilder.create().texOffs(0, 64).addBox(-0.8f, -0.8f, -3.4f, 1.6f, 1.6f, 3.4f),
                PartPose.offsetAndRotation(-3.1f, 0.9f, -3.2f, 0.24f, 0.20f, 0.0f));
        spikeFrontRight.addOrReplaceChild("spike_front_right_tip",
                CubeListBuilder.create().texOffs(20, 64).addBox(-0.5f, -0.5f, -2.8f, 1.0f, 1.0f, 2.8f),
                PartPose.offsetAndRotation(0.0f, 0.0f, -3.4f, 0.08f, 0.07f, 0.0f));

        // --- Fins and dorsal blade ---------------------------------------------------------------------
        // Thin violet plates swept out and back off each flank, each carrying a smaller plate that
        // continues the sweep — one cube per side reads as a slab bolted on, two read as a blade. They
        // are the one non-gold mass on the silhouette, and what fills the gap between the two pairs of
        // spikes so the star reads as four points around a disc instead of four rods on a ball. The
        // dorsal blade closes the same gap along the centre line at the back.
        PartDefinition finLeft = hull.addOrReplaceChild("fin_left",
                CubeListBuilder.create().texOffs(36, 0).addBox(-0.5f, -2.4f, -1.2f, 1.0f, 4.8f, 6.2f),
                PartPose.offsetAndRotation(3.9f, 0.2f, 0.7f, 0.0f, 0.16f, 0.20f));
        finLeft.addOrReplaceChild("fin_left_tip",
                CubeListBuilder.create().texOffs(41, 43).addBox(-0.4f, -1.5f, 0.0f, 0.8f, 3.0f, 3.4f),
                PartPose.offsetAndRotation(0.0f, -0.5f, 4.8f, 0.0f, 0.10f, 0.10f));
        PartDefinition finRight = hull.addOrReplaceChild("fin_right",
                CubeListBuilder.create().texOffs(0, 13).addBox(-0.5f, -2.4f, -1.2f, 1.0f, 4.8f, 6.2f),
                PartPose.offsetAndRotation(-3.9f, 0.2f, 0.7f, 0.0f, -0.16f, -0.20f));
        finRight.addOrReplaceChild("fin_right_tip",
                CubeListBuilder.create().texOffs(51, 43).addBox(-0.4f, -1.5f, 0.0f, 0.8f, 3.0f, 3.4f),
                PartPose.offsetAndRotation(0.0f, -0.5f, 4.8f, 0.0f, -0.10f, -0.10f));
        hull.addOrReplaceChild("dorsal",
                CubeListBuilder.create().texOffs(28, 43).addBox(-0.85f, -2.4f, -2.0f, 1.7f, 2.4f, 4.0f),
                PartPose.offsetAndRotation(0.0f, -2.0f, 3.4f, -0.25f, 0.0f, 0.0f));

        // --- Warp emitters: three nacelles under the nose -----------------------------------------------
        // The working end. Each is a short barrel grown along -z (so +xRot aims it down) capped by a
        // stub that is the only other thing besides the visor painted into probe_glow.png. The outer
        // pair toe outward at rest and are toed back in by the mining stroke, which is what makes the
        // three of them converge on the node instead of firing parallel.
        PartDefinition nacelleMid = hull.addOrReplaceChild("nacelle_mid",
                CubeListBuilder.create().texOffs(44, 51).addBox(-1.1f, -1.1f, -3.2f, 2.2f, 2.2f, 3.2f),
                PartPose.offsetAndRotation(0.0f, 2.2f, -2.6f, 0.26f, 0.0f, 0.0f));
        nacelleMid.addOrReplaceChild("emitter_mid",
                CubeListBuilder.create().texOffs(29, 64).addBox(-0.85f, -0.85f, -0.9f, 1.7f, 1.7f, 0.9f),
                PartPose.offset(0.0f, 0.0f, -3.2f));

        PartDefinition nacelleLeft = hull.addOrReplaceChild("nacelle_left",
                CubeListBuilder.create().texOffs(0, 58).addBox(-0.95f, -0.95f, -2.8f, 1.9f, 1.9f, 2.8f),
                PartPose.offsetAndRotation(2.9f, 1.6f, -2.2f, 0.20f, -0.22f, 0.0f));
        nacelleLeft.addOrReplaceChild("emitter_left",
                CubeListBuilder.create().texOffs(36, 64).addBox(-0.7f, -0.7f, -0.8f, 1.4f, 1.4f, 0.8f),
                PartPose.offset(0.0f, 0.0f, -2.8f));

        PartDefinition nacelleRight = hull.addOrReplaceChild("nacelle_right",
                CubeListBuilder.create().texOffs(11, 58).addBox(-0.95f, -0.95f, -2.8f, 1.9f, 1.9f, 2.8f),
                PartPose.offsetAndRotation(-2.9f, 1.6f, -2.2f, 0.20f, 0.22f, 0.0f));
        nacelleRight.addOrReplaceChild("emitter_right",
                CubeListBuilder.create().texOffs(42, 64).addBox(-0.7f, -0.7f, -0.8f, 1.4f, 1.4f, 0.8f),
                PartPose.offset(0.0f, 0.0f, -2.8f));

        // 64 wide is a floor, not a choice: the hull's island alone is 2*(9.6+7.6) = 35 texels across
        // and the packer shelves the rest against that width; 29 islands then need 128 of height.
        return LayerDefinition.create(mesh, 64, 128);
    }
}
