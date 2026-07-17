package net.bitflora.asteriskcraft.game;

import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Pure-logic guard for the win/lose decision. The world-facing side ({@code onCoreDestroyed} —
 * attachment reads/writes, broadcasts) needs a live server, so it is exercised via runClient; the
 * decision itself is a pure function and fully testable here.
 */
class GameOutcomeTest {
    private static final BlockPos NEXUS = new BlockPos(0, 64, 0);
    private static final List<BlockPos> THREE_HIVES =
            List.of(new BlockPos(100, 64, 0), new BlockPos(112, 64, -7), new BlockPos(112, 64, 7));

    @Test
    void destroyingTheNexusIsDefeat() {
        assertEquals(GameOutcome.Result.DEFEAT, GameOutcome.decide(NEXUS, THREE_HIVES, NEXUS, false));
    }

    @Test
    void razingOneOfSeveralHivesIsNotYetVictory() {
        assertEquals(GameOutcome.Result.HIVE_RAZED, GameOutcome.decide(NEXUS, THREE_HIVES, THREE_HIVES.get(0), false));
    }

    @Test
    void razingTheLastHiveIsVictory() {
        List<BlockPos> lastHive = List.of(THREE_HIVES.get(0));
        assertEquals(GameOutcome.Result.VICTORY, GameOutcome.decide(NEXUS, lastHive, THREE_HIVES.get(0), false));
    }

    @Test
    void destroyingAnUnrelatedBlockDoesNothing() {
        assertEquals(GameOutcome.Result.NONE, GameOutcome.decide(NEXUS, THREE_HIVES, new BlockPos(5, 64, 5), false));
    }

    @Test
    void nothingHappensOnceTheMatchIsOver() {
        assertEquals(GameOutcome.Result.NONE, GameOutcome.decide(NEXUS, THREE_HIVES, NEXUS, true));
        assertEquals(GameOutcome.Result.NONE, GameOutcome.decide(NEXUS, List.of(THREE_HIVES.get(0)), THREE_HIVES.get(0), true));
    }
}
