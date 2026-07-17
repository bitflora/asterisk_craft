package net.bitflora.asteriskcraft.client;

import net.bitflora.asteriskcraft.building.ProductionKind;
import net.bitflora.asteriskcraft.building.ProductionMenu;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;

import java.util.ArrayList;
import java.util.List;

/**
 * Screen for the shared {@link ProductionMenu}. Draws the panel and slot backgrounds with
 * primitives (no texture), one train button per unit option, and — over each button — the
 * live training progress bar and queued count read from the menu's synced data slots.
 *
 * <p>Uses this version's render-state extraction pipeline: {@code extractBackground} draws
 * behind everything in absolute coordinates, {@code extractLabels} draws the foreground in
 * coordinates already translated to {@code leftPos/topPos}.
 *
 * <p>TODO(polish, V5): ship a real {@code textures/gui/production.png} background and
 * distinct per-unit icons instead of the primitive-drawn panel and the placeholder vanilla
 * item icons ({@link ProductionKind}'s golden pickaxe/iron sword/bow) used for v1.
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
        List<ProductionKind.OptionView> options = this.menu.getKind().options();
        for (int i = 0; i < options.size(); i++) {
            final int optionIndex = i;
            ProductionKind.OptionView option = options.get(i);
            int by = this.topPos + ProductionMenu.BUTTON_Y + i * ProductionMenu.BUTTON_SPACING;
            Button button = Button.builder(option.label(), b -> onTrain(optionIndex))
                    .bounds(this.leftPos + ProductionMenu.BUTTON_X, by, ProductionMenu.BUTTON_W, ProductionMenu.BUTTON_H)
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
        List<ProductionKind.OptionView> options = this.menu.getKind().options();
        int buildingIndex = this.menu.buildingOptionIndex();
        int total = this.menu.buildTotal();
        int progress = this.menu.buildProgress();

        for (int i = 0; i < options.size(); i++) {
            int bx = ProductionMenu.BUTTON_X;
            int by = ProductionMenu.BUTTON_Y + i * ProductionMenu.BUTTON_SPACING;

            // Unit icon on the left of the button.
            graphics.item(options.get(i).icon(), bx + 3, by + 2);

            // Queued count on the right.
            int queued = this.menu.queuedCount(i);
            if (queued > 0) {
                Component count = Component.literal("x" + queued);
                int tw = this.font.width(count);
                graphics.text(this.font, count, bx + ProductionMenu.BUTTON_W - tw - 4, by + 6, TEXT_COLOR, true);
            }

            // Progress bar along the bottom edge while this option is the one building.
            int barLeft = bx + 1;
            int barRight = bx + ProductionMenu.BUTTON_W - 1;
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
            int cy = ProductionMenu.BUTTON_Y + ProductionMenu.BUTTON_SPACING / 2;
            graphics.centeredText(this.font, warp, this.imageWidth / 2, cy, WARP_COLOR);
        }
    }
}
