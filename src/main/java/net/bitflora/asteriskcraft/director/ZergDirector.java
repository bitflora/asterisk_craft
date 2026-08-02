package net.bitflora.asteriskcraft.director;

import net.bitflora.asteriskcraft.AsteriskCraft;
import net.bitflora.asteriskcraft.building.HiveBlockEntity;
import net.bitflora.asteriskcraft.building.UnitSpawns;
import net.bitflora.asteriskcraft.command.CommandAttachments;
import net.bitflora.asteriskcraft.command.CommandOrder;
import net.bitflora.asteriskcraft.command.MoveFormation;
import net.bitflora.asteriskcraft.director.script.BuildScript;
import net.bitflora.asteriskcraft.director.script.BuildScriptManager;
import net.bitflora.asteriskcraft.director.script.DirectorWorld;
import net.bitflora.asteriskcraft.director.script.InterpreterState;
import net.bitflora.asteriskcraft.director.script.ScriptInterpreter;
import net.bitflora.asteriskcraft.director.script.ZergUnitCatalog;
import net.bitflora.asteriskcraft.director.script.ZergUnitCatalog.UnitDef;
import net.bitflora.asteriskcraft.entity.zerg.DroneEntity;
import net.bitflora.asteriskcraft.faction.Faction;
import net.bitflora.asteriskcraft.faction.FactionAttachments;
import net.bitflora.asteriskcraft.game.GameAttachments;
import net.bitflora.asteriskcraft.stats.CostPayment;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * The Zerg AI brain (shape A9). It runs the data-driven build script loaded by
 * {@link BuildScriptManager}: the pure {@link ScriptInterpreter} decides what to do each tick and
 * this class is its {@link DirectorWorld} — spawning units at the Hives, paying their cost out of
 * the shared Zerg bank, and issuing attack orders. It also maintains the standing worker floor set
 * by the script's {@code Workers} command (independent of the interpreter's program counter).
 *
 * <p>Runs off {@link ServerTickEvent.Post}; the interpreter cursor lives in
 * {@link GameAttachments#INTERPRETER_STATE} so it survives save/reload. A {@code /reload} that
 * changes the script bumps {@link BuildScriptManager#version()}, which resets the cursor here
 * (preserving the worker floor so the economy doesn't collapse).
 */
@EventBusSubscriber(modid = AsteriskCraft.MODID)
public final class ZergDirector {
    /** How far from a Hive a drone still counts as "ours" for the worker floor. */
    public static final int DRONE_LEASH = 32;
    /** How often (ticks) the standing worker floor is re-checked and topped up (5s). */
    public static final int WORKER_CHECK_INTERVAL = 100;

    private ZergDirector() {
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        ServerLevel overworld = event.getServer().overworld();
        if (!overworld.getData(GameAttachments.BOOTSTRAPPED) || overworld.getData(GameAttachments.GAME_OVER)) {
            return;
        }
        List<BlockPos> hivePositions = overworld.getData(GameAttachments.HIVE_POSITIONS);
        BlockPos nexus = overworld.getData(GameAttachments.NEXUS_POS);
        if (hivePositions.isEmpty() || nexus.equals(BlockPos.ZERO)) {
            return;
        }
        List<HiveBlockEntity> hives = resolveHives(overworld, hivePositions);
        if (hives.isEmpty()) {
            return; // Hive chunks not loaded / already gone
        }

        InterpreterState state = overworld.getData(GameAttachments.INTERPRETER_STATE);
        int scriptVersion = BuildScriptManager.version();
        if (state.scriptVersion() != scriptVersion) {
            // Script (re)loaded: restart the program but keep the worker floor so the economy holds.
            state = InterpreterState.INITIAL.withScriptVersion(scriptVersion).withWorkerTarget(state.workerTarget());
        }

        // The worker floor is a standing concern, maintained regardless of which command is running.
        if (overworld.getGameTime() % WORKER_CHECK_INTERVAL == 0) {
            maintainWorkers(overworld, hives, state.workerTarget());
        }

        BuildScript script = BuildScriptManager.active();
        state = ScriptInterpreter.tick(script, state, new DirectorWorldImpl(overworld, hives, nexus));
        overworld.setData(GameAttachments.INTERPRETER_STATE, state);
    }

    private static List<HiveBlockEntity> resolveHives(ServerLevel level, List<BlockPos> positions) {
        List<HiveBlockEntity> hives = new ArrayList<>();
        for (BlockPos pos : positions) {
            if (level.getBlockEntity(pos) instanceof HiveBlockEntity hive) {
                hives.add(hive);
            }
        }
        return hives;
    }

    /** Tops the Zerg worker count up to {@code target}, training drones at awake Hives it can afford. */
    private static void maintainWorkers(ServerLevel level, List<HiveBlockEntity> hives, int target) {
        int current = countDrones(level, hives);
        while (current < target) {
            HiveBlockEntity hive = randomAwakeHive(level, hives);
            if (hive == null || !payAny(hive, ZergUnitCatalog.drone())) {
                break; // no awake Hive, or can't afford another drone right now
            }
            DroneEntity drone = UnitSpawns.spawn(level, hive.getBlockPos(), AsteriskCraft.DRONE.get(), Faction.ZERG, false);
            if (drone == null) {
                break;
            }
            drone.setHomePos(hive.getBlockPos());
            current++;
        }
    }

    /** Counts living Zerg drones near any Hive, deduped by entity id so overlapping ranges don't double-count. */
    private static int countDrones(ServerLevel level, List<HiveBlockEntity> hives) {
        Set<Integer> ids = new HashSet<>();
        for (HiveBlockEntity hive : hives) {
            AABB range = new AABB(hive.getBlockPos()).inflate(DRONE_LEASH);
            for (DroneEntity drone : level.getEntitiesOfClass(DroneEntity.class, range,
                    d -> d.isAlive() && FactionAttachments.get(d) == Faction.ZERG)) {
                ids.add(drone.getId());
            }
        }
        return ids.size();
    }

    private static HiveBlockEntity randomAwakeHive(ServerLevel level, List<HiveBlockEntity> hives) {
        List<HiveBlockEntity> awake = awakeHives(hives);
        return awake.isEmpty() ? null : awake.get(level.getRandom().nextInt(awake.size()));
    }

    private static List<HiveBlockEntity> awakeHives(List<HiveBlockEntity> hives) {
        List<HiveBlockEntity> awake = new ArrayList<>();
        for (HiveBlockEntity hive : hives) {
            if (hive.isAwake()) {
                awake.add(hive);
            }
        }
        return awake;
    }

    /** Pays any one of a unit's interchangeable cost bundles from the shared bank; false if none affordable. */
    private static boolean payAny(HiveBlockEntity bank, UnitDef def) {
        return CostPayment.payAny(bank, def.cost());
    }

    /** The interpreter's live-world adapter: spawns/pays at the Hives and issues orders for one tick. */
    private record DirectorWorldImpl(ServerLevel level, List<HiveBlockEntity> hives, BlockPos nexus)
            implements DirectorWorld {

        @Override
        public SpawnResult canAffordAndSpawn(String unitName, List<UUID> out) {
            Optional<UnitDef> def = ZergUnitCatalog.resolve(unitName);
            if (def.isEmpty()) {
                return SpawnResult.UNKNOWN;
            }
            List<HiveBlockEntity> awake = awakeHives(this.hives);
            if (awake.isEmpty() || !payAny(awake.get(0), def.get())) {
                return SpawnResult.UNAFFORDABLE;
            }
            HiveBlockEntity spawnHive = awake.get(this.level.getRandom().nextInt(awake.size()));
            BlockPos hivePos = spawnHive.getBlockPos();
            Mob unit = UnitSpawns.spawn(this.level, hivePos, def.get().type(), Faction.ZERG, def.get().dyeArmor());
            if (unit == null) {
                AsteriskCraft.LOGGER.warn("Zerg director paid for {} but failed to spawn it", unitName);
                return SpawnResult.UNAFFORDABLE;
            }
            // Hold near the Hive until the batch completes; a wave later overrides this with a move order.
            CommandAttachments.setOrder(unit, CommandOrder.guard(hivePos));
            out.add(unit.getUUID());
            return SpawnResult.SPAWNED;
        }

        @Override
        public void orderMove(List<UUID> unitIds, BlockPos dest) {
            List<Mob> wave = new ArrayList<>(unitIds.size());
            for (UUID id : unitIds) {
                if (this.level.getEntity(id) instanceof Mob mob && mob.isAlive()) {
                    wave.add(mob);
                }
            }
            if (wave.isEmpty()) {
                return;
            }
            // Spread the wave the same way a player's group move order is spread. This matters more
            // here than anywhere: a wave is aimed at the Nexus, so without it the whole attack piles
            // into one column and everything behind the front rank churns instead of fighting.
            MoveFormation.allocate(this.level, dest, wave)
                    .forEach((mob, slot) -> CommandAttachments.setOrder(mob, CommandOrder.move(slot)));
        }

        @Override
        public BlockPos nexus() {
            return this.nexus;
        }

        @Override
        public RandomSource random() {
            return this.level.getRandom();
        }
    }
}
