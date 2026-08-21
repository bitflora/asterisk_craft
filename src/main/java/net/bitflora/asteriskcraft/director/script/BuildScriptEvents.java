package net.bitflora.asteriskcraft.director.script;

import net.bitflora.asteriskcraft.AsteriskCraft;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.AddServerReloadListenersEvent;

/**
 * Registers the {@link BuildScriptReloadListener} on the server's datapack reload pipeline, so every
 * race's build script is (re)loaded on world load and every {@code /reload}. Game-bus subscriber,
 * mirroring the existing {@code AiDirector}/{@code RegenEventHandler} pattern.
 */
@EventBusSubscriber(modid = AsteriskCraft.MODID)
public final class BuildScriptEvents {
    private BuildScriptEvents() {
    }

    @SubscribeEvent
    public static void onAddReloadListeners(AddServerReloadListenersEvent event) {
        event.addListener(AsteriskCraft.id("build_scripts"), new BuildScriptReloadListener());
    }
}
