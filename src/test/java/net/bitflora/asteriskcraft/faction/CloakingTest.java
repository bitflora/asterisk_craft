package net.bitflora.asteriskcraft.faction;

import net.bitflora.asteriskcraft.entity.Detector;
import net.bitflora.asteriskcraft.entity.protoss.DarkTemplarEntity;
import net.bitflora.asteriskcraft.entity.protoss.PhotonCannonEntity;
import net.bitflora.asteriskcraft.entity.protoss.ZealotEntity;
import net.bitflora.asteriskcraft.entity.zerg.HydraliskEntity;
import net.bitflora.asteriskcraft.entity.zerg.LurkerEntity;
import net.bitflora.asteriskcraft.entity.zerg.SporeColonyEntity;
import net.bitflora.asteriskcraft.entity.zerg.ZerglingEntity;
import net.bitflora.asteriskcraft.stats.UnitCost;
import net.bitflora.asteriskcraft.stats.UnitStat;
import net.bitflora.asteriskcraft.stats.UnitStats;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Guards the cloak visibility rule that sits alongside faction hostility in every targeting
 * decision, and the class-level wiring it depends on.
 *
 * <p>Pure logic only, in the spirit of the note on {@code ProbeEconomyTest}: the detector sweep
 * needs a live {@code ServerLevel} to scan and the render half needs a client, so neither is
 * testable in the JUnit bootstrap. Those are covered by the {@code runClient} demo script instead.
 * What <em>is</em> pinned here is the whole truth table of {@link Cloaking#isVisibleTo}, the bitmask
 * underneath it, and the detector wiring — a marker interface is exactly the kind of thing that is
 * silently easy to drop, since nothing fails to compile without it.
 *
 * <p>Two units carry {@link Cloaked}, one per race, and they carry it differently: the Dark Templar
 * is cloaked always, and the Lurker only while it is burrowed. Cloak is opt-in and is meant to stay
 * rare — a unit that gets it essentially cannot be fought back at, so it is a property a unit is
 * designed around rather than one handed to a line trooper.
 */
class CloakingTest {

    private static final byte NOBODY = 0;

    // --- The visibility rule ---

    @Test
    void anUncloakedUnitIsAlwaysVisible() {
        // The overwhelmingly common case, and the one that must never cost anything: cloak is opt-in
        // and an ordinary unit is seen by everyone regardless of who detects what.
        assertTrue(Cloaking.isVisibleTo(false, Faction.BLUE, Faction.RED, NOBODY));
        assertTrue(Cloaking.isVisibleTo(false, Faction.RED, Faction.BLUE, NOBODY));
        assertTrue(Cloaking.isVisibleTo(false, Faction.NEUTRAL, Faction.BLUE, NOBODY));
    }

    @Test
    void anUndetectedCloakedUnitIsInvisibleToTheEnemy() {
        assertFalse(Cloaking.isVisibleTo(true, Faction.BLUE, Faction.RED, NOBODY),
                "an undetected cloaked unit must not exist as far as the enemy is concerned");
        assertFalse(Cloaking.isVisibleTo(true, Faction.RED, Faction.BLUE, NOBODY),
                "the rule is side-generic, not one-sided");
    }

    @Test
    void aCloakedUnitIsAlwaysVisibleToItsOwnSide() {
        // This is what keeps a cloaked unit commandable: the player never loses track of their own.
        assertTrue(Cloaking.isVisibleTo(true, Faction.BLUE, Faction.BLUE, NOBODY));
        assertTrue(Cloaking.isVisibleTo(true, Faction.RED, Faction.RED, NOBODY));
    }

    @Test
    void aDetectedCloakedUnitIsVisibleToTheDetectingFaction() {
        byte detectedByRed = Cloaking.with(NOBODY, Faction.RED);
        assertTrue(Cloaking.isVisibleTo(true, Faction.BLUE, Faction.RED, detectedByRed));
    }

    @Test
    void detectionIsPerFactionNotGlobal() {
        // One side's Spore Colony revealing a cloaked enemy must not reveal it to a third party.
        // Nothing in the MVP has three sides, which is exactly why this needs a test rather than a
        // playtest: a single-Faction field instead of a mask would pass every game played today.
        byte detectedByRed = Cloaking.with(NOBODY, Faction.RED);
        assertTrue(Cloaking.isDetectedBy(detectedByRed, Faction.RED));
        assertFalse(Cloaking.isDetectedBy(detectedByRed, Faction.NEUTRAL));
        assertFalse(Cloaking.isDetectedBy(detectedByRed, Faction.BLUE));
    }

    @Test
    void neutralsAreBlindToCloakedUnits() {
        // A wandering zombie carries no faction and no detector, so cloak works against the world
        // too. NEUTRAL is nobody else's own side, so the own-faction branch must not let it through.
        assertFalse(Cloaking.isVisibleTo(true, Faction.BLUE, Faction.NEUTRAL, NOBODY));
    }

    // --- The bitmask, since more than one faction may detect the same unit at once ---

    @Test
    void twoDetectingFactionsCoexistInTheMask() {
        byte mask = Cloaking.with(Cloaking.with(NOBODY, Faction.RED), Faction.BLUE);
        assertTrue(Cloaking.isDetectedBy(mask, Faction.RED));
        assertTrue(Cloaking.isDetectedBy(mask, Faction.BLUE));
    }

    @Test
    void clearingOneFactionLeavesTheOther() {
        // The failure this guards: one detector's reveal timing out and un-revealing the unit for
        // everybody, including a second detector still standing right next to it.
        byte both = Cloaking.with(Cloaking.with(NOBODY, Faction.RED), Faction.BLUE);
        byte onlyBlue = Cloaking.without(both, Faction.RED);
        assertFalse(Cloaking.isDetectedBy(onlyBlue, Faction.RED));
        assertTrue(Cloaking.isDetectedBy(onlyBlue, Faction.BLUE));
    }

    @Test
    void maskOperationsAreIdempotent() {
        // A reveal re-arms on every sweep and the countdown runs every tick, so both operations run
        // far more often than the state actually changes; neither may accumulate.
        byte once = Cloaking.with(NOBODY, Faction.RED);
        assertEquals(once, Cloaking.with(once, Faction.RED));
        assertEquals(NOBODY, Cloaking.without(Cloaking.without(once, Faction.RED), Faction.RED));
    }

    @Test
    void everyFactionFitsInTheMask() {
        // The mask is a byte. Faction is small and likely to stay so, but a silent overflow would
        // manifest as one race being permanently undetectable, which is not a fun bug to find.
        assertTrue(Faction.values().length <= Byte.SIZE,
                "DetectionAttachments.DETECTED_BY is a byte — it cannot hold this many factions");
    }

    // --- The wiring: markers are easy to drop, and nothing else notices ---

    @Test
    void eachRaceHasACloakedUnit() {
        // The marker is the entire implementation of a unit's cloak, so nothing else fails if it is
        // dropped by accident. Hence a test.
        assertTrue(Cloaked.class.isAssignableFrom(DarkTemplarEntity.class), "the Protoss cloaked unit");
        assertTrue(Cloaked.class.isAssignableFrom(LurkerEntity.class), "the Zerg cloaked unit");
    }

    @Test
    void aPermanentlyCloakedUnitNeedsNoStateToBeCloaked() {
        // The default the Dark Templar takes. Worth pinning because it is what keeps opting in to a
        // permanent cloak a single `implements` — if the default ever flipped to false, that unit
        // would silently stop being cloaked with nothing failing to compile.
        Cloaked always = new Cloaked() {
        };
        assertTrue(always.isCloakActive());
    }

    @Test
    void cloakIsAskedOfTheUnitNotOfItsClass() {
        // The Lurker's shape, and the reason Cloaked is not a bare instanceof any more: a surfaced
        // Lurker carries the marker but is not cloaked, and would be as unfightable as a buried one
        // if the rule went by class. Only the override is exercisable without a live entity — that a
        // *particular* Lurker's answer tracks its burrow depth needs a world, and is the runClient
        // demo's job.
        Cloaked conditional = new Cloaked() {
            @Override
            public boolean isCloakActive() {
                return false;
            }
        };
        assertFalse(conditional.isCloakActive());
    }

    @Test
    void lineUnitsDoNotCloak() {
        // Cloak is opt-in and meant to stay rare. The Zealot and Hydralisk are named specifically
        // because both carried it during the mechanism's spike and both were taken back off it: an
        // undetected attacker cannot be retaliated against, which is far too much to hand a unit at
        // a Zealot's price.
        //
        // Deliberately spot-checks rather than sweeping the roster: a sweep would turn "somebody
        // gave a unit cloak" into a test failure, and that is a design decision to review, not a
        // regression to block.
        assertFalse(Cloaked.class.isAssignableFrom(ZealotEntity.class));
        assertFalse(Cloaked.class.isAssignableFrom(HydraliskEntity.class));
        assertFalse(Cloaked.class.isAssignableFrom(ZerglingEntity.class));
    }

    @Test
    void eachRaceHasADetector() {
        // Cloak is only a mechanic if the other side has an answer to it; a detector-less faction
        // just loses. Detection is one specific building per race — deliberately not a property every
        // rooted defence carries, which is why the Sunken Colony (a static defence, and not a
        // detector) is absent here.
        assertTrue(Detector.class.isAssignableFrom(PhotonCannonEntity.class), "the Protoss detector");
        assertTrue(Detector.class.isAssignableFrom(SporeColonyEntity.class), "the Zerg detector");
    }

    @Test
    void everyDetectorHasAnEnvelopeInTheBalanceTable() {
        // Detector.detection() reads detectionOrThrow(), so a marker without a matching .detector()
        // entry would throw on the first sweep — in play, not here. Fail at build time instead.
        assertDetectorEnvelope(UnitStats.PHOTON_CANNON);
        assertDetectorEnvelope(UnitStats.SPORE_COLONY);
    }

    private static void assertDetectorEnvelope(UnitStat stat) {
        UnitStat.Detection detection = stat.detectionOrThrow();
        assertTrue(detection.radius() > 0.0, stat.id() + " must actually see something");
        assertTrue(detection.revealTicks() > detection.sweepInterval(),
                stat.id() + ": reveal must outlast the gap between sweeps or the reveal strobes");
    }

    @Test
    void aNonDetectorSaysSoLoudly() {
        // The other half of the same contract: asking a Zealot for a detection envelope is a bug in
        // the caller, and must not quietly answer zero.
        assertThrows(IllegalStateException.class, UnitStats.ZEALOT::detectionOrThrow);
    }

    @Test
    void aDetectorWhoseRevealIsShorterThanItsSweepIsRejectedAtBuildTime() {
        // The builder is where a bad tuning pass gets caught. A 60-tick sweep with a 20-tick reveal
        // would leave the unit visible for a third of the time and flickering the rest.
        assertThrows(IllegalStateException.class, () -> UnitStat.builder("strobe")
                .health(1.0).speed(0.0)
                .detector(16.0, 60, 20)
                .cost(UnitCost.NONE)
                .build());
    }
}
