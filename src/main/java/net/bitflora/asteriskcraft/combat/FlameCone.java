package net.bitflora.asteriskcraft.combat;

import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * The volume a flamethrower covers: a horizontal wedge that starts narrow at the nozzle and widens
 * out to its reach, washing a fixed height above and below the attacker. Pure geometry — no level,
 * no entity, no registries — which is what lets it be unit-tested.
 * {@link net.bitflora.asteriskcraft.entity.ai.FlameAttacks} is the half that touches the world, the
 * same split {@link BounceChain} and {@code entity/ai/HitscanAttacks} already make for the
 * Mutalisk's glave.
 *
 * <p><b>The horizontal and the vertical are two separate tests, and that is the whole trick.</b> The
 * wedge only spreads sideways, so the vertical half is a plain slab check and the horizontal half
 * collapses to a 2D problem: a convex quadrilateral (the {@link #footprint}) against the target's
 * own footprint rectangle. That is decided by separating axes in {@link #catches}, which makes it
 * <em>exact</em> — no tolerance, no fudge factor, and no per-unit tuning for the fact that a
 * Zergling and an Ultralisk are very different boxes.
 *
 * <p>Being exact is worth the six dot products. The obvious cheap alternative — cutting the axis
 * into boxes and asking whether the target overlaps any of them — silently makes the cone about a
 * block longer than its own reach and a block wider than its own nozzle, because an axis-aligned box
 * sized by the local spread extends that spread <em>forwards</em> too. A unit standing safely past
 * the reach burns anyway, and so does one standing at the attacker's shoulder.
 *
 * <p>{@link #samples} is the drawing half: points down the axis with the local spread at each, which
 * the caller throws particles along. It shares this class's {@link Shape} with the hit test rather
 * than being computed beside it, so the fire a player sees can never disagree with the fire that
 * burns them about how far it reaches or how wide it opens.
 *
 * <p>Nothing here knows which unit is firing. The numbers are the caller's {@link Shape}.
 */
public final class FlameCone {

    private FlameCone() {
    }

    /**
     * A cone's dimensions, in blocks.
     *
     * @param reach           how far down the axis the flame carries
     * @param nozzleHalfWidth half-width at the muzzle — the tight end
     * @param mouthHalfWidth  half-width at {@code reach} — the spread that makes this an area attack
     * @param halfHeight      how far above and below the origin the flame washes
     * @param steps           how many puffs {@link #samples} cuts the axis into, for drawing only
     */
    public record Shape(double reach, double nozzleHalfWidth, double mouthHalfWidth, double halfHeight,
            int steps) {
        public Shape {
            if (steps < 1) {
                throw new IllegalArgumentException("a cone needs at least one step, got " + steps);
            }
            if (reach <= 0) {
                throw new IllegalArgumentException("a cone needs a positive reach, got " + reach);
            }
            if (mouthHalfWidth < nozzleHalfWidth) {
                throw new IllegalArgumentException("a cone must not narrow along its own axis");
            }
        }
    }

    /** One puff of the drawn flame: where it sits on the axis, and how wide the cone is there. */
    public record Slice(Vec3 center, double halfWidth) {
    }

    /**
     * The wedge's four horizontal corners, in winding order: near-left, far-left, far-right,
     * near-right. Its {@code y} is the origin's throughout — height is the separate slab test in
     * {@link #catches}.
     *
     * @param bearing direction to fire in; normalised here, and its Y component is dropped — a
     *                flamethrower washes over the ground rather than being aimed up or down, so a
     *                target on a rise is burned by the same wedge as one on the flat
     */
    public static Vec3[] footprint(Vec3 origin, Vec3 bearing, Shape shape) {
        Vec3 axis = horizontal(bearing);
        Vec3 side = new Vec3(-axis.z, 0.0, axis.x);
        Vec3 mouth = origin.add(axis.scale(shape.reach()));
        return new Vec3[] {
                origin.subtract(side.scale(shape.nozzleHalfWidth())),
                mouth.subtract(side.scale(shape.mouthHalfWidth())),
                mouth.add(side.scale(shape.mouthHalfWidth())),
                origin.add(side.scale(shape.nozzleHalfWidth())),
        };
    }

    /**
     * Whether {@code target} is standing in the flame. True as soon as it overlaps the wedge at all,
     * so a unit clipping the edge burns exactly as one standing in the middle does — a flamethrower
     * has no falloff across its spread, which is what makes it the answer to a clump.
     */
    public static boolean catches(Vec3 origin, Vec3 bearing, Shape shape, AABB target) {
        // Vertical first: it is one comparison and rules out anything overhead or down a drop.
        if (target.maxY < origin.y - shape.halfHeight() || target.minY > origin.y + shape.halfHeight()) {
            return false;
        }
        Vec3[] wedge = footprint(origin, bearing, shape);

        // Separating-axis test between two convex 2D shapes, in the x/z plane. The rectangle
        // contributes the two world axes; the wedge contributes one normal per edge. If any axis
        // separates them, they do not overlap — and if none does, they must.
        if (separatedOn(wedge, target, 1.0, 0.0) || separatedOn(wedge, target, 0.0, 1.0)) {
            return false;
        }
        for (int i = 0; i < wedge.length; i++) {
            Vec3 edge = wedge[(i + 1) % wedge.length].subtract(wedge[i]);
            // The edge's 2D normal. Direction doesn't matter: both shapes' spans are projected onto
            // it and the test is symmetric.
            if (separatedOn(wedge, target, -edge.z, edge.x)) {
                return false;
            }
        }
        return true;
    }

    /**
     * The box containing the whole wedge — what a caller queries the level with once, before
     * narrowing the candidates down with {@link #catches}.
     */
    public static AABB envelope(Vec3 origin, Vec3 bearing, Shape shape) {
        Vec3[] wedge = footprint(origin, bearing, shape);
        double minX = wedge[0].x;
        double maxX = wedge[0].x;
        double minZ = wedge[0].z;
        double maxZ = wedge[0].z;
        for (Vec3 corner : wedge) {
            minX = Math.min(minX, corner.x);
            maxX = Math.max(maxX, corner.x);
            minZ = Math.min(minZ, corner.z);
            maxZ = Math.max(maxZ, corner.z);
        }
        return new AABB(minX, origin.y - shape.halfHeight(), minZ,
                maxX, origin.y + shape.halfHeight(), maxZ);
    }

    /**
     * The flame cut into {@link Shape#steps()} puffs for drawing, running out from {@code origin}
     * along {@code bearing}, each carrying the cone's half-width where it sits. Purely presentational
     * — {@link #catches} is the authority on what burns — but it is derived from the same
     * {@link Shape}, so the two can never disagree about the reach or the spread.
     */
    public static List<Slice> samples(Vec3 origin, Vec3 bearing, Shape shape) {
        Vec3 axis = horizontal(bearing);
        List<Slice> slices = new ArrayList<>(shape.steps());
        for (int i = 1; i <= shape.steps(); i++) {
            double fraction = (double) i / shape.steps();
            double halfWidth = shape.nozzleHalfWidth()
                    + (shape.mouthHalfWidth() - shape.nozzleHalfWidth()) * fraction;
            slices.add(new Slice(origin.add(axis.scale(shape.reach() * fraction)), halfWidth));
        }
        return slices;
    }

    /** Whether the wedge and the target's footprint are disjoint when projected onto {@code (ax, az)}. */
    private static boolean separatedOn(Vec3[] wedge, AABB target, double ax, double az) {
        double wedgeMin = Double.POSITIVE_INFINITY;
        double wedgeMax = Double.NEGATIVE_INFINITY;
        for (Vec3 corner : wedge) {
            double projection = corner.x * ax + corner.z * az;
            wedgeMin = Math.min(wedgeMin, projection);
            wedgeMax = Math.max(wedgeMax, projection);
        }
        // A rectangle's extent on any axis is decided by two of its corners; taking the min and max
        // of each term separately picks them without enumerating all four.
        double boxMin = Math.min(target.minX * ax, target.maxX * ax)
                + Math.min(target.minZ * az, target.maxZ * az);
        double boxMax = Math.max(target.minX * ax, target.maxX * ax)
                + Math.max(target.minZ * az, target.maxZ * az);
        return wedgeMax < boxMin || boxMax < wedgeMin;
    }

    /**
     * {@code bearing} flattened onto the horizontal plane and normalised. A zero-length or purely
     * vertical bearing falls back to due south, which is where a zero yaw points — it never happens
     * in play (the caller derives the bearing from a target it can see) and returning a real
     * direction keeps every method here total.
     */
    private static Vec3 horizontal(Vec3 bearing) {
        Vec3 flat = new Vec3(bearing.x, 0.0, bearing.z);
        return flat.lengthSqr() < 1.0e-8 ? new Vec3(0.0, 0.0, 1.0) : flat.normalize();
    }
}
