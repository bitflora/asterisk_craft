package net.bitflora.asteriskcraft.building;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;

/**
 * The "your building is under attack!" ping, throttled so a unit landing a hit a second doesn't spam
 * it. One of these per building that wants to shout for help; the cooldown is deliberately not saved,
 * so it resets on reload.
 */
public final class UnderAttackAlert {
    private static final int COOLDOWN_TICKS = 20 * 30;

    /** Game time of the next allowed alert. */
    private long nextAlertTime = 0L;

    /** Broadcasts {@code messageKey} with a warning sting, unless one went out recently. */
    public void ping(ServerLevel level, String messageKey) {
        long now = level.getGameTime();
        if (now < this.nextAlertTime) {
            return;
        }
        this.nextAlertTime = now + COOLDOWN_TICKS;
        for (ServerPlayer player : level.getServer().getPlayerList().getPlayers()) {
            player.sendSystemMessage(Component.translatable(messageKey));
            level.playSound(null, player.blockPosition(), SoundEvents.WARDEN_NEARBY_CLOSEST, SoundSource.HOSTILE, 0.7f, 1.2f);
        }
    }
}
