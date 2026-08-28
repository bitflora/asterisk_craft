package net.bitflora.asteriskcraft.building;

import net.bitflora.asteriskcraft.entity.WorkerEntity;
import net.bitflora.asteriskcraft.faction.Faction;
import net.bitflora.asteriskcraft.faction.FactionAttachments;
import net.bitflora.asteriskcraft.faction.Race;
import net.bitflora.asteriskcraft.race.Races;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
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
 * <p><b>Unless the worker's race spends it.</b> A race that {@link Race#consumesBuilders} — the
 * Zerg, whose Drone <em>becomes</em> the building — kills its worker the instant it arrives and
 * drops it from the site, which puts the site straight back into the not-required state above. So
 * the whole difference between welding a Bunker together and morphing a colony is which branch
 * arrival takes; {@link #decide} does not know about it, because the state it lands in already
 * existed.
 *
 * <p>A builder that never turns up at all is the other way a site ends: {@value #ARRIVAL_TIMEOUT_TICKS}
 * ticks (30 seconds) after it was called, a site the builder has still not reached gives up, and the
 * building is razed exactly as an abandoned one is. Because nothing was ever built there, the kit
 * that placed it is handed back as an item on the ground — which is also what a builder killed on
 * its way over refunds, since "the worker never arrived" is the same outcome however it happened. A
 * build that has <em>started</em> and then loses its worker still just falls down: it was paid for.
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

    /**
     * How long either end of the arrangement may fail to find the other before giving up on it —
     * the site on an unresolvable builder, and the builder on a site that no longer claims it.
     */
    public static final int LOST_TOLERANCE_TICKS = 40;

    /** How long a site waits for a builder that has never reached it before cancelling itself. */
    static final int ARRIVAL_TIMEOUT_TICKS = 600;

    /** What a site lets its building do this tick. */
    public enum Progress {
        /** A builder is assigned but has never reached the site: nothing has been built yet. */
        WAITING,
        /** Carry on — either the builder is here, or it got here once and the build is under way. */
        BUILDING,
        /**
         * The building will never be finished — its builder was killed, or never turned up at all
         * within {@link #ARRIVAL_TIMEOUT_TICKS}. Raze what stands; the kit has already been refunded
         * if nothing was ever built here.
         */
        ABANDONED
    }

    @Nullable
    private UUID builder;
    /**
     * The item that placed this building, handed back if the builder never arrives. Saved, because a
     * 30-second wait routinely outlives a reload and a refund that forgot what it was refunding
     * would be worse than none.
     */
    private ItemStack kit = ItemStack.EMPTY;
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
     * Ticks since the builder was called, while it has still never arrived. Saved, unlike
     * {@link #missedTicks}: this one is a promise to the player about wall-clock time, so a reload
     * must not quietly restart it.
     */
    private int waitingTicks;

    /**
     * Puts {@code builder} on this site. {@code arrivalRange} is how close it has to get, measured
     * from the position the building's own tick passes to {@link #tick} — so a caller adds
     * {@link #STANDOFF} to whatever its half-width or footprint radius is.
     *
     * @param kit what to drop if the builder never turns up — the stack that placed the building,
     *            copied, so shrinking the placer's hand afterwards cannot empty the refund
     */
    public void assign(UUID builder, double arrivalRange, ItemStack kit) {
        this.builder = builder;
        this.arrivalRange = arrivalRange;
        this.kit = kit.copyWithCount(1);
        this.started = false;
        this.missedTicks = 0;
        this.waitingTicks = 0;
    }

    /** Whether anyone was ever required to build this. */
    public boolean isRequired() {
        return this.builder != null;
    }

    /** Whether this site is still waiting on the worker with this id — see {@link #stillClaims}. */
    public boolean isBuiltBy(UUID id) {
        return id.equals(this.builder);
    }

    /**
     * The builder's half of {@link #resolve}: whether a building still standing at {@code pos} is
     * still waiting on {@code worker}. Both halves are needed because {@link #release} can only
     * reach a worker it can resolve, and a building destroyed while its builder's chunk has not
     * caught up — or one that goes away without ever being released at all — would otherwise leave
     * that worker walking to a hole in the ground for the rest of the match.
     *
     * <p>It covers both of the mod's construction mechanisms, since either kind of building may be
     * the one that vanished: a building that is blocks answers off the {@link SiegeTarget} block
     * entity on the core column, one that is an entity off the {@link PrePlaced} standing there.
     *
     * <p>An unloaded site answers yes. "Nothing is there" is only knowable about ground the server
     * is actually holding, and the caller counts consecutive misses on top of that for the same
     * reason {@link #LOST_TOLERANCE_TICKS} exists on this side.
     */
    public static boolean stillClaims(ServerLevel level, BlockPos pos, WorkerEntity worker) {
        if (!level.isLoaded(pos)) {
            return true;
        }
        UUID id = worker.getUUID();
        if (level.getBlockEntity(pos) instanceof SiegeTarget target
                && target.defense().constructionSite().isBuiltBy(id)) {
            return true;
        }
        // Inflated by a block: a building-as-entity is centred on its spawn position, and the site
        // was recorded as the block it stood in rather than as its exact centre.
        AABB box = new AABB(pos).inflate(1.0);
        return !level.getEntitiesOfClass(Mob.class, box,
                mob -> mob instanceof PrePlaced building && building.constructionSite().isBuiltBy(id)).isEmpty();
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
        if (!this.started) {
            this.waitingTicks++;
        }
        Progress progress =
                decide(true, worker != null, inRange, this.started, this.missedTicks, this.waitingTicks);
        if (progress == Progress.ABANDONED && !this.started) {
            // Nothing was ever built here, so the kit goes back on the ground for whoever placed it.
            this.refundKit(level, site);
        }
        if (inRange) {
            this.started = true;
            if (consumesBuilder(worker)) {
                morph(level, worker);
                // Nobody is on the hook from here on, and dropping the UUID in the same tick the
                // worker dies is load-bearing rather than tidy: a dying entity stops answering
                // isAlive(), so a site that kept it would count past LOST_TOLERANCE_TICKS and raze
                // the structure the worker just paid for. Cleared, the next tick takes the
                // no-builder early return above and the build runs to completion unattended.
                this.builder = null;
            } else {
                weld(level, worker);
            }
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
     * @param waitingTicks ticks since it was called, while it has never arrived
     */
    static Progress decide(boolean required, boolean builderAlive, boolean inRange, boolean started,
            int missedTicks, int waitingTicks) {
        if (!required) {
            return Progress.BUILDING;
        }
        // Checked ahead of whether the builder is alive, because the case this exists for is a
        // worker that is perfectly healthy and simply never gets there — walled off, stuck on
        // terrain, or pulled away by a move order before it ever arrived.
        if (!started && waitingTicks > ARRIVAL_TIMEOUT_TICKS) {
            return Progress.ABANDONED;
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
        this.kit = ItemStack.EMPTY;
        this.started = false;
        this.missedTicks = 0;
        this.waitingTicks = 0;
    }

    /**
     * Puts the placing item back on the ground where the building was going to stand. Cleared as it
     * drops, so a site whose building takes another tick to come down cannot refund twice.
     */
    private void refundKit(ServerLevel level, Vec3 site) {
        if (this.kit.isEmpty()) {
            return;
        }
        ItemEntity dropped = new ItemEntity(level, site.x, site.y + 0.5, site.z, this.kit);
        dropped.setDefaultPickUpDelay();
        level.addFreshEntity(dropped);
        this.kit = ItemStack.EMPTY;
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
     * Whether this worker is spent by arriving rather than putting the building up — asked of the
     * worker's own race, the way {@code FactionAttachments.isHostile} asks an attacker's. That keeps
     * the rule a per-race table entry ({@link Race#consumesBuilders}) rather than a flag every Zerg
     * structure's item would have to remember to repeat.
     */
    private static boolean consumesBuilder(WorkerEntity worker) {
        Race race = FactionAttachments.raceOf(worker);
        return race != null && race.consumesBuilders();
    }

    /**
     * Spends the worker into the building it walked to. Killed rather than discarded: it gets its
     * own death sound and animation, which is what makes a Drone visibly <em>become</em> the colony
     * rather than blinking out of existence beside it.
     *
     * <p>Safe to route through a real death — {@code combat.InfestationHandler} needs both a killer
     * entity and a villager victim, and neither is present here.
     */
    private static void morph(ServerLevel level, WorkerEntity worker) {
        level.sendParticles(ParticleTypes.SCULK_SOUL,
                worker.getX(), worker.getY() + worker.getBbHeight() * 0.5, worker.getZ(),
                24, 0.3, 0.4, 0.3, 0.02);
        worker.kill(level);
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
     * The plume a building that is an entity throws off while it goes up, in its own race's colours
     * ({@code race.RaceProfile.constructionParticle}) — the counterpart of what
     * {@code BuildingDefense.tickWarpIn} emits over a building that is blocks. Read off the
     * building's own race attachment, the way {@code FactionAttachments.isHostile} reads an
     * attacker's, so no caller names a particle and none names a race. The count and spread stay the
     * caller's: how big a cloud a Bunker makes is a fact about the Bunker.
     */
    public static void plume(ServerLevel level, Entity building, int count, double xzSpread, double ySpread) {
        Race race = FactionAttachments.raceOf(building);
        ParticleOptions particle = race == null ? ParticleTypes.SMOKE : Races.of(race).constructionParticle();
        level.sendParticles(particle, building.getX(), building.getY() + 0.6, building.getZ(),
                count, xzSpread, ySpread, xzSpread, 0.01);
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
            output.putInt("BuilderWait", this.waitingTicks);
            if (!this.kit.isEmpty()) {
                output.store("BuilderKit", ItemStack.CODEC, this.kit);
            }
        }
    }

    public void load(ValueInput input) {
        this.builder = input.read("Builder", UUIDUtil.CODEC).orElse(null);
        this.arrivalRange = input.getDoubleOr("BuilderRange", STANDOFF);
        this.started = input.getBooleanOr("BuildStarted", false);
        this.waitingTicks = input.getIntOr("BuilderWait", 0);
        this.kit = input.read("BuilderKit", ItemStack.CODEC).orElse(ItemStack.EMPTY);
        this.missedTicks = 0;
    }
}
