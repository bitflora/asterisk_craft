package net.bitflora.asteriskcraft.building;

import net.bitflora.asteriskcraft.race.RaceProfile;
import net.bitflora.asteriskcraft.race.Races;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Guards how buildings take damage: shields soak first, HP is what actually collapses the building,
 * and a building caught mid-warp comes out of the warp having sustained twice what it was hit for.
 *
 * <p>Only the pure arithmetic and the design constants are covered — {@link BuildingDefense#damage}
 * and {@link BuildingDefense#tickWarpIn} need a live {@code ServerLevel} for their effects and for
 * {@code destroyBlock}, so the warp-in and collapse of a real building are verified via
 * {@code runClient}.
 */
class BuildingDefenseTest {

    @Test
    void buildingStatsMatchDesign() {
        assertEquals(250, GatewayBlockEntity.MAX_HEALTH);
        assertEquals(250, GatewayBlockEntity.SHIELD);
        assertEquals(20 * 60, GatewayBlockEntity.WARP_TICKS, "the Gateway warps in over 1 minute");
        for (RaceProfile profile : Races.all()) {
            assertTrue(profile.baseHealth() > GatewayBlockEntity.MAX_HEALTH,
                    profile.race() + "'s base is the building you lose the game with, so it must be"
                            + " sturdier than a unit factory");
        }
    }

    /**
     * A kit-stamped building has to be <em>told</em> to warp in. It arrives carrying the block-entity
     * NBT the structure block captured in the design world, where it was standing finished — a spent
     * countdown and full pools — so placement resets both rather than trusting what it was handed.
     */
    @Test
    void aBuildingToldToWarpStartsItsCountdownOverAtHalfStrength() {
        BuildingDefense defense = new BuildingDefense(GatewayBlockEntity.MAX_HEALTH,
                GatewayBlockEntity.SHIELD, GatewayBlockEntity.WARP_TICKS);
        defense.skipWarpIn(); // the state a stamped template effectively leaves behind
        assertFalse(defense.isWarping());

        assertTrue(defense.restartWarpIn());
        assertTrue(defense.isWarping());
        assertEquals(GatewayBlockEntity.WARP_TICKS, defense.warpTicksRemaining());
        assertEquals(125, defense.health(), "a warping building stands at half its HP");
        assertEquals(125.0f, defense.shield(), "and half its shields");
    }

    @Test
    void aBuildingWithNoWarpPhaseCannotBeToldToWarp() {
        // A Hive. Refusing here is what stops a scaffold being raised with no countdown to fill it in.
        BuildingDefense defense = new BuildingDefense(Races.ZERG.baseHealth(), 0, 0);
        assertFalse(defense.restartWarpIn());
        assertFalse(defense.isWarping());
        assertEquals(Races.ZERG.baseHealth(), defense.health());
    }

    @Test
    void shieldsSoakDamageBeforeHp() {
        BuildingDefense.Hit hit = BuildingDefense.resolve(250, 250.0f, 20);
        assertEquals(250, hit.health(), "HP is untouched while shields hold");
        assertEquals(230.0f, hit.shield());
        assertTrue(hit.absorbed());
        assertFalse(hit.razed());
    }

    @Test
    void damageBeyondTheShieldsSpillsOntoHp() {
        BuildingDefense.Hit hit = BuildingDefense.resolve(250, 5.0f, 20);
        assertEquals(0.0f, hit.shield(), "the shields are spent");
        assertEquals(235, hit.health(), "the remaining 15 lands on HP");
        assertFalse(hit.absorbed());
    }

    @Test
    void aBuildingIsRazedWhenHpRunsOut() {
        BuildingDefense.Hit hit = BuildingDefense.resolve(15, 0.0f, 20);
        assertEquals(0, hit.health(), "HP floors at zero rather than going negative");
        assertTrue(hit.razed());
    }

    @Test
    void aWarpingBuildingHasHalfItsPools() {
        assertEquals(125.0f, WarpInVulnerability.warpPool(GatewayBlockEntity.MAX_HEALTH));
        assertEquals(125.0f, WarpInVulnerability.warpPool(GatewayBlockEntity.SHIELD));
    }

    /**
     * The warp-in vulnerability, end to end on a Gateway: hits land against the halved pools, then
     * finishing the warp scales what survived back onto the full ones — leaving it down twice what it
     * was hit for.
     */
    @Test
    void damageTakenDuringWarpIsSustainedTwiceOver() {
        int damage = 200;
        int health = Math.round(WarpInVulnerability.warpPool(GatewayBlockEntity.MAX_HEALTH));
        float shield = WarpInVulnerability.warpPool(GatewayBlockEntity.SHIELD);

        BuildingDefense.Hit hit = BuildingDefense.resolve(health, shield, damage);
        assertFalse(hit.razed(), "125 shields + 125 HP survives a 200-point pounding, barely");

        float finishedPool = GatewayBlockEntity.MAX_HEALTH + GatewayBlockEntity.SHIELD;
        float leftStanding = WarpInVulnerability.onWarpComplete(hit.health())
                + WarpInVulnerability.onWarpComplete(hit.shield());
        assertEquals(2.0f * damage, finishedPool - leftStanding,
                "a Gateway caught mid-warp comes out down twice what it was hit for");
    }
}
