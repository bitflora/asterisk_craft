package net.bitflora.asteriskcraft.building;

import net.bitflora.asteriskcraft.AsteriskCraft;
import net.bitflora.asteriskcraft.entity.ProbeEntity;
import net.bitflora.asteriskcraft.entity.TeamColors;
import net.bitflora.asteriskcraft.faction.Faction;
import net.bitflora.asteriskcraft.faction.FactionAttachments;
import net.bitflora.asteriskcraft.game.GameOutcome;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BeaconBeamOwner;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Production logic for the Nexus: a small Probe queue paid for out of the Nexus's own
 * input slots (surfaced through {@link ProductionMenu}). Cost: 50 wood (any logs) OR
 * 50 cobblestone per Probe.
 */
public class NexusBlockEntity extends BlockEntity implements Container, ProductionBuilding, FactionCore, BeaconBeamOwner {
    public static final int PROBE_COST = 50;
    public static final int BUILD_TICKS = 200; // 10 seconds per probe
    public static final int MAX_QUEUE = 5;
    public static final int INPUT_SLOTS = 9;

    private final NonNullList<ItemStack> items = NonNullList.withSize(INPUT_SLOTS, ItemStack.EMPTY);
    private int queued = 0;
    private int buildTicksRemaining = BUILD_TICKS;
    private int coreHealth = FactionCore.CORE_MAX_HEALTH;
    /** Whether the Nexus currently has a clear path to the sky. Transient; null until the first tick evaluates it. */
    private Boolean skyLit = null;

    private final ContainerData dataAccess = new ContainerData() {
        @Override
        public int get(int index) {
            return switch (index) {
                case ProductionMenu.DATA_BUILDING_INDEX -> queued > 0 ? 0 : -1;
                case ProductionMenu.DATA_BUILD_PROGRESS -> queued > 0 ? BUILD_TICKS - buildTicksRemaining : 0;
                case ProductionMenu.DATA_BUILD_TOTAL -> BUILD_TICKS;
                case ProductionMenu.DATA_WARP -> 0;
                case ProductionMenu.DATA_QUEUE_BASE -> queued;
                default -> 0;
            };
        }

        @Override
        public void set(int index, int value) {
            // Server state is authoritative; the client mirror uses a SimpleContainerData.
        }

        @Override
        public int getCount() {
            return ProductionMenu.DATA_COUNT;
        }
    };

    public NexusBlockEntity(BlockPos pos, BlockState state) {
        super(AsteriskCraft.NEXUS_BLOCK_ENTITY.get(), pos, state);
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, NexusBlockEntity nexus) {
        if (!nexus.updateSky(level, pos)) {
            return; // dormant: no clear path to the sky, production is frozen
        }
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

    /**
     * Recomputes whether the Nexus can see the sky, plays an activate/deactivate cue on any
     * change, and cancels the production queue when it goes dormant. Returns the current lit state.
     */
    private boolean updateSky(Level level, BlockPos pos) {
        boolean lit = level.canSeeSky(pos.above());
        if (this.skyLit != null && this.skyLit != lit) {
            level.playSound(null, pos, lit ? SoundEvents.BEACON_ACTIVATE : SoundEvents.BEACON_DEACTIVATE,
                    SoundSource.BLOCKS, 1.0f, 1.0f);
            if (!lit && this.queued > 0) {
                this.queued = 0;
                this.buildTicksRemaining = BUILD_TICKS;
                this.setChanged();
            }
        }
        this.skyLit = lit;
        return lit;
    }

    // --- BeaconBeamOwner (client-side beam locator) ---

    @Override
    public List<BeaconBeamOwner.Section> getBeamSections() {
        if (this.level == null || !this.level.canSeeSky(this.worldPosition.above())) {
            return List.of();
        }
        return List.of(new BeaconBeamOwner.Section(TeamColors.factionColor(Faction.PROTOSS)));
    }

    // --- ProductionBuilding ---

    @Override
    public ProductionKind kind() {
        return ProductionKind.NEXUS;
    }

    @Override
    public Container inputContainer() {
        return this;
    }

    @Override
    public ContainerData dataAccess() {
        return this.dataAccess;
    }

    @Override
    public void trainOption(int optionIndex, Player player) {
        if (optionIndex != 0) {
            return;
        }
        if (this.skyLit == Boolean.FALSE) {
            overlay(player, Component.translatable("message.asteriskcraft.nexus.dormant"));
            return;
        }
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

    private boolean payProbeCost() {
        return ResourceBank.extract(this, stack -> stack.is(ItemTags.LOGS), PROBE_COST)
                || ResourceBank.extract(this, stack -> stack.is(Items.COBBLESTONE), PROBE_COST);
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

    // --- FactionCore ---

    @Override
    public Faction coreFaction() {
        return Faction.PROTOSS;
    }

    @Override
    public void damageCore(int amount, ServerLevel level, BlockPos pos) {
        this.coreHealth = FactionCore.applyDamage(this.coreHealth, amount, level, pos);
        this.setChanged();
    }

    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        super.preRemoveSideEffects(pos, state); // keeps vanilla drop-contents for the input slots
        GameOutcome.onCoreDestroyed(this.level, pos);
    }

    // --- MenuProvider ---

    @Override
    public Component getDisplayName() {
        return Component.translatable("block.asteriskcraft.nexus_core");
    }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory playerInventory, Player player) {
        return new ProductionMenu(containerId, playerInventory, this, this.dataAccess,
                ContainerLevelAccess.create(this.level, this.worldPosition));
    }

    // --- Container ---

    @Override
    public int getContainerSize() {
        return this.items.size();
    }

    @Override
    public boolean isEmpty() {
        return this.items.stream().allMatch(ItemStack::isEmpty);
    }

    @Override
    public ItemStack getItem(int slot) {
        return this.items.get(slot);
    }

    @Override
    public ItemStack removeItem(int slot, int amount) {
        ItemStack removed = ContainerHelper.removeItem(this.items, slot, amount);
        if (!removed.isEmpty()) {
            this.setChanged();
        }
        return removed;
    }

    @Override
    public ItemStack removeItemNoUpdate(int slot) {
        return ContainerHelper.takeItem(this.items, slot);
    }

    @Override
    public void setItem(int slot, ItemStack stack) {
        this.items.set(slot, stack);
        stack.limitSize(this.getMaxStackSize(stack));
        this.setChanged();
    }

    @Override
    public boolean stillValid(Player player) {
        return Container.stillValidBlockEntity(this, player);
    }

    @Override
    public void clearContent() {
        this.items.clear();
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.putInt("Queued", this.queued);
        output.putInt("BuildTicks", this.buildTicksRemaining);
        output.putInt("CoreHealth", this.coreHealth);
        ContainerHelper.saveAllItems(output, this.items);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        this.queued = input.getIntOr("Queued", 0);
        this.buildTicksRemaining = input.getIntOr("BuildTicks", BUILD_TICKS);
        this.coreHealth = input.getIntOr("CoreHealth", FactionCore.CORE_MAX_HEALTH);
        this.items.clear();
        ContainerHelper.loadAllItems(input, this.items);
    }
}
