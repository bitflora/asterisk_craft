package net.bitflora.asteriskcraft.building;

import net.bitflora.asteriskcraft.AsteriskCraft;
import net.bitflora.asteriskcraft.director.ZergSpawns;
import net.bitflora.asteriskcraft.entity.DroneEntity;
import net.bitflora.asteriskcraft.faction.Faction;
import net.bitflora.asteriskcraft.game.GameOutcome;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.AABB;

import java.util.List;

/**
 * A Zerg Hive: the AI's economy building. It is not player-operated (no GUI); instead it runs
 * its own per-Hive brain each tick — maintaining a small pool of {@link DroneEntity} workers
 * that mine {@code #asteriskcraft:harvestable} blocks and deposit the yield back into this
 * Hive's inventory. The {@link net.bitflora.asteriskcraft.director.ZergDirector} spends those
 * pooled resources on attack waves. Also a {@link FactionCore}, so the player's army can raze it.
 */
public class HiveBlockEntity extends BlockEntity implements Container, FactionCore {
    public static final int INPUT_SLOTS = 27;
    public static final int DRONE_COST = 50; // 50 wood OR 50 cobblestone, mirrors the Protoss Probe
    public static final int DRONE_TARGET = 3; // drones this Hive tries to keep alive
    public static final int DRONE_CHECK_INTERVAL = 100; // re-evaluate drone count every 5s
    public static final int DRONE_LEASH = 32; // how far from the Hive a drone still counts as "ours"

    private final NonNullList<ItemStack> items = NonNullList.withSize(INPUT_SLOTS, ItemStack.EMPTY);
    private Faction faction = Faction.ZERG;
    private int coreHealth = FactionCore.CORE_MAX_HEALTH;
    private int droneCheckCooldown = DRONE_CHECK_INTERVAL;

    public HiveBlockEntity(BlockPos pos, BlockState state) {
        super(AsteriskCraft.HIVE_BLOCK_ENTITY.get(), pos, state);
    }

    public void setFaction(Faction faction) {
        this.faction = faction;
        this.setChanged();
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, HiveBlockEntity hive) {
        if (--hive.droneCheckCooldown > 0) {
            return;
        }
        hive.droneCheckCooldown = DRONE_CHECK_INTERVAL;
        hive.maintainDrones((ServerLevel) level, pos);
    }

    /** Keeps this Hive stocked with workers: spawns a Drone when short and it can pay for one. */
    private void maintainDrones(ServerLevel level, BlockPos pos) {
        AABB range = new AABB(pos).inflate(DRONE_LEASH);
        List<DroneEntity> drones = level.getEntitiesOfClass(DroneEntity.class, range,
                drone -> drone.isAlive() && drone.getHomePos().equals(pos));
        if (drones.size() >= DRONE_TARGET) {
            return;
        }
        if (!payDroneCost()) {
            return;
        }
        DroneEntity drone = ZergSpawns.spawn(level, pos, AsteriskCraft.DRONE.get(), this.faction, false);
        if (drone != null) {
            drone.setHomePos(pos);
        }
    }

    private boolean payDroneCost() {
        return ResourceBank.extract(this, stack -> stack.is(ItemTags.LOGS), DRONE_COST)
                || ResourceBank.extract(this, stack -> stack.is(Items.COBBLESTONE), DRONE_COST);
    }

    // --- FactionCore ---

    @Override
    public Faction coreFaction() {
        return this.faction;
    }

    @Override
    public void damageCore(int amount, ServerLevel level, BlockPos pos) {
        this.coreHealth = FactionCore.applyDamage(this.coreHealth, amount, level, pos);
        this.setChanged();
    }

    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        super.preRemoveSideEffects(pos, state); // drops the Hive's stored resources
        GameOutcome.onCoreDestroyed(this.level, pos);
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
        output.store("Faction", Faction.CODEC, this.faction);
        output.putInt("CoreHealth", this.coreHealth);
        ContainerHelper.saveAllItems(output, this.items);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        this.faction = input.read("Faction", Faction.CODEC).orElse(Faction.ZERG);
        this.coreHealth = input.getIntOr("CoreHealth", FactionCore.CORE_MAX_HEALTH);
        this.items.clear();
        ContainerHelper.loadAllItems(input, this.items);
    }
}
