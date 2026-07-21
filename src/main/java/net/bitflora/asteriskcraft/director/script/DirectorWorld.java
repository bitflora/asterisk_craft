package net.bitflora.asteriskcraft.director.script;

import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;

import java.util.List;
import java.util.UUID;

/**
 * The side-effect seam the {@link ScriptInterpreter} drives — every interaction with the live
 * world goes through this interface so the VM itself stays pure and unit-testable against a fake.
 * The real implementation is {@code ZergDirector}.
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
     * Attempts to pay for and spawn one unit of {@code unitName}, held near its producing building
     * with a guard order until the batch completes. On {@link SpawnResult#SPAWNED} the new unit's
     * UUID is appended to {@code out}.
     */
    SpawnResult canAffordAndSpawn(String unitName, List<UUID> out);

    /** Orders the given (possibly since-deceased) units to attack-move to {@code dest} together. */
    void orderMove(List<UUID> unitIds, BlockPos dest);

    /** The enemy base the waves march on (the player's Nexus). */
    BlockPos nexus();

    /** The world random, used to roll ranged quantities when a training command starts. */
    RandomSource random();
}
