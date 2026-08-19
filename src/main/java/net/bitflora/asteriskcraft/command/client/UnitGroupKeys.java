package net.bitflora.asteriskcraft.command.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.bitflora.asteriskcraft.AsteriskCraft;
import net.bitflora.asteriskcraft.command.CursorItem;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.settings.KeyConflictContext;
import org.lwjgl.glfw.GLFW;

/**
 * The two rebindable keys that open the unit-group overlay while the Cursor is held: <b>C</b>
 * assigns the current selection to a slot, <b>V</b> selects a slot's units. Both are real
 * {@link KeyMapping}s in their own category, so the player can rebind them in Options → Controls —
 * which is the whole reason this replaced the old hardcoded numpad handler.
 *
 * <p>Presses are drained with {@code consumeClick()} on the client tick rather than read off
 * {@code InputEvent.Key}: that's what makes a rebind (including to a mouse button or a modified
 * key) work without this class knowing anything about which key it ended up on.
 *
 * <p>Client-dist only — never loaded on a dedicated server.
 */
@EventBusSubscriber(modid = AsteriskCraft.MODID, value = Dist.CLIENT)
public final class UnitGroupKeys {
    /** Registered on the mod bus below, not via the deprecated {@code Category.register}. */
    public static final KeyMapping.Category CATEGORY = new KeyMapping.Category(AsteriskCraft.id("main"));

    public static final KeyMapping ASSIGN_GROUP = new KeyMapping("key.asteriskcraft.assign_group",
            KeyConflictContext.IN_GAME, InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_C, CATEGORY);

    public static final KeyMapping SELECT_GROUP = new KeyMapping("key.asteriskcraft.select_group",
            KeyConflictContext.IN_GAME, InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_V, CATEGORY);

    private UnitGroupKeys() {
    }

    // No bus= on the annotation: this NeoForge version routes each @SubscribeEvent method by its
    // event type, so a mod-bus event (RegisterKeyMappingsEvent) and a game-bus one (ClientTickEvent)
    // live side by side in one subscriber class.
    @SubscribeEvent
    static void onRegisterKeyMappings(RegisterKeyMappingsEvent event) {
        event.registerCategory(CATEGORY);
        event.register(ASSIGN_GROUP);
        event.register(SELECT_GROUP);
    }

    @SubscribeEvent
    static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        // Drain both mappings every tick, even when the overlay can't open: a press left queued
        // would otherwise fire the moment the player next picks the Cursor up.
        boolean assign = ASSIGN_GROUP.consumeClick();
        boolean select = SELECT_GROUP.consumeClick();
        if (!assign && !select) {
            return;
        }
        if (mc.player == null || mc.level == null || mc.screen != null) {
            return;
        }
        if (!(mc.player.getMainHandItem().getItem() instanceof CursorItem)) {
            return;
        }
        // If a player has bound both to the same key, open the select overlay: it never overwrites
        // a group that already exists, and its empty-slot rule still lets them assign from there.
        mc.setScreen(new UnitGroupScreen(select ? UnitGroupScreen.Mode.SELECT : UnitGroupScreen.Mode.ASSIGN));
    }
}
