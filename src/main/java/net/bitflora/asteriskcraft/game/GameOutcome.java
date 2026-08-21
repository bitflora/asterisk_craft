package net.bitflora.asteriskcraft.game;

import net.bitflora.asteriskcraft.building.CoreCensus;
import net.bitflora.asteriskcraft.building.FactionCore;
import net.bitflora.asteriskcraft.faction.Faction;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;

/**
 * Decides and applies the match outcome when a faction core block is destroyed. Every removal of a
 * base's core routes here through its block entity's {@code preRemoveSideEffects}, so this is the
 * single place win/lose is resolved regardless of what did the destroying (a siege unit, the player
 * mining, an explosion). The core decision is a pure function ({@link #decide}) so it can be
 * unit-tested without a world.
 *
 * <p>The rule is <b>symmetric</b>: a side is out when its <em>last</em> base falls, whichever side
 * that is. It used to be asymmetric — one recorded Nexus position meant instant defeat while the
 * swarm got to lose three Hives — which stopped being expressible the moment both sides became the
 * same kind of thing. It is also the better rule: an expansion base warped in from a kit is now
 * worth what it looks like it is worth, on both sides.
 */
public final class GameOutcome {
    /** The consequence of a single core being destroyed. */
    public enum Result {
        /** The destroyed block was not a tracked core (or the match is already over). */
        NONE,
        /** A base fell, but its army still holds others. */
        BASE_RAZED,
        /** The computer's last base fell — the player wins. */
        VICTORY,
        /** The player's last base fell — the player loses. */
        DEFEAT
    }

    private GameOutcome() {
    }

    /**
     * Pure outcome decision. Given whose base was just destroyed, how many bases that side still
     * has standing afterwards, and which side the human commands, returns what it means for the
     * match.
     */
    public static Result decide(Faction razedFaction, int remainingCores, Faction playerFaction, boolean alreadyOver) {
        if (alreadyOver || razedFaction == Faction.NEUTRAL) {
            return Result.NONE;
        }
        if (remainingCores > 0) {
            return Result.BASE_RAZED;
        }
        return razedFaction == playerFaction ? Result.DEFEAT : Result.VICTORY;
    }

    /** Called from a base's {@code preRemoveSideEffects} when its core block is removed. */
    public static void onCoreDestroyed(Level level, BlockPos pos) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }
        if (!(level.getBlockEntity(pos) instanceof FactionCore core)) {
            return;
        }
        ServerLevel overworld = serverLevel.getServer().overworld();
        boolean over = overworld.getData(GameAttachments.GAME_OVER);
        Faction razed = core.buildingFaction();
        // Excludes the core being removed explicitly: CoreSpoils.spill has already dropped it from
        // the census by now, but this must not depend on which of the two side effects runs first.
        int remaining = CoreCensus.count(serverLevel, razed, pos);

        Result result = decide(razed, remaining, MatchSetup.of(level).playerFaction(), over);
        switch (result) {
            case DEFEAT -> {
                overworld.setData(GameAttachments.GAME_OVER, true);
                broadcast(overworld, Component.translatable("message.asteriskcraft.defeat"));
                playOutcomeSound(overworld, false);
            }
            case BASE_RAZED -> broadcast(overworld,
                    Component.translatable("message.asteriskcraft.base_razed", remaining));
            case VICTORY -> {
                overworld.setData(GameAttachments.GAME_OVER, true);
                broadcast(overworld, Component.translatable("message.asteriskcraft.victory"));
                playOutcomeSound(overworld, true);
            }
            case NONE -> {
            }
        }
    }

    private static void broadcast(ServerLevel level, Component message) {
        for (ServerPlayer player : level.getServer().getPlayerList().getPlayers()) {
            player.sendSystemMessage(message);
        }
    }

    private static void playOutcomeSound(ServerLevel level, boolean victory) {
        for (ServerPlayer player : level.getServer().getPlayerList().getPlayers()) {
            level.playSound(null, player.blockPosition(),
                    victory ? SoundEvents.UI_TOAST_CHALLENGE_COMPLETE : SoundEvents.WITHER_SPAWN,
                    SoundSource.MASTER, 1.0f, 1.0f);
        }
    }
}
