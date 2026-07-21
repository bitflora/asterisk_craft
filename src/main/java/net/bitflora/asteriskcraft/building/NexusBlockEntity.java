package net.bitflora.asteriskcraft.building;

import net.bitflora.asteriskcraft.AsteriskCraft;
import net.bitflora.asteriskcraft.entity.protoss.ProbeEntity;
import net.bitflora.asteriskcraft.entity.TeamColors;
import net.bitflora.asteriskcraft.faction.Faction;
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
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.item.Item;
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
 * Production logic for the Nexus: a small Probe queue paid for out of the shared Protoss
 * army bank (surfaced through {@link ProductionMenu}). Cost: 50 wood (any logs) OR
 * 50 cobblestone per Probe.
 *
 * <p>Acts as a "linked chest" onto {@link ArmyBank#PROTOSS_BANK}: {@link GatewayBlockEntity}
 * reads and writes the same underlying data, so every Protoss production building draws from
 * one shared pool. See {@link ArmyLinkedContainer}.
 */
public class NexusBlockEntity extends BlockEntity implements ArmyLinkedContainer, ProductionBuilding, FactionCore, BeaconBeamOwner {
    public static final int PROBE_COST = 50;
    /** Wood/cobblestone (any mix) to warp out a building kit — Gateway or Photon Cannon. */
    public static final int BUILDING_COST = 150;
    public static final int BUILD_TICKS = 200; // 10 seconds per probe
    public static final int MAX_QUEUE = 5;
    public static final int INPUT_SLOTS = ArmyBank.PROTOSS_SLOTS;

    private int queued = 0;
    private int buildTicksRemaining = BUILD_TICKS;
    private int coreHealth = FactionCore.CORE_MAX_HEALTH;
    /** Game time of the next allowed "under attack" alert. Deliberately not saved: the cooldown resets on reload. */
    private long nextAlertTime = 0L;
    /** Tracks whether the Nexus can see the sky (dormant when buried); cancels the queue on going dark. */
    private final SkyGate skyGate = new SkyGate();

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
        if (!nexus.skyGate.update(level, pos, nexus::cancelQueueOnDormant)) {
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

    /** Clears any in-progress Probe production when the Nexus goes dormant (loses its sky). */
    private void cancelQueueOnDormant() {
        if (this.queued > 0) {
            this.queued = 0;
            this.buildTicksRemaining = BUILD_TICKS;
            this.setChanged();
        }
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
        if (this.skyGate.lit() == Boolean.FALSE) {
            overlay(player, Component.translatable("message.asteriskcraft.nexus.dormant"));
            return;
        }
        switch (optionIndex) {
            case 0 -> trainProbe(player);
            case 1 -> warpInKit(player, AsteriskCraft.GATEWAY_KIT.get(),
                    "message.asteriskcraft.nexus.gateway_ready");
            case 2 -> warpInKit(player, AsteriskCraft.PHOTON_CANNON_KIT.get(),
                    "message.asteriskcraft.nexus.photon_cannon_ready");
            default -> {
            }
        }
    }

    private void trainProbe(Player player) {
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

    /** Warp-in kits are instant: pay the shared cost and hand the player the building kit item. */
    private void warpInKit(Player player, Item kit, String readyKey) {
        if (!payBuildingCost()) {
            overlay(player, Component.translatable("message.asteriskcraft.nexus.cannot_afford_building", BUILDING_COST));
            return;
        }
        giveOrDrop(player, new ItemStack(kit));
        if (this.level != null) {
            this.level.playSound(null, this.worldPosition, SoundEvents.BEACON_ACTIVATE, SoundSource.BLOCKS, 0.6f, 1.0f);
        }
        overlay(player, Component.translatable(readyKey));
    }

    private static void giveOrDrop(Player player, ItemStack stack) {
        if (!player.getInventory().add(stack)) {
            player.drop(stack, false);
        }
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

    /** Any mix of wood and cobblestone that together totals {@link #BUILDING_COST}. */
    private boolean payBuildingCost() {
        return ResourceBank.extract(this,
                stack -> stack.is(ItemTags.LOGS) || stack.is(Items.COBBLESTONE), BUILDING_COST);
    }

    private void spawnProbe(ServerLevel level, BlockPos pos) {
        ProbeEntity probe = UnitSpawns.spawn(level, pos, AsteriskCraft.PROBE.get(), Faction.PROTOSS, false);
        if (probe != null) {
            probe.setHomePos(pos);
        }
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
        notifyUnderAttack(level);
    }

    /** "Nexus under attack" alert, throttled to once per {@link #ALERT_COOLDOWN_TICKS} so repeated hits don't spam it. */
    private static final int ALERT_COOLDOWN_TICKS = 20 * 30;

    private void notifyUnderAttack(ServerLevel level) {
        long now = level.getGameTime();
        if (now < this.nextAlertTime) {
            return;
        }
        this.nextAlertTime = now + ALERT_COOLDOWN_TICKS;
        for (ServerPlayer player : level.getServer().getPlayerList().getPlayers()) {
            player.sendSystemMessage(Component.translatable("message.asteriskcraft.under_attack"));
            level.playSound(null, player.blockPosition(), SoundEvents.WARDEN_NEARBY_CLOSEST, SoundSource.HOSTILE, 0.7f, 1.2f);
        }
    }

    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        // Deliberately skip super: vanilla would drop+clear this Container's contents, but that
        // Container is the shared Protoss army bank (ArmyLinkedContainer) — the Nexus breaking
        // must not dump/clear resources Gateways still depend on.
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

    // --- ArmyLinkedContainer ---

    @Override
    public NonNullList<ItemStack> armyItems() {
        return ArmyBank.of(this.level, this.coreFaction());
    }

    @Override
    public void markArmyBankChanged() {
        this.setChanged();
    }

    @Override
    public boolean stillValid(Player player) {
        return Container.stillValidBlockEntity(this, player);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.putInt("Queued", this.queued);
        output.putInt("BuildTicks", this.buildTicksRemaining);
        output.putInt("CoreHealth", this.coreHealth);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        this.queued = input.getIntOr("Queued", 0);
        this.buildTicksRemaining = input.getIntOr("BuildTicks", BUILD_TICKS);
        this.coreHealth = input.getIntOr("CoreHealth", FactionCore.CORE_MAX_HEALTH);
    }
}
