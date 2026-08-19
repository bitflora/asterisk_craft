package net.bitflora.asteriskcraft.command.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.bitflora.asteriskcraft.command.UnitGroupPacket;
import net.bitflora.asteriskcraft.command.UnitGroupSnapshot;
import net.bitflora.asteriskcraft.command.UnitGroups;
import net.bitflora.asteriskcraft.command.UnitLabels;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import org.lwjgl.glfw.GLFW;

/**
 * The unit-group overlay: ten blocks in 2 columns of 5, numbered 1-5 down the left column and 6-9
 * then 0 down the right. Opened by {@link UnitGroupKeys} in one of two {@link Mode}s — assign (C)
 * writes the current selection into the block you pick, select (V) makes that block's units the
 * selection. An assigned block shows what it holds, e.g. {@code 1Z 3D}.
 *
 * <p>Picking a slot only ever <em>sends</em> a {@link UnitGroupPacket}; the server decides what the
 * pick means (see {@code command.UnitGroupResolver}), including the rule that picking an empty
 * block from the select overlay assigns to it instead.
 *
 * <p>Not a pause screen, and it paints no vanilla menu background: this opens mid-match, over a
 * running battle the player still needs to see.
 *
 * <p>This version's GUI is extraction-based — there is no {@code render(GuiGraphics, ...)} to
 * override, and key/mouse callbacks take event records. See docs/neoforge-api-notes.md.
 */
public class UnitGroupScreen extends Screen {
    /** Which key opened the overlay. */
    public enum Mode {
        ASSIGN("gui.asteriskcraft.groups.assign_title"),
        SELECT("gui.asteriskcraft.groups.select_title");

        private final String titleKey;

        Mode(String titleKey) {
            this.titleKey = titleKey;
        }

        Component title() {
            return Component.translatable(this.titleKey);
        }
    }

    /** Rows per column; two columns of these make up the {@link UnitGroups#SLOTS} blocks. */
    public static final int ROWS = 5;
    public static final int COLUMNS = UnitGroups.SLOTS / ROWS;

    public static final int BLOCK_W = 80;
    public static final int BLOCK_H = 24;
    public static final int GAP = 4;
    private static final int PANEL_PAD = 8;
    private static final int TITLE_H = 16;

    private static final int PANEL_BG = 0xD0101010;
    private static final int PANEL_EDGE = 0xFF6A6A6A;
    private static final int DIGIT_COLOR = 0xFFFFE070;
    private static final int DIGIT_EMPTY_COLOR = 0xFF6A6A6A;
    private static final int LABEL_COLOR = 0xFFFFFFFF;
    private static final int TITLE_COLOR = 0xFFFFFFFF;

    private final Mode mode;
    private final UnitGroupSnapshot snapshot;
    private int gridLeft;
    private int gridTop;

    public UnitGroupScreen(Mode mode) {
        super(mode.title());
        this.mode = mode;
        // Taken once at open: a sync landing mid-frame would shuffle labels under the player's cursor.
        this.snapshot = ClientUnitGroups.snapshot();
    }

    /** A block's rectangle, in coordinates relative to the grid's top-left corner. */
    public record Rect(int x, int y, int width, int height) {
    }

    /**
     * Where a slot sits in the grid — column-major, so the left column reads 1-5 top to bottom and
     * the right column 6-9 then 0. Pure, so the layout is unit-testable without a client.
     */
    public static Rect slotBounds(int index) {
        if (!UnitGroups.isValidSlot(index)) {
            throw new IllegalArgumentException("slot " + index + " outside 0.." + (UnitGroups.SLOTS - 1));
        }
        int column = index / ROWS;
        int row = index % ROWS;
        return new Rect(column * (BLOCK_W + GAP), row * (BLOCK_H + GAP), BLOCK_W, BLOCK_H);
    }

    /** The digit a slot is labelled with: slots 0-8 read 1..9, slot 9 reads 0. */
    public static String displayDigit(int index) {
        return String.valueOf((index + 1) % 10);
    }

    /** Number-row or numpad key to slot index, or -1 for anything else. Pure, unit-testable. */
    public static int slotForKey(int key) {
        if (key >= GLFW.GLFW_KEY_1 && key <= GLFW.GLFW_KEY_9) {
            return key - GLFW.GLFW_KEY_1;
        }
        if (key >= GLFW.GLFW_KEY_KP_1 && key <= GLFW.GLFW_KEY_KP_9) {
            return key - GLFW.GLFW_KEY_KP_1;
        }
        if (key == GLFW.GLFW_KEY_0 || key == GLFW.GLFW_KEY_KP_0) {
            return UnitGroups.SLOTS - 1;
        }
        return -1;
    }

    private static int gridWidth() {
        return COLUMNS * BLOCK_W + (COLUMNS - 1) * GAP;
    }

    private static int gridHeight() {
        return ROWS * BLOCK_H + (ROWS - 1) * GAP;
    }

    @Override
    protected void init() {
        this.gridLeft = (this.width - gridWidth()) / 2;
        this.gridTop = (this.height - gridHeight() - TITLE_H) / 2 + TITLE_H;
        for (int slot = 0; slot < UnitGroups.SLOTS; slot++) {
            final int index = slot;
            Rect bounds = slotBounds(slot);
            // Empty label: the digit and the group's contents are drawn manually over the button.
            Button button = Button.builder(CommonComponents.EMPTY, b -> pick(index))
                    .bounds(this.gridLeft + bounds.x(), this.gridTop + bounds.y(), bounds.width(), bounds.height())
                    .build();
            Component tooltip = tooltipFor(slot);
            if (tooltip != null) {
                button.setTooltip(Tooltip.create(tooltip));
            }
            addRenderableWidget(button);
        }
    }

    /** The full unit names behind a block's abbreviation, or null for an empty slot. */
    private Component tooltipFor(int slot) {
        UnitGroupSnapshot.Slot contents = this.snapshot.slot(slot);
        if (contents.isEmpty()) {
            return null;
        }
        MutableComponent lines = Component.empty();
        boolean first = true;
        for (UnitGroupSnapshot.Entry entry : contents.entries()) {
            if (!first) {
                lines.append("\n");
            }
            lines.append(Component.translatable("gui.asteriskcraft.groups.tooltip_line",
                    entry.count(), entry.type().getDescription()));
            first = false;
        }
        return lines;
    }

    private void pick(int slot) {
        ClientPacketDistributor.sendToServer(new UnitGroupPacket(slot, this.mode == Mode.SELECT));
        onClose();
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        int slot = slotForKey(event.key());
        if (slot >= 0) {
            pick(slot);
            return true;
        }
        // A second press of whichever key opened this closes it again. Compared against the raw key
        // rather than KeyMapping#isActiveAndMatches, which reports inactive while any screen is up
        // (both mappings are bound with KeyConflictContext.IN_GAME).
        if (matchesOpeningKey(event)) {
            onClose();
            return true;
        }
        return super.keyPressed(event);
    }

    private boolean matchesOpeningKey(KeyEvent event) {
        KeyMapping mapping = this.mode == Mode.ASSIGN ? UnitGroupKeys.ASSIGN_GROUP : UnitGroupKeys.SELECT_GROUP;
        return mapping.getKey().equals(InputConstants.getKey(event));
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        // Deliberately no super call: the vanilla background blurs/dims the world behind the panel,
        // and this overlay is meant to sit over a match still being fought.
        int left = this.gridLeft - PANEL_PAD;
        int top = this.gridTop - PANEL_PAD - TITLE_H;
        int right = this.gridLeft + gridWidth() + PANEL_PAD;
        int bottom = this.gridTop + gridHeight() + PANEL_PAD;
        graphics.fill(left, top, right, bottom, PANEL_BG);
        graphics.fill(left, top, right, top + 1, PANEL_EDGE);
        graphics.fill(left, bottom - 1, right, bottom, PANEL_EDGE);
        graphics.fill(left, top, left + 1, bottom, PANEL_EDGE);
        graphics.fill(right - 1, top, right, bottom, PANEL_EDGE);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        super.extractRenderState(graphics, mouseX, mouseY, a); // the buttons themselves

        graphics.centeredText(this.font, this.title, this.width / 2,
                this.gridTop - TITLE_H + (TITLE_H - this.font.lineHeight) / 2, TITLE_COLOR);

        for (int slot = 0; slot < UnitGroups.SLOTS; slot++) {
            Rect bounds = slotBounds(slot);
            int x = this.gridLeft + bounds.x();
            int y = this.gridTop + bounds.y();
            int textY = y + (BLOCK_H - this.font.lineHeight) / 2;
            UnitGroupSnapshot.Slot contents = this.snapshot.slot(slot);

            String digit = displayDigit(slot);
            graphics.text(this.font, digit, x + 6, textY,
                    contents.isEmpty() ? DIGIT_EMPTY_COLOR : DIGIT_COLOR, true);
            if (!contents.isEmpty()) {
                graphics.text(this.font, UnitLabels.format(contents.entries()),
                        x + 6 + this.font.width(digit) + 6, textY, LABEL_COLOR, true);
            }
        }
    }
}
