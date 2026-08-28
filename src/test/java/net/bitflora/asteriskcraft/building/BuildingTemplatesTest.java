package net.bitflora.asteriskcraft.building;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Guards the hand-authored structure templates every building is stamped from, and the
 * site-preparation geometry they are stamped onto.
 *
 * <p>The templates are read straight out of the resource files rather than through
 * {@link BuildingTemplates}: {@code StructureTemplateManager} needs a running server's resource
 * manager, which the JUnit bootstrap has no equivalent of, so placement itself is verified with
 * {@code runClient}. Everything here therefore stays on raw NBT — no block/state lookups — which
 * covers what the placement code actually assumes about a template: that it holds exactly one core
 * block, which is what anchors it in the world. Re-exporting a redesigned building is expected to
 * keep that true; its shape and bounding box are free to change.
 */
class BuildingTemplatesTest {

    @Test
    void nexusTemplateHasOneCore() {
        assertSingleCore("nexus", "asteriskcraft:nexus_core");
    }

    @Test
    void gatewayTemplateHasOneCore() {
        assertSingleCore("gateway", "asteriskcraft:gateway_core");
    }

    @Test
    void hiveTemplateHasOneCore() {
        assertSingleCore("hive", "asteriskcraft:hive_core");
    }

    @Test
    void pylonTemplateHasOneCore() {
        assertSingleCore("pylon", "asteriskcraft:pylon_core");
    }

    @Test
    void commandCenterTemplateHasOneCore() {
        assertSingleCore("command_center", "asteriskcraft:command_center_core");
    }

    @Test
    void barracksTemplateHasOneCore() {
        assertSingleCore("barracks", "asteriskcraft:barracks_core");
    }

    @Test
    void stargateTemplateHasOneCore() {
        assertSingleCore("stargate", "asteriskcraft:stargate_core");
    }

    @Test
    void spawningPoolTemplateHasOneCore() {
        assertSingleCore("spawning_pool", "asteriskcraft:spawning_pool_core");
    }

    @Test
    void spireTemplateHasOneCore() {
        assertSingleCore("spire", "asteriskcraft:spire_core");
    }

    @Test
    void starportTemplateHasOneCore() {
        assertSingleCore("starport", "asteriskcraft:starport_core");
    }

    // --- declared footprints (the client's placement outline) vs. the templates themselves ---
    // A client can't load a template — they live under data/ and only a server's
    // StructureTemplateManager reads them — so each kit declares its building's box up front. These
    // pin the declaration to the .nbt, so re-exporting a resized building fails the build here
    // instead of silently drawing the player an outline of the wrong volume.

    @Test
    void nexusFootprintMatchesTemplate() {
        assertFootprint("nexus", "asteriskcraft:nexus_core", BuildingTemplates.NEXUS_FOOTPRINT);
    }

    @Test
    void gatewayFootprintMatchesTemplate() {
        assertFootprint("gateway", "asteriskcraft:gateway_core", BuildingTemplates.GATEWAY_FOOTPRINT);
    }

    @Test
    void hiveFootprintMatchesTemplate() {
        assertFootprint("hive", "asteriskcraft:hive_core", BuildingTemplates.HIVE_FOOTPRINT);
    }

    @Test
    void pylonFootprintMatchesTemplate() {
        assertFootprint("pylon", "asteriskcraft:pylon_core", BuildingTemplates.PYLON_FOOTPRINT);
    }

    @Test
    void commandCenterFootprintMatchesTemplate() {
        assertFootprint("command_center", "asteriskcraft:command_center_core",
                BuildingTemplates.COMMAND_CENTER_FOOTPRINT);
    }

    @Test
    void barracksFootprintMatchesTemplate() {
        assertFootprint("barracks", "asteriskcraft:barracks_core", BuildingTemplates.BARRACKS_FOOTPRINT);
    }

    @Test
    void stargateFootprintMatchesTemplate() {
        assertFootprint("stargate", "asteriskcraft:stargate_core", BuildingTemplates.STARGATE_FOOTPRINT);
    }

    @Test
    void spawningPoolFootprintMatchesTemplate() {
        assertFootprint("spawning_pool", "asteriskcraft:spawning_pool_core",
                BuildingTemplates.SPAWNING_POOL_FOOTPRINT);
    }

    @Test
    void spireFootprintMatchesTemplate() {
        assertFootprint("spire", "asteriskcraft:spire_core", BuildingTemplates.SPIRE_FOOTPRINT);
    }

    @Test
    void starportFootprintMatchesTemplate() {
        assertFootprint("starport", "asteriskcraft:starport_core", BuildingTemplates.STARPORT_FOOTPRINT);
    }

    private static void assertFootprint(String template, String coreBlockId, BuildingTemplates.Footprint footprint) {
        CompoundTag tag = load(template);
        assertEquals(readPos(tag.getListOrEmpty("size")), new BlockPos(footprint.size()),
                "declared size of " + template + " no longer matches " + template + ".nbt");
        assertEquals(findBlocks(tag, coreBlockId).getFirst(), footprint.coreOffset(),
                "declared core offset of " + template + " no longer matches " + template + ".nbt");
    }

    private static void assertSingleCore(String template, String coreBlockId) {
        CompoundTag tag = load(template);
        BlockPos size = readPos(tag.getListOrEmpty("size"));
        assertTrue(size.getX() > 0 && size.getY() > 0 && size.getZ() > 0,
                template + " template is empty: " + size);

        List<BlockPos> cores = findBlocks(tag, coreBlockId);
        assertEquals(1, cores.size(), template + " template must contain exactly one " + coreBlockId);
        // The core anchors the building, so it has to be inside the template's own bounds.
        BlockPos core = cores.getFirst();
        assertTrue(core.getX() < size.getX() && core.getY() < size.getY() && core.getZ() < size.getZ(),
                coreBlockId + " at " + core + " lies outside the template bounds " + size);
    }

    // --- site-prep geometry, exercised through the pure planSitePrep (no live ServerLevel needed) ---
    // The probe is a Predicate<BlockPos> reporting whether an EXISTING world block is solid-render;
    // support fill treats anything non-solid (air OR fluids) as needing footing.

    /** Min corner of the prepared footprint — the template's bottom-layer corner, not its center. */
    private static final BlockPos MIN = new BlockPos(0, 64, 0);
    /** Flat ground: every block strictly below the platform is solid, so support fill stops immediately. */
    private static final Predicate<BlockPos> FLAT_GROUND = pos -> pos.getY() < MIN.getY();
    /** Support material fed to planSitePrep; the fill mechanism is material-agnostic. */
    private static final BlockState SUPPORT = Blocks.SMOOTH_QUARTZ.defaultBlockState();

    @Test
    void headroomIsClearedOverTheWholeFootprint() {
        Map<BlockPos, BlockState> edits = BuildingTemplates.planSitePrep(MIN, 7, 7, FLAT_GROUND, 5, 6, SUPPORT);
        BlockState air = Blocks.AIR.defaultBlockState();
        for (int dx = 0; dx < 7; dx++) {
            for (int dz = 0; dz < 7; dz++) {
                for (int dy = 1; dy <= 5; dy++) {
                    assertEquals(air, edits.get(MIN.offset(dx, dy, dz)),
                            "head-room not cleared at " + dx + "," + dy + "," + dz);
                }
            }
        }
        assertNull(edits.get(MIN.offset(7, 1, 0)), "prep must not spill outside the footprint");
        assertNull(edits.get(MIN.offset(0, 6, 0)), "prep must not clear above the requested head-room");
    }

    @Test
    void flatGroundGetsNoSupportFill() {
        Map<BlockPos, BlockState> edits = BuildingTemplates.planSitePrep(MIN, 7, 7, FLAT_GROUND, 5, 6, SUPPORT);
        assertFalse(edits.containsValue(SUPPORT), "flat ground should need no support fill");
    }

    @Test
    void nullSupportLeavesTheGroundAlone() {
        // The Protoss buildings sit on their own stonework, so nothing is stamped under them even
        // where the terrain falls away — head-room is still cleared.
        Predicate<BlockPos> probe = pos -> pos.getY() < MIN.getY() - 3; // a three-deep gap below
        Map<BlockPos, BlockState> edits = BuildingTemplates.planSitePrep(MIN, 5, 5, probe, 5, 6, null);
        for (int dy = -1; dy >= -6; dy--) {
            assertNull(edits.get(MIN.offset(0, dy, 0)), "nothing may be placed below the platform");
        }
        assertEquals(Blocks.AIR.defaultBlockState(), edits.get(MIN.above()), "head-room is still cleared");
    }

    @Test
    void dipIsFilledDownToGround() {
        // Column at world (3,0) has its ground three blocks below the platform; everything else is flat.
        Predicate<BlockPos> probe = pos -> (pos.getX() == 3 && pos.getZ() == 0)
                ? pos.getY() <= 60 : pos.getY() <= 63;
        Map<BlockPos, BlockState> edits = BuildingTemplates.planSitePrep(MIN, 7, 7, probe, 5, 6, SUPPORT);
        assertEquals(SUPPORT, edits.get(new BlockPos(3, 63, 0)));
        assertEquals(SUPPORT, edits.get(new BlockPos(3, 62, 0)));
        assertEquals(SUPPORT, edits.get(new BlockPos(3, 61, 0)));
        assertNull(edits.get(new BlockPos(3, 60, 0)), "fill must stop at the first solid ground block");
    }

    @Test
    void supportFillIsBoundedByMaxDepth() {
        // Column at world (3,3) is bottomless void; fill must cap at maxSupportDepth, never a runaway shaft.
        Predicate<BlockPos> probe = pos -> !(pos.getX() == 3 && pos.getZ() == 3) && pos.getY() <= 63;
        int maxDepth = 6;
        Map<BlockPos, BlockState> edits = BuildingTemplates.planSitePrep(MIN, 7, 7, probe, 5, maxDepth, SUPPORT);
        for (int dy = -1; dy >= -maxDepth; dy--) {
            assertEquals(SUPPORT, edits.get(MIN.offset(3, dy, 3)), "expected support at dy=" + dy);
        }
        assertNull(edits.get(MIN.offset(3, -maxDepth - 1, 3)), "fill must not exceed maxSupportDepth");
    }

    @Test
    void fluidPocketUnderPlatformIsFilled() {
        // A fluid pocket reads as non-solid, so the two water blocks below (2,2) get footing.
        Predicate<BlockPos> probe = pos -> (pos.getX() == 2 && pos.getZ() == 2)
                ? pos.getY() <= 61 : pos.getY() <= 63;
        Map<BlockPos, BlockState> edits = BuildingTemplates.planSitePrep(MIN, 7, 7, probe, 5, 6, SUPPORT);
        assertEquals(SUPPORT, edits.get(new BlockPos(2, 63, 2)));
        assertEquals(SUPPORT, edits.get(new BlockPos(2, 62, 2)));
        assertNull(edits.get(new BlockPos(2, 61, 2)), "fill stops at the solid floor under the water");
    }

    @Test
    void supportMaterialIsHonored() {
        // Zerg Hives fill their footing with mycelium — the same material as the creep around them.
        BlockState mycelium = Blocks.MYCELIUM.defaultBlockState();
        Predicate<BlockPos> probe = pos -> pos.getY() <= 62; // one air block below the platform
        Map<BlockPos, BlockState> edits = BuildingTemplates.planSitePrep(MIN, 7, 7, probe, 5, 6, mycelium);
        assertEquals(mycelium, edits.get(new BlockPos(0, 63, 0)), "support fill must use the given material");
        assertFalse(edits.containsValue(SUPPORT), "no quartz should appear when mycelium is the support material");
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
