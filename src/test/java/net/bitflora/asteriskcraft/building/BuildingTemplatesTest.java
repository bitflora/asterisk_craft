package net.bitflora.asteriskcraft.building;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Guards the two hand-authored structure templates the Protoss buildings are stamped from.
 *
 * <p>The templates are read straight out of the resource files rather than through
 * {@link BuildingTemplates}: {@code StructureTemplateManager} needs a running server's resource
 * manager, which the JUnit bootstrap has no equivalent of, so placement itself is verified with
 * {@code runClient}. Everything here therefore stays on raw NBT — no block/state lookups — which
 * is enough to cover what the placement code actually assumes about a template:
 * an odd-sized footprint (so a center column exists) holding exactly one core block, centered.
 * Re-exporting a redesigned building is expected to keep those true; its shape is free to change.
 */
class BuildingTemplatesTest {

    @Test
    void nexusTemplateHasOneCenteredCore() {
        assertCenteredCore("nexus", "asteriskcraft:nexus_core");
    }

    @Test
    void gatewayTemplateHasOneCenteredCore() {
        assertCenteredCore("gateway", "asteriskcraft:gateway_core");
    }

    private static void assertCenteredCore(String template, String coreBlockId) {
        CompoundTag tag = load(template);
        BlockPos size = readSize(tag);
        assertTrue(size.getX() > 0 && size.getY() > 0 && size.getZ() > 0,
                template + " template is empty: " + size);
        // BuildingTemplates anchors a template on the center column of its bottom layer, which only
        // exists when both horizontal extents are odd.
        assertTrue(size.getX() % 2 == 1 && size.getZ() % 2 == 1,
                template + " template must be odd-sized horizontally to have a center column, was " + size);

        List<BlockPos> cores = findBlocks(tag, coreBlockId);
        assertEquals(1, cores.size(), template + " template must contain exactly one " + coreBlockId);
        BlockPos core = cores.getFirst();
        assertEquals((size.getX() - 1) / 2, core.getX(), coreBlockId + " must sit on the center column");
        assertEquals((size.getZ() - 1) / 2, core.getZ(), coreBlockId + " must sit on the center column");
    }

    /** Every position holding {@code blockId}, in template-local coordinates. */
    private static List<BlockPos> findBlocks(CompoundTag tag, String blockId) {
        ListTag palette = tag.getListOrEmpty("palette");
        List<Integer> matching = new ArrayList<>();
        for (int i = 0; i < palette.size(); i++) {
            if (blockId.equals(palette.getCompoundOrEmpty(i).getStringOr("Name", ""))) {
                matching.add(i);
            }
        }

        ListTag blocks = tag.getListOrEmpty("blocks");
        List<BlockPos> found = new ArrayList<>();
        for (int i = 0; i < blocks.size(); i++) {
            CompoundTag block = blocks.getCompoundOrEmpty(i);
            if (matching.contains(block.getIntOr("state", -1))) {
                found.add(readPos(block.getListOrEmpty("pos")));
            }
        }
        return found;
    }

    private static BlockPos readSize(CompoundTag tag) {
        return readPos(tag.getListOrEmpty("size"));
    }

    private static BlockPos readPos(ListTag list) {
        assertEquals(3, list.size(), "expected a 3-element position/size list");
        return new BlockPos(list.getIntOr(0, 0), list.getIntOr(1, 0), list.getIntOr(2, 0));
    }

    private static CompoundTag load(String template) {
        String path = "/data/asteriskcraft/structure/" + template + ".nbt";
        try (InputStream in = BuildingTemplatesTest.class.getResourceAsStream(path)) {
            assertNotNull(in, "missing structure template resource " + path);
            return NbtIo.readCompressed(in, NbtAccounter.unlimitedHeap());
        } catch (IOException e) {
            throw new AssertionError("could not read " + path, e);
        }
    }
}
