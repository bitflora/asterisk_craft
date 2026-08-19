package net.bitflora.asteriskcraft.command;

import net.bitflora.asteriskcraft.AsteriskCraft;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * Keeps every player's client copy of their unit groups current: a full push on login (the client
 * starts with nothing), then a once-a-second sweep so a group whose units died out of sight still
 * shows the right label the next time the overlay opens. {@link UnitGroupSync#sync} only puts a
 * packet on the wire when the composition actually changed, so the sweep is silent in a quiet match.
 */
@EventBusSubscriber(modid = AsteriskCraft.MODID)
public final class UnitGroupSyncEvents {
    /** How often (ticks) group composition is re-checked for every player (1s). */
    public static final int SYNC_INTERVAL = 20;

    private UnitGroupSyncEvents() {
    }

    @SubscribeEvent
    static void onServerTick(ServerTickEvent.Post event) {
        if (event.getServer().getTickCount() % SYNC_INTERVAL != 0) {
            return;
        }
        for (ServerPlayer player : event.getServer().getPlayerList().getPlayers()) {
            UnitGroupSync.sync(player);
        }
    }

    @SubscribeEvent
    static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            UnitGroupSync.forceSync(player);
        }
    }
}
