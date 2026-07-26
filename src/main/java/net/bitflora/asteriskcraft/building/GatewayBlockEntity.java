package net.bitflora.asteriskcraft.building;

import com.mojang.serialization.Codec;
import net.bitflora.asteriskcraft.AsteriskCraft;
import net.bitflora.asteriskcraft.faction.Faction;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
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
 */
public class GatewayBlockEntity extends BlockEntity implements ArmyLinkedContainer, ProductionBuilding, WarpInBuilding {
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

    public static final int ZEALOT_WOOD_COST = 50;
    public static final int ZEALOT_COBBLE_COST = 50;
    public static final int DRAGOON_WOOD_COST = 100;
    public static final int DRAGOON_COBBLE_COST = 50;
    // The air unit is the first thing in the mod paid for in refined metal rather than wood: Probes
    // already deliver iron ore as ingots straight into the bank, so no new economy plumbing is needed.
    public static final int SCOUT_COBBLE_COST = 150;
    public static final int SCOUT_IRON_COST = 20;
    public static final int BUILD_TICKS = 200; // 10 seconds per unit
    public static final int MAX_QUEUE = 5;
    public static final int WARP_TICKS = 200; // 10 seconds to warp in

    private final Deque<UnitType> queue = new ArrayDeque<>();
    private int buildTicksRemaining = BUILD_TICKS;
    private int warpTicksRemaining = WARP_TICKS;
    private Faction faction = Faction.PROTOSS;

    private final ContainerData dataAccess = new ContainerData() {
        @Override
        public int get(int index) {
            boolean building = !isWarping() && !queue.isEmpty();
            return switch (index) {
                case ProductionMenu.DATA_BUILDING_INDEX -> building ? queue.peek().ordinal() : -1;
                case ProductionMenu.DATA_BUILD_PROGRESS -> building ? BUILD_TICKS - buildTicksRemaining : 0;
                case ProductionMenu.DATA_BUILD_TOTAL -> BUILD_TICKS;
                case ProductionMenu.DATA_WARP -> warpTicksRemaining;
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
        return this.warpTicksRemaining > 0;
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
        if (gateway.warpTicksRemaining > 0) {
            gateway.warpTicksRemaining--;
            if (level instanceof ServerLevel serverLevel) {
                serverLevel.sendParticles(ParticleTypes.SOUL_FIRE_FLAME,
                        pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
                        6, 0.4, 0.6, 0.4, 0.02);
            }
            if (gateway.warpTicksRemaining == 0) {
                level.playSound(null, pos, SoundEvents.BEACON_ACTIVATE, SoundSource.BLOCKS, 1.0f, 1.0f);
            }
            gateway.setChanged();
            return;
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
            overlay(player, Component.translatable("message.asteriskcraft.gateway.cannot_afford"));
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
        return switch (type) {
            case ZEALOT -> ResourceBank.extractAll(this, List.of(
                    new ResourceBank.Cost(stack -> stack.is(ItemTags.LOGS), ZEALOT_WOOD_COST),
                    new ResourceBank.Cost(stack -> stack.is(Items.COBBLESTONE), ZEALOT_COBBLE_COST)));
            case DRAGOON -> ResourceBank.extractAll(this, List.of(
                    new ResourceBank.Cost(stack -> stack.is(ItemTags.LOGS), DRAGOON_WOOD_COST),
                    new ResourceBank.Cost(stack -> stack.is(Items.COBBLESTONE), DRAGOON_COBBLE_COST)));
            case SCOUT -> ResourceBank.extractAll(this, List.of(
                    new ResourceBank.Cost(stack -> stack.is(Items.COBBLESTONE), SCOUT_COBBLE_COST),
                    new ResourceBank.Cost(stack -> stack.is(Items.IRON_INGOT), SCOUT_IRON_COST)));
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
        output.putInt("WarpTicks", this.warpTicksRemaining);
        output.store("Faction", Faction.CODEC, this.faction);
        output.store("Queue", UnitType.LIST_CODEC, List.copyOf(this.queue));
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        this.buildTicksRemaining = input.getIntOr("BuildTicks", BUILD_TICKS);
        this.warpTicksRemaining = input.getIntOr("WarpTicks", WARP_TICKS);
        this.faction = input.read("Faction", Faction.CODEC).orElse(Faction.PROTOSS);
        this.queue.clear();
        this.queue.addAll(input.read("Queue", UnitType.LIST_CODEC).orElse(List.of()));
    }
}
