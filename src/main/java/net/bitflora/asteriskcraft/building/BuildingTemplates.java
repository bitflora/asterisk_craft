package net.bitflora.asteriskcraft.building;

import net.bitflora.asteriskcraft.AsteriskCraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.templatesystem.LiquidSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Optional;

/**
 * Places the Protoss buildings from the {@code .nbt} structure templates in
 * {@code data/asteriskcraft/structure/}, so their shape is authored in-game with a structure
 * block and re-exported rather than written out in Java (the Zerg Hive is still code-defined —
 * see {@link BuildingLayouts}).
 *
 * <p>Every entry point takes the same {@code origin} as the code-defined layouts did: the center
 * column of the structure's bottom layer, i.e. the block the building stands on. The template's
 * own min corner is derived from that and its size, and the core block's position is looked up in
 * the template instead of being hardcoded as an offset — so a redesign that moves the core needs
 * no code change.
 */
public final class BuildingTemplates {
    private BuildingTemplates() {
    }

    public static final Identifier NEXUS = AsteriskCraft.id("nexus");
    public static final Identifier GATEWAY = AsteriskCraft.id("gateway");

    /**
     * Head-room cleared above the platform even for a short template, so a building is never warped
     * in with terrain pressing against its roof.
     */
    private static final int MIN_HEADROOM = 5;

    /** Templates are stamped in their authored orientation — no rotation, no mirroring. */
    private static StructurePlaceSettings settings() {
        return new StructurePlaceSettings()
                .setIgnoreEntities(true)
                // Without this, waterloggable stairs/slabs soak up the water they replace and a
                // shoreline base warps in flooded.
                .setLiquidSettings(LiquidSettings.IGNORE_WATERLOGGING);
    }

    /** The template's size, or null if it failed to load (a broken install, so it is logged). */
    public static @Nullable Vec3i size(ServerLevel level, Identifier template) {
        Optional<StructureTemplate> loaded = level.getStructureManager().get(template);
        if (loaded.isEmpty()) {
            AsteriskCraft.LOGGER.error("AsteriskCraft: missing structure template {}", template);
            return null;
        }
        return loaded.get().getSize();
    }

    /** Horizontal radius of a template's footprint around its center column. */
    public static int footprintRadius(Vec3i size) {
        return Math.max(size.getX() - 1, size.getZ() - 1) / 2;
    }

    /**
     * True if the template's whole bounding box over {@code origin} is free to build in. This is
     * the volume — {@link StructureTemplate} offers no way to enumerate just its non-air blocks —
     * so warping a building into a hillside is refused rather than carving it out.
     */
    public static boolean isSiteClear(ServerLevel level, BlockPos origin, Identifier template) {
        Vec3i size = size(level, template);
        if (size == null) {
            return false;
        }
        BlockPos min = minCorner(origin, size);
        for (int dx = 0; dx < size.getX(); dx++) {
            for (int dy = 0; dy < size.getY(); dy++) {
                for (int dz = 0; dz < size.getZ(); dz++) {
                    BlockState current = level.getBlockState(min.offset(dx, dy, dz));
                    if (!current.isAir() && !current.canBeReplaced()) {
                        return false;
                    }
                }
            }
        }
        return true;
    }

    /**
     * Preps the site and stamps the template centered on {@code origin}, returning the world
     * position of its {@code coreBlock} — or null if the template is missing or holds no core,
     * which leaves the caller free to bail out instead of guessing where the core landed.
     *
     * <p>{@code support} fills any gap under the footprint; pass null to leave the ground as it
     * lies. The Protoss buildings pass null so their own stonework isn't sat on a plinth of some
     * other material, at the cost of a platform edge that can overhang where the terrain falls away.
     */
    public static @Nullable BlockPos place(ServerLevel level, BlockPos origin, Identifier template,
            Block coreBlock, @Nullable BlockState support) {
        Optional<StructureTemplate> loaded = level.getStructureManager().get(template);
        if (loaded.isEmpty()) {
            AsteriskCraft.LOGGER.error("AsteriskCraft: missing structure template {}", template);
            return null;
        }
        StructureTemplate structure = loaded.get();
        Vec3i size = structure.getSize();
        BlockPos min = minCorner(origin, size);
        StructurePlaceSettings settings = settings();

        // Site prep first: it clears the head-room band the structure is about to occupy, so
        // running it afterwards would erase the building it just supported.
        BuildingLayouts.prepareSite(level, origin, (size.getX() - 1) / 2, (size.getZ() - 1) / 2,
                Math.max(size.getY() - 1, MIN_HEADROOM), support);
        structure.placeInWorld(level, min, min, settings, level.getRandom(), Block.UPDATE_ALL);

        List<StructureTemplate.StructureBlockInfo> cores = structure.filterBlocks(min, settings, coreBlock);
        if (cores.isEmpty()) {
            AsteriskCraft.LOGGER.error("AsteriskCraft: structure template {} contains no {}",
                    template, coreBlock);
            return null;
        }
        return cores.getFirst().pos();
    }

    /**
     * Template-local (0,0,0) in world space: the origin is the center of the bottom layer, so the
     * min corner sits half the footprint away on each horizontal axis.
     */
    private static BlockPos minCorner(BlockPos origin, Vec3i size) {
        return origin.offset(-(size.getX() - 1) / 2, 0, -(size.getZ() - 1) / 2);
    }
}
