package net.bitflora.asteriskcraft.stats;

import net.bitflora.asteriskcraft.faction.Race;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The balance table's file format, which became load-bearing the moment the numbers moved out of
 * Java: a typo that used to be a compile error is now a cell, and this is what still catches it.
 *
 * <p>Deliberately about the <em>format</em> and not about any unit's numbers — the shipped table's
 * design invariants live in {@link UnitStatsTest}, which reads the real file.
 */
class UnitStatCsvTest {

    private static final String HEADER =
            "race,id,health,armor,speed,knockback_resistance,step_height,follow_range,shield,"
                    + "attack_damage,anti_air_bonus,attack_anim_ticks,range,cooldown,"
                    + "fly_speed,hover_height,path_length,detect_radius,detect_sweep,detect_reveal,"
                    + "bounce_hits,bounce_falloff,bounce_radius,splash_radius,splash_fraction,"
                    + "blast_radius,blast_fuse,build_ticks,cost";

    /** A minimal, valid row; every test below is this with one or two cells changed. */
    private static final String MINIMAL =
            "zerg,thing,10.0,0.0,0.25,0.0,0.6,32.0,0,,0.0,0,,,,,,,,,,,,,,,,20,any 25";

    @Test
    void aRowBecomesTheStatItDescribes() {
        UnitStat stat = one(MINIMAL);
        assertEquals("thing", stat.id());
        assertEquals(10.0, stat.maxHealth());
        assertEquals(0.25, stat.movementSpeed());
        assertEquals(20, stat.buildTicks());
    }

    @Test
    void raceIsReadButIsNotPartOfTheStat() {
        // The race column exists to group rosters; UnitStat deliberately carries no race of its own.
        assertEquals(Race.ZERG, parse(MINIMAL).get(0).race());
        assertEquals(Race.PROTOSS, parse(MINIMAL.replaceFirst("zerg", "protoss")).get(0).race());
    }

    @Test
    void anEmptyAttackDamageMeansNoAttackRatherThanZero() {
        // The distinction the format has to keep: a worker registers no ATTACK_DAMAGE attribute at
        // all, which is not the same as registering one of 0.
        assertTrue(one(MINIMAL).attackDamage().isEmpty());
        assertEquals(3.0, one(cells(MINIMAL, "attack_damage", "3.0")).attackDamageOrThrow());
    }

    @Test
    void anEmptyOptionalGroupLeavesTheUnitWithoutThatCapability() {
        UnitStat stat = one(MINIMAL);
        assertTrue(stat.ranged().isEmpty());
        assertTrue(stat.flight().isEmpty());
        assertTrue(stat.detection().isEmpty());
        assertTrue(stat.bounce().isEmpty());
        assertTrue(stat.blast().isEmpty());
    }

    @Test
    void aFilledOptionalGroupBecomesItsSubRecord() {
        String row = cells(MINIMAL, "attack_damage", "5.0", "range", "6.0", "cooldown", "20");
        assertEquals(new UnitStat.Ranged(6.0f, 20), one(row).rangedOrThrow());
    }

    @Test
    void aHalfFilledGroupIsRejected() {
        // Always a typo: the missing half would otherwise be read as a default nobody designed.
        String row = cells(MINIMAL, "attack_damage", "5.0", "range", "6.0");
        IllegalStateException e = assertThrows(IllegalStateException.class, () -> one(row));
        assertTrue(e.getMessage().contains("cooldown"), e.getMessage());
    }

    @Test
    void aSplashNeedsDamageToTakeAFractionOf() {
        // The rule is the builder's, not the parser's: splash_fraction is a share of attack_damage,
        // so a row that fills the group and leaves the damage blank has described nothing.
        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> one(cells(MINIMAL, "range", "2.0", "cooldown", "20",
                        "splash_radius", "1.0", "splash_fraction", "0.5")));
        assertTrue(e.getMessage().contains("attackDamage"), e.getMessage());
    }

    @Test
    void aSplashFractionAboveOneIsRejected() {
        // It is the falloff onto everyone who was not aimed at. Above 1 the bystanders take more
        // than the target, which is not a balance choice but a misread of what the column means.
        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> one(cells(MINIMAL, "attack_damage", "15.0", "range", "2.0", "cooldown", "20",
                        "splash_radius", "1.0", "splash_fraction", "1.5")));
        assertTrue(e.getMessage().contains("damageFraction"), e.getMessage());
    }

    @Test
    void aSplashRadiusOfZeroIsRejected() {
        // Zero reaches nobody, so the row would claim a splash and behave as a plain single hit.
        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> one(cells(MINIMAL, "attack_damage", "15.0", "range", "2.0", "cooldown", "20",
                        "splash_radius", "0.0", "splash_fraction", "0.5")));
        assertTrue(e.getMessage().contains("radius"), e.getMessage());
    }

    @Test
    void aSingleResourceCostIsOneAlternativeOfOneLine() {
        UnitCost cost = one(cells(MINIMAL, "cost", "any 25")).cost();
        assertEquals(List.of(List.of(new ResourceAmount(Resource.ANY, 25))), cost.alternatives());
    }

    @Test
    void plusJoinsLinesThatMustAllBePaid() {
        UnitCost cost = one(cells(MINIMAL, "cost", "wood 100 + stone 50")).cost();
        assertEquals(1, cost.alternatives().size());
        assertEquals(List.of(new ResourceAmount(Resource.WOOD, 100), new ResourceAmount(Resource.STONE, 50)),
                cost.alternatives().get(0));
    }

    @Test
    void barSeparatesAlternativesAndKeepsTheirOrder() {
        // Order is load-bearing: it is the order of the buttons on a base's command card.
        UnitCost cost = one(cells(MINIMAL, "cost", "wood 50 | stone 50")).cost();
        assertEquals(2, cost.alternatives().size());
        assertEquals(Resource.WOOD, cost.alternatives().get(0).get(0).resource());
        assertEquals(Resource.STONE, cost.alternatives().get(1).get(0).resource());
    }

    @Test
    void aResourceMayBeSpelledByItsEnumNameOrItsPlayerFacingLabel() {
        assertEquals(one(cells(MINIMAL, "cost", "stone 50")).cost(),
                one(cells(MINIMAL, "cost", "cobblestone 50")).cost());
    }

    @Test
    void anEmptyCostMeansTheUnitIsNeverTrained() {
        String row = cells(MINIMAL, "cost", "none", "build_ticks", "0");
        assertEquals(UnitCost.NONE, one(row).cost());
        assertEquals(UnitCost.NONE, one(cells(MINIMAL, "cost", "", "build_ticks", "0")).cost());
    }

    @Test
    void anUnknownResourceNamesTheCellItIsIn() {
        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> one(cells(MINIMAL, "cost", "gravel 50")));
        assertTrue(e.getMessage().contains("cost"), e.getMessage());
        assertTrue(e.getMessage().contains("gravel"), e.getMessage());
    }

    @Test
    void anUnparseableNumberNamesItsLineAndColumn() {
        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> one(cells(MINIMAL, "health", "lots")));
        assertTrue(e.getMessage().contains("test.csv:2"), e.getMessage());
        assertTrue(e.getMessage().contains("health"), e.getMessage());
    }

    @Test
    void aDesignRuleBrokenInTheFileStillReadsAsThatRule() {
        // The builder owns the design rules; the parser only adds the address of the offending row.
        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> one(cells(MINIMAL, "build_ticks", "0")));
        assertTrue(e.getMessage().contains("test.csv:2"), e.getMessage());
        assertTrue(e.getMessage().contains("buildTicks"), e.getMessage());
    }

    @Test
    void twoRowsWithTheSameIdAreRejected() {
        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> UnitStatCsv.parse(List.of(HEADER, MINIMAL, MINIMAL), "test.csv"));
        assertTrue(e.getMessage().contains("duplicate id"), e.getMessage());
    }

    @Test
    void aMisspelledHeaderIsRejectedRatherThanIgnored() {
        String header = HEADER.replace(",armor,", ",armour,");
        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> UnitStatCsv.parse(List.of(header, MINIMAL), "test.csv"));
        assertTrue(e.getMessage().contains("armour"), e.getMessage());
    }

    @Test
    void aMissingColumnIsRejected() {
        String header = HEADER.replace(",armor", "");
        assertThrows(IllegalStateException.class, () -> UnitStatCsv.parse(List.of(header, MINIMAL), "test.csv"));
    }

    @Test
    void columnOrderIsNotLoadBearing() {
        // So a balance pass may drag a column next to the one it is comparing it against.
        List<String> names = Arrays.asList(HEADER.split(","));
        List<String> values = Arrays.asList(MINIMAL.split(",", -1));
        int from = names.indexOf("cost");
        List<String> reorderedNames = new java.util.ArrayList<>(names);
        List<String> reorderedValues = new java.util.ArrayList<>(values);
        reorderedNames.add(0, reorderedNames.remove(from));
        reorderedValues.add(0, reorderedValues.remove(from));
        List<UnitStatCsv.Row> rows = UnitStatCsv.parse(
                List.of(String.join(",", reorderedNames), String.join(",", reorderedValues)), "test.csv");
        assertEquals(one(MINIMAL).cost(), rows.get(0).stat().cost());
    }

    @Test
    void blankLinesAreSkipped() {
        assertEquals(1, UnitStatCsv.parse(List.of("", HEADER, "", MINIMAL, ""), "test.csv").size());
    }

    private static UnitStat one(String row) {
        return parse(row).get(0).stat();
    }

    private static List<UnitStatCsv.Row> parse(String row) {
        return UnitStatCsv.parse(List.of(HEADER, row), "test.csv");
    }

    /** Rewrites the named cells of a row, so a test states only what it is changing. */
    private static String cells(String row, String... columnsAndValues) {
        List<String> names = Arrays.asList(HEADER.split(","));
        String[] values = row.split(",", -1);
        for (int i = 0; i < columnsAndValues.length; i += 2) {
            int index = names.indexOf(columnsAndValues[i]);
            assertTrue(index >= 0, "no such column: " + columnsAndValues[i]);
            values[index] = columnsAndValues[i + 1];
        }
        return String.join(",", values);
    }
}
