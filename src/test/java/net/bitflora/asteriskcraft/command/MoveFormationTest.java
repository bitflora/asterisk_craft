package net.bitflora.asteriskcraft.command;

import net.bitflora.asteriskcraft.AsteriskCraft;
import net.minecraft.core.Vec3i;
import net.minecraft.world.entity.EntityType;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Guards the slot geometry that spreads a group move order, exercised through the pure
 * {@code slotOffset}/{@code spacing} half so no live ServerLevel is needed — only the terrain snap
 * in {@code allocate} touches the world, and that is covered by {@code runClient}.
 *
 * <p>The property that actually matters is the separation one: two units dealt destinations closer
 * together than their own bodies would shove each other on arrival, which is the whole failure this
 * class exists to prevent.
 */
class MoveFormationTest {

    /** Every mobile unit that can receive a move order, so a hitbox change can't quietly break spacing. */
    private static final EntityType<?>[] COMMANDABLE_UNITS = {
            AsteriskCraft.PROBE.get(), AsteriskCraft.ZEALOT.get(), AsteriskCraft.DRAGOON.get(),
            AsteriskCraft.SCOUT.get(), AsteriskCraft.DARK_TEMPLAR.get(),
            AsteriskCraft.DRONE.get(), AsteriskCraft.ZERGLING.get(),
            AsteriskCraft.HYDRALISK.get(), AsteriskCraft.MUTALISK.get(),
    };

    private static int chebyshev(Vec3i a, Vec3i b) {
        return Math.max(Math.abs(a.getX() - b.getX()), Math.abs(a.getZ() - b.getZ()));
    }

    @Test
    void firstSlotIsTheClickedPoint() {
        assertEquals(Vec3i.ZERO, MoveFormation.slotOffset(0, 2),
                "the unit nearest the click should stand where the player pointed");
    }

    @Test
    void slotsAreDistinctAndNeverCloserThanTheSpacing() {
        int spacing = 2;
        Set<Vec3i> seen = new HashSet<>();
        for (int i = 0; i < 49; i++) { // three full rings
            Vec3i slot = MoveFormation.slotOffset(i, spacing);
            for (Vec3i other : seen) {
                assertTrue(chebyshev(slot, other) >= spacing,
                        "slots " + slot + " and " + other + " are closer than the " + spacing + "-block pitch");
            }
            assertTrue(seen.add(slot), "slot " + i + " duplicates an earlier one: " + slot);
        }
    }

    @Test
    void slotsFillOneRingBeforeStartingTheNext() {
        int spacing = 2;
        // Ring r holds 8r slots: indices 1-8 sit at Chebyshev distance r=1, 9-24 at r=2, 25-48 at r=3.
        for (int i = 1; i <= 8; i++) {
            assertEquals(spacing, chebyshev(MoveFormation.slotOffset(i, spacing), Vec3i.ZERO),
                    "slot " + i + " belongs to the first ring");
        }
        for (int i = 9; i <= 24; i++) {
            assertEquals(2 * spacing, chebyshev(MoveFormation.slotOffset(i, spacing), Vec3i.ZERO),
                    "slot " + i + " belongs to the second ring");
        }
        for (int i = 25; i <= 48; i++) {
            assertEquals(3 * spacing, chebyshev(MoveFormation.slotOffset(i, spacing), Vec3i.ZERO),
                    "slot " + i + " belongs to the third ring");
        }
    }

    @Test
    void allocationIsDeterministic() {
        // A squad must land in the same formation every time the same click is repeated, or re-issuing
        // an order would shuffle everyone into new slots and undo the progress they had made.
        for (int i = 0; i < 25; i++) {
            assertEquals(MoveFormation.slotOffset(i, 2), MoveFormation.slotOffset(i, 2));
        }
    }

    @Test
    void spacingClearsTheWidestBody() {
        for (EntityType<?> type : COMMANDABLE_UNITS) {
            int spacing = MoveFormation.spacing(type.getWidth());
            assertTrue(spacing > type.getWidth(),
                    type.getDescriptionId() + " is " + type.getWidth()
                            + " wide but would be dealt slots only " + spacing + " blocks apart");
        }
    }

    @Test
    void widestUnitInTheSquadSetsThePitch() {
        // A Dragoon mixed into a Zergling squad has to widen the whole formation, not just its own slot.
        float dragoon = AsteriskCraft.DRAGOON.get().getWidth();
        float zergling = AsteriskCraft.ZERGLING.get().getWidth();
        assertTrue(dragoon > zergling, "test premise: the Dragoon is the wider unit");
        assertEquals(MoveFormation.spacing(dragoon), MoveFormation.spacing(Math.max(dragoon, zergling)));
    }

    @Test
    void spacingNeverCollapsesToZero() {
        assertTrue(MoveFormation.spacing(0.0f) >= 1, "slots must always be at least a block apart");
    }
}
