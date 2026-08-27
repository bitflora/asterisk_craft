package net.bitflora.asteriskcraft.building;

import net.bitflora.asteriskcraft.entity.WorkerEntity;
import net.bitflora.asteriskcraft.faction.Faction;
import net.bitflora.asteriskcraft.faction.FactionAttachments;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * The worker that has to be standing over a building for it to go up, and the single answer to
 * "may this structure make any progress this tick" — the way {@link PsiField} is the single answer
 * to "may this be placed here".
 *
 * <p>It sits above <em>both</em> of the mod's construction mechanisms, which is the whole reason it
 * is its own object rather than a field on either. A building that is blocks counts down inside
 * {@link BuildingDefense#tickWarpIn}; a building that is an entity ({@code entity.terran.BunkerEntity},
 * {@code entity.terran.MissileTurretEntity} — see {@link PrePlaced}) counts down in its own
 * {@code tick()}. Both own one of these and both consult it first, so a Terran structure later
 * re-authored as an {@code .nbt} template needs nothing new here.
 *
 * <p>It names no race and no building. Who needs a builder at all is a flag on the <em>placing
 * item</em> ({@link BuilderDependent}), and who may <em>be</em> one is {@link WorkerEntity} — not an
 * SCV — so a Protoss kit could opt in later with no edit to this class.
 *
 * <h2>The rule</h2>
 * A site with no builder assigned is a building nobody was ever required to put up (a Protoss
 * warp-in, a world-generated Bunker), and always answers {@link Progress#BUILDING}. Otherwise:
 * nothing happens until the worker arrives ({@link Progress#WAITING}), everything happens once it
 * has ({@link Progress#BUILDING} — <em>including</em> after it walks off again, so pulling a worker
 * away with a move order no longer stalls a build that has started), and the structure is
 * {@link Progress#ABANDONED} the moment the worker is killed, at any point before completion.
 *
 * <p>"Killed" is deliberately "gone for {@value #LOST_TOLERANCE_TICKS} consecutive ticks" rather
 * than "gone this tick": {@code ServerLevel.getEntity} answers null for an entity whose chunk has
 * not caught up yet, so razing on the first miss would demolish every site in progress across a
 * world reload.
 */
public final class ConstructionSite {

    /** How far a placement will call for a worker. */
    public static final int CALL_RADIUS = 32;

    /**
     * How far outside a building's own footprint its builder counts as "at the site". Added to the
     * building's radius at assignment time, because the worker cannot stand inside the thing it is
     * welding and a 9x9 template puts its core five blocks further away than a Bunker does.
     */
    public static final double STANDOFF = 4.0;

    /** How long a builder may be unresolvable before the site gives up on it. */
    static final int LOST_TOLERANCE_TICKS = 40;

    /** What a site lets its building do this tick. */
    public enum Progress {
        /** A builder is assigned but has never reached the site: nothing has been built yet. */
        WAITING,
        /** Carry on — either the builder is here, or it got here once and the build is under way. */
        BUILDING,
        /** The builder was killed before the building was finished; raze what stands. */
        ABANDONED
    }

    @Nullable
    private UUID builder;
    /** How close the builder has to get. Saved, because it is a fact about the building's size. */
    private double arrivalRange = STANDOFF;
    /** Whether the builder has ever reached the site — the latch that makes a build survive it leaving. */
    private boolean started;
    /**
     * Consecutive ticks the builder has failed to resolve. Deliberately not saved: a reload is
     * exactly the case the tolerance exists for, and it has to start counting from zero there.
     */
    private int missedTicks;

    /**
     * Puts {@code builder} on this site. {@code arrivalRange} is how close it has to get, measured
     * from the position the building's own tick passes to {@link #tick} — so a caller adds
     * {@link #STANDOFF} to whatever its half-width or footprint radius is.
     */
    public void assign(UUID builder, double arrivalRange) {
        this.builder = builder;
        this.arrivalRange = arrivalRange;
        this.started = false;
        this.missedTicks = 0;
    }

    /** Whether anyone was ever required to build this. */
    public boolean isRequired() {
        return this.builder != null;
    }

    /**
     * Advances the site by a tick and says what its building may do. Emits the welding plume while
     * the builder is actually standing there — this is the one place that decides it is, so nothing
     * else has to answer the same question a second time.
     *
     * @param site where the building is, in world coordinates
     */
    public Progress tick(ServerLevel level, Vec3 site) {
        if (this.builder == null) {
            return Progress.BUILDING;
        }
        WorkerEntity worker = this.resolve(level);
        this.missedTicks = worker == null ? this.missedTicks + 1 : 0;
        boolean inRange = worker != null
                && worker.position().distanceToSqr(site) <= this.arrivalRange * this.arrivalRange;
        Progress progress = decide(true, worker != null, inRange, this.started, this.missedTicks);
        if (inRange) {
            this.started = true;
            weld(level, worker);
        }
        return progress;
    }

    /**
     * The rule itself, with no world in it so the whole transition table is unit-testable.
     *
     * @param required     whether a builder was ever assigned
     * @param builderAlive whether it resolves to a living worker right now
     * @param inRange      whether that worker is standing at the site
     * @param started      whether it has ever been
     * @param missedTicks  consecutive ticks it has failed to resolve
     */
    static Progress decide(boolean required, boolean builderAlive, boolean inRange, boolean started,
            int missedTicks) {
        if (!required) {
            return Progress.BUILDING;
        }
        if (!builderAlive) {
            if (missedTicks > LOST_TOLERANCE_TICKS) {
                return Progress.ABANDONED;
            }
            return started ? Progress.BUILDING : Progress.WAITING;
        }
        return inRange || started ? Progress.BUILDING : Progress.WAITING;
    }

    /**
     * Lets the builder go — the building is finished, or gone. Clears the worker's own assignment so
     * it goes back to the economy, naming the site it is being released from so a late release from
     * a razed building cannot strip a newer one.
     */
    public void release(ServerLevel level, BlockPos site) {
        WorkerEntity worker = this.resolve(level);
        if (worker != null) {
            worker.clearBuildSite(site);
        }
        this.builder = null;
        this.started = false;
        this.missedTicks = 0;
    }

    @Nullable
    private WorkerEntity resolve(ServerLevel level) {
        if (this.builder == null) {
            return null;
        }
        return level.getEntity(this.builder) instanceof WorkerEntity worker && worker.isAlive() ? worker : null;
    }

    /** The cutting torch, thrown from the worker's own arm towards whatever it is looking at. */
    private static void weld(ServerLevel level, WorkerEntity worker) {
        Vec3 origin = worker.getEyePosition().add(worker.getLookAngle().scale(0.6));
        level.sendParticles(ParticleTypes.WHITE_SMOKE, origin.x, origin.y, origin.z, 2, 0.1, 0.1, 0.1, 0.01);
    }

    /**
     * The nearest idle worker of {@code faction} within {@link #CALL_RADIUS} of {@code site}, or
     * {@code null} if none will answer — which is a refusal to place, not a building that goes up
     * unattended.
     *
     * <p>A worker already on another site is skipped rather than stolen: an unfinished structure
     * whose builder wandered off to a newer one would sit frozen forever. The second pass is on real
     * distance because the search box's corners reach further than its radius.
     */
    @Nullable
    public static WorkerEntity callBuilder(ServerLevel level, Vec3 site, Faction faction) {
        AABB box = new AABB(site, site).inflate(CALL_RADIUS);
        WorkerEntity best = null;
        double bestDistSqr = Double.MAX_VALUE;
        for (WorkerEntity worker : level.getEntitiesOfClass(WorkerEntity.class, box,
                w -> w.isAlive() && !w.hasBuildSite() && FactionAttachments.get(w) == faction)) {
            double distSqr = worker.position().distanceToSqr(site);
            if (distSqr <= (double) CALL_RADIUS * CALL_RADIUS && distSqr < bestDistSqr) {
                bestDistSqr = distSqr;
                best = worker;
            }
        }
        return best;
    }

    /**
     * Takes a half-built building-as-entity away, for a build whose worker was killed. Discarded
     * rather than killed: it never finished, so it owes no death drops and no death event — it was
     * only ever a construction site.
     */
    public static void razeUnbuilt(Entity building, ServerLevel level) {
        level.playSound(null, building.blockPosition(), SoundEvents.ANVIL_DESTROY, SoundSource.BLOCKS, 0.8f, 0.9f);
        level.sendParticles(ParticleTypes.LARGE_SMOKE,
                building.getX(), building.getY() + 0.6, building.getZ(), 24, 0.6, 0.6, 0.6, 0.02);
        building.discard();
    }

    public void save(ValueOutput output) {
        if (this.builder != null) {
            output.store("Builder", UUIDUtil.CODEC, this.builder);
            output.putDouble("BuilderRange", this.arrivalRange);
            output.putBoolean("BuildStarted", this.started);
        }
    }

    public void load(ValueInput input) {
        this.builder = input.read("Builder", UUIDUtil.CODEC).orElse(null);
        this.arrivalRange = input.getDoubleOr("BuilderRange", STANDOFF);
        this.started = input.getBooleanOr("BuildStarted", false);
        this.missedTicks = 0;
    }
}
