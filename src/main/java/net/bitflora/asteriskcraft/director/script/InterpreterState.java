package net.bitflora.asteriskcraft.director.script;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.UUIDUtil;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * The persisted execution state of the build-script interpreter — a tiny VM cursor stored as a
 * level attachment so a match resumes exactly where it left off across save/reload.
 *
 * @param pc            program counter: index of the command currently executing.
 * @param waitTicks     ticks left on an active {@code Wait} ({@code -1} = not waiting).
 * @param workerTarget  the standing worker floor set by the most recent {@code Workers} command.
 * @param trainCooldown ticks until the next train attempt within an active {@code Defence}/{@code Wave}.
 * @param scriptVersion the {@code BuildScriptManager} version this state was advanced against; a
 *                      mismatch means the script was reloaded and the cursor must reset.
 * @param batch         the in-progress training batch for a {@code Defence}/{@code Wave}, if any.
 */
public record InterpreterState(int pc, int waitTicks, int workerTarget, int trainCooldown,
                               int scriptVersion, Optional<Batch> batch) {

    /** The worker floor before any {@code Workers} command runs — matches the old game-start drone count. */
    public static final int DEFAULT_WORKER_FLOOR = 6;

    /** Sentinel for {@link #waitTicks} meaning "no wait has started for the current command". */
    public static final int NO_WAIT = -1;

    public static final InterpreterState INITIAL =
            new InterpreterState(0, NO_WAIT, DEFAULT_WORKER_FLOOR, 0, -1, Optional.empty());

    public static final Codec<InterpreterState> CODEC = RecordCodecBuilder.create(inst -> inst.group(
            Codec.INT.fieldOf("pc").forGetter(InterpreterState::pc),
            Codec.INT.fieldOf("wait_ticks").forGetter(InterpreterState::waitTicks),
            Codec.INT.fieldOf("worker_target").forGetter(InterpreterState::workerTarget),
            Codec.INT.fieldOf("train_cooldown").forGetter(InterpreterState::trainCooldown),
            Codec.INT.fieldOf("script_version").forGetter(InterpreterState::scriptVersion),
            Batch.CODEC.optionalFieldOf("batch").forGetter(InterpreterState::batch)
    ).apply(inst, InterpreterState::new));

    /** One still-unfinished unit requirement within a {@link Batch}. */
    public record PendingUnit(String unit, int remaining) {
        public static final Codec<PendingUnit> CODEC = RecordCodecBuilder.create(inst -> inst.group(
                Codec.STRING.fieldOf("unit").forGetter(PendingUnit::unit),
                Codec.INT.fieldOf("remaining").forGetter(PendingUnit::remaining)
        ).apply(inst, PendingUnit::new));

        public PendingUnit minusOne() {
            return new PendingUnit(this.unit, this.remaining - 1);
        }
    }

    /**
     * A {@code Defence}/{@code Wave} being trained: what's left to spawn (quantities already rolled)
     * and the UUIDs of units spawned so far, held near a Hive until the batch completes. On
     * completion a {@code Wave} orders all {@code spawned} units to attack together.
     */
    public record Batch(boolean isWave, List<PendingUnit> remaining, List<UUID> spawned) {
        public Batch {
            remaining = List.copyOf(remaining);
            spawned = List.copyOf(spawned);
        }

        public static final Codec<Batch> CODEC = RecordCodecBuilder.create(inst -> inst.group(
                Codec.BOOL.fieldOf("is_wave").forGetter(Batch::isWave),
                PendingUnit.CODEC.listOf().fieldOf("remaining").forGetter(Batch::remaining),
                UUIDUtil.CODEC.listOf().fieldOf("spawned").forGetter(Batch::spawned)
        ).apply(inst, Batch::new));

        public boolean isComplete() {
            return this.remaining.stream().allMatch(unit -> unit.remaining() <= 0);
        }
    }

    // --- copy-with helpers (records are immutable) ---

    public InterpreterState withPc(int newPc) {
        return new InterpreterState(newPc, this.waitTicks, this.workerTarget, this.trainCooldown, this.scriptVersion, this.batch);
    }

    public InterpreterState withWaitTicks(int ticks) {
        return new InterpreterState(this.pc, ticks, this.workerTarget, this.trainCooldown, this.scriptVersion, this.batch);
    }

    public InterpreterState withWorkerTarget(int target) {
        return new InterpreterState(this.pc, this.waitTicks, target, this.trainCooldown, this.scriptVersion, this.batch);
    }

    public InterpreterState withTrainCooldown(int cooldown) {
        return new InterpreterState(this.pc, this.waitTicks, this.workerTarget, cooldown, this.scriptVersion, this.batch);
    }

    public InterpreterState withScriptVersion(int version) {
        return new InterpreterState(this.pc, this.waitTicks, this.workerTarget, this.trainCooldown, version, this.batch);
    }

    public InterpreterState withBatch(Batch newBatch) {
        return new InterpreterState(this.pc, this.waitTicks, this.workerTarget, this.trainCooldown, this.scriptVersion, Optional.of(newBatch));
    }

    /** Clears the per-command transient state (wait timer + batch) but keeps pc/workerTarget/version. */
    public InterpreterState clearTransient() {
        return new InterpreterState(this.pc, NO_WAIT, this.workerTarget, 0, this.scriptVersion, Optional.empty());
    }
}
