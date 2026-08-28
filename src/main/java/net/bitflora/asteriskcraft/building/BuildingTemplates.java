package net.bitflora.asteriskcraft.building;

import net.bitflora.asteriskcraft.AsteriskCraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.templatesystem.LiquidSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;

/**
 * Places every building from the hand-authored {@code .nbt} structure templates in
 * {@code data/asteriskcraft/structure/}, so their shapes are designed in-game with a structure
 * block and re-exported rather than written out in Java.
 *
 * <p>A template is anchored on its <b>core block's column</b>: the caller's {@code origin} is the
 * block the core stands over, and the template's bottom layer is laid at that origin's height.
 * Anchoring on the core rather than the bounding box's center means an export whose selection was
 * framed loosely (the Hive's is a block wider on one side) still lands where the game means it to,
 * and the core's position is looked up in the template instead of being hardcoded as an offset — so
 * a redesign can move the core with no code change.
 */
public final class BuildingTemplates {
    private BuildingTemplates() {
    }

    public static final Identifier NEXUS = AsteriskCraft.id("nexus");
    public static final Identifier GATEWAY = AsteriskCraft.id("gateway");
    public static final Identifier HIVE = AsteriskCraft.id("hive");
    public static final Identifier PYLON = AsteriskCraft.id("pylon");
    public static final Identifier COMMAND_CENTER = AsteriskCraft.id("command_center");
    public static final Identifier BARRACKS = AsteriskCraft.id("barracks");
    public static final Identifier STARGATE = AsteriskCraft.id("stargate");
    public static final Identifier SPAWNING_POOL = AsteriskCraft.id("spawning_pool");
    public static final Identifier SPIRE = AsteriskCraft.id("spire");
    public static final Identifier STARPORT = AsteriskCraft.id("starport");

    /**
     * A template's bounding box and where its core sits inside it, declared up front so the
     * <em>client</em> can outline the volume a kit needs free before anything is stamped. Templates
     * live under {@code data/} and only ever load through a server's {@code StructureTemplateManager}
     * (see docs/neoforge-api-notes.md), so a client holding a kit has nothing to ask.
     *
     * <p>The {@code .nbt} stays the source of truth — the server reads both numbers out of the loaded
     * template and never consults these — and {@code BuildingTemplatesTest} fails the build if a
     * re-exported building drifts from what is declared here.
     */
    public record Footprint(Vec3i size, BlockPos coreOffset) {
        /** Template-local (0,0,0) in world space, for a core warped in over {@code origin}. */
        public BlockPos minCorner(BlockPos origin) {
            return BuildingTemplates.minCorner(origin, this.coreOffset);
        }
    }

    public static final Footprint NEXUS_FOOTPRINT = new Footprint(new Vec3i(9, 4, 9), new BlockPos(4, 3, 4));
    // Not a square: the Hive's export was framed a block wider on one side, and the core is not at
    // its middle. That is exactly why the anchor is the core column rather than a hardcoded centre.
    public static final Footprint HIVE_FOOTPRINT = new Footprint(new Vec3i(8, 7, 7), new BlockPos(4, 2, 3));
    public static final Footprint GATEWAY_FOOTPRINT = new Footprint(new Vec3i(5, 5, 5), new BlockPos(2, 2, 2));
    public static final Footprint PYLON_FOOTPRINT = new Footprint(new Vec3i(5, 5, 5), new BlockPos(2, 2, 2));
    public static final Footprint COMMAND_CENTER_FOOTPRINT = new Footprint(new Vec3i(5, 5, 5), new BlockPos(2, 0, 2));
    // Cores that sit on a template's bottom layer (y == 0) or against one of its walls are ordinary:
    // the anchor is the core's own column, so where it sits inside the box is the designer's call
    // and not something the placement code has an opinion about. The Command Center's is on the
    // floor, the Barracks' and the Stargate's are on an edge.
    public static final Footprint BARRACKS_FOOTPRINT = new Footprint(new Vec3i(3, 5, 3), new BlockPos(1, 2, 0));
    public static final Footprint STARGATE_FOOTPRINT = new Footprint(new Vec3i(6, 6, 4), new BlockPos(3, 3, 1));
    public static final Footprint SPAWNING_POOL_FOOTPRINT = new Footprint(new Vec3i(6, 2, 6), new BlockPos(3, 0, 2));
    public static final Footprint SPIRE_FOOTPRINT = new Footprint(new Vec3i(7, 8, 7), new BlockPos(3, 3, 3));
    public static final Footprint STARPORT_FOOTPRINT = new Footprint(new Vec3i(8, 8, 7), new BlockPos(5, 1, 3));

    /** Where a stamped template landed: its core block, its min corner, and its size. */
    public record Placed(BlockPos core, BlockPos min, Vec3i size) {
    }

    /**
     * Head-room cleared above the platform even for a short template, so a building is never warped
     * in with terrain pressing against its roof.
     */
    private static final int MIN_HEADROOM = 5;

    /** Bounds the support fill so a deep or void column can't carve a shaft down to bedrock. */
    private static final int MAX_SUPPORT_DEPTH = 6;

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
        StructureTemplate structure = load(level, template);
        return structure == null ? null : structure.getSize();
    }

    /** Rough horizontal reach of a template's footprint, for sizing the ground work around it. */
    public static int footprintRadius(Vec3i size) {
        return Math.max(size.getX(), size.getZ()) / 2;
    }

    /**
     * True if the template's whole bounding box over {@code origin} is free to build in. This is
     * the volume — {@link StructureTemplate} offers no way to enumerate just its non-air blocks —
     * so warping a building into a hillside is refused rather than carving it out.
     */
    public static boolean isSiteClear(ServerLevel level, BlockPos origin, Identifier template, Block coreBlock) {
        StructureTemplate structure = load(level, template);
        if (structure == null) {
            return false;
        }
        BlockPos coreOffset = coreOffset(structure, template, coreBlock);
        if (coreOffset == null) {
            return false;
        }
        return isAreaClear(level, minCorner(origin, coreOffset), structure.getSize());
    }

    /**
     * The same "is there room here" rule over an already-known box, so the client can outline it
     * (see {@code client/KitPlacementPreview}) with exactly the test the server will apply.
     */
    public static boolean isAreaClear(BlockGetter level, BlockPos min, Vec3i size) {
        for (int dx = 0; dx < size.getX(); dx++) {
            for (int dy = 0; dy < size.getY(); dy++) {
                for (int dz = 0; dz < size.getZ(); dz++) {
                    if (!canBuildIn(level.getBlockState(min.offset(dx, dy, dz)))) {
                        return false;
                    }
                }
            }
        }
        return true;
    }

    private static boolean canBuildIn(BlockState state) {
        return state.isAir() || state.canBeReplaced();
    }

    /**
     * Preps the site and stamps the template over {@code origin}, returning where it landed — or
     * null if the template is missing or holds no core, which leaves the caller free to bail out
     * instead of guessing where the core landed.
     *
     * <p>{@code support} fills any gap under the footprint; pass null to leave the ground as it
     * lies. The Protoss buildings pass null so their own stonework isn't sat on a plinth of some
     * other material, at the cost of a platform edge that can overhang where the terrain falls away.
     */
    public static @Nullable Placed place(ServerLevel level, BlockPos origin, Identifier template,
            Block coreBlock, @Nullable BlockState support) {
        StructureTemplate structure = load(level, template);
        if (structure == null) {
            return null;
        }
        BlockPos coreOffset = coreOffset(structure, template, coreBlock);
        if (coreOffset == null) {
            return null;
        }
        Vec3i size = structure.getSize();
        BlockPos min = minCorner(origin, coreOffset);

        // Site prep first: it clears the head-room band the structure is about to occupy, so
        // running it afterwards would erase the building it just supported.
        prepareSite(level, min, size.getX(), size.getZ(), Math.max(size.getY(), MIN_HEADROOM), support);
        structure.placeInWorld(level, min, min, settings(), level.getRandom(), Block.UPDATE_ALL);
        return new Placed(min.offset(coreOffset.getX(), coreOffset.getY(), coreOffset.getZ()), min, size);
    }

    private static @Nullable StructureTemplate load(ServerLevel level, Identifier template) {
        Optional<StructureTemplate> loaded = level.getStructureManager().get(template);
        if (loaded.isEmpty()) {
            AsteriskCraft.LOGGER.error("AsteriskCraft: missing structure template {}", template);
            return null;
        }
        return loaded.get();
    }

    /** Where the core block sits inside the template, in template-local coordinates. */
    private static @Nullable BlockPos coreOffset(StructureTemplate structure, Identifier template, Block coreBlock) {
        // absolute = false keeps the positions template-local; there is no world anchor yet.
        List<StructureTemplate.StructureBlockInfo> cores =
                structure.filterBlocks(BlockPos.ZERO, settings(), coreBlock, false);
        if (cores.isEmpty()) {
            AsteriskCraft.LOGGER.error("AsteriskCraft: structure template {} contains no {}", template, coreBlock);
            return null;
        }
        return cores.getFirst().pos();
    }

    /** Template-local (0,0,0) in world space, placing the core's column over the origin. */
    private static BlockPos minCorner(BlockPos origin, BlockPos coreOffset) {
        return origin.offset(-coreOffset.getX(), 0, -coreOffset.getZ());
    }

    /**
     * Clears head-room over a footprint and, when {@code support} is non-null, fills it beneath —
     * leaving the site ready for the structure to be stamped on top.
     */
    private static void prepareSite(ServerLevel level, BlockPos min, int sizeX, int sizeZ,
            int headroom, @Nullable BlockState support) {
        planSitePrep(min, sizeX, sizeZ, pos -> level.getBlockState(pos).isSolidRender(),
                headroom, MAX_SUPPORT_DEPTH, support)
                .forEach((pos, state) -> level.setBlock(pos, state, Block.UPDATE_ALL));
    }

    /**
     * Computes every site-preparation edit without touching the world, so the geometry is
     * unit-testable. The prepared footprint is the template's own, anchored at its bottom-layer min
     * corner, so a wide building is never left overhanging raw terrain. {@code isSolid} reports
     * whether the EXISTING world block at a position is solid-render; support fill runs from just
     * under the platform down to the first solid block in each column (fluids and air are not
     * solid, so they get filled) with {@code support}, which keeps the platform on solid footing
     * even when it was raised onto the highest ground in a dip. Pass a null {@code support} to
     * leave the ground alone entirely and only clear head-room — the Protoss buildings do, so no
     * foreign plinth is stamped under their own stonework.
     */
    static Map<BlockPos, BlockState> planSitePrep(BlockPos min, int sizeX, int sizeZ,
            Predicate<BlockPos> isSolid, int headroom, int maxSupportDepth, @Nullable BlockState support) {
        Map<BlockPos, BlockState> edits = new LinkedHashMap<>();
        BlockState air = Blocks.AIR.defaultBlockState();
        for (int dx = 0; dx < sizeX; dx++) {
            for (int dz = 0; dz < sizeZ; dz++) {
                for (int dy = 1; dy <= headroom; dy++) {
                    edits.put(min.offset(dx, dy, dz), air);
                }
                for (int dy = -1; support != null && dy >= -maxSupportDepth; dy--) {
                    BlockPos pos = min.offset(dx, dy, dz);
                    if (isSolid.test(pos)) {
                        break; // reached solid ground; stop filling this column
                    }
                    edits.put(pos, support);
                }
            }
        }
        return edits;
    }
}
