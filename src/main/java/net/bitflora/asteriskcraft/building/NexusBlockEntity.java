package net.bitflora.asteriskcraft.building;

import net.bitflora.asteriskcraft.AsteriskCraft;
import net.bitflora.asteriskcraft.entity.ProbeEntity;
import net.bitflora.asteriskcraft.faction.Faction;
import net.bitflora.asteriskcraft.faction.FactionAttachments;
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
        BlockPos spawnPos = SpawnSpots.findGroundSpot(level, pos);
        probe.snapTo(spawnPos.getX() + 0.5, spawnPos.getY(), spawnPos.getZ() + 0.5, level.getRandom().nextFloat() * 360f, 0f);
        probe.setHomePos(pos);
        FactionAttachments.set(probe, Faction.PROTOSS);
        level.addFreshEntity(probe);
        level.playSound(null, spawnPos, SoundEvents.PLAYER_TELEPORT, SoundSource.BLOCKS, 0.8f, 1.6f);
    }

    private boolean payProbeCost() {
        if (!(this.level instanceof ServerLevel serverLevel)) {
            return false;
        }
        return ResourceBank.extract(serverLevel, this.worldPosition, CHEST_SCAN_RADIUS, stack -> stack.is(ItemTags.LOGS), PROBE_COST)
                || ResourceBank.extract(serverLevel, this.worldPosition, CHEST_SCAN_RADIUS, stack -> stack.is(Items.COBBLESTONE), PROBE_COST);
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
