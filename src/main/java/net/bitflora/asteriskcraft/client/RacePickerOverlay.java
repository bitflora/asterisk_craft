package net.bitflora.asteriskcraft.client;

import net.bitflora.asteriskcraft.AsteriskCraft;
import net.bitflora.asteriskcraft.faction.Race;
import net.bitflora.asteriskcraft.game.AsteriskCraftGameRules;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.world.level.gamerules.GameRules;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ScreenEvent;
import org.jetbrains.annotations.Nullable;

/**
 * The race picker on the world-creation screen: a cycle button reading
 * {@code Race: Protoss}/{@code Race: Zerg}, sitting in the Game tab directly under Allow Commands.
 *
 * <p>It stores nothing of its own. The answer lives in the {@code player_race} game rule
 * ({@link AsteriskCraftGameRules}), which is already on the {@code GameRules} the screen's
 * {@code WorldCreationUiState} carries into the world it builds — so this is purely a nicer front
 * door onto the same value that More &rarr; Game Rules edits, and the two can never disagree.
 * Recreating a world seeds that state from the old world's rules, so the button comes up showing
 * the race that world was played as with no handling here.
 *
 * <p>{@link ScreenEvent.Init.Post} is the only seam: {@code CreateWorldScreen}'s three tabs are
 * private inner classes with no NeoForge hook, so a widget cannot be added <em>to</em> the Game
 * tab's grid — it can only be added to the screen and then made to behave like a member of it.
 * That takes two things:
 * <ul>
 *   <li><b>An anchor.</b> The Allow Commands button is found by walking each widget's message for
 *       the {@code selectWorld.allowCommands} translation key rather than by matching its text —
 *       a {@code CycleButton}'s message is name-and-value, and matching rendered text would break
 *       in every language but English. The picker then borrows that button's x and width, so it
 *       lines up with the column whatever the screen's layout does with it.</li>
 *   <li><b>A tab check, every frame.</b> {@code TabManager} adds and removes a tab's widgets as
 *       tabs are switched, and a widget owned by the screen is not among them — so without this it
 *       would hang over the World and More tabs. Whether the anchor is currently one of the
 *       screen's children is exactly "is the Game tab open", and {@link ScreenEvent.Render.Pre} is
 *       where that is applied, since {@code AbstractWidget.extractRenderState} is final and skips
 *       an invisible widget outright.</li>
 * </ul>
 *
 * <p>If the anchor ever goes missing — a Mojang rename, another mod rebuilding the tab — the picker
 * falls back to the bottom-left of the footer band and stays visible on every tab. Losing the tidy
 * placement is worth keeping the only in-screen way to pick a race.
 */
@EventBusSubscriber(modid = AsteriskCraft.MODID, value = Dist.CLIENT)
public final class RacePickerOverlay {
    /** The Game tab's own button width; the picker matches its column rather than setting its own. */
    private static final int FALLBACK_WIDTH = 210;
    private static final int HEIGHT = 20;
    /** {@code CreateWorldScreen.GameTab}'s grid row spacing, so the picker sits on the next row. */
    private static final int ROW_SPACING = 8;
    /** Where the picker goes when there is no Allow Commands button to sit under. */
    private static final int FALLBACK_MARGIN = 6;

    private static final String ANCHOR_KEY = "selectWorld.allowCommands";

    /**
     * The live picker and what it is following, for the per-frame sync below. Held statically
     * because {@code init()} is the only place a widget can be added and the tab it belongs to
     * changes long afterwards; {@link #screen} is what keeps a stale pair from being applied to a
     * different screen, and re-entering world creation simply overwrites all three.
     */
    private static @Nullable Screen screen;
    private static @Nullable CycleButton<Race> picker;
    private static @Nullable AbstractWidget anchor;

    private RacePickerOverlay() {
    }

    @SubscribeEvent
    public static void onScreenInit(ScreenEvent.Init.Post event) {
        if (!(event.getScreen() instanceof CreateWorldScreen created)) {
            return;
        }
        GameRules rules = created.getUiState().getGameRules();
        CycleButton<Race> button = CycleButton
                .builder(RacePickerOverlay::label, AsteriskCraftGameRules.raceOf(current(rules)))
                .withValues(Race.values())
                .create(0, 0, FALLBACK_WIDTH, HEIGHT,
                        Component.translatable("gamerule.asteriskcraft.player_race"),
                        (widget, race) -> rules.set(AsteriskCraftGameRules.PLAYER_RACE.get(),
                                race.ordinal(), null));

        screen = created;
        picker = button;
        anchor = findAnchor(event.getListenersList());
        // Positioned before the first frame as well as on every one after it, so the picker is
        // never drawn for a frame at (0, 0).
        place(created);
        event.addListener(button);
    }

    /**
     * Keeps the picker on the Game tab, under the button it follows. Cheap enough to do every
     * frame — one scan of a screen's handful of children — and doing it every frame is what
     * survives a tab switch, a window resize and a layout the screen re-arranges on its own.
     */
    @SubscribeEvent
    public static void onScreenRender(ScreenEvent.Render.Pre event) {
        if (event.getScreen() == screen) {
            place(event.getScreen());
        }
    }

    private static void place(Screen host) {
        CycleButton<Race> button = picker;
        if (button == null) {
            return;
        }
        AbstractWidget followed = anchor;
        if (followed == null) {
            // No anchor to follow: the footer band is the one strip no tab owns, so the picker is
            // always visible there rather than lost.
            button.visible = true;
            button.setWidth(FALLBACK_WIDTH / 2);
            button.setPosition(FALLBACK_MARGIN, host.height - HEIGHT - FALLBACK_MARGIN);
            return;
        }
        boolean gameTabOpen = host.children().contains(followed);
        button.visible = gameTabOpen;
        button.active = gameTabOpen;
        if (gameTabOpen) {
            button.setWidth(followed.getWidth());
            button.setPosition(followed.getX(), followed.getY() + followed.getHeight() + ROW_SPACING);
        }
    }

    /**
     * The Allow Commands button, found by the translation key buried in its message rather than by
     * the text that message renders to. A {@code CycleButton}'s message is
     * {@code options.generic_value(name, value)}, so the key sits one level down in the arguments —
     * hence the walk.
     */
    private static @Nullable AbstractWidget findAnchor(Iterable<GuiEventListener> listeners) {
        for (GuiEventListener listener : listeners) {
            if (listener instanceof AbstractWidget widget && mentions(widget.getMessage(), ANCHOR_KEY)) {
                return widget;
            }
        }
        return null;
    }

    private static boolean mentions(Component component, String key) {
        if (component.getContents() instanceof TranslatableContents translatable) {
            if (translatable.getKey().equals(key)) {
                return true;
            }
            for (Object arg : translatable.getArgs()) {
                if (arg instanceof Component nested && mentions(nested, key)) {
                    return true;
                }
            }
        }
        for (Component sibling : component.getSiblings()) {
            if (mentions(sibling, key)) {
                return true;
            }
        }
        return false;
    }

    private static int current(GameRules rules) {
        return rules.get(AsteriskCraftGameRules.PLAYER_RACE.get());
    }

    private static Component label(Race race) {
        return Component.translatable("race.asteriskcraft." + race.getSerializedName());
    }
}
