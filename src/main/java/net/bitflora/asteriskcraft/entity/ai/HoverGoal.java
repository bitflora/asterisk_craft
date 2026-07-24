package net.bitflora.asteriskcraft.entity.ai;

import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.EnumSet;

/**
 * Keeps an air unit at its cruising altitude when nothing else is moving it. Installed at the
 * <em>lowest</em> priority and holding {@link Flag#MOVE}, so it only ever runs in the gaps between
 * real orders — the moment a move, attack or siege goal wants the unit, that goal wins.
 *
 * <p>It earns its place at spawn time: {@link net.bitflora.asteriskcraft.building.UnitSpawns} places
 * every unit on solid ground via {@code SpawnSpots.findGroundSpot}, and
 * {@code FlyingMoveControl} only switches gravity off once it has been given somewhere to go. Without
 * this goal a freshly warped-in flyer would simply stand in the dirt until something attacked it.
 * It also pulls the unit back up after anything (a fall, a knockback) drags it below altitude.
 *
 * <p>Faction-generic and unit-generic: it drives the {@code MoveControl} directly rather than the
 * navigation, because there is no path to follow — just an altitude to hold.
 */
public class HoverGoal extends Goal {
    /** How far below cruising altitude the unit may drift before this goal corrects it. */
    private static final double SLACK = 1.0;

    private final Mob mob;
    private final int hoverHeight;
    private final double speed;

    public HoverGoal(Mob mob, int hoverHeight, double speed) {
        this.mob = mob;
        this.hoverHeight = hoverHeight;
        this.speed = speed;
        this.setFlags(EnumSet.of(Flag.MOVE));
    }

    private double targetY() {
        return this.mob.level()
                .getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, this.mob.blockPosition())
                .getY() + this.hoverHeight;
    }

    @Override
    public boolean canUse() {
        return this.mob.getY() < targetY() - SLACK;
    }

    @Override
    public boolean canContinueToUse() {
        return canUse();
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public void tick() {
        // Climb straight up over the unit's own column: no horizontal component, so this never
        // fights whatever put the unit where it is.
        this.mob.getMoveControl().setWantedPosition(this.mob.getX(), targetY(), this.mob.getZ(), this.speed);
    }
}
