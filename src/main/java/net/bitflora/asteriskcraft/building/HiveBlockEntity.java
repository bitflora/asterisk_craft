package net.bitflora.asteriskcraft.building;

import net.bitflora.asteriskcraft.AsteriskCraft;
import net.bitflora.asteriskcraft.director.ZergSpawns;
import net.bitflora.asteriskcraft.entity.DroneEntity;
import net.bitflora.asteriskcraft.entity.TeamColors;
import net.bitflora.asteriskcraft.faction.Faction;
import net.bitflora.asteriskcraft.game.GameOutcome;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BeaconBeamOwner;
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
 * that mine {@code #asteriskcraft:harvestable} blocks and deposit the yield into the shared Zerg
 * army bank. The {@link net.bitflora.asteriskcraft.director.ZergDirector} spends those pooled
 * resources on attack waves. Also a {@link FactionCore}, so the player's army can raze it.
 *
 * <p>Acts as a "linked chest" onto {@link ArmyBank#ZERG_BANK}: every Hive reads and writes the
 * same underlying data, so all three Hives draw from one shared pool. See {@link ArmyLinkedContainer}.
 */
public class HiveBlockEntity extends BlockEntity implements ArmyLinkedContainer, FactionCore, BeaconBeamOwner {
    public static final int INPUT_SLOTS = ArmyBank.ZERG_SLOTS;
    public static final int DRONE_COST = 50; // 50 wood OR 50 cobblestone, mirrors the Protoss Probe
    public static final int DRONE_TARGET = 3; // drones this Hive tries to keep alive
    public static final int DRONE_CHECK_INTERVAL = 100; // re-evaluate drone count every 5s
    public static final int DRONE_LEASH = 32; // how far from the Hive a drone still counts as "ours"

    private Faction faction = Faction.ZERG;
    private int coreHealth = FactionCore.CORE_MAX_HEALTH;
    private int droneCheckCooldown = DRONE_CHECK_INTERVAL;
    /** Whether the Hive currently has a clear path to the sky. Transient; null until the first tick evaluates it. */
    private Boolean skyLit = null;

    public HiveBlockEntity(BlockPos pos, BlockState state) {
        super(AsteriskCraft.HIVE_BLOCK_ENTITY.get(), pos, state);
    }

    public void setFaction(Faction faction) {
        this.faction = faction;
        this.setChanged();
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, HiveBlockEntity hive) {
        if (!hive.updateSky(level, pos)) {
            return; // dormant: no clear path to the sky, the Hive stops maintaining drones
        }
        if (--hive.droneCheckCooldown > 0) {
            return;
        }
        hive.droneCheckCooldown = DRONE_CHECK_INTERVAL;
        hive.maintainDrones((ServerLevel) level, pos);
    }

    /** Recomputes sky visibility, playing an activate/deactivate cue on any change. Returns the current lit state. */
    private boolean updateSky(Level level, BlockPos pos) {
        boolean lit = level.canSeeSky(pos.above());
        if (this.skyLit != null && this.skyLit != lit) {
            level.playSound(null, pos, lit ? SoundEvents.BEACON_ACTIVATE : SoundEvents.BEACON_DEACTIVATE,
                    SoundSource.BLOCKS, 1.0f, 1.0f);
        }
        this.skyLit = lit;
        return lit;
    }

    // --- BeaconBeamOwner (client-side beam locator) ---

    @Override
    public List<BeaconBeamOwner.Section> getBeamSections() {
        int color = TeamColors.factionColor(this.faction);
        if (color < 0 || this.level == null || !this.level.canSeeSky(this.worldPosition.above())) {
            return List.of();
        }
        return List.of(new BeaconBeamOwner.Section(color));
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
        // Deliberately skip super: vanilla would drop+clear this Container's contents, but that
        // Container is the shared Zerg army bank (ArmyLinkedContainer) — one Hive breaking must
        // not dump/clear resources the other two Hives still depend on.
        GameOutcome.onCoreDestroyed(this.level, pos);
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
        output.store("Faction", Faction.CODEC, this.faction);
        output.putInt("CoreHealth", this.coreHealth);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        this.faction = input.read("Faction", Faction.CODEC).orElse(Faction.ZERG);
        this.coreHealth = input.getIntOr("CoreHealth", FactionCore.CORE_MAX_HEALTH);
    }
}
