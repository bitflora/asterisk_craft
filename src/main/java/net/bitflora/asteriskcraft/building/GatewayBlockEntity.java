package net.bitflora.asteriskcraft.building;

import com.mojang.serialization.Codec;
import net.bitflora.asteriskcraft.AsteriskCraft;
import net.bitflora.asteriskcraft.faction.Faction;
import net.bitflora.asteriskcraft.faction.Race;
import net.bitflora.asteriskcraft.game.MatchSetup;
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
 * Production logic for the Gateway: a Zealot/Dragoon/Dark Templar queue — the Protoss ground army,
 * the flyer having moved to the Stargate ({@link FactoryBlockEntity}) — gated by a one-time warp-in
 * countdown after the kit places the structure. Costs are paid atomically out of the shared Protoss
 * army bank (surfaced through {@link ProductionMenu}).
 *
 * <p>{@link UnitType}'s constants are this building's saved queue, so a queue saved by a build that
 * still trained Scouts here no longer decodes and comes back empty. Deliberate: the alternative is
 * a dead enum constant nothing can produce, and what is lost is at most five units in progress.
 *
 * <p>Acts as a "linked chest" onto its faction's {@link ArmyBank}: {@link BaseBlockEntity}
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
        ZEALOT("zealot"), DRAGOON("dragoon"), DARK_TEMPLAR("dark_templar");

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

    public static final int MAX_QUEUE = 5;
    public static final int WARP_TICKS = 20 * 60; // 1 minute to warp in
    /** Tougher than a unit but well short of the Nexus: losing a Gateway costs production, not the game. */
    public static final int MAX_HEALTH = 250;
    public static final int SHIELD = 250;

    private final Deque<UnitType> queue = new ArrayDeque<>();
    /** Counts down the head of the queue; 0 whenever nothing is in production. See {@link UnitStat#buildTicks()}. */
    private int buildTicksRemaining = 0;
    private final BuildingDefense defense = new BuildingDefense(MAX_HEALTH, SHIELD, WARP_TICKS);
    private final UnderAttackAlert alert = new UnderAttackAlert();
    /**
     * Which side owns this building. Null until set at placement or resolved on first use — a
     * Protoss building placed by hand (creative, {@code /setblock}) belongs to whichever side is
     * playing the Protoss, which {@code MatchSetup.sidePlaying} answers even in a mirror.
     */
    private @Nullable Faction faction;

    private final ContainerData dataAccess = new ContainerData() {
        @Override
        public int get(int index) {
            boolean building = !isWarping() && !queue.isEmpty();
            return switch (index) {
                case ProductionMenu.DATA_BUILDING_INDEX -> building ? queue.peek().ordinal() : -1;
                case ProductionMenu.DATA_BUILD_PROGRESS ->
                        building ? statFor(queue.peek()).buildTicks() - buildTicksRemaining : 0;
                case ProductionMenu.DATA_BUILD_TOTAL -> building ? statFor(queue.peek()).buildTicks() : 0;
                case ProductionMenu.DATA_WARP -> defense.warpTicksRemaining();
                case ProductionMenu.DATA_QUEUE_BASE -> countQueued(UnitType.ZEALOT);
                case ProductionMenu.DATA_QUEUE_BASE + 1 -> countQueued(UnitType.DRAGOON);
                case ProductionMenu.DATA_QUEUE_BASE + 2 -> countQueued(UnitType.DARK_TEMPLAR);
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
        // The next unit's own build time, not this one's: the queue is mixed, and a Dragoon behind a
        // Zealot must take a Dragoon's time.
        gateway.buildTicksRemaining = gateway.queue.isEmpty() ? 0 : statFor(gateway.queue.peek()).buildTicks();
        gateway.setChanged();
        gateway.spawnUnit((ServerLevel) level, pos, type);
    }

    // --- SiegeTarget ---

    @Override
    public Faction buildingFaction() {
        if (this.level == null) {
            // Asked before the block entity has a level (a freshly constructed one being loaded):
            // answer, but don't cache it, or the building would be stuck belonging to nobody.
            return this.faction == null ? Faction.NEUTRAL : this.faction;
        }
        if (this.faction == null) {
            this.faction = MatchSetup.of(this.level).sidePlaying(Race.PROTOSS);
        }
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
        this.alert.ping(level, buildingFaction(), "message.asteriskcraft.gateway.under_attack");
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
            case 2 -> UnitType.DARK_TEMPLAR;
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
        boolean wasIdle = this.queue.isEmpty();
        this.queue.add(type);
        if (wasIdle) {
            this.buildTicksRemaining = statFor(type).buildTicks();
        }
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
            case DARK_TEMPLAR -> UnitStats.DARK_TEMPLAR;
        };
    }

    private void spawnUnit(ServerLevel level, BlockPos pos, UnitType type) {
        EntityType<? extends Mob> entityType = switch (type) {
            case ZEALOT -> AsteriskCraft.ZEALOT.get();
            case DRAGOON -> AsteriskCraft.DRAGOON.get();
            case DARK_TEMPLAR -> AsteriskCraft.DARK_TEMPLAR.get();
        };
        UnitSpawns.spawn(level, pos, entityType, buildingFaction(), Race.PROTOSS, false);
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
        if (this.level != null) {
            this.defense.collapseScaffold(this.level, pos);
        }
    }

    // --- ArmyLinkedContainer ---

    @Override
    public NonNullList<ItemStack> armyItems() {
        return ArmyBank.of(this.level, buildingFaction(), Race.PROTOSS);
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
        if (this.faction != null) {
            output.store("Faction", Faction.CODEC, this.faction);
        }
        output.store("Queue", UnitType.LIST_CODEC, List.copyOf(this.queue));
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        this.defense.load(input);
        this.faction = input.read("Faction", Faction.CODEC).orElse(null);
        this.queue.clear();
        this.queue.addAll(input.read("Queue", UnitType.LIST_CODEC).orElse(List.of()));
        // Read after the queue: the fallback for a save with no BuildTicks is now the head unit's own
        // build time, which there is no way to know before the queue is in hand.
        this.buildTicksRemaining = input.getIntOr("BuildTicks",
                this.queue.isEmpty() ? 0 : statFor(this.queue.peek()).buildTicks());
    }
}
