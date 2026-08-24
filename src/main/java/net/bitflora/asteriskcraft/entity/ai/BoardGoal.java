package net.bitflora.asteriskcraft.entity.ai;

import net.bitflora.asteriskcraft.command.CommandAttachments;
import net.bitflora.asteriskcraft.command.CommandOrder;
import net.bitflora.asteriskcraft.entity.terran.BunkerEntity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import org.jetbrains.annotations.Nullable;

import java.util.EnumSet;

/**
 * Carries out a {@link CommandOrder.Kind#LOAD} order: walk to the transport named by the order and
 * climb inside it.
 *
 * <p>The shape is {@link CommandedMoveGoal}'s, with an entity for a destination instead of a block
 * and a boarding attempt where that goal has an arrival test. It sits at the same priority for the
 * same reason: getting into cover is a march, and outranks fighting for as long as the order's focus
 * window holds (see {@code CommandAttachments.setOrder}).
 *
 * <p><b>It decides nothing about who may board.</b> That rule lives at the door, on the transport
 * itself, so there is exactly one place to read it and a second transport needs no change here. This
 * goal only walks, knocks, and clears the order — on success, or on any refusal at all: the Bunker
 * full, still going up, dead, unloaded from the world, or simply not something this unit fits in.
 * An order that cannot be carried out must not be a unit standing at a door forever.
 *
 * <p>Installed on every commandable unit through {@link CommandableGoals}, not only on the ones that
 * can board. A unit that cannot is never handed the order in the first place — {@code
 * command.CommandInputResolver} filters at the point of issue — and if one somehow were, the first
 * refusal clears it.
 */
public class BoardGoal extends Goal {
    private final Mob mob;
    private final double speed;

    public BoardGoal(Mob mob, double speed) {
        this.mob = mob;
        this.speed = speed;
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    /** The transport this unit has been told to get into, or null if it has no live LOAD order. */
    @Nullable
    private BunkerEntity transport() {
        CommandOrder order = CommandAttachments.getOrder(this.mob);
        if (order.kind() != CommandOrder.Kind.LOAD || order.target().isEmpty()) {
            return null;
        }
        if (!(this.mob.level() instanceof ServerLevel level)) {
            return null;
        }
        Entity target = level.getEntity(order.target().get());
        return target instanceof BunkerEntity bunker ? bunker : null;
    }

    @Override
    public boolean canUse() {
        return !this.mob.isPassenger() && this.transport() != null;
    }

    @Override
    public boolean canContinueToUse() {
        return this.canUse();
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public void stop() {
        this.mob.getNavigation().stop();
    }

    @Override
    public void tick() {
        BunkerEntity transport = this.transport();
        if (transport == null) {
            return;
        }
        // Re-read the door's answer every tick rather than only on arrival: a Bunker that fills up
        // or is destroyed while this unit is still walking should release it now, not when it gets
        // there.
        if (!transport.boardable(this.mob)) {
            CommandAttachments.clearOrder(this.mob);
            return;
        }
        this.mob.getLookControl().setLookAt(transport, 30.0f, 30.0f);
        if (this.mob.distanceToSqr(transport) > BunkerEntity.BOARDING_REACH * BunkerEntity.BOARDING_REACH) {
            this.mob.getNavigation().moveTo(transport, this.speed);
            return;
        }
        this.mob.getNavigation().stop();
        transport.board(this.mob);
        // Cleared whether or not the ride was taken: a refusal at this range is one of the permanent
        // kinds (full, still building), and the boardable() check above already released the rest.
        CommandAttachments.clearOrder(this.mob);
    }
}
