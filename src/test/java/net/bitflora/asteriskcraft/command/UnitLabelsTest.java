package net.bitflora.asteriskcraft.command;

import net.bitflora.asteriskcraft.AsteriskCraft;
import net.bitflora.asteriskcraft.stats.UnitStat;
import net.bitflora.asteriskcraft.stats.UnitStats;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * {@link UnitLabels} is pure and registry-backed, and entity types do resolve in the bootstrapped
 * JUnit environment, so both halves are testable here: the formatter, and the invariant that keeps
 * a label unambiguous — no two units of the same faction sharing a letter.
 */
class UnitLabelsTest {

    @Test
    void formatsCountsAndLettersInOrder() {
        List<UnitGroupSnapshot.Entry> entries = List.of(
                new UnitGroupSnapshot.Entry(AsteriskCraft.ZEALOT.get(), 1),
                new UnitGroupSnapshot.Entry(AsteriskCraft.DRAGOON.get(), 3));
        assertEquals("1Z 3D", UnitLabels.format(entries));
    }

    @Test
    void entryOrderIsTheOrderGiven() {
        List<UnitGroupSnapshot.Entry> entries = List.of(
                new UnitGroupSnapshot.Entry(AsteriskCraft.DRAGOON.get(), 3),
                new UnitGroupSnapshot.Entry(AsteriskCraft.ZEALOT.get(), 1));
        assertEquals("3D 1Z", UnitLabels.format(entries));
    }

    @Test
    void aSingleTypeAndAnEmptyGroup() {
        assertEquals("12P", UnitLabels.format(
                List.of(new UnitGroupSnapshot.Entry(AsteriskCraft.PROBE.get(), 12))));
        assertEquals("", UnitLabels.format(List.of()));
    }

    @Test
    void beyondThreeTypesTheLabelCollapses() {
        List<UnitGroupSnapshot.Entry> entries = List.of(
                new UnitGroupSnapshot.Entry(AsteriskCraft.ZEALOT.get(), 1),
                new UnitGroupSnapshot.Entry(AsteriskCraft.DRAGOON.get(), 2),
                new UnitGroupSnapshot.Entry(AsteriskCraft.SCOUT.get(), 3),
                new UnitGroupSnapshot.Entry(AsteriskCraft.PROBE.get(), 4));
        assertEquals("1Z 2D 3S+", UnitLabels.format(entries));
    }

    @Test
    void everyUnitInTheTableIsReachableFromItsEntityType() {
        assertEquals("Z", UnitLabels.letterFor(AsteriskCraft.ZEALOT.get()));
        assertEquals("D", UnitLabels.letterFor(AsteriskCraft.DRAGOON.get()));
        assertEquals("K", UnitLabels.letterFor(AsteriskCraft.SUNKEN_COLONY.get()));
        assertEquals("S", UnitLabels.letterFor(AsteriskCraft.SPORE_COLONY.get()));
    }

    @Test
    void anUnlistedTypeFallsBackToItsFirstLetter() {
        // Never happens in play (only own-faction units enter a selection), but a label must not
        // come out blank if one ever does.
        assertEquals("Z", UnitLabels.letterFor(net.minecraft.world.entity.EntityType.ZOMBIE));
    }

    @Test
    void everyRosteredUnitHasALetter() {
        for (UnitStat stat : UnitStats.all()) {
            assertFalse(UnitLabels.letterForId(stat.id()).isEmpty(),
                    stat.id() + " has no unit-group letter");
        }
    }

    @Test
    void lettersAreUniqueWithinEachFaction() {
        assertNull(duplicateLetter(UnitStats.PROTOSS_ROSTER), "two Protoss units share a letter");
        assertNull(duplicateLetter(UnitStats.ZERG_ROSTER), "two Zerg units share a letter");
    }

    /** The first letter used twice in a roster, or null if they are all distinct. */
    private static String duplicateLetter(List<UnitStat> roster) {
        Map<String, String> seen = new HashMap<>();
        for (UnitStat stat : roster) {
            String previous = seen.put(UnitLabels.letterForId(stat.id()), stat.id());
            if (previous != null) {
                return UnitLabels.letterForId(stat.id());
            }
        }
        return null;
    }
}
