package net.bitflora.asteriskcraft.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.bitflora.asteriskcraft.AsteriskCraft;
import net.bitflora.asteriskcraft.building.BuildingKitItem;
import net.bitflora.asteriskcraft.building.BuildingTemplates;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.SubmitCustomGeometryEvent;
import org.jetbrains.annotations.Nullable;

/**
 * Outlines where a held building kit would warp its building in, and whether the site is free.
 *
 * <p>The box is the template's whole bounding volume — the same volume
 * {@link BuildingTemplates#isAreaClear} refuses to build into — drawn green while it is clear and red
 * once anything is in the way, so "it says blocked and I can't see why" is answerable by looking.
 * Its dimensions come from the kit's declared {@link BuildingTemplates.Footprint} rather than from the
 * template itself, which a client has no way to load (see that record).
 *
 * <p>Purely a preview: the server re-checks the real template on placement and is the authority. This
 * only has to agree with it, which {@code BuildingTemplatesTest} is what keeps true.
 */
@EventBusSubscriber(modid = AsteriskCraft.MODID, value = Dist.CLIENT)
public final class KitPlacementPreview {
    private static final int CLEAR_COLOR = 0xFF55FF99;
    private static final int BLOCKED_COLOR = 0xFFFF5555;
    private static final float LINE_WIDTH = 3.0f;

    /**
     * Whether the site was clear, recomputed once a tick rather than once a frame: the check walks
     * the whole volume (up to ~700 block reads for a Nexus) and the answer can only change on a tick.
     */
    private static boolean lastClear;
    private static @Nullable BlockPos lastMin;
    private static int lastCheckedTick = -1;

    private KitPlacementPreview() {
    }

    @SubscribeEvent
    static void onSubmitGeometry(SubmitCustomGeometryEvent event) {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        ClientLevel level = minecraft.level;
        if (player == null || level == null) {
            return;
        }
        BuildingTemplates.Footprint footprint = heldKitFootprint(player);
        if (footprint == null) {
            return;
        }
        BlockPos origin = targetedOrigin(minecraft.hitResult);
        if (origin == null) {
            return;
        }

        BlockPos min = footprint.minCorner(origin);
        boolean clear = isClear(level, min, footprint.size());
        Vec3 camera = event.getLevelRenderState().cameraRenderState.pos;
        Vec3i size = footprint.size();
        int color = clear ? CLEAR_COLOR : BLOCKED_COLOR;
        event.getSubmitNodeCollector().submitCustomGeometry(event.getPoseStack(), RenderTypes.lines(),
                (pose, buffer) -> submitBox(pose, buffer,
                        (float) (min.getX() - camera.x), (float) (min.getY() - camera.y), (float) (min.getZ() - camera.z),
                        size.getX(), size.getY(), size.getZ(), color));
    }

    /** The footprint of a kit in either hand, or null if the player isn't holding one. */
    private static BuildingTemplates.@Nullable Footprint heldKitFootprint(LocalPlayer player) {
        for (InteractionHand hand : InteractionHand.values()) {
            if (player.getItemInHand(hand).getItem() instanceof BuildingKitItem kit) {
                return kit.footprint();
            }
        }
        return null;
    }

    /** The block a kit right-clicked now would build over — the same one {@code UseOnContext} reports. */
    private static @Nullable BlockPos targetedOrigin(@Nullable HitResult hit) {
        if (hit instanceof BlockHitResult blockHit && hit.getType() == HitResult.Type.BLOCK) {
            return blockHit.getBlockPos().relative(blockHit.getDirection());
        }
        return null;
    }

    private static boolean isClear(ClientLevel level, BlockPos min, Vec3i size) {
        int tick = (int) level.getGameTime();
        if (tick != lastCheckedTick || !min.equals(lastMin)) {
            lastCheckedTick = tick;
            lastMin = min;
            lastClear = BuildingTemplates.isAreaClear(level, min, size);
        }
        return lastClear;
    }

    /** The twelve edges of the box spanning {@code (x,y,z)} to {@code (x+sx, y+sy, z+sz)}. */
    private static void submitBox(PoseStack.Pose pose, VertexConsumer buffer,
            float x, float y, float z, int sizeX, int sizeY, int sizeZ, int color) {
        float x1 = x + sizeX;
        float y1 = y + sizeY;
        float z1 = z + sizeZ;
        for (float edgeY : new float[] { y, y1 }) {
            line(pose, buffer, x, edgeY, z, x1, edgeY, z, color);
            line(pose, buffer, x1, edgeY, z, x1, edgeY, z1, color);
            line(pose, buffer, x1, edgeY, z1, x, edgeY, z1, color);
            line(pose, buffer, x, edgeY, z1, x, edgeY, z, color);
        }
        line(pose, buffer, x, y, z, x, y1, z, color);
        line(pose, buffer, x1, y, z, x1, y1, z, color);
        line(pose, buffer, x1, y, z1, x1, y1, z1, color);
        line(pose, buffer, x, y, z1, x, y1, z1, color);
    }

    private static void line(PoseStack.Pose pose, VertexConsumer buffer,
            float x1, float y1, float z1, float x2, float y2, float z2, int color) {
        // The lines pipeline takes a per-vertex normal along the segment and a per-vertex width,
        // exactly as vanilla's own ShapeRenderer feeds it.
        float dx = x2 - x1;
        float dy = y2 - y1;
        float dz = z2 - z1;
        float length = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
        float nx = dx / length;
        float ny = dy / length;
        float nz = dz / length;
        buffer.addVertex(pose, x1, y1, z1).setColor(color).setNormal(pose, nx, ny, nz).setLineWidth(LINE_WIDTH);
        buffer.addVertex(pose, x2, y2, z2).setColor(color).setNormal(pose, nx, ny, nz).setLineWidth(LINE_WIDTH);
    }
}
