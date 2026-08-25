package net.bitflora.asteriskcraft.entity;

import net.bitflora.asteriskcraft.AsteriskCraft;
import net.bitflora.asteriskcraft.stats.UnitStat;
import net.bitflora.asteriskcraft.stats.UnitStats;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntityType;
import org.junit.jupiter.api.Test;

import java.util.Set;

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
 *
 * <p>Width has a second consequence beyond which gaps a unit fits, and it is the one that bites in
 * play: a two-node-wide unit halts half its width short of a ledge, while {@code MoveControl.tick}
 * only fires the jump that clears a 1-block rise once the mob is within {@code sqrt(max(1, width))}
 * of its waypoint. Since {@code Attributes.STEP_HEIGHT} defaults to 0.6 — under a full block — such a
 * unit stalls on hills unless it is given a step height it can walk them with.
 */
class UnitFootprintTest {

    /**
     * A walking unit's registered hitbox paired with its balance-table entry. The two live apart on
     * purpose — {@code stats} is registry-free so the whole table loads in a plain unit test — but
     * the pathfinding rules below span both, since a hitbox size decides which of them apply and a
     * stat decides whether the unit can cope.
     */
    private record Ground(EntityType<?> type, UnitStat stat) {
    }

    /** Every unit that walks, and therefore every unit these rules apply to. */
    private static Ground[] groundUnits() {
        return new Ground[]{
                new Ground(AsteriskCraft.PROBE.get(), UnitStats.PROBE),
                new Ground(AsteriskCraft.SCV.get(), UnitStats.SCV),
                new Ground(AsteriskCraft.ZEALOT.get(), UnitStats.ZEALOT),
                new Ground(AsteriskCraft.DRAGOON.get(), UnitStats.DRAGOON),
                new Ground(AsteriskCraft.DARK_TEMPLAR.get(), UnitStats.DARK_TEMPLAR),
                new Ground(AsteriskCraft.DRONE.get(), UnitStats.DRONE),
                new Ground(AsteriskCraft.ZERGLING.get(), UnitStats.ZERGLING),
                new Ground(AsteriskCraft.HYDRALISK.get(), UnitStats.HYDRALISK),
                new Ground(AsteriskCraft.LURKER.get(), UnitStats.LURKER),
                new Ground(AsteriskCraft.ULTRALISK.get(), UnitStats.ULTRALISK),
                new Ground(AsteriskCraft.INFESTED_VILLAGER.get(), UnitStats.INFESTED_VILLAGER),
                new Ground(AsteriskCraft.MARINE.get(), UnitStats.MARINE),
                new Ground(AsteriskCraft.FIREBAT.get(), UnitStats.FIREBAT),
        };
    }

    /**
     * The units allowed to take two pathfinding nodes of width, each a deliberate exception argued
     * for on its own registration: the Dragoon's walker silhouette, and the Ultralisk being a
     * Zergling at double size. Everything else must fit through a 1-block gap.
     */
    private static Set<EntityType<?>> wideUnits() {
        return Set.of(AsteriskCraft.DRAGOON.get(), AsteriskCraft.ULTRALISK.get());
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
        for (Ground ground : groundUnits()) {
            EntityType<?> type = ground.type();
            assertTrue(nodeExtent(type.getHeight()) <= 2,
                    type.getDescriptionId() + " is " + type.getHeight()
                            + " tall, so it needs " + nodeExtent(type.getHeight())
                            + " blocks of clearance and cannot path through a doorway. Keep it under 2.0.");
        }
    }

    @Test
    void everyWideUnitCanStepAFullBlockRatherThanJumpIt() {
        // The other half of being two nodes wide. STEP_HEIGHT defaults to 0.6 — under a full block —
        // so an ordinary unit jumps every 1-block rise, and MoveControl.tick only fires that jump
        // within sqrt(max(1, width)) of the waypoint. A wide unit halts half its width short of the
        // ledge and its waypoints sit on 2x2 corners, so it can sit below that threshold and stall on
        // hills. Anything wide enough to hit that must be able to walk the step instead.
        for (Ground ground : groundUnits()) {
            if (nodeExtent(ground.type().getWidth()) < 2) {
                continue;
            }
            assertTrue(ground.stat().stepHeight() >= 1.0,
                    ground.stat().id() + " is two pathfinding nodes wide, so it must step a full block"
                            + " rather than rely on MoveControl's jump trigger");
        }
    }

    @Test
    void noUnitStepsHighEnoughToScaleASheerTwoBlockWall() {
        // WalkNodeEvaluator.getNeighbors takes its climb allowance as floor(max(1, maxUpStep)), so a
        // step height of 2.0 silently turns every two-block wall into a walkable route — including
        // the walls of a base. Raising a step height is meant to change how a path is executed, never
        // which paths exist.
        for (UnitStat stat : UnitStats.all()) {
            assertEquals(1, Mth.floor(Math.max(1.0f, (float) stat.stepHeight())),
                    stat.id() + ": step height must stay under 2.0");
        }
    }

    @Test
    void onlyTheDesignatedHeaviesAreWiderThanOnePathfindingNode() {
        // Two deliberate exceptions, each documented on its registration; the crowding they cause is
        // handled in the command layer instead. Every other ground unit must stay one node wide so it
        // can squeeze through a 1-block gap. A unit growing into this set is a design decision, not
        // something a model retouch should be able to do quietly.
        Set<EntityType<?>> wide = wideUnits();
        for (Ground ground : groundUnits()) {
            EntityType<?> type = ground.type();
            int width = nodeExtent(type.getWidth());
            if (wide.contains(type)) {
                assertEquals(2, width,
                        type.getDescriptionId() + "'s two-node width is deliberate — see AsteriskCraft");
            } else {
                assertEquals(1, width, type.getDescriptionId() + " must fit through a one-block gap");
            }
        }
    }
}
