package net.bitflora.asteriskcraft.game;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.List;

/**
 * Decides and applies the match outcome when a faction core block is destroyed. Every removal
 * of a Nexus/Hive core routes here through the block entity's {@code preRemoveSideEffects}, so
 * this is the single place win/lose is resolved regardless of what did the destroying (a siege
 * unit, the player mining, an explosion). The core decision is a pure function ({@link #decide})
 * so it can be unit-tested without a world.
 */
public final class GameOutcome {
    /** The consequence of a single core being destroyed. */
    public enum Result {
        /** The destroyed block was not a tracked core (or the match is already over). */
        NONE,
        /** A Hive fell, but others remain. */
        HIVE_RAZED,
        /** The last Hive fell — the player wins. */
        VICTORY,
        /** The Nexus fell — the player loses. */
        DEFEAT
    }

    private GameOutcome() {
    }

    /**
     * Pure outcome decision. Given the current Nexus position, surviving Hive positions, and the
     * block that was just destroyed, returns what it means for the match.
     */
    public static Result decide(BlockPos nexusPos, List<BlockPos> hivePositions, BlockPos destroyedPos, boolean alreadyOver) {
        if (alreadyOver) {
            return Result.NONE;
        }
        if (destroyedPos.equals(nexusPos)) {
            return Result.DEFEAT;
        }
        if (hivePositions.contains(destroyedPos)) {
            return hivePositions.size() <= 1 ? Result.VICTORY : Result.HIVE_RAZED;
        }
        return Result.NONE;
    }

    /** Called from a core block entity's {@code preRemoveSideEffects} when its block is removed. */
    public static void onCoreDestroyed(Level level, BlockPos pos) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }
        ServerLevel overworld = serverLevel.getServer().overworld();
        boolean over = overworld.getData(GameAttachments.GAME_OVER);
        BlockPos nexus = overworld.getData(GameAttachments.NEXUS_POS);
        List<BlockPos> hives = new ArrayList<>(overworld.getData(GameAttachments.HIVE_POSITIONS));

        Result result = decide(nexus, hives, pos, over);
        switch (result) {
            case DEFEAT -> {
                overworld.setData(GameAttachments.GAME_OVER, true);
                broadcast(overworld, Component.translatable("message.asteriskcraft.defeat"));
                playOutcomeSound(overworld, pos, false);
            }
            case HIVE_RAZED -> {
                hives.remove(pos);
                overworld.setData(GameAttachments.HIVE_POSITIONS, List.copyOf(hives));
                broadcast(overworld, Component.translatable("message.asteriskcraft.hive_razed", hives.size()));
            }
            case VICTORY -> {
                hives.remove(pos);
                overworld.setData(GameAttachments.HIVE_POSITIONS, List.copyOf(hives));
                overworld.setData(GameAttachments.GAME_OVER, true);
                broadcast(overworld, Component.translatable("message.asteriskcraft.victory"));
                playOutcomeSound(overworld, pos, true);
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

    private static void playOutcomeSound(ServerLevel level, BlockPos pos, boolean victory) {
        for (ServerPlayer player : level.getServer().getPlayerList().getPlayers()) {
            level.playSound(null, player.blockPosition(),
                    victory ? SoundEvents.UI_TOAST_CHALLENGE_COMPLETE : SoundEvents.WITHER_SPAWN,
                    SoundSource.MASTER, 1.0f, 1.0f);
        }
    }
}
