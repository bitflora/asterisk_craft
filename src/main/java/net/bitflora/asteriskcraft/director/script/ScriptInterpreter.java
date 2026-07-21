package net.bitflora.asteriskcraft.director.script;

import net.bitflora.asteriskcraft.director.script.InterpreterState.Batch;
import net.bitflora.asteriskcraft.director.script.InterpreterState.PendingUnit;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * The pure build-script VM. {@link #tick} advances the {@link InterpreterState} by one server tick
 * against a {@link DirectorWorld} — all world mutation is delegated through that seam, so the
 * control-flow logic (blocking vs. instantaneous commands, the {@code Repeat} loop, batch training,
 * program-counter clamping, and the infinite-loop guard) is deterministic and unit-testable.
 *
 * <p>Within a tick the VM keeps executing <i>instantaneous</i> commands ({@code Workers},
 * {@code Repeat}, and completed/empty training batches) in a loop and stops on the first
 * <i>blocking</i> command ({@code Wait} with time left, or a training batch still gathering
 * resources). The {@link #MAX_STEPS_PER_TICK} budget bounds a degenerate all-instantaneous
 * {@code Repeat} loop so it can never hang the server thread.
 */
public final class ScriptInterpreter {
    /** Max instantaneous commands executed in a single tick (guards all-instantaneous Repeat loops). */
    public static final int MAX_STEPS_PER_TICK = 64;
    /** Ticks between individual unit spawns while a training batch gathers (a visible cadence). */
    public static final int TRAIN_INTERVAL = 20;

    private ScriptInterpreter() {
    }

    public static InterpreterState tick(BuildScript script, InterpreterState state, DirectorWorld world) {
        if (script.isEmpty()) {
            return state;
        }
        int steps = 0;
        while (true) {
            // Program-counter safety. pc == size() is the natural "script finished" terminal: idle
            // until the script is reloaded. pc < 0 or pc > size() means corruption or a shrunk
            // reload — restart cleanly from the top.
            if (state.pc() == script.size()) {
                return state;
            }
            if (state.pc() < 0 || state.pc() > script.size()) {
                state = state.withPc(0).clearTransient();
            }

            StepResult result = step(script.commands().get(state.pc()), state, world);
            state = result.state();
            if (!result.advanced()) {
                return state; // blocking command consumed this tick
            }
            if (++steps >= MAX_STEPS_PER_TICK) {
                return state; // budget exhausted; resume next tick from the current pc
            }
        }
    }

    private static StepResult step(BuildCommand command, InterpreterState state, DirectorWorld world) {
        return switch (command) {
            case BuildCommand.Workers workers ->
                    advanced(state.withWorkerTarget(workers.count()).withPc(state.pc() + 1));
            case BuildCommand.Repeat repeat ->
                    advanced(state.withPc(Math.max(0, state.pc() - repeat.count())));
            case BuildCommand.Wait wait -> stepWait(wait, state);
            case BuildCommand.Defence defence -> stepTraining(defence.units(), false, state, world);
            case BuildCommand.Wave wave -> stepTraining(wave.units(), true, state, world);
        };
    }

    private static StepResult stepWait(BuildCommand.Wait wait, InterpreterState state) {
        int remaining = state.waitTicks();
        if (remaining == InterpreterState.NO_WAIT) {
            remaining = Math.max(0, wait.seconds()) * 20;
        }
        if (remaining <= 0) {
            return advanced(state.withWaitTicks(InterpreterState.NO_WAIT).withPc(state.pc() + 1));
        }
        return blocked(state.withWaitTicks(remaining - 1));
    }

    private static StepResult stepTraining(UnitList units, boolean isWave, InterpreterState state, DirectorWorld world) {
        Batch batch = state.batch().orElse(null);
        if (batch == null) {
            batch = rollBatch(units, isWave, world);
            state = state.withBatch(batch).withTrainCooldown(0);
        }
        if (batch.isComplete()) {
            return finishBatch(batch, isWave, state, world);
        }
        if (state.trainCooldown() > 0) {
            return blocked(state.withTrainCooldown(state.trainCooldown() - 1));
        }

        int idx = firstPendingIndex(batch.remaining());
        PendingUnit target = batch.remaining().get(idx);
        List<UUID> spawned = new ArrayList<>(batch.spawned());

        DirectorWorld.SpawnResult result = world.canAffordAndSpawn(target.unit(), spawned);
        switch (result) {
            case SPAWNED -> {
                Batch next = new Batch(isWave, replace(batch.remaining(), idx, target.minusOne()), spawned);
                if (next.isComplete()) {
                    return finishBatch(next, isWave, state, world);
                }
                return blocked(state.withBatch(next).withTrainCooldown(TRAIN_INTERVAL));
            }
            case UNAFFORDABLE -> {
                return blocked(state.withTrainCooldown(0)); // retry next tick
            }
            default -> { // UNKNOWN — drop this requirement rather than waiting forever
                Batch next = new Batch(isWave, replace(batch.remaining(), idx, new PendingUnit(target.unit(), 0)),
                        batch.spawned());
                if (next.isComplete()) {
                    return finishBatch(next, isWave, state, world);
                }
                return blocked(state.withBatch(next).withTrainCooldown(0));
            }
        }
    }

    private static Batch rollBatch(UnitList units, boolean isWave, DirectorWorld world) {
        List<PendingUnit> pending = new ArrayList<>();
        for (UnitReq req : units.reqs()) {
            int qty = req.quantity().roll(world.random());
            if (qty > 0) {
                pending.add(new PendingUnit(req.unitName(), qty));
            }
        }
        return new Batch(isWave, pending, List.of());
    }

    private static StepResult finishBatch(Batch batch, boolean isWave, InterpreterState state, DirectorWorld world) {
        if (isWave) {
            world.orderMove(batch.spawned(), world.nexus());
        }
        // Defence: units keep their guard order; nothing more to do.
        return advanced(state.withPc(state.pc() + 1).clearTransient());
    }

    private static int firstPendingIndex(List<PendingUnit> remaining) {
        for (int i = 0; i < remaining.size(); i++) {
            if (remaining.get(i).remaining() > 0) {
                return i;
            }
        }
        return -1; // unreachable: callers guard with !isComplete()
    }

    private static List<PendingUnit> replace(List<PendingUnit> list, int index, PendingUnit value) {
        List<PendingUnit> copy = new ArrayList<>(list);
        copy.set(index, value);
        return copy;
    }

    private static StepResult advanced(InterpreterState state) {
        return new StepResult(state, true);
    }

    private static StepResult blocked(InterpreterState state) {
        return new StepResult(state, false);
    }

    /** A single command's effect: the resulting state and whether the VM should keep executing this tick. */
    private record StepResult(InterpreterState state, boolean advanced) {
    }
}
