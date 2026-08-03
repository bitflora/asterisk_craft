package net.bitflora.asteriskcraft.director.script;

import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Exercises the pure interpreter VM against a fake {@link DirectorWorld}: control flow (Workers,
 * Wait, Repeat), resource-gated batch training, the Wave-vs-Defence order distinction, the
 * infinite-loop step budget, and program-counter clamping after a reload.
 */
class ScriptInterpreterTest {

    /** A scriptable stand-in for the world: controls affordability and records spawns/orders. */
    private static final class FakeWorld implements DirectorWorld {
        final Set<String> known = new HashSet<>(Set.of("zergling", "hydralisk"));
        boolean affordable = true;
        int spawnCounter = 0;
        final List<String> spawnedNames = new ArrayList<>();
        /** The staging site handed to each spawn, in order — one entry per attempt. */
        final List<BlockPos> spawnSites = new ArrayList<>();
        List<UUID> lastMoveUnits;
        BlockPos lastMoveDest;
        /** Sites handed out by pickStagingSite, in order; the last is reused once exhausted. */
        final Deque<BlockPos> stagingSites = new ArrayDeque<>(List.of(new BlockPos(10, 64, 10)));
        /** Targets handed out by pickAttackTarget, same rule. */
        final Deque<BlockPos> attackTargets = new ArrayDeque<>(List.of(new BlockPos(100, 64, 100)));
        final List<BlockPos> pickedTargets = new ArrayList<>();
        final RandomSource random = RandomSource.create(42L);

        @Override
        public Optional<BlockPos> pickStagingSite() {
            return Optional.ofNullable(next(this.stagingSites));
        }

        @Override
        public SpawnResult canAffordAndSpawn(String unitName, BlockPos site, List<UUID> out) {
            String key = unitName.toLowerCase(Locale.ROOT);
            if (!known.contains(key)) {
                return SpawnResult.UNKNOWN;
            }
            if (!affordable) {
                return SpawnResult.UNAFFORDABLE;
            }
            out.add(new UUID(0, ++spawnCounter));
            spawnedNames.add(key);
            spawnSites.add(site);
            return SpawnResult.SPAWNED;
        }

        @Override
        public void orderMove(List<UUID> unitIds, BlockPos dest) {
            this.lastMoveUnits = List.copyOf(unitIds);
            this.lastMoveDest = dest;
        }

        @Override
        public BlockPos pickAttackTarget() {
            BlockPos target = next(this.attackTargets);
            this.pickedTargets.add(target);
            return target;
        }

        @Override
        public RandomSource random() {
            return this.random;
        }

        /** Pops the next scripted position, keeping the last one on tap for further calls. */
        private static BlockPos next(Deque<BlockPos> queue) {
            return queue.size() > 1 ? queue.poll() : queue.peek();
        }
    }

    private static BuildScript script(String... lines) {
        return BuildScriptParser.parse(List.of(lines));
    }

    @Test
    void workersSetsFloorThenIdlesAtEnd() {
        FakeWorld world = new FakeWorld();
        BuildScript script = script("Workers: 6");
        InterpreterState state = ScriptInterpreter.tick(script, InterpreterState.INITIAL, world);

        assertEquals(6, state.workerTarget());
        assertEquals(1, state.pc(), "pc lands one past the last command");

        // A finished script idles: further ticks change nothing.
        InterpreterState again = ScriptInterpreter.tick(script, state, world);
        assertEquals(state, again);
    }

    @Test
    void waitBlocksThenAdvances() {
        FakeWorld world = new FakeWorld();
        BuildScript script = script("Wait 1", "Workers: 3"); // 1s = 20 ticks

        InterpreterState state = InterpreterState.INITIAL;
        state = ScriptInterpreter.tick(script, state, world);
        assertEquals(0, state.pc(), "still waiting after the first tick");
        assertEquals(InterpreterState.DEFAULT_WORKER_FLOOR, state.workerTarget(),
                "the worker command after the wait must not run early");

        for (int i = 0; i < 25 && state.pc() < 2; i++) {
            state = ScriptInterpreter.tick(script, state, world);
        }
        assertEquals(2, state.pc(), "wait expires and the following command runs");
        assertEquals(3, state.workerTarget());
    }

    @Test
    void waveBlocksUntilAffordableThenOrdersAttack() {
        FakeWorld world = new FakeWorld();
        world.affordable = false;
        BuildScript script = script("Wave: 1 Zergling");

        InterpreterState state = InterpreterState.INITIAL;
        for (int i = 0; i < 5; i++) {
            state = ScriptInterpreter.tick(script, state, world);
        }
        assertEquals(0, state.pc(), "an unaffordable wave blocks");
        assertNull(world.lastMoveDest, "no attack order while still training");
        assertTrue(state.batch().isPresent(), "the batch is held across ticks");

        world.affordable = true;
        state = ScriptInterpreter.tick(script, state, world);

        assertEquals(1, world.spawnCounter, "one zergling trained");
        assertNotNull(world.lastMoveDest, "the completed wave is ordered to attack");
        assertEquals(new BlockPos(100, 64, 100), world.lastMoveDest);
        assertEquals(1, world.lastMoveUnits.size());
        assertEquals(1, state.pc(), "the command completes and advances");
    }

    @Test
    void wholeBatchTrainsAtTheOneStagingSite() {
        // The point of rolling the site per batch: a wave masses in one place and leaves as a group,
        // instead of trickling out of every Hive at once as it did when the site was rolled per unit.
        FakeWorld world = new FakeWorld();
        BlockPos site = new BlockPos(-40, 70, 25);
        world.stagingSites.clear();
        world.stagingSites.add(site);
        BuildScript script = script("Wave: 3 Zergling");

        InterpreterState state = InterpreterState.INITIAL;
        for (int i = 0; i < 100 && state.pc() < 1; i++) {
            state = ScriptInterpreter.tick(script, state, world);
        }
        assertEquals(3, world.spawnCounter);
        assertEquals(List.of(site, site, site), world.spawnSites,
                "every unit in the batch is produced at the site rolled when the batch started");
    }

    @Test
    void defenceAlsoStagesAtOneSite() {
        FakeWorld world = new FakeWorld();
        BlockPos site = new BlockPos(7, 64, -7);
        world.stagingSites.clear();
        world.stagingSites.add(site);
        BuildScript script = script("Defence: 2 Zergling");

        InterpreterState state = InterpreterState.INITIAL;
        for (int i = 0; i < 100 && state.pc() < 1; i++) {
            state = ScriptInterpreter.tick(script, state, world);
        }
        assertEquals(List.of(site, site), world.spawnSites, "a Defence batch is one group in one place too");
    }

    @Test
    void stagingSiteIsRolledOncePerBatchNotPerUnit() {
        // Two sites queued: a per-unit roll would burn both inside one wave.
        FakeWorld world = new FakeWorld();
        BlockPos first = new BlockPos(1, 64, 1);
        BlockPos second = new BlockPos(2, 64, 2);
        world.stagingSites.clear();
        world.stagingSites.add(first);
        world.stagingSites.add(second);
        BuildScript script = script("Wave: 2 Zergling", "Wave: 2 Zergling");

        InterpreterState state = InterpreterState.INITIAL;
        for (int i = 0; i < 200 && state.pc() < 2; i++) {
            state = ScriptInterpreter.tick(script, state, world);
        }
        assertEquals(List.of(first, first, second, second), world.spawnSites,
                "each wave keeps its own site across its whole batch, and the next wave rolls a fresh one");
    }

    @Test
    void stagingSiteSurvivesAnUnaffordableStall() {
        // The site rides in the persisted batch, so a wave paused on resources — or reloaded from
        // disk mid-training — resumes at the same Hive rather than scattering the rest of the batch.
        FakeWorld world = new FakeWorld();
        BlockPos site = new BlockPos(5, 64, 5);
        world.stagingSites.clear();
        world.stagingSites.add(site);
        world.stagingSites.add(new BlockPos(999, 64, 999)); // would be picked by any re-roll
        BuildScript script = script("Wave: 2 Zergling");

        InterpreterState state = InterpreterState.INITIAL;
        state = ScriptInterpreter.tick(script, state, world); // rolls the batch, spawns the first
        assertEquals(1, world.spawnCounter);

        world.affordable = false;
        for (int i = 0; i < 30; i++) {
            state = ScriptInterpreter.tick(script, state, world);
        }
        world.affordable = true;
        for (int i = 0; i < 100 && state.pc() < 1; i++) {
            state = ScriptInterpreter.tick(script, state, world);
        }
        assertEquals(List.of(site, site), world.spawnSites, "the stall must not re-roll the batch's site");
    }

    @Test
    void eachWavePicksItsOwnAttackTarget() {
        FakeWorld world = new FakeWorld();
        BlockPos firstNexus = new BlockPos(100, 64, 100);
        BlockPos secondNexus = new BlockPos(-300, 64, 80);
        world.attackTargets.clear();
        world.attackTargets.add(firstNexus);
        world.attackTargets.add(secondNexus);
        BuildScript script = script("Wave: 1 Zergling", "Wave: 1 Zergling");

        InterpreterState state = InterpreterState.INITIAL;
        for (int i = 0; i < 200 && state.pc() < 2; i++) {
            state = ScriptInterpreter.tick(script, state, world);
        }
        assertEquals(List.of(firstNexus, secondNexus), world.pickedTargets,
                "the target is re-picked at each wave's launch, so waves spread across the enemy's bases");
        assertEquals(secondNexus, world.lastMoveDest);
    }

    @Test
    void attackTargetIsPickedAtLaunchNotAtRollTime() {
        // A core razed while the wave was training must never be the one it marches on — so the pick
        // happens once the batch completes, not when it started.
        FakeWorld world = new FakeWorld();
        world.affordable = false;
        BuildScript script = script("Wave: 1 Zergling");

        InterpreterState state = InterpreterState.INITIAL;
        for (int i = 0; i < 5; i++) {
            state = ScriptInterpreter.tick(script, state, world);
        }
        assertTrue(world.pickedTargets.isEmpty(), "nothing is targeted while the wave is still training");

        world.affordable = true;
        ScriptInterpreter.tick(script, state, world);
        assertEquals(1, world.pickedTargets.size(), "the target is picked exactly once, at launch");
    }

    @Test
    void defenceTrainsButNeverOrdersAttack() {
        FakeWorld world = new FakeWorld();
        BuildScript script = script("Defence: 2 Zergling");

        InterpreterState state = InterpreterState.INITIAL;
        for (int i = 0; i < 60 && state.pc() < 1; i++) {
            state = ScriptInterpreter.tick(script, state, world);
        }
        assertEquals(2, world.spawnCounter, "both defenders trained");
        assertNull(world.lastMoveDest, "defenders stay home — never ordered to attack");
        assertEquals(1, state.pc());
    }

    @Test
    void unknownUnitIsDroppedNotWaitedOn() {
        FakeWorld world = new FakeWorld();
        BuildScript script = script("Wave: 1 Ultralisk"); // not in the fake catalog

        InterpreterState state = InterpreterState.INITIAL;
        for (int i = 0; i < 5 && state.pc() < 1; i++) {
            state = ScriptInterpreter.tick(script, state, world);
        }
        assertEquals(0, world.spawnCounter, "nothing spawned for an unknown unit");
        assertEquals(1, state.pc(), "the command drops the unknown unit and advances instead of hanging");
    }

    @Test
    void allInstantaneousRepeatIsBoundedByStepBudget() {
        FakeWorld world = new FakeWorld();
        BuildScript script = script("Workers: 5", "Repeat 1"); // degenerate: no blocking command

        // If the step budget were absent this would spin forever; the call must return.
        InterpreterState state = ScriptInterpreter.tick(script, InterpreterState.INITIAL, world);
        assertEquals(5, state.workerTarget());
    }

    @Test
    void repeatReRunsABlockingBody() {
        FakeWorld world = new FakeWorld();
        BuildScript script = script("Wave: 1 Zergling", "Repeat 1");

        InterpreterState state = ScriptInterpreter.tick(script, InterpreterState.INITIAL, world);
        // The wave completes, Repeat jumps back, and it runs again — repeatedly within one tick,
        // bounded by the step budget. So more than one zergling is produced, but not unboundedly.
        assertTrue(world.spawnCounter > 1, "the repeat re-runs the wave");
        assertTrue(world.spawnCounter <= ScriptInterpreter.MAX_STEPS_PER_TICK, "the budget bounds the loop");
        assertNotNull(world.lastMoveDest);
    }

    @Test
    void programCounterClampsAfterShrunkScript() {
        FakeWorld world = new FakeWorld();
        BuildScript script = script("Workers: 9", "Wait 100");
        InterpreterState stale = InterpreterState.INITIAL.withPc(5); // pc past the end of a shrunk script

        InterpreterState state = ScriptInterpreter.tick(script, stale, world);
        assertEquals(9, state.workerTarget(), "restarts cleanly from the top");
        assertEquals(1, state.pc());
    }
}
