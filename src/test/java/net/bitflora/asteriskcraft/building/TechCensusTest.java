package net.bitflora.asteriskcraft.building;

import net.bitflora.asteriskcraft.AsteriskCraft;
import net.bitflora.asteriskcraft.faction.Faction;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The tech prerequisite's ownership rule, exercised through the pure overload that takes the census
 * outright instead of a level — the same split {@link PsiField} draws between its geometry and the
 * chunk walk that feeds it, so the rule is testable without a running server. What actually enrols a
 * structure (a finished build countdown) is a tick-order question and is verified in-game instead.
 */
class TechCensusTest {

    private static final Identifier POOL = AsteriskCraft.id("spawning_pool_core");
    private static final Identifier SPIRE = AsteriskCraft.id("spire_core");

    private static TechCensus.Entry owned(Faction faction, Identifier block) {
        return new TechCensus.Entry(faction, block, BlockPos.ZERO);
    }

    @Test
    void anEmptyCensusHoldsNothing() {
        assertFalse(TechCensus.holds(List.of(), Faction.BLUE, POOL));
    }

    @Test
    void anArmyThatOwnsTheBuildingHoldsIt() {
        assertTrue(TechCensus.holds(List.of(owned(Faction.BLUE, POOL)), Faction.BLUE, POOL));
    }

    /** Tech is per army, so standing next to the enemy's Spawning Pool unlocks nothing. */
    @Test
    void theOtherSidesBuildingDoesNotCount() {
        assertFalse(TechCensus.holds(List.of(owned(Faction.RED, POOL)), Faction.BLUE, POOL));
    }

    /** One tech building is not another: a Spawning Pool never stands in for a Spire. */
    @Test
    void aDifferentBuildingDoesNotCount() {
        assertFalse(TechCensus.holds(List.of(owned(Faction.BLUE, POOL)), Faction.BLUE, SPIRE));
    }

    @Test
    void oneArmyCanHoldSeveralDifferentBuildings() {
        List<TechCensus.Entry> census = List.of(owned(Faction.BLUE, POOL), owned(Faction.BLUE, SPIRE));
        assertTrue(TechCensus.holds(census, Faction.BLUE, POOL));
        assertTrue(TechCensus.holds(census, Faction.BLUE, SPIRE));
    }

    /**
     * A mirror match is an ordinary match: both sides own the same block, and each is answered for
     * its own. This is the case a census keyed by race rather than by side would get wrong.
     */
    @Test
    void bothSidesMayOwnTheSameBuilding() {
        List<TechCensus.Entry> census = List.of(owned(Faction.BLUE, POOL), owned(Faction.RED, POOL));
        assertTrue(TechCensus.holds(census, Faction.BLUE, POOL));
        assertTrue(TechCensus.holds(census, Faction.RED, POOL));
    }

    /** NEUTRAL is the unfactioned world, which owns no army's tech however much of it is standing. */
    @Test
    void theUnfactionedWorldHoldsNothing() {
        assertFalse(TechCensus.holds(List.of(owned(Faction.BLUE, POOL)), Faction.NEUTRAL, POOL));
    }
}
