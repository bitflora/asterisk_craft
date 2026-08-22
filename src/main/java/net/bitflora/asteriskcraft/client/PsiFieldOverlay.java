package net.bitflora.asteriskcraft.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.bitflora.asteriskcraft.AsteriskCraft;
import net.bitflora.asteriskcraft.building.PsiDependent;
import net.bitflora.asteriskcraft.building.PsiField;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.SubmitCustomGeometryEvent;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Paints the ground a Pylon powers, while the player holds something that needs one.
 *
 * <p>{@link KitPlacementPreview} answers "may I build <em>here</em>" for the one square being
 * pointed at; this answers "where may I build at all", which is the question a player actually has
 * while walking around looking for a spot. Without it, finding the edge of a Pylon's reach means
 * sweeping the crosshair over the terrain and watching for the outline to change colour.
 *
 * <p>It asks {@link PsiField} the same question the server will, once per ground column, so the
 * paint and the refusal cannot disagree: a column is powered exactly when a right-click on that
 * surface would be allowed. The height used is the one
 * {@link Heightmap.Types#MOTION_BLOCKING_NO_LEAVES} reports, which <em>is</em> the block a click on
 * that surface resolves to — the same position the placing item itself would be handed. Ownership is
 * the one thing it cannot check; see {@link PsiField#onlinePylons}.
 *
 * <p>Only appears while the player holds something that is actually gated
 * ({@link PsiDependent#requiresPylon()}), so a Nexus or Pylon kit paints nothing. It keys off that
 * interface rather than off a kit because the Photon Cannon is placed by a spawn egg, and the egg is
 * the case that needs this most: a kit at least gets a coloured volume outline from
 * {@link KitPlacementPreview}, while an egg draws nothing at all.
 */
@EventBusSubscriber(modid = AsteriskCraft.MODID, value = Dist.CLIENT)
public final class PsiFieldOverlay {
    /** How far around the player the ground is painted. Beyond this the field is simply not drawn. */
    private static final int DISPLAY_RADIUS = 16;
    private static final int GRID = DISPLAY_RADIUS * 2 + 1;

    private static final int FILL_COLOR = 0x3355FF99;
    private static final int EDGE_COLOR = 0xDD8CFFC4;
    private static final float EDGE_WIDTH = 2.5f;
    /** Lifted off the surface so it doesn't z-fight with the block face it lies on. */
    private static final float LIFT = 0.015f;

    /** Rebuild cadence, so a Pylon coming online shows up without the player having to move. */
    private static final int REFRESH_TICKS = 10;

    private static final boolean[] POWERED = new boolean[GRID * GRID];
    private static final int[] SURFACE_Y = new int[GRID * GRID];
    private static @Nullable BlockPos gridCenter;
    private static int gridBuiltTick = -1;
    private static boolean gridEmpty = true;

    private PsiFieldOverlay() {
    }

    @SubscribeEvent
    static void onSubmitGeometry(SubmitCustomGeometryEvent event) {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        ClientLevel level = minecraft.level;
        if (player == null || level == null) {
            return;
        }
        if (!holdingPsiDependent(player)) {
            return;
        }

        refresh(level, player.blockPosition());
        BlockPos center = gridCenter;
        if (gridEmpty || center == null) {
            return;
        }
        Vec3 camera = event.getLevelRenderState().cameraRenderState.pos;

        event.getSubmitNodeCollector().submitCustomGeometry(event.getPoseStack(), RenderTypes.debugQuads(),
                (pose, buffer) -> submitFill(pose, buffer, center, camera));
        event.getSubmitNodeCollector().submitCustomGeometry(event.getPoseStack(), RenderTypes.lines(),
                (pose, buffer) -> submitEdges(pose, buffer, center, camera));
    }

    /** Whether either hand holds an item whose building needs a Pylon in range. */
    private static boolean holdingPsiDependent(LocalPlayer player) {
        for (InteractionHand hand : InteractionHand.values()) {
            if (player.getItemInHand(hand).getItem() instanceof PsiDependent placing && placing.requiresPylon()) {
                return true;
            }
        }
        return false;
    }

    /**
     * Rebuilds the powered/height grid when the player has moved a block or the refresh interval has
     * elapsed. The Pylons are looked up once for the whole grid — the reason
     * {@link PsiField#onlinePylons} exists as its own step rather than being folded into the
     * per-position test.
     */
    private static void refresh(ClientLevel level, BlockPos playerPos) {
        int tick = (int) level.getGameTime();
        if (playerPos.equals(gridCenter) && tick - gridBuiltTick < REFRESH_TICKS) {
            return;
        }
        gridCenter = playerPos.immutable();
        gridBuiltTick = tick;

        List<BlockPos> pylons = PsiField.onlinePylons(level, playerPos, DISPLAY_RADIUS + PsiField.RADIUS, null);
        gridEmpty = pylons.isEmpty();
        if (gridEmpty) {
            return;
        }
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int gz = 0; gz < GRID; gz++) {
            for (int gx = 0; gx < GRID; gx++) {
                int x = playerPos.getX() - DISPLAY_RADIUS + gx;
                int z = playerPos.getZ() - DISPLAY_RADIUS + gz;
                // The y a click on this surface resolves to: getHeight reports the first free block
                // above the terrain, which is exactly what the placing item is handed.
                int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
                int i = gz * GRID + gx;
                SURFACE_Y[i] = y;
                POWERED[i] = PsiField.covered(cursor.set(x, y, z), pylons);
            }
        }
    }

    private static void submitFill(PoseStack.Pose pose, VertexConsumer buffer, BlockPos center, Vec3 camera) {
        for (int gz = 0; gz < GRID; gz++) {
            for (int gx = 0; gx < GRID; gx++) {
                int i = gz * GRID + gx;
                if (!POWERED[i]) {
                    continue;
                }
                float x = (float) (center.getX() - DISPLAY_RADIUS + gx - camera.x);
                float z = (float) (center.getZ() - DISPLAY_RADIUS + gz - camera.z);
                float y = (float) (SURFACE_Y[i] - camera.y) + LIFT;
                // Culling is off for this pipeline, so winding doesn't matter — the field reads the
                // same from under an overhang as from above it.
                buffer.addVertex(pose, x, y, z).setColor(FILL_COLOR);
                buffer.addVertex(pose, x, y, z + 1).setColor(FILL_COLOR);
                buffer.addVertex(pose, x + 1, y, z + 1).setColor(FILL_COLOR);
                buffer.addVertex(pose, x + 1, y, z).setColor(FILL_COLOR);
            }
        }
    }

    /**
     * The boundary of the powered area: every edge between a powered square and an unpowered one.
     * Interior edges cancel, so two overlapping Pylons draw one contour around their union rather
     * than a circle each. Edges against the grid's own border are skipped — that is where the
     * drawing stops, not where the power does, and a line there would claim otherwise.
     */
    private static void submitEdges(PoseStack.Pose pose, VertexConsumer buffer, BlockPos center, Vec3 camera) {
        for (int gz = 0; gz < GRID; gz++) {
            for (int gx = 0; gx < GRID; gx++) {
                int i = gz * GRID + gx;
                if (!POWERED[i]) {
                    continue;
                }
                float x = (float) (center.getX() - DISPLAY_RADIUS + gx - camera.x);
                float z = (float) (center.getZ() - DISPLAY_RADIUS + gz - camera.z);
                float y = (float) (SURFACE_Y[i] - camera.y) + LIFT;
                if (isBoundary(gx - 1, gz)) {
                    line(pose, buffer, x, y, z, x, y, z + 1);
                }
                if (isBoundary(gx + 1, gz)) {
                    line(pose, buffer, x + 1, y, z, x + 1, y, z + 1);
                }
                if (isBoundary(gx, gz - 1)) {
                    line(pose, buffer, x, y, z, x + 1, y, z);
                }
                if (isBoundary(gx, gz + 1)) {
                    line(pose, buffer, x, y, z + 1, x + 1, y, z + 1);
                }
            }
        }
    }

    /** Whether the neighbour at these grid coordinates is inside the grid and unpowered. */
    private static boolean isBoundary(int gx, int gz) {
        if (gx < 0 || gz < 0 || gx >= GRID || gz >= GRID) {
            return false;
        }
        return !POWERED[gz * GRID + gx];
    }

    private static void line(PoseStack.Pose pose, VertexConsumer buffer,
            float x1, float y1, float z1, float x2, float y2, float z2) {
        // Same per-vertex normal and width the lines pipeline needs everywhere else; see
        // KitPlacementPreview, which feeds it identically.
        float dx = x2 - x1;
        float dy = y2 - y1;
        float dz = z2 - z1;
        float length = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
        float nx = dx / length;
        float ny = dy / length;
        float nz = dz / length;
        buffer.addVertex(pose, x1, y1, z1).setColor(EDGE_COLOR).setNormal(pose, nx, ny, nz).setLineWidth(EDGE_WIDTH);
        buffer.addVertex(pose, x2, y2, z2).setColor(EDGE_COLOR).setNormal(pose, nx, ny, nz).setLineWidth(EDGE_WIDTH);
    }
}
