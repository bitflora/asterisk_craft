package net.bitflora.asteriskcraft.command;

import net.bitflora.asteriskcraft.faction.Faction;
import net.minecraft.world.entity.player.Player;

/**
 * Which faction a given player <em>commands</em> — the single chokepoint for command
 * ownership. This is deliberately NOT the player's own combat faction: players stay
 * {@link Faction#NEUTRAL} so enemy units never target them ({@code FactionTargetGoal}
 * skips NEUTRAL). MVP has one human commanding Protoss; race selection / PvP will replace
 * the body here with a per-player lookup, so keep every "what does this player command"
 * decision routed through this method.
 */
public final class ControlledFaction {
    private ControlledFaction() {
    }

    public static Faction of(Player player) {
        return Faction.PROTOSS;
    }
}
