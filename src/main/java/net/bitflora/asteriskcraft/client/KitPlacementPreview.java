package net.bitflora.asteriskcraft.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.bitflora.asteriskcraft.AsteriskCraft;
import net.bitflora.asteriskcraft.building.BuildingKitItem;
import net.bitflora.asteriskcraft.building.BuildingTemplates;
import net.bitflora.asteriskcraft.building.CreepField;
import net.bitflora.asteriskcraft.building.PsiField;
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
 * <p>There is a third answer, and it is why this is a {@link Verdict} rather than a boolean: a site
 * can be perfectly clear and still be refused for having no Pylon in range ({@link PsiField}), which
 * would be invisible without its own colour. Where that power actually reaches is painted on the
 * ground by {@link PsiFieldOverlay}, so a player hunting for a spot doesn't have to find the edge by
 * sweeping the crosshair around.
 *
 * <p>Purely a preview: the server re-checks the real template on placement and is the authority. This
 * only has to agree with it, which {@code BuildingTemplatesTest} is what keeps true. The one place it
 * is deliberately the more permissive of the two is Pylon <em>ownership</em> — see
 * {@link PsiField#covered(net.minecraft.world.level.BlockGetter, BlockPos)}.
 *
 * <p>The ground prerequisites are two, not one, and each gets its own {@code Verdict} so the outline
 * says which rule refused: psi for a Protoss kit ({@link PsiField}) and creep for a Zerg one
 * ({@link CreepField}). A kit asks at most one of them — a building is one race's — and a kit exempt
 * from both (a Nexus, a Pylon, a Hive) asks neither. Where each field actually reaches is painted on
 * the ground by {@link PsiFieldOverlay} and {@link CreepFieldOverlay}.
 */
@EventBusSubscriber(modid = AsteriskCraft.MODID, value = Dist.CLIENT)
public final class KitPlacementPreview {
    private static final float LINE_WIDTH = 3.0f;

    /** Why the outline is the colour it is. */
    private enum Verdict {
        /** Nothing in the way and, if the kit needs one, a Pylon in range. */
        CLEAR(0xFF55FF99),
        /** Something is standing in the volume. */
        BLOCKED(0xFFFF5555),
        /** The volume is free, but no Pylon reaches it. */
        UNPOWERED(0xFFFFCC55),
        /** The volume is free, but there is no creep under or near it. */
        UNSEEDED(0xFFCC66FF);

        private final int color;

        Verdict(int color) {
            this.color = color;
        }
    }

    /**
     * The last verdict, recomputed once a tick rather than once a frame: the checks walk the whole
     * volume (up to ~700 block reads for a Nexus) plus the Pylon radius, and the answer can only
     * change on a tick.
     */
    private static Verdict lastVerdict = Verdict.BLOCKED;
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
        BuildingKitItem kit = heldKit(player);
        if (kit == null) {
            return;
        }
        BuildingTemplates.Footprint footprint = kit.footprint();
        BlockPos origin = targetedOrigin(minecraft.hitResult);
        if (origin == null) {
            return;
        }

        BlockPos min = footprint.minCorner(origin);
        Vec3 camera = event.getLevelRenderState().cameraRenderState.pos;
        Vec3i size = footprint.size();
        int color = verdict(level, player, kit, origin, min).color;
        event.getSubmitNodeCollector().submitCustomGeometry(event.getPoseStack(), RenderTypes.lines(),
                (pose, buffer) -> submitBox(pose, buffer,
                        (float) (min.getX() - camera.x), (float) (min.getY() - camera.y), (float) (min.getZ() - camera.z),
                        size.getX(), size.getY(), size.getZ(), color));
    }

    /** The kit in either hand, or null if the player isn't holding one. */
    private static @Nullable BuildingKitItem heldKit(LocalPlayer player) {
        for (InteractionHand hand : InteractionHand.values()) {
            if (player.getItemInHand(hand).getItem() instanceof BuildingKitItem kit) {
                return kit;
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

    private static Verdict verdict(ClientLevel level, LocalPlayer player, BuildingKitItem kit,
            BlockPos origin, BlockPos min) {
        int tick = (int) level.getGameTime();
        if (tick != lastCheckedTick || !min.equals(lastMin)) {
            lastCheckedTick = tick;
            lastMin = min;
            lastVerdict = compute(level, player, kit, origin, min);
        }
        return lastVerdict;
    }

    private static Verdict compute(ClientLevel level, LocalPlayer player, BuildingKitItem kit,
            BlockPos origin, BlockPos min) {
        if (!BuildingTemplates.isAreaClear(level, min, kit.footprint().size())) {
            return Verdict.BLOCKED;
        }
        if (kit.requiresPylon() && !PsiField.covered(level, origin)) {
            return Verdict.UNPOWERED;
        }
        // The same question the server asks in BuildingKitItem, through the same rule and off the
        // same race — the kit's own placingRace, so this names no race any more than the rule does.
        if (kit.requiresCreep() && !CreepField.covered(level, origin, kit.placingRace(level, player))) {
            return Verdict.UNSEEDED;
        }
        return Verdict.CLEAR;
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
