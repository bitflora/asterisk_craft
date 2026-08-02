package net.bitflora.asteriskcraft.command;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Deals every unit in a squad its own destination around a clicked point, instead of sending the
 * whole selection to one block.
 *
 * <p>A shared destination is what made group move orders unreliable. Units pile into one column,
 * shove each other (nothing in the mod overrides {@code isPushable}, and there is no unit-vs-unit
 * path avoidance), and all but the two or three that fit inside the arrival radius orbit it until
 * their order times out. It is worst for units wider than one pathfinder node — the Dragoon, at 1.1
 * blocks, aims at block seams with about five hundredths of a block of waypoint margin, so a single
 * shove from a neighbour stops it advancing along its path at all (see
 * {@code entity/ai/CommandedMoveGoal} and docs/neoforge-api-notes.md).
 *
 * <p>Split the way {@code building/SpawnSpots} and {@code building/BuildingTemplates.planSitePrep}
 * are: the slot geometry is pure and unit-testable, and only the terrain snap needs a live level.
 * It is <em>not</em> built on {@code SpawnSpots} itself despite the similar ring shape — that finds
 * the single best open spot near a point and would hand every unit in the squad the same answer.
 */
public final class MoveFormation {
    /**
     * How far a slot may sit above or below the clicked block before it is abandoned for the centre.
     * Keeps a formation from spilling off a cliff edge or onto a roof the player never pointed at.
     */
    private static final int MAX_SLOT_STEP = 4;

    private MoveFormation() {
    }

    /**
     * Block pitch between slots: the widest body in the squad plus half a block of air, so no two
     * units are dealt destinations whose bodies overlap and there is nothing to shove over.
     *
     * <p>Every unit in the mod today lands on 2 (widths run 0.6–1.1). The formula is kept rather than
     * hardcoding that, so a future unit narrower than half a block packs tighter automatically and so
     * the reason the number is 2 stays visible.
     */
    static int spacing(float widestWidth) {
        return Math.max(1, Mth.ceil(widestWidth + 0.5f));
    }

    /** {@link #spacing(float)} for a whole squad — the widest member sets the pitch for everyone. */
    public static int spacingFor(Collection<? extends Entity> squad) {
        float widest = 0.0f;
        for (Entity unit : squad) {
            widest = Math.max(widest, unit.getBbWidth());
        }
        return spacing(widest);
    }

    /**
     * The offset of slot {@code index} from the centre. Slot 0 is the centre itself; slots 1–8 are
     * the first ring out, 9–24 the second, and so on — ring {@code r} holds {@code 8r} slots, walked
     * as four legs around its perimeter. Pure and deterministic: the same index always yields the
     * same offset, so a squad packs outward from where the player clicked.
     */
    static Vec3i slotOffset(int index, int spacing) {
        int ring = 0;
        while ((2 * ring + 1) * (2 * ring + 1) <= index) {
            ring++;
        }
        if (ring == 0) {
            return Vec3i.ZERO;
        }
        int step = index - (2 * ring - 1) * (2 * ring - 1);
        int legLength = 2 * ring;
        int leg = step / legLength;
        int along = step % legLength;
        int dx = switch (leg) {
            case 0 -> -ring + along;
            case 1 -> ring;
            case 2 -> ring - along;
            default -> -ring;
        };
        int dz = switch (leg) {
            case 0 -> -ring;
            case 1 -> -ring + along;
            case 2 -> ring;
            default -> ring - along;
        };
        return new Vec3i(dx * spacing, 0, dz * spacing);
    }

    /**
     * The squad sorted nearest-to-{@code centre} first, so the unit already closest takes the middle
     * slot and nobody is sent across the formation to reach its own destination.
     */
    public static List<Mob> orderByProximity(List<Mob> units, BlockPos centre) {
        List<Mob> ordered = new ArrayList<>(units);
        ordered.sort(Comparator.comparingDouble(unit -> unit.distanceToSqr(
                centre.getX() + 0.5, centre.getY(), centre.getZ() + 0.5)));
        return ordered;
    }

    /**
     * A destination per unit, spread around {@code centre} and snapped to the terrain under each
     * slot. Iteration order is nearest-unit-first.
     *
     * <p>Slots are not checked for a body-sized gap. The pathfinder already degrades an unreachable
     * target to the closest node it can get to, and {@code CommandedMoveGoal} treats a stalled unit
     * that is nearly there as arrived — so paying for a collision scan per unit per click would buy
     * very little.
     */
    public static Map<Mob, BlockPos> allocate(ServerLevel level, BlockPos centre, List<Mob> units) {
        int spacing = spacingFor(units);
        List<Mob> ordered = orderByProximity(units, centre);
        Map<Mob, BlockPos> slots = new LinkedHashMap<>();
        for (int i = 0; i < ordered.size(); i++) {
            BlockPos slot = centre.offset(slotOffset(i, spacing));
            slots.put(ordered.get(i), snapToGround(level, centre, slot));
        }
        return slots;
    }

    /**
     * Puts {@code slot} on the solid block whose top face a unit would stand on, keeping the whole
     * formation on the surface the player clicked rather than at the clicked block's flat Y.
     */
    private static BlockPos snapToGround(ServerLevel level, BlockPos centre, BlockPos slot) {
        // getHeightmapPos returns the open block above the terrain, so the block below it is the one
        // a unit stands on — the same convention a clicked block already carries.
        int standOn = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, slot).getY() - 1;
        if (Math.abs(standOn - centre.getY()) > MAX_SLOT_STEP) {
            return centre;
        }
        return new BlockPos(slot.getX(), standOn, slot.getZ());
    }
}
