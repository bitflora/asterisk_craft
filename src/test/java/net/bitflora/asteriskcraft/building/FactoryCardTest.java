package net.bitflora.asteriskcraft.building;

import net.bitflora.asteriskcraft.race.Races;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.Block;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A unit factory trains units of its own race's roster and does nothing else — it has no worker to
 * make and no kits to sell, both of which are a base's job. {@link FactoryBlockEntity} therefore
 * ignores any other action rather than branching on it, and a card that carried one would be a
 * button that silently did nothing when pressed.
 *
 * <p>The roster check is the same one {@code race.RacesTest} makes of a base's card, and for the
 * same reason: a card names a unit by the id a build script spells, so a typo is a dead button that
 * nothing else in the build would notice.
 */
class FactoryCardTest {

    @Test
    void everyFactoryCardOnlyTrainsUnitsItsOwnRaceCanBuild() {
        for (Block block : BuiltInRegistries.BLOCK) {
            if (!(block instanceof FactoryBlock factory)) {
                continue;
            }
            String name = String.valueOf(BuiltInRegistries.BLOCK.getKey(block));
            for (ProductionKind.OptionView option : factory.production().options()) {
                ProductionKind.Action.TrainUnit train = assertInstanceOf(
                        ProductionKind.Action.TrainUnit.class, option.action(),
                        name + ": a factory card may only train units — a worker or a kit belongs "
                                + "on a base's card, and this button would do nothing");
                assertTrue(Races.of(factory.defence().race()).roster().resolve(train.rosterId()).isPresent(),
                        name + ": trains '" + train.rosterId() + "', which its race's roster "
                                + "doesn't resolve");
            }
        }
    }
}
