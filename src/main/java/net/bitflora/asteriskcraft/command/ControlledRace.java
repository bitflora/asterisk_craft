package net.bitflora.asteriskcraft.command;

import net.bitflora.asteriskcraft.faction.FactionAttachments;
import net.bitflora.asteriskcraft.faction.Race;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.Nullable;

/**
 * Which race a given player's army <em>is</em> — the sibling of {@link ControlledFaction}, and the
 * other half of the same question. A side no longer names a race (both sides may play the same
 * one), so anything that needs to know what the player is building, mining or standing on asks
 * here rather than deriving it from {@code ControlledFaction.of}.
 *
 * <p>Read off the player's own synced race attachment, tagged by {@code game.GameBootstrap} on
 * every login, so this answers correctly on the client too — which is what the placement previews
 * and the creep overlay need, since the match itself is a level attachment and is not synced.
 *
 * <p>Null for a player with no army yet (before their first login is processed), which is the
 * correct answer rather than a guess at Protoss.
 */
public final class ControlledRace {
    private ControlledRace() {
    }

    public static @Nullable Race of(Player player) {
        return FactionAttachments.raceOf(player);
    }
}
