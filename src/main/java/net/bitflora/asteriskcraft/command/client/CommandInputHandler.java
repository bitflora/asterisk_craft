package net.bitflora.asteriskcraft.command.client;

import net.bitflora.asteriskcraft.AsteriskCraft;
import net.bitflora.asteriskcraft.command.CursorItem;
import net.bitflora.asteriskcraft.command.CommandInputPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import org.lwjgl.glfw.GLFW;

/**
 * Captures every in-world mouse click while the Command Crystal is held and forwards it to the
 * server as a {@link CommandInputPacket}. This has to run client-side: the server is never told
 * whether Ctrl is held, and only the client can raycast the crosshair to classify what was clicked
 * (entity / block / empty air). Cancels the vanilla click so command mode never attacks or uses.
 * Client-dist only — never loaded on a dedicated server.
 */
@EventBusSubscriber(modid = AsteriskCraft.MODID, value = Dist.CLIENT)
public final class CommandInputHandler {
    /** How far a command click reaches — R5.7's "farthest point in view". */
    private static final double COMMAND_REACH = 64.0;

    private CommandInputHandler() {
    }

    @SubscribeEvent
    static void onMouseButton(InputEvent.MouseButton.Pre event) {
        if (event.getAction() != GLFW.GLFW_PRESS) {
            return;
        }
        int button = event.getButton();
        if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT && button != GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || mc.screen != null) {
            return;
        }
        if (!(mc.player.getMainHandItem().getItem() instanceof CursorItem)) {
            return;
        }
        event.setCanceled(true); // command mode owns this click; suppress vanilla attack/use
        ClientPacketDistributor.sendToServer(buildPacket(mc, button, event.getModifiers()));
    }

    private static CommandInputPacket buildPacket(Minecraft mc, int button, int modifiers) {
        Player player = mc.player;
        Level level = mc.level;
        Vec3 eye = player.getEyePosition(1.0f);
        Vec3 view = player.getViewVector(1.0f);
        Vec3 end = eye.add(view.scale(COMMAND_REACH));

        BlockHitResult blockHit = level.clip(new ClipContext(
                eye, end, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, player));
        double blockDistSqr = blockHit.getType() == HitResult.Type.BLOCK
                ? blockHit.getLocation().distanceToSqr(eye)
                : COMMAND_REACH * COMMAND_REACH;

        AABB searchBox = player.getBoundingBox().expandTowards(view.scale(COMMAND_REACH)).inflate(1.0);
        EntityHitResult entityHit = ProjectileUtil.getEntityHitResult(
                player, eye, end, searchBox, e -> e.isPickable() && !e.isSpectator(), blockDistSqr);

        if (entityHit != null) {
            return new CommandInputPacket(button, modifiers, CommandInputPacket.HitKind.ENTITY,
                    entityHit.getEntity().getId(), entityHit.getEntity().blockPosition());
        }
        if (blockHit.getType() == HitResult.Type.BLOCK) {
            return new CommandInputPacket(button, modifiers, CommandInputPacket.HitKind.BLOCK,
                    -1, blockHit.getBlockPos());
        }
        return new CommandInputPacket(button, modifiers, CommandInputPacket.HitKind.MISS,
                -1, BlockPos.containing(end));
    }
}
