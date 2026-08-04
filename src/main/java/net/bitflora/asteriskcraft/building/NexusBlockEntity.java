package net.bitflora.asteriskcraft.building;

import net.bitflora.asteriskcraft.AsteriskCraft;
import net.bitflora.asteriskcraft.entity.protoss.ProbeEntity;
import net.bitflora.asteriskcraft.entity.TeamColors;
import net.bitflora.asteriskcraft.faction.Faction;
import net.bitflora.asteriskcraft.game.GameOutcome;
import net.bitflora.asteriskcraft.stats.CostPayment;
import net.bitflora.asteriskcraft.stats.Resource;
import net.bitflora.asteriskcraft.stats.UnitStats;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
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
 * 50 cobblestone per Probe. Same either/or split applies to the Gateway and Photon Cannon
 * kits: the player picks a Wood or a Stone button per unit (see {@link #trainOption}) —
 * there is deliberately no button that pays with a mix of both. The Nexus Kit (an
 * expansion Nexus) is the one exception: 400 cobblestone only, no wood alternative.
 *
 * <p>Acts as a "linked chest" onto {@link ArmyBank#PROTOSS_BANK}: {@link GatewayBlockEntity}
 * reads and writes the same underlying data, so every Protoss production building draws from
 * one shared pool. See {@link ArmyLinkedContainer}.
 *
 * <p>Its staying power — {@value #MAX_HEALTH} HP behind {@value #SHIELD} shields, and the
 * {@value #WARP_TICKS}-tick warp-in an expansion Nexus spends half-built — lives in the shared
 * {@link BuildingDefense}. The starting Nexus skips that warp entirely ({@link #skipWarpIn}).
 */
public class NexusBlockEntity extends BlockEntity implements ArmyLinkedContainer, ProductionBuilding, FactionCore, BeaconBeamOwner {
    /** All-wood or all-cobblestone (player's choice) to warp out a building kit — Gateway or Photon Cannon. */
    public static final int BUILDING_COST = 150;
    /** Cobblestone-only cost for a second/expansion Nexus — deliberately pricier and not wood-alternative. */
    public static final int NEXUS_KIT_COST = 400;
    /** A Nexus only ever builds Probes, so its one build time comes straight off the balance table. */
    public static final int BUILD_TICKS = UnitStats.PROBE.buildTicks();
    public static final int MAX_QUEUE = 5;
    public static final int INPUT_SLOTS = ArmyBank.PROTOSS_SLOTS;
    /** The sturdiest building in the mod, as the thing you lose the game with should be. */
    public static final int MAX_HEALTH = 325;
    public static final int SHIELD = 325;
    public static final int WARP_TICKS = 20 * 120; // 2 minutes — an expansion Nexus is a long commitment

    private int queued = 0;
    private int buildTicksRemaining = BUILD_TICKS;
    private final BuildingDefense defense = new BuildingDefense(MAX_HEALTH, SHIELD, WARP_TICKS);
    private final UnderAttackAlert alert = new UnderAttackAlert();
    /** Whether this Nexus has enrolled in the {@link CoreCensus} since loading. Not saved — the census is. */
    private boolean enrolled = false;
    /** Tracks whether the Nexus can see the sky (dormant when buried); cancels the queue on going dark. */
    private final SkyGate skyGate = new SkyGate();

    private final ContainerData dataAccess = new ContainerData() {
        @Override
        public int get(int index) {
            return switch (index) {
                case ProductionMenu.DATA_BUILDING_INDEX -> queued > 0 ? 0 : -1;
                case ProductionMenu.DATA_BUILD_PROGRESS -> queued > 0 ? BUILD_TICKS - buildTicksRemaining : 0;
                case ProductionMenu.DATA_BUILD_TOTAL -> BUILD_TICKS;
                case ProductionMenu.DATA_WARP -> defense.warpTicksRemaining();
                case ProductionMenu.DATA_QUEUE_BASE, ProductionMenu.DATA_QUEUE_BASE + 1 -> queued;
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
        if (!nexus.enrolled) {
            CoreCensus.ensureRegistered(level, nexus.buildingFaction(), pos);
            nexus.enrolled = true;
        }
        // Ticked ahead of the sky/queue gates so shields recharge even while the Nexus is buried.
        if (nexus.defense.tickWarpIn(level, pos)) {
            nexus.setChanged();
            return; // still warping in: no production yet
        }
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
        if (this.defense.isWarping()) {
            overlay(player, Component.translatable("message.asteriskcraft.nexus.warping"));
            return;
        }
        if (this.skyGate.lit() == Boolean.FALSE) {
            overlay(player, Component.translatable("message.asteriskcraft.nexus.dormant"));
            return;
        }
        switch (optionIndex) {
            // Wood is alternative 0, Stone is alternative 1 in UnitStats.PROBE.cost() — pinned by
            // stats.UnitStatsTest.probeCostIsWoodThenStoneInThatOrder.
            case 0 -> trainProbe(player, 0);
            case 1 -> trainProbe(player, 1);
            case 2 -> warpInKit(player, AsteriskCraft.GATEWAY_KIT.get(), Resource.WOOD, BUILDING_COST,
                    "message.asteriskcraft.nexus.gateway_ready");
            case 3 -> warpInKit(player, AsteriskCraft.GATEWAY_KIT.get(), Resource.STONE, BUILDING_COST,
                    "message.asteriskcraft.nexus.gateway_ready");
            case 4 -> warpInKit(player, AsteriskCraft.PHOTON_CANNON_KIT.get(), Resource.WOOD, BUILDING_COST,
                    "message.asteriskcraft.nexus.photon_cannon_ready");
            case 5 -> warpInKit(player, AsteriskCraft.PHOTON_CANNON_KIT.get(), Resource.STONE, BUILDING_COST,
                    "message.asteriskcraft.nexus.photon_cannon_ready");
            case 6 -> warpInKit(player, AsteriskCraft.NEXUS_KIT.get(), Resource.STONE, NEXUS_KIT_COST,
                    "message.asteriskcraft.nexus.nexus_kit_ready");
            default -> {
            }
        }
    }

    private void trainProbe(Player player, int alternative) {
        if (this.queued >= MAX_QUEUE) {
            overlay(player, Component.translatable("message.asteriskcraft.nexus.queue_full"));
            return;
        }
        if (!CostPayment.pay(this, UnitStats.PROBE.cost(), alternative)) {
            Resource resource = UnitStats.PROBE.cost().alternatives().get(alternative).get(0).resource();
            int amount = UnitStats.PROBE.cost().amountOf(alternative, resource);
            overlay(player, Component.translatable("message.asteriskcraft.nexus.cannot_afford", amount, resourceName(resource)));
            return;
        }
        this.queued++;
        this.setChanged();
        if (this.level != null) {
            this.level.playSound(null, this.worldPosition, SoundEvents.BEACON_ACTIVATE, SoundSource.BLOCKS, 0.6f, 1.4f);
        }
        overlay(player, Component.translatable("message.asteriskcraft.nexus.queued", this.queued));
    }

    /** Warp-in kits are instant: pay the chosen resource's share and hand the player the building kit item. */
    private void warpInKit(Player player, Item kit, Resource resource, int cost, String readyKey) {
        if (!ResourceBank.extract(this, resource.matches(), cost)) {
            overlay(player, Component.translatable("message.asteriskcraft.nexus.cannot_afford_building", cost, resourceName(resource)));
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

    /** Translatable player-facing name for a resource, keyed by its serialized name (see {@link Resource}). */
    private static Component resourceName(Resource resource) {
        return Component.translatable("gui.asteriskcraft.resource." + resource.getSerializedName());
    }

    private void spawnProbe(ServerLevel level, BlockPos pos) {
        ProbeEntity probe = UnitSpawns.spawn(level, pos, AsteriskCraft.PROBE.get(), Faction.PROTOSS, false);
        if (probe != null) {
            probe.setHomePos(pos);
        }
    }

    // --- SiegeTarget / FactionCore ---

    @Override
    public Faction buildingFaction() {
        return Faction.PROTOSS;
    }

    @Override
    public BuildingDefense defense() {
        return this.defense;
    }

    @Override
    public void damageBuilding(int amount, ServerLevel level, BlockPos pos) {
        this.defense.damage(amount, level, pos);
        this.setChanged();
        this.alert.ping(level, "message.asteriskcraft.under_attack");
    }

    /**
     * Stands the Nexus up fully warped in — for the starting one, which the world begins with rather
     * than warping in from a kit (see {@code GameBootstrap}).
     */
    public void skipWarpIn() {
        this.defense.skipWarpIn();
        this.setChanged();
    }

    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        // Deliberately skip super: vanilla would drop+clear this Container's contents, but that
        // Container is the shared Protoss army bank (ArmyLinkedContainer) — the Nexus breaking
        // must not dump/clear resources Gateways still depend on. CoreSpoils knocks a measured
        // share of that pool loose instead.
        if (this.level != null) {
            this.defense.collapseScaffold(this.level);
        }
        CoreSpoils.spill(this.level, this.buildingFaction(), pos, this);
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
        return ArmyBank.of(this.level, this.buildingFaction());
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
        this.defense.save(output);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        this.queued = input.getIntOr("Queued", 0);
        this.buildTicksRemaining = input.getIntOr("BuildTicks", BUILD_TICKS);
        this.defense.load(input);
    }
}
