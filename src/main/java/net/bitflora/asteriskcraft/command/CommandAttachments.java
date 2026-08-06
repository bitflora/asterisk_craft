package net.bitflora.asteriskcraft.command;

import com.mojang.serialization.Codec;
import net.bitflora.asteriskcraft.AsteriskCraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

import java.util.function.Supplier;

/**
 * Data attachments for the command system: a per-unit standing {@link CommandOrder} and a
 * per-player {@link PlayerSelection}. Faction-generic — any {@code Mob} can carry an order.
 */
public final class CommandAttachments {
    public static final DeferredRegister<AttachmentType<?>> ATTACHMENT_TYPES =
            DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, AsteriskCraft.MODID);

    public static final Supplier<AttachmentType<CommandOrder>> ORDER = ATTACHMENT_TYPES.register(
            "command_order", () -> AttachmentType.builder(() -> CommandOrder.NONE)
                    .serialize(CommandOrder.CODEC.fieldOf("order")).build());

    public static final Supplier<AttachmentType<PlayerSelection>> SELECTION = ATTACHMENT_TYPES.register(
            "selection", () -> AttachmentType.<PlayerSelection>builder(PlayerSelection::new).build());

    public static final Supplier<AttachmentType<ControlGroups>> CONTROL_GROUPS = ATTACHMENT_TYPES.register(
            "control_groups", () -> AttachmentType.<ControlGroups>builder(ControlGroups::new)
                    .serialize(ControlGroups.CODEC.fieldOf("groups")).build());

    /**
     * Game time at which the unit's current move order stops overriding combat, or 0 for "not
     * marching under one". A deadline rather than a countdown because nothing ticks a unit's command
     * state — only its goals run, and a unit whose goals are all yielding is exactly the case this
     * has to survive.
     */
    public static final Supplier<AttachmentType<Long>> MOVE_FOCUS_UNTIL = ATTACHMENT_TYPES.register(
            "move_focus_until", () -> AttachmentType.<Long>builder(() -> 0L)
                    .serialize(Codec.LONG.fieldOf("moveFocusUntil")).build());

    /**
     * How long a fresh move order wins over fighting. Five seconds is enough to walk a unit out of a
     * skirmish it is standing in; without a window at all, clearing the target in {@link #setOrder}
     * accomplishes nothing, because {@code FactionTargetGoal} simply re-acquires the same enemy on
     * the very next tick and the unit never takes a step.
     */
    public static final int MOVE_FOCUS_TICKS = 100;

    private CommandAttachments() {
    }

    public static CommandOrder getOrder(Entity entity) {
        return entity.getData(ORDER);
    }

    public static void setOrder(Entity entity, CommandOrder order) {
        if (order.kind() == CommandOrder.Kind.MOVE && entity instanceof Mob mob) {
            // A fresh move order overrides whatever the unit is doing, including a fight already in
            // progress, so CommandedMoveGoal can take over immediately instead of yielding to a stale
            // target acquired before the order landed. Clearing the target only sticks because of the
            // focus window opened below; after it lapses the unit fights again as before, stopping for
            // hostiles it acquires or is struck by en route.
            mob.setTarget(null);
            entity.setData(MOVE_FOCUS_UNTIL, entity.level().getGameTime() + MOVE_FOCUS_TICKS);
            // Drop the path too, so re-issuing an order visibly restarts a unit that is already
            // marching. CommandedMoveGoal re-paths the moment navigation reports done, and a running
            // goal is never re-start()ed by GoalSelector, so without this a stalled unit told to move
            // again would keep grinding away on the path that was already failing it.
            mob.getNavigation().stop();
        } else {
            // Any other order supersedes the march, so it must not go on suppressing combat — an
            // attack order in particular is the player asking for the opposite.
            entity.setData(MOVE_FOCUS_UNTIL, 0L);
        }
        entity.setData(ORDER, order);
    }

    public static void clearOrder(Entity entity) {
        entity.setData(ORDER, CommandOrder.NONE);
        entity.setData(MOVE_FOCUS_UNTIL, 0L);
    }

    /**
     * Whether a move order is currently overriding this unit's combat behaviour. Combat goals honour
     * this instead of each carrying their own notion of a march: targeting goals refuse to acquire,
     * and {@code CommandedMoveGoal} refuses to yield, for as long as it holds.
     */
    public static boolean isMoveFocused(Entity entity) {
        return isMoveFocused(entity.getData(MOVE_FOCUS_UNTIL), entity.level().getGameTime());
    }

    /** The pure rule behind {@link #isMoveFocused(Entity)}, free of any live entity. */
    public static boolean isMoveFocused(long focusUntil, long gameTime) {
        return focusUntil > gameTime;
    }

    public static PlayerSelection selection(Player player) {
        return player.getData(SELECTION);
    }

    public static ControlGroups controlGroups(Player player) {
        return player.getData(CONTROL_GROUPS);
    }
}
