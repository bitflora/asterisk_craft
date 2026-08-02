package net.bitflora.asteriskcraft.command;

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

    private CommandAttachments() {
    }

    public static CommandOrder getOrder(Entity entity) {
        return entity.getData(ORDER);
    }

    public static void setOrder(Entity entity, CommandOrder order) {
        if (order.kind() == CommandOrder.Kind.MOVE && entity instanceof Mob mob) {
            // A fresh move order overrides whatever the unit is doing, including a fight already in
            // progress, so CommandedMoveGoal can take over immediately instead of yielding to a stale
            // target acquired before the order landed. It can still retaliate if attacked again en
            // route (see CommandedMoveGoal/RetaliateGoal), which re-yields movement until that fight ends.
            mob.setTarget(null);
            // Drop the path too, so re-issuing an order visibly restarts a unit that is already
            // marching. CommandedMoveGoal re-paths the moment navigation reports done, and a running
            // goal is never re-start()ed by GoalSelector, so without this a stalled unit told to move
            // again would keep grinding away on the path that was already failing it.
            mob.getNavigation().stop();
        }
        entity.setData(ORDER, order);
    }

    public static void clearOrder(Entity entity) {
        entity.setData(ORDER, CommandOrder.NONE);
    }

    public static PlayerSelection selection(Player player) {
        return player.getData(SELECTION);
    }

    public static ControlGroups controlGroups(Player player) {
        return player.getData(CONTROL_GROUPS);
    }
}
