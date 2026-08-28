package net.bitflora.asteriskcraft.director;

import net.bitflora.asteriskcraft.AsteriskCraft;
import net.bitflora.asteriskcraft.building.BaseBlockEntity;
import net.bitflora.asteriskcraft.building.CoreCensus;
import net.bitflora.asteriskcraft.building.TechCensus;
import net.bitflora.asteriskcraft.building.UnitSpawns;
import net.bitflora.asteriskcraft.command.CommandAttachments;
import net.bitflora.asteriskcraft.command.CommandOrder;
import net.bitflora.asteriskcraft.command.MoveFormation;
import net.bitflora.asteriskcraft.director.script.BuildScript;
import net.bitflora.asteriskcraft.director.script.BuildScriptManager;
import net.bitflora.asteriskcraft.director.script.DirectorWorld;
import net.bitflora.asteriskcraft.director.script.InterpreterState;
import net.bitflora.asteriskcraft.director.script.ScriptInterpreter;
import net.bitflora.asteriskcraft.entity.WorkerEntity;
import net.bitflora.asteriskcraft.faction.Faction;
import net.bitflora.asteriskcraft.faction.FactionAttachments;
import net.bitflora.asteriskcraft.game.GameAttachments;
import net.bitflora.asteriskcraft.game.MatchSetup;
import net.bitflora.asteriskcraft.race.RaceProfile;
import net.bitflora.asteriskcraft.race.UnitRoster.UnitDef;
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
 * The computer player's brain (shape A9). It runs the data-driven build script loaded by
 * {@link BuildScriptManager} for whichever race the computer drew: the pure
 * {@link ScriptInterpreter} decides what to do each tick and this class is its
 * {@link DirectorWorld} — spawning units at that army's bases, paying their cost out of its shared
 * bank, and issuing attack orders. It also maintains the standing worker floor set by the script's
 * {@code Workers} command (independent of the interpreter's program counter).
 *
 * <p>Race-agnostic by construction: which side it plays comes from {@code game.MatchSetup}, and
 * everything that side builds comes off its {@code race.RaceProfile}. Nothing here names a Hive, a
 * Drone or the Zerg — putting the computer on the Protoss is a change to the match setup and no
 * change at all to this file.
 *
 * <p>Runs off {@link ServerTickEvent.Post}; the interpreter cursor lives in
 * {@link GameAttachments#INTERPRETER_STATE} so it survives save/reload. A {@code /reload} that
 * changes the script bumps that race's {@link BuildScriptManager#version}, which resets the cursor
 * here (preserving the worker floor so the economy doesn't collapse).
 */
@EventBusSubscriber(modid = AsteriskCraft.MODID)
public final class AiDirector {
    /** How far from a base a worker still counts as "ours" for the worker floor. */
    public static final int WORKER_LEASH = 32;
    /** How often (ticks) the standing worker floor is re-checked and topped up (5s). */
    public static final int WORKER_CHECK_INTERVAL = 100;

    private AiDirector() {
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        ServerLevel overworld = event.getServer().overworld();
        if (!overworld.getData(GameAttachments.BOOTSTRAPPED) || overworld.getData(GameAttachments.GAME_OVER)) {
            return;
        }
        MatchSetup setup = MatchSetup.of(overworld);
        RaceProfile profile = setup.aiProfile();

        List<BaseBlockEntity> bases = resolveBases(overworld, setup.aiFaction());
        if (bases.isEmpty()) {
            return; // base chunks not loaded / already gone
        }

        InterpreterState state = overworld.getData(GameAttachments.INTERPRETER_STATE);
        int scriptVersion = BuildScriptManager.version(profile.race());
        if (state.scriptVersion() != scriptVersion) {
            // Script (re)loaded: restart the program but keep the worker floor so the economy holds.
            state = InterpreterState.INITIAL.withScriptVersion(scriptVersion).withWorkerTarget(state.workerTarget());
        }

        // The worker floor is a standing concern, maintained regardless of which command is running.
        if (overworld.getGameTime() % WORKER_CHECK_INTERVAL == 0) {
            maintainWorkers(overworld, bases, profile, setup.aiFaction(), state.workerTarget());
        }

        BuildScript script = BuildScriptManager.active(profile.race());
        state = ScriptInterpreter.tick(script, state,
                new DirectorWorldImpl(overworld, bases, profile, setup.aiFaction(), setup.playerFaction()));
        overworld.setData(GameAttachments.INTERPRETER_STATE, state);
    }

    /**
     * The computer's standing bases, from {@link CoreCensus} — which every base enrols itself in
     * from its own tick, so a base built after bootstrap counts like any other.
     */
    private static List<BaseBlockEntity> resolveBases(ServerLevel level, Faction faction) {
        List<BaseBlockEntity> bases = new ArrayList<>();
        for (BlockPos pos : CoreCensus.standing(level, faction)) {
            if (level.getBlockEntity(pos) instanceof BaseBlockEntity base) {
                bases.add(base);
            }
        }
        return bases;
    }

    /** Tops the worker count up to {@code target}, training at awake bases it can afford. */
    private static void maintainWorkers(ServerLevel level, List<BaseBlockEntity> bases,
            RaceProfile profile, Faction faction, int target) {
        int current = countWorkers(level, bases, faction);
        while (current < target) {
            BaseBlockEntity base = randomAwakeBase(level, bases);
            if (base == null || !CostPayment.payAny(base, profile.worker().cost())) {
                break; // no awake base, or can't afford another worker right now
            }
            Mob unit = UnitSpawns.spawn(level, base.getBlockPos(), profile.worker().type(), faction,
                    profile.race(), false);
            if (!(unit instanceof WorkerEntity worker)) {
                break;
            }
            worker.setHomePos(base.getBlockPos());
            current++;
        }
    }

    /** Counts living friendly workers near any base, deduped by entity id so overlapping ranges don't double-count. */
    private static int countWorkers(ServerLevel level, List<BaseBlockEntity> bases, Faction faction) {
        Set<Integer> ids = new HashSet<>();
        for (BaseBlockEntity base : bases) {
            AABB range = new AABB(base.getBlockPos()).inflate(WORKER_LEASH);
            for (WorkerEntity worker : level.getEntitiesOfClass(WorkerEntity.class, range,
                    w -> w.isAlive() && FactionAttachments.get(w) == faction)) {
                ids.add(worker.getId());
            }
        }
        return ids.size();
    }

    private static BaseBlockEntity randomAwakeBase(ServerLevel level, List<BaseBlockEntity> bases) {
        List<BaseBlockEntity> awake = awakeBases(bases);
        return awake.isEmpty() ? null : awake.get(level.getRandom().nextInt(awake.size()));
    }

    private static List<BaseBlockEntity> awakeBases(List<BaseBlockEntity> bases) {
        List<BaseBlockEntity> awake = new ArrayList<>();
        for (BaseBlockEntity base : bases) {
            if (base.isAwake()) {
                awake.add(base);
            }
        }
        return awake;
    }

    /**
     * The interpreter's live-world adapter: spawns/pays at the computer's bases and issues orders
     * for one tick.
     */
    private record DirectorWorldImpl(ServerLevel level, List<BaseBlockEntity> bases, RaceProfile profile,
                                     Faction aiFaction, Faction playerFaction) implements DirectorWorld {

        @Override
        public Optional<BlockPos> pickStagingSite() {
            BaseBlockEntity base = randomAwakeBase(this.level, this.bases);
            return base == null ? Optional.empty() : Optional.of(base.getBlockPos());
        }

        @Override
        public SpawnResult canAffordAndSpawn(String unitName, BlockPos site, List<UUID> out) {
            Optional<UnitDef> def = this.profile.roster().resolve(unitName);
            if (def.isEmpty()) {
                return SpawnResult.UNKNOWN;
            }
            List<BaseBlockEntity> awake = awakeBases(this.bases);
            if (awake.isEmpty()) {
                return SpawnResult.UNAFFORDABLE;
            }
            // The batch's base if it is still standing and awake, else any — a razed or shaded base
            // must not stall the batch. Paying at the base we spawn from is bookkeeping only: every
            // base is a view onto the one ArmyBank, so which one pays makes no difference.
            BaseBlockEntity spawnBase = findAwake(awake, site);
            if (spawnBase == null) {
                spawnBase = awake.get(this.level.getRandom().nextInt(awake.size()));
            }
            // The same gate the player's command card applies, reached through the same roster
            // column — which is the whole reason the prerequisite lives there and not on a card the
            // director never opens. UNAFFORDABLE rather than a new result: "wait and retry" is
            // exactly right for a Spire that has been razed and could be rebuilt.
            if (TechCensus.missing(this.level, this.aiFaction, def.get()) != null) {
                return SpawnResult.UNAFFORDABLE;
            }
            if (!CostPayment.payAny(spawnBase, def.get().cost())) {
                return SpawnResult.UNAFFORDABLE;
            }
            BlockPos basePos = spawnBase.getBlockPos();
            Mob unit = UnitSpawns.spawn(this.level, basePos, def.get().type(), this.aiFaction,
                    this.profile.race(), false);
            if (unit == null) {
                AsteriskCraft.LOGGER.warn("AI director paid for {} but failed to spawn it", unitName);
                return SpawnResult.UNAFFORDABLE;
            }
            // Hold near the base until the batch completes; a wave later overrides this with a move
            // order. Because the whole batch shares one base, this is also what masses it in one
            // place, so a wave leaves as a group instead of trickling in from three directions.
            CommandAttachments.setOrder(unit, CommandOrder.guard(basePos));
            out.add(unit.getUUID());
            return SpawnResult.SPAWNED;
        }

        @Override
        public int buildTicks(String unitName) {
            return this.profile.roster().resolve(unitName)
                    .map(UnitDef::buildTicks)
                    .orElse(ScriptInterpreter.TRAIN_INTERVAL);
        }

        /** The awake base at {@code pos}, or null if it is gone, shaded, or {@code pos} is null. */
        private static BaseBlockEntity findAwake(List<BaseBlockEntity> awake, BlockPos pos) {
            if (pos == null) {
                return null;
            }
            for (BaseBlockEntity base : awake) {
                if (base.getBlockPos().equals(pos)) {
                    return base;
                }
            }
            return null;
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
            // here than anywhere: a wave is aimed at a base, so without it the whole attack piles
            // into one column and everything behind the front rank churns instead of fighting.
            MoveFormation.allocate(this.level, dest, wave)
                    .forEach((mob, slot) -> CommandAttachments.setOrder(mob, CommandOrder.move(slot)));
        }

        /**
         * A random standing enemy core, read from {@link CoreCensus} so an expansion base warped in
         * from a kit is attacked like any other. Random rather than nearest, deliberately, so the
         * computer spreads its attention across a player's expansions.
         *
         * <p>Returns null when the player has no base standing, which the interpreter treats as
         * "nowhere to send this wave yet" — the match is about to be decided anyway.
         */
        @Override
        public BlockPos pickAttackTarget() {
            List<BlockPos> cores = CoreCensus.standing(this.level, this.playerFaction);
            if (cores.isEmpty()) {
                return null;
            }
            return cores.get(this.level.getRandom().nextInt(cores.size()));
        }

        @Override
        public RandomSource random() {
            return this.level.getRandom();
        }
    }
}
