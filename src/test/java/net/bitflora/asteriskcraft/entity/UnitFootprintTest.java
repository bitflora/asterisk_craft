package net.bitflora.asteriskcraft.entity;

import net.bitflora.asteriskcraft.AsteriskCraft;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntityType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins the pathfinding consequences of unit hitbox sizes, which are load-bearing and easy to break by
 * accident when a model is retouched for looks.
 *
 * <p>{@code NodeEvaluator.prepare} sizes a pathfinding node's footprint as
 * {@code floor(width + 1) x floor(height + 1) x floor(width + 1)} — <b>floor of the dimension plus
 * one, not {@code ceil} of the dimension</b>. The difference is invisible until it bites: a unit
 * exactly 2.0 tall needs <em>three</em> blocks of vertical clearance to path, so it silently refuses
 * doorways and 2-high tunnels that a 1.99-tall unit walks through. Both Protoss ground units were in
 * that state, from a comment that stated the rule as {@code ceil}.
 *
 * <p>Rooted units (Photon Cannon, Sunken Colony) and flyers are excluded: they never path through
 * {@code WalkNodeEvaluator}.
 */
class UnitFootprintTest {

    /** Every unit that walks, and therefore every unit these rules apply to. */
    private static EntityType<?>[] groundUnits() {
        return new EntityType<?>[]{
                AsteriskCraft.PROBE.get(), AsteriskCraft.ZEALOT.get(), AsteriskCraft.DRAGOON.get(),
                AsteriskCraft.DRONE.get(), AsteriskCraft.ZERGLING.get(), AsteriskCraft.HYDRALISK.get(),
        };
    }

    /** The vanilla rule, restated here so a reader can see why the bounds below are what they are. */
    private static int nodeExtent(float dimension) {
        return Mth.floor(dimension + 1.0f);
    }

    @Test
    void theRoundingRuleIsFloorOfPlusOneNotCeil() {
        // Documents the trap directly: these two are one hundredth of a block apart and differ by a
        // whole block of required clearance. If someone "simplifies" nodeExtent to ceil, this fails.
        assertEquals(2, nodeExtent(1.99f), "1.99 tall fits a 2-high opening");
        assertEquals(3, nodeExtent(2.0f), "a flat 2.0 demands 3 blocks of clearance");
        assertEquals(1, nodeExtent(0.8f));
        assertEquals(2, nodeExtent(1.1f), "anything 1.0 or wider takes two pathfinding nodes");
    }

    @Test
    void everyGroundUnitFitsATwoHighOpening() {
        for (EntityType<?> type : groundUnits()) {
            assertTrue(nodeExtent(type.getHeight()) <= 2,
                    type.getDescriptionId() + " is " + type.getHeight()
                            + " tall, so it needs " + nodeExtent(type.getHeight())
                            + " blocks of clearance and cannot path through a doorway. Keep it under 2.0.");
        }
    }

    @Test
    void onlyTheDragoonIsWiderThanOnePathfindingNode() {
        // A deliberate exception, documented on its registration: the walker's silhouette is worth the
        // cost, and the crowding it used to cause is handled in the command layer instead. Every other
        // ground unit must stay one node wide so it can squeeze through a 1-block gap.
        for (EntityType<?> type : groundUnits()) {
            int width = nodeExtent(type.getWidth());
            if (type == AsteriskCraft.DRAGOON.get()) {
                assertEquals(2, width, "the Dragoon's two-node width is deliberate — see AsteriskCraft");
            } else {
                assertEquals(1, width, type.getDescriptionId() + " must fit through a one-block gap");
            }
        }
    }
}
