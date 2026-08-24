package net.bitflora.asteriskcraft.command;

import net.bitflora.asteriskcraft.entity.WorkerEntity;
import net.bitflora.asteriskcraft.entity.terran.BunkerEntity;
import net.bitflora.asteriskcraft.faction.Faction;
import net.bitflora.asteriskcraft.faction.FactionAttachments;
import net.bitflora.asteriskcraft.faction.WildKind;
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

import java.util.ArrayList;
import java.util.List;

/**
 * Server-side interpretation of a {@link CommandInputPacket}. Left-click mutates the player's
 * {@link PlayerSelection}; right-click stamps a {@link CommandOrder} onto every selected unit.
 * The Probe MINE-vs-MOVE split (R5 addendum) lives in {@link #issueMove}: a Probe told to
 * right-click a harvestable block gets MINE, everything else gets MOVE.
 * <p>
 * Move orders are spread through {@link MoveFormation} rather than stamping one shared destination
 * on the whole selection — see that class for why a squad sent to a single block used to jam.
 */
public final class CommandInputResolver {
    /** GLFW modifier bits (see {@code net.minecraft.client.gui.screens.Screen}). */
    private static final int MOD_SHIFT = 0x0001;
    private static final int MOD_CONTROL = 0x0002;

    /** Brief flash confirming a MOVE order landed. */
    private static final DustParticleOptions MOVE_TARGET_FLASH = new DustParticleOptions(0x33FF33, 1.2f);
    /** Brief flash confirming an ATTACK order landed. */
    private static final DustParticleOptions ATTACK_TARGET_FLASH = new DustParticleOptions(0xFF3333, 1.2f);
    /** Brief flash confirming a valid MINE order landed on a harvestable block. */
    private static final DustParticleOptions MINE_TARGET_FLASH = new DustParticleOptions(0xFFFF33, 1.2f);
    /** Brief flash confirming a LOAD order landed on a friendly transport. */
    private static final DustParticleOptions LOAD_TARGET_FLASH = new DustParticleOptions(0x33CCFF, 1.2f);

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
            // A miss (empty air/block/enemy/non-friendly) leaves the current selection untouched.
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
        BunkerEntity transport = friendlyTransportAt(packet, level, owner);
        LivingEntity attackTarget = transport == null ? enemyTargetAt(packet, level, player, owner) : null;
        boolean issued;
        if (transport != null) {
            issued = issueLoad(units, transport, level);
        } else if (attackTarget != null) {
            issued = issueAttack(units, attackTarget, level);
        } else {
            issued = issueMove(units, packet, level);
        }
        if (issued) {
            level.playSound(null, player.blockPosition(), SoundEvents.NOTE_BLOCK_PLING.value(),
                    SoundSource.PLAYERS, 0.5f, 1.6f);
            player.swing(InteractionHand.MAIN_HAND, true);
        }
    }

    /**
     * Get in: every selected unit the transport would actually take is sent to climb inside it, and
     * the rest of the selection is left alone rather than being marched at a door it can't use.
     *
     * <p>Deliberately not spread through {@link MoveFormation}: there is one destination and it is an
     * entity, so a ring around it would only put units further from the thing they are walking to.
     */
    private static boolean issueLoad(List<Mob> units, BunkerEntity transport, ServerLevel level) {
        boolean issued = false;
        for (Mob unit : units) {
            if (!transport.boardable(unit)) {
                continue;
            }
            CommandAttachments.setOrder(unit, CommandOrder.load(transport.getUUID()));
            issued = true;
        }
        if (issued) {
            flash(level, transport.blockPosition(), LOAD_TARGET_FLASH);
        }
        return issued;
    }

    /** Focus-fire: everything that can shoot converges on one target, so there is nothing to spread. */
    private static boolean issueAttack(List<Mob> units, LivingEntity attackTarget, ServerLevel level) {
        boolean issued = false;
        for (Mob unit : unloadTransports(units)) {
            // Probes have no attack; they sit the attack order out rather than march into it.
            if (unit instanceof WorkerEntity) {
                continue;
            }
            CommandAttachments.setOrder(unit, CommandOrder.attack(attackTarget.getUUID()));
            issued = true;
        }
        if (issued) {
            flash(level, attackTarget.blockPosition(), ATTACK_TARGET_FLASH);
        }
        return issued;
    }

    /**
     * Move (and the Probe's MINE special case). Every unit that ends up marching gets its own
     * {@link MoveFormation} slot around the clicked point rather than the point itself — including
     * on the "right-clicked a friendly unit" path, which shares the destination just as readily.
     */
    private static boolean issueMove(List<Mob> units, CommandInputPacket packet, ServerLevel level) {
        BlockPos centre = moveCentre(packet, level);
        if (centre == null) {
            return false;
        }
        boolean atBlock = packet.kind() != CommandInputPacket.HitKind.ENTITY;
        boolean harvestable = atBlock && level.getBlockState(packet.pos()).is(WorkerEntity.HARVESTABLE);
        List<Mob> movers = new ArrayList<>(units.size());
        boolean mining = false;
        for (Mob unit : unloadTransports(units)) {
            if (harvestable && unit instanceof WorkerEntity) {
                // The block itself is the order — a mining Probe is not spread into the formation.
                CommandAttachments.setOrder(unit, CommandOrder.mine(packet.pos()));
                mining = true;
            } else {
                movers.add(unit);
            }
        }
        if (mining) {
            flash(level, packet.pos(), MINE_TARGET_FLASH);
        }
        if (!movers.isEmpty()) {
            MoveFormation.allocate(level, centre, movers)
                    .forEach((unit, slot) -> CommandAttachments.setOrder(unit, CommandOrder.move(slot)));
            // One burst at the point that was clicked, not one per unit: the player asked for a
            // single destination and a 24-unit selection should not fire 24 particle packets.
            flash(level, centre, MOVE_TARGET_FLASH);
        }
        return mining || !movers.isEmpty();
    }

    /**
     * Turns out any transport in the selection and hands back the selection with each transport
     * replaced by the units that were inside it.
     *
     * <p><b>A move or attack order given to a Bunker unloads it</b> — that is the rule, rather than a
     * special case for one of the two. A Bunker cannot march and cannot shoot, so an order of either
     * kind aimed at it can only ever have been meant for its garrison; the alternative is a
     * right-click that visibly does nothing. The ejected units then take their formation slots or
     * their attack order exactly like any other squad, which is why this returns a list rather than
     * issuing anything itself.
     *
     * <p>A <em>load</em> order is the exception and never reaches here: right-clicking one Bunker
     * while another is selected would be a transfer, which is a feature rather than a natural reading
     * of the click, so it does nothing at all.
     */
    private static List<Mob> unloadTransports(List<Mob> units) {
        if (units.stream().noneMatch(BunkerEntity.class::isInstance)) {
            return units; // the overwhelmingly common case, and no list to build
        }
        List<Mob> resolved = new ArrayList<>(units.size());
        for (Mob unit : units) {
            if (unit instanceof BunkerEntity transport) {
                resolved.addAll(transport.unload());
            } else {
                resolved.add(unit);
            }
        }
        return resolved;
    }

    /** The point a move order is centred on: a right-clicked entity's block, else the clicked block. */
    private static BlockPos moveCentre(CommandInputPacket packet, ServerLevel level) {
        if (packet.kind() != CommandInputPacket.HitKind.ENTITY) {
            return packet.pos();
        }
        Entity target = level.getEntity(packet.entityId());
        return target == null ? null : target.blockPosition();
    }

    private static void flash(ServerLevel level, BlockPos pos, DustParticleOptions particle) {
        level.sendParticles(particle,
                pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 12, 0.25, 0.25, 0.25, 0.0);
    }

    private static Mob friendlyUnitAt(CommandInputPacket packet, ServerLevel level, Faction owner) {
        if (packet.kind() != CommandInputPacket.HitKind.ENTITY) {
            return null;
        }
        // A garrisoned unit is skipped rather than returned: its hitbox is clipped up to the
        // transport's ride position and overlaps it, so a click meant for the Bunker could otherwise
        // land on whatever is inside it. Nothing sheltered is selectable — see PlayerSelection.
        if (level.getEntity(packet.entityId()) instanceof Mob unit
                && unit.isAlive() && !unit.isPassenger() && FactionAttachments.get(unit) == owner) {
            return unit;
        }
        return null;
    }

    /**
     * A friendly transport under the crosshair — the thing that turns a right-click into "get in"
     * rather than "walk over there". Only the owner's own: right-clicking an <em>enemy</em> Bunker
     * has to keep meaning attack, which is why this is checked before {@link #enemyTargetAt} rather
     * than instead of it.
     */
    private static BunkerEntity friendlyTransportAt(CommandInputPacket packet, ServerLevel level, Faction owner) {
        if (packet.kind() != CommandInputPacket.HitKind.ENTITY) {
            return null;
        }
        if (level.getEntity(packet.entityId()) instanceof BunkerEntity transport
                && transport.isAlive() && FactionAttachments.get(transport) == owner) {
            return transport;
        }
        return null;
    }

    /**
     * A valid attack-order target: a living enemy-faction unit, or whatever part of the neutral
     * world the owner's race hunts (wild hostiles for the Protoss, villagers and golems for the
     * Zerg) — those default to {@link Faction#NEUTRAL} but units should still be orderable onto
     * them, same as the Photon Cannon auto-targets what it may. Both cases
     * are exactly {@link FactionAttachments#isHostile}, so ask it rather than restating the rule
     * here; this used to carry its own {@code instanceof Monster} copy and so silently refused
     * attack orders on every hostile that isn't a {@code Monster} subclass.
     */
    private static LivingEntity enemyTargetAt(CommandInputPacket packet, ServerLevel level,
            ServerPlayer player, Faction owner) {
        if (packet.kind() != CommandInputPacket.HitKind.ENTITY) {
            return null;
        }
        if (level.getEntity(packet.entityId()) instanceof LivingEntity target && target.isAlive()
                && FactionAttachments.isHostile(owner, ControlledRace.of(player),
                        FactionAttachments.get(target), WildKind.of(target),
                        FactionAttachments.isCommanded(level, owner))) {
            return target;
        }
        return null;
    }
}
