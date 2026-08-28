package net.bitflora.asteriskcraft.command;

import net.bitflora.asteriskcraft.faction.Faction;
import net.bitflora.asteriskcraft.faction.FactionAttachments;
import net.minecraft.world.entity.player.Player;

/**
 * Which faction a given player <em>commands</em> — the single chokepoint for command ownership.
 *
 * <p>It is the player's own combat faction, tagged on them by {@code game.GameBootstrap} from the
 * match's {@code game.MatchSetup} on every login. Reading it off the player rather than off the
 * match is what makes this per-player already: enemy units target the player directly through the
 * normal {@code FactionTargetGoal}/{@code RetaliateGoal} path, and PvP later needs two players
 * tagged differently and nothing else. It is also why this works client-side — the faction
 * attachment is synced, which the level-scoped match setup is not, and
 * {@code client.DetectionRenderStateModifier} asks this question on the client every frame.
 *
 * <p>A player with no faction yet (before their first login is processed) commands nothing, which
 * is the correct answer rather than a guess at Protoss.
 */
public final class ControlledFaction {
    private ControlledFaction() {
    }

    public static Faction of(Player player) {
        return FactionAttachments.get(player);
    }
}
