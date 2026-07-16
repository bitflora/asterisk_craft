package com.timja.asteriskcraft.building;

import com.timja.asteriskcraft.AsteriskCraft;
import com.timja.asteriskcraft.entity.ProbeEntity;
import com.timja.asteriskcraft.faction.Faction;
import com.timja.asteriskcraft.faction.FactionAttachments;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/**
 * Production logic for the Nexus: a small Probe queue paid for out of nearby chests.
 * Cost: 50 wood (any logs) OR 50 cobblestone per Probe.
 */
public class NexusBlockEntity extends BlockEntity {
    public static final int PROBE_COST = 50;
    public static final int BUILD_TICKS = 200; // 10 seconds per probe
    public static final int MAX_QUEUE = 5;
    private static final int CHEST_SCAN_RADIUS = 6;

    private int queued = 0;
    private int buildTicksRemaining = BUILD_TICKS;

    public NexusBlockEntity(BlockPos pos, BlockState state) {
        super(AsteriskCraft.NEXUS_BLOCK_ENTITY.get(), pos, state);
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, NexusBlockEntity nexus) {
        if (nexus.queued <= 0) {
            return;
        }
        if (--nexus.buildTicksRemaining > 0) {
            nexus.setChanged();
            return;
        }
        nexus.queued--;
        nexus.buildTicksRemaining = BUILD_TICKS;
        nexus.setChanged();
        nexus.spawnProbe((ServerLevel) level, pos);
    }

    public void tryQueueProbe(Player player) {
        if (this.queued >= MAX_QUEUE) {
            overlay(player, Component.translatable("message.asteriskcraft.nexus.queue_full"));
            return;
        }
        if (!payProbeCost()) {
            overlay(player, Component.translatable("message.asteriskcraft.nexus.cannot_afford", PROBE_COST));
            return;
        }
        this.queued++;
        this.setChanged();
        if (this.level != null) {
            this.level.playSound(null, this.worldPosition, SoundEvents.BEACON_ACTIVATE, SoundSource.BLOCKS, 0.6f, 1.4f);
        }
        overlay(player, Component.translatable("message.asteriskcraft.nexus.queued", this.queued));
    }

    private static void overlay(Player player, Component message) {
        if (player instanceof ServerPlayer serverPlayer) {
            serverPlayer.sendSystemMessage(message, true);
        }
    }

    private void spawnProbe(ServerLevel level, BlockPos pos) {
        ProbeEntity probe = AsteriskCraft.PROBE.get().create(level, EntitySpawnReason.TRIGGERED);
        if (probe == null) {
            return;
        }
        BlockPos spawnPos = findSpawnSpot(level, pos);
        probe.snapTo(spawnPos.getX() + 0.5, spawnPos.getY(), spawnPos.getZ() + 0.5, level.getRandom().nextFloat() * 360f, 0f);
        probe.setHomePos(pos);
        FactionAttachments.set(probe, Faction.PROTOSS);
        level.addFreshEntity(probe);
        level.playSound(null, spawnPos, SoundEvents.PLAYER_TELEPORT, SoundSource.BLOCKS, 0.8f, 1.6f);
    }

    private BlockPos findSpawnSpot(ServerLevel level, BlockPos pos) {
        for (int r = 2; r <= 4; r++) {
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != r) {
                        continue;
                    }
                    for (int dy = -3; dy <= 1; dy++) {
                        BlockPos candidate = pos.offset(dx, dy, dz);
                        if (level.getBlockState(candidate).isAir()
                                && level.getBlockState(candidate.above()).isAir()
                                && level.getBlockState(candidate.below()).isSolidRender()) {
                            return candidate;
                        }
                    }
                }
            }
        }
        return pos.above();
    }

    private boolean payProbeCost() {
        return extractResource(stack -> stack.is(ItemTags.LOGS), PROBE_COST)
                || extractResource(stack -> stack.is(Items.COBBLESTONE), PROBE_COST);
    }

    /**
     * Counts matching items across all container handlers near the Nexus; if at least
     * {@code amount} are present, extracts exactly that many (in one transaction) and
     * returns true. Uses NeoForge 26.1's transactional ResourceHandler API.
     */
    private boolean extractResource(Predicate<ItemStack> matches, int amount) {
        if (!(this.level instanceof ServerLevel serverLevel)) {
            return false;
        }
        List<ResourceHandler<ItemResource>> handlers = new ArrayList<>();
        int available = 0;
        for (BlockPos scanPos : BlockPos.betweenClosed(
                this.worldPosition.offset(-CHEST_SCAN_RADIUS, -3, -CHEST_SCAN_RADIUS),
                this.worldPosition.offset(CHEST_SCAN_RADIUS, 3, CHEST_SCAN_RADIUS))) {
            ResourceHandler<ItemResource> handler = serverLevel.getCapability(Capabilities.Item.BLOCK, scanPos.immutable(), null);
            if (handler == null) {
                continue;
            }
            handlers.add(handler);
            for (int i = 0; i < handler.size(); i++) {
                ItemResource res = handler.getResource(i);
                if (!res.isEmpty() && matches.test(res.toStack(1))) {
                    available += handler.getAmountAsInt(i);
                }
            }
        }
        if (available < amount) {
            return false;
        }
        try (Transaction tx = Transaction.openRoot()) {
            int remaining = amount;
            for (ResourceHandler<ItemResource> handler : handlers) {
                for (int i = 0; i < handler.size() && remaining > 0; i++) {
                    ItemResource res = handler.getResource(i);
                    if (!res.isEmpty() && matches.test(res.toStack(1))) {
                        remaining -= handler.extract(i, res, remaining, tx);
                    }
                }
            }
            if (remaining == 0) {
                tx.commit();
                return true;
            }
        }
        return false;
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.putInt("Queued", this.queued);
        output.putInt("BuildTicks", this.buildTicksRemaining);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        this.queued = input.getIntOr("Queued", 0);
        this.buildTicksRemaining = input.getIntOr("BuildTicks", BUILD_TICKS);
    }
}
