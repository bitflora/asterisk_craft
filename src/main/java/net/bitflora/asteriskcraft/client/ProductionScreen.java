package net.bitflora.asteriskcraft.client;

import net.bitflora.asteriskcraft.building.ProductionKind;
import net.bitflora.asteriskcraft.building.ProductionMenu;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * Screen for the shared {@link ProductionMenu}. Draws the panel and slot backgrounds with
 * primitives (no texture), laid out column-major per {@link ProductionKind#columns()} — one
 * column per unit, each holding that unit's buttons stacked vertically (e.g. the Nexus's
 * Wood/Stone pair). Each button is otherwise empty (vanilla draws just its background); the
 * icon, unit name, live training progress bar, and queued count are all drawn manually over
 * it from the menu's synced data slots, with the icon centered above the name so neither
 * collides at these narrow column widths.
 *
 * <p>Uses this version's render-state extraction pipeline: {@code extractBackground} draws
 * behind everything in absolute coordinates, {@code extractLabels} draws the foreground in
 * coordinates already translated to {@code leftPos/topPos}.
 *
 * <p>TODO(polish, V5): ship a real {@code textures/gui/production.png} background instead of
 * the primitive-drawn panel used for v1. Unit icons themselves are real command-card-style
 * textures (see {@link ProductionKind.Icon}), not placeholders.
 */
public class ProductionScreen extends AbstractContainerScreen<ProductionMenu> {
    private static final int PANEL_BG = 0xFFC6C6C6;
    private static final int EDGE_LIGHT = 0xFFFFFFFF;
    private static final int EDGE_DARK = 0xFF555555;
    private static final int SLOT_BG = 0xFF8B8B8B;
    private static final int SLOT_DARK = 0xFF373737;
    private static final int BAR_BG = 0xFF373737;
    private static final int BAR_FILL = 0xFF41C74F;
    private static final int TEXT_COLOR = 0xFFFFFFFF;
    private static final int WARP_COLOR = 0xFFFFAA00;

    private final List<Button> optionButtons = new ArrayList<>();

    public ProductionScreen(ProductionMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title, ProductionMenu.IMAGE_WIDTH, ProductionMenu.IMAGE_HEIGHT);
        this.inventoryLabelY = this.imageHeight - 94;
    }

    @Override
    protected void init() {
        super.init();
        this.optionButtons.clear();
        ProductionKind kind = this.menu.getKind();
        List<ProductionKind.OptionView> options = kind.options();
        int rowsPerColumn = kind.optionsPerColumn();
        int buttonWidth = ProductionMenu.buttonWidth(kind.columns());
        for (int i = 0; i < options.size(); i++) {
            final int optionIndex = i;
            ProductionKind.OptionView option = options.get(i);
            int col = i / rowsPerColumn;
            int row = i % rowsPerColumn;
            int bx = this.leftPos + ProductionMenu.BUTTON_X + col * (buttonWidth + ProductionMenu.BUTTON_COLUMN_GAP);
            int by = this.topPos + ProductionMenu.BUTTON_Y + row * ProductionMenu.BUTTON_ROW_SPACING;
            // Empty label: the icon and unit name are drawn manually in extractLabels so they can
            // be stacked (icon above name) instead of vanilla Button's single centered line, which
            // would collide with the icon at these narrow column widths.
            Button button = Button.builder(Component.empty(), b -> onTrain(optionIndex))
                    .bounds(bx, by, buttonWidth, ProductionMenu.BUTTON_H)
                    .tooltip(Tooltip.create(option.costTooltip()))
                    .build();
            this.optionButtons.add(button);
            addRenderableWidget(button);
        }
    }

    private void onTrain(int optionIndex) {
        if (this.minecraft != null && this.minecraft.gameMode != null) {
            this.minecraft.gameMode.handleInventoryButtonClick(this.menu.containerId, optionIndex);
        }
    }

    private static final int ICON_SIZE = 16;

    /**
     * Draws a button's {@link ProductionKind.Icon}, centered horizontally on {@code centerX}
     * with its top at {@code top}: a real item render for kit items, or a scaled blit of a
     * hand-picked texture (arbitrary source resolution — {@code width}/{@code height} are the
     * source PNG's own pixel size, only used to compute UVs) for units with no item form.
     */
    private static void drawIcon(GuiGraphicsExtractor graphics, ProductionKind.Icon icon, int centerX, int top) {
        int x = centerX - ICON_SIZE / 2;
        switch (icon) {
            case ProductionKind.Icon.FromItem(ItemStack stack) -> graphics.item(stack, x, top);
            case ProductionKind.Icon.FromTexture(Identifier location, int width, int height) ->
                    graphics.blit(RenderPipelines.GUI_TEXTURED, location, x, top, 0f, 0f, ICON_SIZE, ICON_SIZE, width, height, width, height);
        }
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        super.extractBackground(graphics, mouseX, mouseY, a);

        boolean warping = this.menu.warpTicks() > 0;
        for (Button button : this.optionButtons) {
            button.active = !warping;
        }

        int x = this.leftPos;
        int y = this.topPos;
        int w = this.imageWidth;
        int h = this.imageHeight;

        // Raised panel.
        graphics.fill(x, y, x + w, y + h, PANEL_BG);
        graphics.fill(x, y, x + w, y + 1, EDGE_LIGHT);
        graphics.fill(x, y, x + 1, y + h, EDGE_LIGHT);
        graphics.fill(x, y + h - 1, x + w, y + h, EDGE_DARK);
        graphics.fill(x + w - 1, y, x + w, y + h, EDGE_DARK);

        // Recessed background for every slot (input row + player inventory).
        for (Slot slot : this.menu.slots) {
            int sx = x + slot.x - 1;
            int sy = y + slot.y - 1;
            graphics.fill(sx, sy, sx + 18, sy + 18, SLOT_DARK);
            graphics.fill(sx + 1, sy + 1, sx + 17, sy + 17, SLOT_BG);
        }
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        super.extractLabels(graphics, mouseX, mouseY);

        // Coordinates here are already translated to (leftPos, topPos); use local offsets.
        ProductionKind kind = this.menu.getKind();
        List<ProductionKind.OptionView> options = kind.options();
        int rowsPerColumn = kind.optionsPerColumn();
        int buttonWidth = ProductionMenu.buttonWidth(kind.columns());
        int buildingIndex = this.menu.buildingOptionIndex();
        int total = this.menu.buildTotal();
        int progress = this.menu.buildProgress();

        for (int i = 0; i < options.size(); i++) {
            int col = i / rowsPerColumn;
            int row = i % rowsPerColumn;
            int bx = ProductionMenu.BUTTON_X + col * (buttonWidth + ProductionMenu.BUTTON_COLUMN_GAP);
            int by = ProductionMenu.BUTTON_Y + row * ProductionMenu.BUTTON_ROW_SPACING;
            int centerX = bx + buttonWidth / 2;

            // Icon centered on top, unit name centered below it — no resource mention in the
            // label itself; that only appears in the button's cost tooltip on hover.
            drawIcon(graphics, options.get(i).icon(), centerX, by + 3);
            graphics.centeredText(this.font, options.get(i).label(), centerX, by + 21, TEXT_COLOR);

            // Queued count badge in the top-right corner.
            int queued = this.menu.queuedCount(i);
            if (queued > 0) {
                Component count = Component.literal("x" + queued);
                int tw = this.font.width(count);
                graphics.text(this.font, count, bx + buttonWidth - tw - 3, by + 3, TEXT_COLOR, true);
            }

            // Progress bar along the bottom edge while this option is the one building.
            int barLeft = bx + 1;
            int barRight = bx + buttonWidth - 1;
            int barTop = by + ProductionMenu.BUTTON_H - 4;
            graphics.fill(barLeft, barTop, barRight, barTop + 3, BAR_BG);
            if (i == buildingIndex && total > 0) {
                int filled = (int) ((barRight - barLeft) * (long) progress / total);
                graphics.fill(barLeft, barTop, barLeft + filled, barTop + 3, BAR_FILL);
            }
        }

        if (this.menu.warpTicks() > 0) {
            int secs = (this.menu.warpTicks() + 19) / 20;
            Component warp = Component.translatable("gui.asteriskcraft.warping", secs);
            int cy = ProductionMenu.BUTTON_Y + ProductionMenu.BUTTON_ROW_SPACING * rowsPerColumn / 2;
            graphics.centeredText(this.font, warp, this.imageWidth / 2, cy, WARP_COLOR);
        }
    }
}
