package net.bitflora.asteriskcraft.building;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The command cards have to fit the fixed-size surfaces that draw them, and nothing at runtime
 * checks that they do — a card is a table of constants, and both surfaces size themselves from
 * constants of their own.
 *
 * <p>This exists because adding one button to the Hive's card silently pushed it past
 * {@link ProductionMenu#MAX_OPTIONS}, and the failure was an
 * {@code ArrayIndexOutOfBoundsException} out of {@code SimpleContainerData} while <em>rendering</em>
 * — a crash on right-clicking the building, not a wrong number on screen. The Hive's card is the one
 * that grows: it has no factory building to send unit production to, so every unit the swarm gains
 * is another button on it.
 */
class ProductionCardLayoutTest {

    @Test
    void noCardHasMoreOptionsThanTheMenuCarriesDataSlotsFor() {
        for (ProductionKind kind : ProductionKind.values()) {
            assertTrue(kind.options().size() <= ProductionMenu.MAX_OPTIONS,
                    kind + ": has " + kind.options().size() + " options but ProductionMenu carries "
                            + "queue data for only " + ProductionMenu.MAX_OPTIONS
                            + " — raise MAX_OPTIONS, or the screen reads off the end of the data array");
        }
    }

    @Test
    void noCardIsTallerThanTheTwoButtonRowsThePanelLeavesRoomFor() {
        // The panel's height is spent on the input bank's three rows, two button rows and the player
        // inventory (see ProductionMenu.IMAGE_HEIGHT). A third button row would start at
        // BUTTON_Y + 2 * BUTTON_ROW_SPACING and run straight into the player inventory at
        // PLAYER_INV_Y, so a column that grows a third button needs the panel to grow with it.
        int thirdRowTop = ProductionMenu.BUTTON_Y + 2 * ProductionMenu.BUTTON_ROW_SPACING;
        assertTrue(thirdRowTop + ProductionMenu.BUTTON_H > ProductionMenu.PLAYER_INV_Y,
                "the panel now has room for a third button row — relax this test rather than working around it");
        for (ProductionKind kind : ProductionKind.values()) {
            assertTrue(kind.maxRows() <= 2,
                    kind + ": stacks " + kind.maxRows() + " buttons in a column, but the panel only "
                            + "leaves room for 2 before they overlap the player inventory");
        }
    }

    /**
     * A kit button names a cost <em>alternative</em>, and a card that names one its cost doesn't
     * have is a table typo with no runtime branch to catch it: the tooltip is built at this enum's
     * class-init, so the whole mod fails to load rather than the button misbehaving. Pinning it here
     * turns that into a named test failure.
     */
    @Test
    void everyKitButtonNamesACostAlternativeThatExists() {
        for (ProductionKind kind : ProductionKind.values()) {
            for (ProductionKind.OptionView option : kind.options()) {
                if (!(option.action() instanceof ProductionKind.Action.GiveKit kit)) {
                    continue;
                }
                assertTrue(kit.alternative() >= 0 && kit.alternative() < kit.cost().alternatives().size(),
                        kind + ": a kit button pays alternative " + kit.alternative()
                                + " of a cost that has " + kit.cost().alternatives().size());
            }
        }
    }

    @Test
    void everyCardsButtonGridFitsThePanelWidth() {
        for (ProductionKind kind : ProductionKind.values()) {
            assertTrue(ProductionMenu.buttonStartX(kind.columns()) >= 0,
                    kind + ": its " + kind.columns() + " button columns are wider than the panel");
        }
    }
}
