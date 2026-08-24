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
import net.minecraft.world.level.gamerules.GameRule;
import net.minecraft.world.level.gamerules.GameRules;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ScreenEvent;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.function.Supplier;

/**
 * The race pickers on the world-creation screen: two cycle buttons, {@code Race} and
 * {@code Enemy Race}, stacked in the Game tab directly under Allow Commands.
 *
 * <p>Two rather than one because with three races the opponent stopped being derivable — see
 * {@code game.MatchSetup.forRaces}. They store nothing of their own. The answers live in the
 * {@code player_race} and {@code ai_race} game rules ({@link AsteriskCraftGameRules}), which are
 * already on the {@code GameRules} the screen's {@code WorldCreationUiState} carries into the world
 * it builds — so this is purely a nicer front door onto the same values that More &rarr; Game Rules
 * edits, and the two can never disagree. Recreating a world seeds that state from the old world's
 * rules, so the buttons come up showing the matchup that world was played as with no handling here.
 *
 * <p>The pair also owns the "the two sides may not be the same race" invariant at the point of
 * choosing: setting one to what the other already shows pushes the other off it. That is a
 * convenience, not the guarantee — a rule is an int that a command can set, and a dedicated server
 * has no screen at all — so {@code MatchSetup.forRaces} re-derives the opponent server-side and is
 * where the reasoning for the invariant lives.
 *
 * <p>{@link ScreenEvent.Init.Post} is the only seam: {@code CreateWorldScreen}'s three tabs are
 * private inner classes with no NeoForge hook, so a widget cannot be added <em>to</em> the Game
 * tab's grid — it can only be added to the screen and then made to behave like a member of it.
 * That takes two things:
 * <ul>
 *   <li><b>An anchor.</b> The Allow Commands button is found by walking each widget's message for
 *       the {@code selectWorld.allowCommands} translation key rather than by matching its text —
 *       a {@code CycleButton}'s message is name-and-value, and matching rendered text would break
 *       in every language but English. The pickers then borrow that button's x and width, so they
 *       line up with the column whatever the screen's layout does with it.</li>
 *   <li><b>A tab check, every frame.</b> {@code TabManager} adds and removes a tab's widgets as
 *       tabs are switched, and a widget owned by the screen is not among them — so without this it
 *       would hang over the World and More tabs. Whether the anchor is currently one of the
 *       screen's children is exactly "is the Game tab open", and {@link ScreenEvent.Render.Pre} is
 *       where that is applied, since {@code AbstractWidget.extractRenderState} is final and skips
 *       an invisible widget outright.</li>
 * </ul>
 *
 * <p>If the anchor ever goes missing — a Mojang rename, another mod rebuilding the tab — the
 * pickers fall back to the footer band and stay visible on every tab. Losing the tidy placement is
 * worth keeping the only in-screen way to pick a matchup.
 */
@EventBusSubscriber(modid = AsteriskCraft.MODID, value = Dist.CLIENT)
public final class RacePickerOverlay {
    /** The Game tab's own button width; the pickers match its column rather than setting their own. */
    private static final int FALLBACK_WIDTH = 210;
    private static final int HEIGHT = 20;
    /** {@code CreateWorldScreen.GameTab}'s grid row spacing, so the pickers sit on the rows below. */
    private static final int ROW_SPACING = 8;
    /** Where the pickers go when there is no Allow Commands button to sit under. */
    private static final int FALLBACK_MARGIN = 6;

    private static final String ANCHOR_KEY = "selectWorld.allowCommands";

    /**
     * The live pickers and what they are following, for the per-frame sync below. Held statically
     * because {@code init()} is the only place a widget can be added and the tab it belongs to
     * changes long afterwards; {@link #screen} is what keeps a stale pair from being applied to a
     * different screen, and re-entering world creation simply overwrites all three.
     */
    private static @Nullable Screen screen;
    private static @Nullable CycleButton<Race> playerPicker;
    private static @Nullable CycleButton<Race> aiPicker;
    private static @Nullable AbstractWidget anchor;

    private RacePickerOverlay() {
    }

    @SubscribeEvent
    public static void onScreenInit(ScreenEvent.Init.Post event) {
        if (!(event.getScreen() instanceof CreateWorldScreen created)) {
            return;
        }
        GameRules rules = created.getUiState().getGameRules();
        CycleButton<Race> player = picker(rules, "gamerule.asteriskcraft.player_race",
                AsteriskCraftGameRules.PLAYER_RACE.get(), () -> aiPicker);
        CycleButton<Race> ai = picker(rules, "gamerule.asteriskcraft.ai_race",
                AsteriskCraftGameRules.AI_RACE.get(), () -> playerPicker);

        screen = created;
        playerPicker = player;
        aiPicker = ai;
        anchor = findAnchor(event.getListenersList());
        // Positioned before the first frame as well as on every one after it, so the pickers are
        // never drawn for a frame at (0, 0).
        place(created);
        event.addListener(player);
        event.addListener(ai);
    }

    /**
     * One picker: it writes its own rule, then shoves the other one off the race it just took.
     *
     * <p>{@code other} is a supplier because the two buttons refer to each other and one has to be
     * built first. The nudge writes the other rule explicitly: {@code CycleButton.setValue} updates
     * the value and the label but does <em>not</em> fire its {@code OnValueChange} (verified
     * against the decompiled 26.1.2 source), which is what keeps this from recursing and what makes
     * the second {@code rules.set} necessary rather than redundant.
     */
    private static CycleButton<Race> picker(GameRules rules, String nameKey, GameRule<Integer> rule,
            Supplier<CycleButton<Race>> other) {
        return CycleButton
                .builder(RacePickerOverlay::label, AsteriskCraftGameRules.raceOf(rules.get(rule)))
                .withValues(Race.values())
                .create(0, 0, FALLBACK_WIDTH, HEIGHT, Component.translatable(nameKey),
                        (widget, race) -> {
                            rules.set(rule, race.ordinal(), null);
                            displace(rules, other.get(), race);
                        });
    }

    /** Moves {@code button} off {@code taken} if it is showing it, keeping its rule in step. */
    private static void displace(GameRules rules, @Nullable CycleButton<Race> button, Race taken) {
        if (button == null || button.getValue() != taken) {
            return;
        }
        GameRule<Integer> rule = button == playerPicker
                ? AsteriskCraftGameRules.PLAYER_RACE.get()
                : AsteriskCraftGameRules.AI_RACE.get();
        for (Race race : Race.values()) {
            if (race != taken) {
                button.setValue(race);
                rules.set(rule, race.ordinal(), null);
                return;
            }
        }
    }

    /**
     * Keeps the pickers on the Game tab, under the button they follow. Cheap enough to do every
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
        CycleButton<Race> player = playerPicker;
        CycleButton<Race> ai = aiPicker;
        if (player == null || ai == null) {
            return;
        }
        AbstractWidget followed = anchor;
        if (followed == null) {
            // No anchor to follow: the footer band is the one strip no tab owns, so the pickers are
            // always visible there rather than lost. Side by side, since only one row is spare
            // down there.
            int width = FALLBACK_WIDTH / 2;
            int y = host.height - HEIGHT - FALLBACK_MARGIN;
            for (CycleButton<Race> button : List.of(player, ai)) {
                button.visible = true;
                button.setWidth(width);
            }
            player.setPosition(FALLBACK_MARGIN, y);
            ai.setPosition(FALLBACK_MARGIN + width + FALLBACK_MARGIN, y);
            return;
        }
        boolean gameTabOpen = host.children().contains(followed);
        int y = followed.getY() + followed.getHeight() + ROW_SPACING;
        for (CycleButton<Race> button : List.of(player, ai)) {
            button.visible = gameTabOpen;
            button.active = gameTabOpen;
            if (gameTabOpen) {
                button.setWidth(followed.getWidth());
                button.setPosition(followed.getX(), y);
            }
            y += HEIGHT + ROW_SPACING;
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

    private static Component label(Race race) {
        return Component.translatable("race.asteriskcraft." + race.getSerializedName());
    }
}
