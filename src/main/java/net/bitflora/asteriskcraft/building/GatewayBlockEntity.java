package net.bitflora.asteriskcraft.building;

import com.mojang.serialization.Codec;
import net.bitflora.asteriskcraft.AsteriskCraft;
import net.bitflora.asteriskcraft.faction.Faction;
import net.bitflora.asteriskcraft.stats.CostPayment;
import net.bitflora.asteriskcraft.stats.CostText;
import net.bitflora.asteriskcraft.stats.UnitStat;
import net.bitflora.asteriskcraft.stats.UnitStats;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

/**
 * Production logic for the Gateway: a Zealot/Dragoon/Scout queue, gated by a one-time
 * warp-in countdown after the kit places the structure. Costs are paid atomically out of the
 * shared Protoss army bank (surfaced through {@link ProductionMenu}).
 *
 * <p>Acts as a "linked chest" onto {@link ArmyBank#PROTOSS_BANK}: {@link NexusBlockEntity}
 * reads and writes the same underlying data, so every Protoss production building draws from
 * one shared pool. See {@link ArmyLinkedContainer}.
 *
 * <p>A {@link SiegeTarget} but deliberately not a {@link FactionCore}: it holds {@value #MAX_HEALTH}
 * HP behind {@value #SHIELD} shields in the shared {@link BuildingDefense} and an enemy army can
 * batter it down, but razing one only costs the player their unit production — the match is decided
 * by cores alone.
 */
public class GatewayBlockEntity extends BlockEntity
        implements ArmyLinkedContainer, ProductionBuilding, WarpInBuilding, SiegeTarget {
    public enum UnitType implements StringRepresentable {
        ZEALOT("zealot"), DRAGOON("dragoon"), SCOUT("scout");

        public static final Codec<UnitType> CODEC = StringRepresentable.fromEnum(UnitType::values);
        public static final Codec<List<UnitType>> LIST_CODEC = CODEC.listOf();

        private final String name;

        UnitType(String name) {
            this.name = name;
        }

        @Override
        public String getSerializedName() {
            return this.name;
        }
    }

    public static final int BUILD_TICKS = 200; // 10 seconds per unit
    public static final int MAX_QUEUE = 5;
    public static final int WARP_TICKS = 20 * 60; // 1 minute to warp in
    /** Tougher than a unit but well short of the Nexus: losing a Gateway costs production, not the game. */
    public static final int MAX_HEALTH = 250;
    public static final int SHIELD = 250;

    private final Deque<UnitType> queue = new ArrayDeque<>();
    private int buildTicksRemaining = BUILD_TICKS;
    private final BuildingDefense defense = new BuildingDefense(MAX_HEALTH, SHIELD, WARP_TICKS);
    private final UnderAttackAlert alert = new UnderAttackAlert();
    private Faction faction = Faction.PROTOSS;

    private final ContainerData dataAccess = new ContainerData() {
        @Override
        public int get(int index) {
            boolean building = !isWarping() && !queue.isEmpty();
            return switch (index) {
                case ProductionMenu.DATA_BUILDING_INDEX -> building ? queue.peek().ordinal() : -1;
                case ProductionMenu.DATA_BUILD_PROGRESS -> building ? BUILD_TICKS - buildTicksRemaining : 0;
                case ProductionMenu.DATA_BUILD_TOTAL -> BUILD_TICKS;
                case ProductionMenu.DATA_WARP -> defense.warpTicksRemaining();
                case ProductionMenu.DATA_QUEUE_BASE -> countQueued(UnitType.ZEALOT);
                case ProductionMenu.DATA_QUEUE_BASE + 1 -> countQueued(UnitType.DRAGOON);
                case ProductionMenu.DATA_QUEUE_BASE + 2 -> countQueued(UnitType.SCOUT);
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

    public GatewayBlockEntity(BlockPos pos, BlockState state) {
        super(AsteriskCraft.GATEWAY_BLOCK_ENTITY.get(), pos, state);
    }

    public void setFaction(Faction faction) {
        this.faction = faction;
        this.setChanged();
    }

    public boolean isWarping() {
        return this.defense.isWarping();
    }

    private int countQueued(UnitType type) {
        int count = 0;
        for (UnitType queued : this.queue) {
            if (queued == type) {
                count++;
            }
        }
        return count;
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, GatewayBlockEntity gateway) {
        // Ticked before the queue so shields recharge whether or not anything is in production.
        if (gateway.defense.tickWarpIn(level, pos)) {
            gateway.setChanged();
            return; // still warping in: no production yet
        }

        if (gateway.queue.isEmpty()) {
            return;
        }
        if (--gateway.buildTicksRemaining > 0) {
            gateway.setChanged();
            return;
        }
        UnitType type = gateway.queue.poll();
        gateway.buildTicksRemaining = BUILD_TICKS;
        gateway.setChanged();
        gateway.spawnUnit((ServerLevel) level, pos, type);
    }

    // --- SiegeTarget ---

    @Override
    public Faction buildingFaction() {
        return this.faction;
    }

    @Override
    public BuildingDefense defense() {
        return this.defense;
    }

    @Override
    public void damageBuilding(int amount, ServerLevel level, BlockPos pos) {
        this.defense.damage(amount, level, pos);
        this.setChanged();
        this.alert.ping(level, "message.asteriskcraft.gateway.under_attack");
    }

    // --- ProductionBuilding ---

    @Override
    public ProductionKind kind() {
        return ProductionKind.GATEWAY;
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
        UnitType type = switch (optionIndex) {
            case 0 -> UnitType.ZEALOT;
            case 1 -> UnitType.DRAGOON;
            case 2 -> UnitType.SCOUT;
            default -> null;
        };
        if (type != null) {
            tryQueueUnit(player, type);
        }
    }

    public void tryQueueUnit(Player player, UnitType type) {
        if (this.isWarping()) {
            overlay(player, Component.translatable("message.asteriskcraft.gateway.warping"));
            return;
        }
        if (this.queue.size() >= MAX_QUEUE) {
            overlay(player, Component.translatable("message.asteriskcraft.gateway.queue_full"));
            return;
        }
        if (!payCost(type)) {
            overlay(player, Component.translatable("message.asteriskcraft.gateway.cannot_afford",
                    Component.translatable("entity.asteriskcraft." + type.getSerializedName()),
                    CostText.costOnly(statFor(type).cost(), 0)));
            return;
        }
        this.queue.add(type);
        this.setChanged();
        if (this.level != null) {
            this.level.playSound(null, this.worldPosition, SoundEvents.BEACON_ACTIVATE, SoundSource.BLOCKS, 0.6f, 1.4f);
        }
        overlay(player, Component.translatable("message.asteriskcraft.gateway.queued", this.queue.size()));
    }

    private static void overlay(Player player, Component message) {
        if (player instanceof ServerPlayer serverPlayer) {
            serverPlayer.sendSystemMessage(message, true);
        }
    }

    private boolean payCost(UnitType type) {
        return CostPayment.payAny(this, statFor(type).cost());
    }

    private static UnitStat statFor(UnitType type) {
        return switch (type) {
            case ZEALOT -> UnitStats.ZEALOT;
            case DRAGOON -> UnitStats.DRAGOON;
            case SCOUT -> UnitStats.SCOUT;
        };
    }

    private void spawnUnit(ServerLevel level, BlockPos pos, UnitType type) {
        EntityType<? extends Mob> entityType = switch (type) {
            case ZEALOT -> AsteriskCraft.ZEALOT.get();
            case DRAGOON -> AsteriskCraft.DRAGOON.get();
            case SCOUT -> AsteriskCraft.SCOUT.get();
        };
        UnitSpawns.spawn(level, pos, entityType, this.faction, false);
    }

    // --- MenuProvider ---

    @Override
    public Component getDisplayName() {
        return Component.translatable("block.asteriskcraft.gateway_core");
    }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory playerInventory, Player player) {
        return new ProductionMenu(containerId, playerInventory, this, this.dataAccess,
                ContainerLevelAccess.create(this.level, this.worldPosition));
    }

    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        // Deliberately skip super: vanilla would drop+clear this Container's contents, but that
        // Container is the shared Protoss army bank (ArmyLinkedContainer) — one Gateway breaking
        // must not dump/clear resources the Nexus and other Gateways still depend on.
    }

    // --- ArmyLinkedContainer ---

    @Override
    public NonNullList<ItemStack> armyItems() {
        return ArmyBank.of(this.level, this.faction);
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
        output.putInt("BuildTicks", this.buildTicksRemaining);
        this.defense.save(output);
        output.store("Faction", Faction.CODEC, this.faction);
        output.store("Queue", UnitType.LIST_CODEC, List.copyOf(this.queue));
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        this.buildTicksRemaining = input.getIntOr("BuildTicks", BUILD_TICKS);
        this.defense.load(input);
        this.faction = input.read("Faction", Faction.CODEC).orElse(Faction.PROTOSS);
        this.queue.clear();
        this.queue.addAll(input.read("Queue", UnitType.LIST_CODEC).orElse(List.of()));
    }
}
