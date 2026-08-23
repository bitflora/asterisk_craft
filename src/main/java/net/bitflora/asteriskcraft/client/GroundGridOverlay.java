package net.bitflora.asteriskcraft.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.SubmitCustomGeometryEvent;
import org.jetbrains.annotations.Nullable;

import java.util.function.Predicate;

/**
 * The ground-highlight rendering {@link PsiFieldOverlay} and {@code CreepFieldOverlay} share: a
 * square grid of columns around the player, redrawn as a filled surface with an outline around
 * whichever columns pass a caller-supplied test. Neither field's own coverage rule lives here — this
 * only knows how to sample a height, cache it, and turn a boolean grid into geometry, the same way
 * for both.
 *
 * <p>Each overlay owns its own instance rather than sharing static state, since psi and creep must be
 * able to be drawn independently (a player could in principle hold something from each mechanism, and
 * always could in a PvP match where both races are in play at once).
 *
 * <p>The height sampled is {@link Heightmap.Types#MOTION_BLOCKING_NO_LEAVES}, which <em>is</em> the
 * block a click on that surface resolves to — the same position the placing item itself is handed —
 * so the paint and the server's refusal cannot disagree.
 */
final class GroundGridOverlay {
    private final int displayRadius;
    private final int grid;
    private final int fillColor;
    private final int edgeColor;
    private final float edgeWidth;
    /** Lifted off the surface so it doesn't z-fight with the block face it lies on. */
    private final float lift;
    /** Rebuild cadence, so a source coming online shows up without the player having to move. */
    private final int refreshTicks;

    private final boolean[] covered;
    private final int[] surfaceY;
    private @Nullable BlockPos gridCenter;
    private int gridBuiltTick = -1;
    private boolean gridEmpty = true;

    GroundGridOverlay(int displayRadius, int fillColor, int edgeColor, float edgeWidth, float lift, int refreshTicks) {
        this.displayRadius = displayRadius;
        this.grid = displayRadius * 2 + 1;
        this.fillColor = fillColor;
        this.edgeColor = edgeColor;
        this.edgeWidth = edgeWidth;
        this.lift = lift;
        this.refreshTicks = refreshTicks;
        this.covered = new boolean[this.grid * this.grid];
        this.surfaceY = new int[this.grid * this.grid];
    }

    int displayRadius() {
        return this.displayRadius;
    }

    /**
     * Rebuilds the covered/height grid when the player has moved a block or the refresh interval has
     * elapsed. {@code test} answers one column; anything the test needs looked up once for the whole
     * grid (like {@link net.bitflora.asteriskcraft.building.PsiField#onlinePylons}) belongs in the
     * closure the caller builds before passing it in, not inside the test itself.
     */
    void refresh(ClientLevel level, BlockPos playerPos, Predicate<BlockPos> test) {
        int tick = (int) level.getGameTime();
        if (playerPos.equals(this.gridCenter) && tick - this.gridBuiltTick < this.refreshTicks) {
            return;
        }
        this.gridCenter = playerPos.immutable();
        this.gridBuiltTick = tick;

        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        boolean anyCovered = false;
        for (int gz = 0; gz < this.grid; gz++) {
            for (int gx = 0; gx < this.grid; gx++) {
                int x = playerPos.getX() - this.displayRadius + gx;
                int z = playerPos.getZ() - this.displayRadius + gz;
                int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
                int i = gz * this.grid + gx;
                this.surfaceY[i] = y;
                boolean here = test.test(cursor.set(x, y, z));
                this.covered[i] = here;
                anyCovered |= here;
            }
        }
        this.gridEmpty = !anyCovered;
    }

    void submit(SubmitCustomGeometryEvent event) {
        BlockPos center = this.gridCenter;
        if (this.gridEmpty || center == null) {
            return;
        }
        Vec3 camera = event.getLevelRenderState().cameraRenderState.pos;
        event.getSubmitNodeCollector().submitCustomGeometry(event.getPoseStack(), RenderTypes.debugQuads(),
                (pose, buffer) -> submitFill(pose, buffer, center, camera));
        event.getSubmitNodeCollector().submitCustomGeometry(event.getPoseStack(), RenderTypes.lines(),
                (pose, buffer) -> submitEdges(pose, buffer, center, camera));
    }

    private void submitFill(PoseStack.Pose pose, VertexConsumer buffer, BlockPos center, Vec3 camera) {
        for (int gz = 0; gz < this.grid; gz++) {
            for (int gx = 0; gx < this.grid; gx++) {
                int i = gz * this.grid + gx;
                if (!this.covered[i]) {
                    continue;
                }
                float x = (float) (center.getX() - this.displayRadius + gx - camera.x);
                float z = (float) (center.getZ() - this.displayRadius + gz - camera.z);
                float y = (float) (this.surfaceY[i] - camera.y) + this.lift;
                // Culling is off for this pipeline, so winding doesn't matter — the field reads the
                // same from under an overhang as from above it.
                buffer.addVertex(pose, x, y, z).setColor(this.fillColor);
                buffer.addVertex(pose, x, y, z + 1).setColor(this.fillColor);
                buffer.addVertex(pose, x + 1, y, z + 1).setColor(this.fillColor);
                buffer.addVertex(pose, x + 1, y, z).setColor(this.fillColor);
            }
        }
    }

    /**
     * The boundary of the covered area: every edge between a covered square and an uncovered one.
     * Interior edges cancel, so two overlapping sources draw one contour around their union rather
     * than a circle each. Edges against the grid's own border are skipped — that is where the drawing
     * stops, not where the coverage does, and a line there would claim otherwise.
     */
    private void submitEdges(PoseStack.Pose pose, VertexConsumer buffer, BlockPos center, Vec3 camera) {
        for (int gz = 0; gz < this.grid; gz++) {
            for (int gx = 0; gx < this.grid; gx++) {
                int i = gz * this.grid + gx;
                if (!this.covered[i]) {
                    continue;
                }
                float x = (float) (center.getX() - this.displayRadius + gx - camera.x);
                float z = (float) (center.getZ() - this.displayRadius + gz - camera.z);
                float y = (float) (this.surfaceY[i] - camera.y) + this.lift;
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

    /** Whether the neighbour at these grid coordinates is inside the grid and uncovered. */
    private boolean isBoundary(int gx, int gz) {
        if (gx < 0 || gz < 0 || gx >= this.grid || gz >= this.grid) {
            return false;
        }
        return !this.covered[gz * this.grid + gx];
    }

    private void line(PoseStack.Pose pose, VertexConsumer buffer,
            float x1, float y1, float z1, float x2, float y2, float z2) {
        // Same per-vertex normal and width the lines pipeline needs everywhere else.
        float dx = x2 - x1;
        float dy = y2 - y1;
        float dz = z2 - z1;
        float length = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
        float nx = dx / length;
        float ny = dy / length;
        float nz = dz / length;
        buffer.addVertex(pose, x1, y1, z1).setColor(this.edgeColor).setNormal(pose, nx, ny, nz).setLineWidth(this.edgeWidth);
        buffer.addVertex(pose, x2, y2, z2).setColor(this.edgeColor).setNormal(pose, nx, ny, nz).setLineWidth(this.edgeWidth);
    }
}
