package net.bitflora.asteriskcraft.command;

import net.bitflora.asteriskcraft.entity.ProbeEntity;
import net.bitflora.asteriskcraft.faction.Faction;
import net.bitflora.asteriskcraft.faction.FactionAttachments;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Monster;

import java.util.List;

/**
 * Server-side interpretation of a {@link CommandInputPacket}. Left-click mutates the player's
 * {@link PlayerSelection}; right-click stamps a {@link CommandOrder} onto every selected unit.
 * The Probe MINE-vs-MOVE split (R5 addendum) lives in {@link #orderFor}: a Probe told to
 * right-click a harvestable block gets MINE, everything else gets MOVE.
 */
public final class CommandInputResolver {
    /** GLFW modifier bits (see {@code net.minecraft.client.gui.screens.Screen}). */
    private static final int MOD_SHIFT = 0x0001;
    private static final int MOD_CONTROL = 0x0002;

    /** Brief green flash confirming a valid MINE order landed on a harvestable block. */
    private static final DustParticleOptions MINE_TARGET_FLASH = new DustParticleOptions(0x33FF33, 1.2f);

    private CommandInputResolver() {
    }

    public static void handle(CommandInputPacket packet, ServerPlayer player) {
        // Re-validate the Crystal is actually held — the packet is client-asserted.
        if (!(player.getMainHandItem().getItem() instanceof CursorItem)) {
            return;
        }
        ServerLevel level = (ServerLevel) player.level();
        Faction owner = ControlledFaction.of(player);
        PlayerSelection selection = CommandAttachments.selection(player);
        boolean ctrl = (packet.modifiers() & MOD_CONTROL) != 0;
        boolean shift = (packet.modifiers() & MOD_SHIFT) != 0;

        if (packet.button() == 0) {
            handleSelect(packet, level, selection, owner, ctrl, shift);
        } else if (packet.button() == 1) {
            handleOrder(packet, level, player, selection, owner);
        }
    }

    private static void handleSelect(CommandInputPacket packet, ServerLevel level, PlayerSelection selection,
                                     Faction owner, boolean ctrl, boolean shift) {
        Mob clicked = friendlyUnitAt(packet, level, owner);
        if (clicked == null) {
            // Left-click on ground/air/enemy with no modifiers clears the selection.
            if (!ctrl && !shift) {
                selection.clear(level);
            }
            return;
        }
        if (ctrl && shift) {
            selection.toggleAllOfType(level, clicked.getType(), clicked.position(), owner);
        } else if (ctrl) {
            selection.selectAllOfType(level, clicked.getType(), clicked.position(), owner);
        } else if (shift) {
            selection.toggle(level, clicked);
        } else {
            selection.setSingle(level, clicked);
        }
    }

    private static void handleOrder(CommandInputPacket packet, ServerLevel level, ServerPlayer player,
                                    PlayerSelection selection, Faction owner) {
        List<Mob> units = selection.pruneAndGet(level);
        if (units.isEmpty()) {
            return;
        }
        LivingEntity attackTarget = enemyTargetAt(packet, level, owner);
        boolean issued = false;
        for (Mob unit : units) {
            CommandOrder order = orderFor(unit, packet, level, attackTarget);
            if (order != null) {
                CommandAttachments.setOrder(unit, order);
                issued = true;
            }
        }
        if (issued) {
            level.playSound(null, player.blockPosition(), SoundEvents.NOTE_BLOCK_PLING.value(),
                    SoundSource.PLAYERS, 0.5f, 1.6f);
            player.swing(InteractionHand.MAIN_HAND, true);
        }
    }

    /**
     * The order a single unit should take from this right-click, or {@code null} if it should
     * ignore the click (e.g. a Probe told to attack).
     */
    private static CommandOrder orderFor(Mob unit, CommandInputPacket packet, ServerLevel level,
                                         LivingEntity attackTarget) {
        if (attackTarget != null) {
            // Probes have no attack; they sit the attack order out rather than march into it.
            if (unit instanceof ProbeEntity) {
                return null;
            }
            return CommandOrder.attack(attackTarget.getUUID());
        }
        if (packet.kind() == CommandInputPacket.HitKind.ENTITY) {
            // Right-clicked a non-enemy entity: just move to it.
            Entity target = level.getEntity(packet.entityId());
            return target != null ? CommandOrder.move(target.blockPosition()) : null;
        }
        BlockPos pos = packet.pos();
        if (unit instanceof ProbeEntity && level.getBlockState(pos).is(ProbeEntity.HARVESTABLE)) {
            level.sendParticles(MINE_TARGET_FLASH,
                    pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 12, 0.25, 0.25, 0.25, 0.0);
            return CommandOrder.mine(pos);
        }
        return CommandOrder.move(pos);
    }

    private static Mob friendlyUnitAt(CommandInputPacket packet, ServerLevel level, Faction owner) {
        if (packet.kind() != CommandInputPacket.HitKind.ENTITY) {
            return null;
        }
        if (level.getEntity(packet.entityId()) instanceof Mob unit
                && unit.isAlive() && FactionAttachments.get(unit) == owner) {
            return unit;
        }
        return null;
    }

    /**
     * A valid attack-order target: a living enemy-faction unit, or a vanilla hostile monster
     * (zombies, creepers, etc.) — those default to {@link Faction#NEUTRAL} but units should
     * still be orderable to fight them off, same as the Photon Cannon auto-targets them.
     */
    private static LivingEntity enemyTargetAt(CommandInputPacket packet, ServerLevel level, Faction owner) {
        if (packet.kind() != CommandInputPacket.HitKind.ENTITY) {
            return null;
        }
        if (level.getEntity(packet.entityId()) instanceof LivingEntity target && target.isAlive()
                && (owner.isEnemy(FactionAttachments.get(target)) || target instanceof Monster)) {
            return target;
        }
        return null;
    }
}
