package net.bitflora.asteriskcraft.client;

import net.bitflora.asteriskcraft.AsteriskCraft;
import net.bitflora.asteriskcraft.faction.Race;
import net.bitflora.asteriskcraft.game.AsteriskCraftGameRules;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.gamerules.GameRules;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ScreenEvent;

/**
 * The race picker on the world-creation screen: a cycle button reading
 * {@code Race: Protoss}/{@code Race: Zerg}, sitting in the footer band beside Create/Cancel.
 *
 * <p>It stores nothing of its own. The answer lives in the {@code player_race} game rule
 * ({@link AsteriskCraftGameRules}), which is already on the {@code GameRules} the screen's
 * {@code WorldCreationUiState} carries into the world it builds — so this is purely a nicer front
 * door onto the same value that More &rarr; Game Rules edits, and the two can never disagree.
 * Recreating a world seeds that state from the old world's rules, so the button comes up showing
 * the race that world was played as with no handling here.
 *
 * <p>{@link ScreenEvent.Init.Post} is the only seam: {@code CreateWorldScreen}'s three tabs are
 * private inner classes with no NeoForge hook, so a widget cannot be added <em>to</em> a tab. That
 * is actually what we want — a listener added here belongs to the screen rather than to a tab, so
 * the picker stays visible whichever tab is open. The event re-fires on every {@code init()},
 * including resizes, so the position is recomputed from the live screen each time and no widget
 * reference is kept.
 *
 * <p>The footer band is the one strip the tab content area does not own. On a very narrow window
 * the button can crowd the centred Create/Cancel pair; the game-rules screen remains a complete
 * fallback if it ever does.
 */
@EventBusSubscriber(modid = AsteriskCraft.MODID, value = Dist.CLIENT)
public final class RacePickerOverlay {
    private static final int MARGIN = 6;
    private static final int WIDTH = 104;
    private static final int HEIGHT = 20;

    private RacePickerOverlay() {
    }

    @SubscribeEvent
    public static void onScreenInit(ScreenEvent.Init.Post event) {
        if (!(event.getScreen() instanceof CreateWorldScreen screen)) {
            return;
        }
        GameRules rules = screen.getUiState().getGameRules();
        event.addListener(CycleButton
                .builder(RacePickerOverlay::label, AsteriskCraftGameRules.raceOf(current(rules)))
                .withValues(Race.values())
                .create(MARGIN, screen.height - HEIGHT - MARGIN, WIDTH, HEIGHT,
                        Component.translatable("gamerule.asteriskcraft.player_race"),
                        (button, race) -> rules.set(AsteriskCraftGameRules.PLAYER_RACE.get(),
                                race.ordinal(), null)));
    }

    private static int current(GameRules rules) {
        return rules.get(AsteriskCraftGameRules.PLAYER_RACE.get());
    }

    private static Component label(Race race) {
        return Component.translatable("race.asteriskcraft." + race.getSerializedName());
    }
}
