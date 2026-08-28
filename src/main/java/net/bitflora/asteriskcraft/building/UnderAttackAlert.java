package net.bitflora.asteriskcraft.building;

import net.bitflora.asteriskcraft.command.ControlledFaction;
import net.bitflora.asteriskcraft.faction.Faction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;

/**
 * The "your building is under attack!" ping, throttled so a unit landing a hit a second doesn't spam
 * it. One of these per building that wants to shout for help; the cooldown is deliberately not saved,
 * so it resets on reload.
 *
 * <p>It goes only to the players who actually <em>command</em> the building's faction — an enemy
 * base being battered is not your base being attacked, and in a PvP match each side must hear only
 * its own. A NEUTRAL building has no commander and so pings nobody.
 */
public final class UnderAttackAlert {
    private static final int COOLDOWN_TICKS = 20 * 30;

    /** Game time of the next allowed alert. */
    private long nextAlertTime = 0L;

    /**
     * Broadcasts {@code messageKey} with a warning sting to the commanders of {@code owner}, unless
     * one went out recently.
     */
    public void ping(ServerLevel level, Faction owner, String messageKey) {
        ping(level, owner, Component.translatable(messageKey));
    }

    /**
     * The same ping for a building whose message is composed rather than named — a structure that
     * shares its block entity with three others and can only say which of them it is by its own
     * block name (see {@code StructureBlockEntity}).
     */
    public void ping(ServerLevel level, Faction owner, Component message) {
        if (owner == Faction.NEUTRAL) {
            return;
        }
        long now = level.getGameTime();
        if (now < this.nextAlertTime) {
            return;
        }
        this.nextAlertTime = now + COOLDOWN_TICKS;
        for (ServerPlayer player : level.getServer().getPlayerList().getPlayers()) {
            if (ControlledFaction.of(player) != owner) {
                continue;
            }
            player.sendSystemMessage(message);
            level.playSound(null, player.blockPosition(), SoundEvents.WARDEN_NEARBY_CLOSEST, SoundSource.HOSTILE, 0.7f, 1.2f);
        }
    }
}
