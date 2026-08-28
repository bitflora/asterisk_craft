package net.bitflora.asteriskcraft.building;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A {@link StructureBlock} is only a building because a {@link StructureBlockEntity} stands behind
 * it — that block entity is where its build time runs and where its siege HP lives. A core block
 * registered without being listed on the block entity type still places, still looks right and
 * still costs the player its price; it simply stands up instantly and can be dug out with a pick.
 * Nothing at runtime complains, which is why this is checked here.
 */
class StructureBlockEntityTest {

    @Test
    void everyStructureBlockIsListedOnTheBlockEntityTypeItCreates() {
        for (Block block : BuiltInRegistries.BLOCK) {
            if (!(block instanceof StructureBlock structure)) {
                continue;
            }
            BlockState state = structure.defaultBlockState();
            // Asked of whichever type the block actually builds rather than of one named here, so a
            // factory (which registers its own) is checked as strictly as a plain structure.
            BlockEntityType<?> type = structure.newBlockEntity(BlockPos.ZERO, state).getType();
            assertTrue(type.isValid(state),
                    BuiltInRegistries.BLOCK.getKey(block) + ": creates a block entity its own type "
                            + "doesn't list it for, so the game drops it — no build time, no siege HP");
        }
    }

    @Test
    void everyStructureBlockDeclaresABuildTimeAndSomethingToBatterDown() {
        for (Block block : BuiltInRegistries.BLOCK) {
            if (!(block instanceof StructureBlock structure)) {
                continue;
            }
            StructureBlock.Defence defence = structure.defence();
            String name = String.valueOf(BuiltInRegistries.BLOCK.getKey(block));
            // A zero warp is how a pre-placed building says "already standing" (BuildingDefense),
            // so a kit-stamped one that declared zero would come up finished the instant it landed.
            assertTrue(defence.warpTicks() > 0, name + ": declares no build time");
            assertTrue(defence.health() > 0, name + ": declares no siege HP");
        }
    }
}
