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
 * icon, live training progress bar, and queued count are all drawn manually over it from the
 * menu's synced data slots. Buttons carry no visible name — the icon identifies the unit and
 * the tooltip gives its cost.
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

    /** Top-left of one option's (icon-sized) button, in local coordinates relative to (leftPos, topPos). */
    private record Placement(int x, int y) {
    }

    /**
     * Assigns every option a cell in its {@link ProductionKind.OptionView#column()}, stacked
     * top-to-bottom in list order. Cells are exactly button-sized (see {@link ProductionMenu}),
     * so the grid packs tight; it's centered in the panel as a whole, and a column shorter than
     * {@link ProductionKind#maxRows()} is vertically centered against the tallest one instead of
     * hugging the top.
     */
    private static List<Placement> layOutOptions(ProductionKind kind) {
        List<ProductionKind.OptionView> options = kind.options();
        int startX = ProductionMenu.buttonStartX(kind.columns());
        int maxRows = kind.maxRows();
        int[] rowCounter = new int[kind.columns()];
        List<Placement> placements = new ArrayList<>(options.size());
        for (ProductionKind.OptionView option : options) {
            int col = option.column();
            int row = rowCounter[col]++;
            int verticalOffset = (maxRows - kind.rowsInColumn(col)) * ProductionMenu.BUTTON_ROW_SPACING / 2;
            int x = startX + col * (ProductionMenu.BUTTON_W + ProductionMenu.BUTTON_COLUMN_GAP);
            int y = ProductionMenu.BUTTON_Y + verticalOffset + row * ProductionMenu.BUTTON_ROW_SPACING;
            placements.add(new Placement(x, y));
        }
        return placements;
    }

    @Override
    protected void init() {
        super.init();
        this.optionButtons.clear();
        ProductionKind kind = this.menu.getKind();
        List<ProductionKind.OptionView> options = kind.options();
        List<Placement> placements = layOutOptions(kind);
        for (int i = 0; i < options.size(); i++) {
            final int optionIndex = i;
            ProductionKind.OptionView option = options.get(i);
            Placement placement = placements.get(i);
            int bx = this.leftPos + placement.x();
            int by = this.topPos + placement.y();
            // Empty label: buttons show no name, just the icon drawn manually in extractLabels.
            Button button = Button.builder(Component.empty(), b -> onTrain(optionIndex))
                    .bounds(bx, by, ProductionMenu.BUTTON_W, ProductionMenu.BUTTON_H)
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
    private static final int PROGRESS_BAR_HEIGHT = 4;

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
        List<Placement> placements = layOutOptions(kind);
        int buildingIndex = this.menu.buildingOptionIndex();
        int total = this.menu.buildTotal();
        int progress = this.menu.buildProgress();

        for (int i = 0; i < options.size(); i++) {
            int bx = placements.get(i).x();
            int by = placements.get(i).y();
            int centerX = bx + ProductionMenu.BUTTON_W / 2;

            // Icon centered in the space above the progress bar — no name label; the icon
            // identifies the unit and the resource it pays with is only in the hover tooltip.
            int iconTop = by + (ProductionMenu.BUTTON_H - PROGRESS_BAR_HEIGHT - ICON_SIZE) / 2;
            drawIcon(graphics, options.get(i).icon(), centerX, iconTop);

            // Queued count badge in the top-right corner.
            int queued = this.menu.queuedCount(i);
            if (queued > 0) {
                Component count = Component.literal("x" + queued);
                int tw = this.font.width(count);
                graphics.text(this.font, count, bx + ProductionMenu.BUTTON_W - tw - 3, by + 3, TEXT_COLOR, true);
            }

            // Progress bar along the bottom edge while this option is the one building.
            int barLeft = bx + 1;
            int barRight = bx + ProductionMenu.BUTTON_W - 1;
            int barTop = by + ProductionMenu.BUTTON_H - PROGRESS_BAR_HEIGHT;
            graphics.fill(barLeft, barTop, barRight, barTop + 3, BAR_BG);
            if (i == buildingIndex && total > 0) {
                int filled = (int) ((barRight - barLeft) * (long) progress / total);
                graphics.fill(barLeft, barTop, barLeft + filled, barTop + 3, BAR_FILL);
            }
        }

        if (this.menu.warpTicks() > 0) {
            int secs = (this.menu.warpTicks() + 19) / 20;
            Component warp = Component.translatable("gui.asteriskcraft.warping", secs);
            int cy = ProductionMenu.BUTTON_Y + ProductionMenu.BUTTON_ROW_SPACING * kind.maxRows() / 2;
            graphics.centeredText(this.font, warp, this.imageWidth / 2, cy, WARP_COLOR);
        }
    }
}
