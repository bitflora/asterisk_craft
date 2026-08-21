package net.bitflora.asteriskcraft.entity.ai.zerg;

import net.bitflora.asteriskcraft.building.SiegeTarget;
import net.bitflora.asteriskcraft.entity.zerg.InfestedVillagerEntity;
import net.bitflora.asteriskcraft.faction.FactionAttachments;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import org.jetbrains.annotations.Nullable;

import java.util.EnumSet;

/**
 * Lights an Infested Villager's fuse once it is on top of something worth spending itself on, and puts
 * it out again if that thing gets away. Vanilla's {@code SwellGoal} does the first half of this and
 * cannot be reused — it takes a {@code Creeper} in its constructor, the same shape of trap
 * {@code ZombieAttackGoal} carries.
 *
 * <p>Two things arm it, and the second is the addition over vanilla:
 * <ul>
 *   <li>a live target within {@link #ARM_RANGE} — the ordinary case, a bomber reaching a Zealot;</li>
 *   <li>an enemy {@link SiegeTarget} block within the same range. Without this a bomber that walked
 *       all the way to an <em>undefended</em> Nexus would stand against it doing nothing, since a
 *       building is not a target and this unit has no swing to fall back on. Blowing up buildings is
 *       most of what the unit is for, so it cannot be the one case it fumbles.</li>
 * </ul>
 *
 * <p>It holds {@code MOVE} and is installed above every other goal, so an armed bomber cannot be
 * walked away from what it armed on. The fuse still <em>reverses</em> on its own terms, in
 * {@link #tick}: a target that breaks line of sight or opens past {@link #ABORT_RANGE} unwinds the
 * count rather than detonating it, so a bomber that is kited disarms instead of wasting itself, and
 * the unit does not need a second goal to tell it so.
 */
public class InfestedSwellGoal extends Goal {

    /** How close is close enough to commit. Vanilla's creeper uses the same 3 blocks. */
    private static final double ARM_RANGE = 3.0;
    /** Past this the fuse unwinds — the target got away. Vanilla's creeper uses the same 7. */
    private static final double ABORT_RANGE = 7.0;
    /** How far around itself a bomber looks for a building to spend itself on. */
    private static final int BUILDING_SCAN_RADIUS = 3;
    /**
     * Ticks between building scans. The scan is 343 block-entity lookups, and both {@code canUse} and
     * a per-tick {@code tick} would otherwise run it every tick for every bomber alive — and this is
     * a unit that arrives in crowds, since one overrun village raises a dozen. {@code SiegeBlockGoal}
     * throttles its identical scan the same way and by the same amount.
     */
    private static final int SCAN_INTERVAL = 10;

    private final InfestedVillagerEntity bomber;
    private @Nullable LivingEntity target;
    private @Nullable BlockPos building;
    private int scanCooldown;

    public InfestedSwellGoal(InfestedVillagerEntity bomber) {
        this.bomber = bomber;
        this.setFlags(EnumSet.of(Flag.MOVE));
    }

    @Override
    public boolean canUse() {
        // A fuse already burning keeps this goal running whatever else is true — it owns the unwind.
        if (this.bomber.getSwellDir() > 0) {
            return true;
        }
        LivingEntity current = this.bomber.getTarget();
        if (current != null && !current.isDeadOrDying()
                && this.bomber.distanceToSqr(current) < ARM_RANGE * ARM_RANGE) {
            return true;
        }
        return enemyBuildingNearby() != null;
    }

    @Override
    public void start() {
        this.bomber.getNavigation().stop();
        this.target = this.bomber.getTarget();
    }

    @Override
    public void stop() {
        this.target = null;
        // Both halves of the cache, or the next run would spend up to SCAN_INTERVAL ticks believing
        // the stale "no building" it was cleared to while standing against one.
        this.building = null;
        this.scanCooldown = 0;
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public void tick() {
        // A building underfoot is unconditional: it cannot run, cannot break line of sight, and is
        // what this unit was aimed at. Checked first so a bomber standing on a Nexus goes off even
        // while the unit that was chasing it dies or slips out of view.
        if (enemyBuildingNearby() != null) {
            this.bomber.setSwellDir(1);
            return;
        }
        if (this.target == null || this.target.isDeadOrDying()) {
            this.bomber.setSwellDir(-1);
            return;
        }
        boolean lost = this.bomber.distanceToSqr(this.target) > ABORT_RANGE * ABORT_RANGE
                || !this.bomber.getSensing().hasLineOfSight(this.target);
        this.bomber.setSwellDir(lost ? -1 : 1);
    }

    /**
     * An enemy building whose core block sits within reach, or null — answered from a cache that is
     * refreshed at most every {@link #SCAN_INTERVAL} ticks, since the scan itself is not cheap and
     * both entry points want the answer every tick. A bomber moves at most a fraction of a block in
     * that window, so a half-second-stale answer cannot put it on the wrong side of a wall.
     */
    private @Nullable BlockPos enemyBuildingNearby() {
        if (--this.scanCooldown > 0) {
            return this.building;
        }
        this.scanCooldown = SCAN_INTERVAL;
        this.building = scanForEnemyBuilding();
        return this.building;
    }

    /**
     * The scan itself. Faction-generic: it asks {@link SiegeTarget#buildingFaction()} and never names
     * a Nexus, Gateway or Hive — the same rule {@code SiegeBlockGoal} follows, and the same radius, so
     * the two agree on what "arrived at a building" means.
     */
    private @Nullable BlockPos scanForEnemyBuilding() {
        BlockPos origin = this.bomber.blockPosition();
        for (BlockPos pos : BlockPos.betweenClosed(
                origin.offset(-BUILDING_SCAN_RADIUS, -BUILDING_SCAN_RADIUS, -BUILDING_SCAN_RADIUS),
                origin.offset(BUILDING_SCAN_RADIUS, BUILDING_SCAN_RADIUS, BUILDING_SCAN_RADIUS))) {
            if (this.bomber.level().getBlockEntity(pos) instanceof SiegeTarget target
                    && FactionAttachments.get(this.bomber).isEnemy(target.buildingFaction())) {
                return pos.immutable();
            }
        }
        return null;
    }
}
