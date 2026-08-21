package net.bitflora.asteriskcraft.director.script;

import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * The side-effect seam the {@link ScriptInterpreter} drives — every interaction with the live
 * world goes through this interface so the VM itself stays pure and unit-testable against a fake.
 * The real implementation is {@code AiDirector}.
 */
public interface DirectorWorld {

    /** Outcome of a {@link #canAffordAndSpawn} attempt. */
    enum SpawnResult {
        /** The unit was paid for and spawned; its UUID was appended to the {@code out} list. */
        SPAWNED,
        /** The unit is known but the army can't currently afford it — the batch should wait. */
        UNAFFORDABLE,
        /** The unit name isn't in the catalog — the requirement should be dropped, not waited on. */
        UNKNOWN
    }

    /**
     * The building a whole batch is trained at and staged from, rolled once when the batch starts;
     * empty if the army has no building able to produce right now. Rolling it per batch rather than
     * per unit is what makes a wave arrive as one group from one place instead of trickling out of
     * every building at once.
     */
    Optional<BlockPos> pickStagingSite();

    /**
     * Attempts to pay for and spawn one unit of {@code unitName}, held near its producing building
     * with a guard order until the batch completes. On {@link SpawnResult#SPAWNED} the new unit's
     * UUID is appended to {@code out}.
     *
     * <p>{@code site} is the batch's staging site from {@link #pickStagingSite}, or null if none was
     * available then. Implementations produce there when it can still produce, and fall back to any
     * building that can otherwise — a batch must not stall because its chosen building was razed.
     */
    SpawnResult canAffordAndSpawn(String unitName, @Nullable BlockPos site, List<UUID> out);

    /**
     * How long {@code unitName} takes to produce, in ticks — the gap the interpreter waits after
     * spawning one before starting the next. Comes from the unit's {@code UnitStat.buildTicks()};
     * an unknown name falls back to {@link ScriptInterpreter#TRAIN_INTERVAL}, though a batch only
     * reaches this having already resolved the name.
     */
    int buildTicks(String unitName);

    /** Orders the given (possibly since-deceased) units to attack-move to {@code dest} together. */
    void orderMove(List<UUID> unitIds, BlockPos dest);

    /**
     * The enemy core a finished wave marches on. Re-picked at each wave's launch, so successive
     * waves spread across the enemy's bases instead of all walking into the same defended one.
     *
     * @return null when the enemy has no core standing — the wave simply holds where it is, and the
     *         match is about to be decided anyway
     */
    @Nullable
    BlockPos pickAttackTarget();

    /** The world random, used to roll ranged quantities when a training command starts. */
    RandomSource random();
}
