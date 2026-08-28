package net.bitflora.asteriskcraft.building;

import net.bitflora.asteriskcraft.AsteriskCraft;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * Generic production-building menu shared by the Nexus and Gateway. Exposes the building's
 * input slots plus the player inventory, dispatches train-button presses to the block entity
 * through the vanilla {@link #clickMenuButton} mechanism, and syncs live production state
 * (build progress, warp-in, per-option queue counts) via {@link ContainerData} data slots.
 */
public class ProductionMenu extends AbstractContainerMenu {
    // ContainerData layout (must match ProductionBuilding.dataAccess()).
    public static final int DATA_BUILDING_INDEX = 0; // option currently building, -1 if idle
    public static final int DATA_BUILD_PROGRESS = 1; // ticks elapsed on the current unit
    public static final int DATA_BUILD_TOTAL = 2;    // ticks a unit takes (for the fraction)
    public static final int DATA_WARP = 3;           // warp-in ticks remaining (0 = ready)
    public static final int DATA_QUEUE_BASE = 4;     // queued count per option
    // The screen reads a queued count for every option, so this has to cover the widest card there
    // is: the Nexus spends its buttons on Wood/Stone pairs, and the Hive - which has no factory
    // building to send unit production to - on one button per unit it can morph, so the Hive's card
    // is the one that grows every time the swarm gains a unit. It is at twelve as of the Spawning
    // Pool and the Spire.
    // ProductionCardLayoutTest fails the build if a card outgrows this: the count is not checked
    // anywhere at runtime, and a card with one option too many read off the end of the data array
    // and crashed the screen rather than degrading.
    public static final int MAX_OPTIONS = 12;
    public static final int DATA_COUNT = DATA_QUEUE_BASE + MAX_OPTIONS;

    // Shared layout so the screen positions buttons/slots to match the menu's slots.
    // Options are arranged column-major (see ProductionKind): each unit gets its own column,
    // holding its buttons (e.g. the Nexus's Wood/Stone pair) stacked vertically within it.
    // Every button is icon-sized, so the whole grid packs tight and the panel stays at the
    // vanilla 176 width — the 9-wide slot grid, not the buttons, is what sets the width.
    public static final int IMAGE_WIDTH = 176;
    public static final int INPUT_COLUMNS = 9;
    public static final int IMAGE_HEIGHT = 230; // input bank's 3 rows + 2 button rows + player inventory
    public static final int SLOT_START_X = (IMAGE_WIDTH - INPUT_COLUMNS * 18) / 2; // 7
    public static final int INPUT_ROW_Y = 20;
    public static final int PLAYER_INV_Y = IMAGE_HEIGHT - 82; // 148
    public static final int HOTBAR_Y = IMAGE_HEIGHT - 24;     // 206
    public static final int BUTTON_Y = 80;
    // The button is sized to just fit its 16x16 icon, plus a px of padding either side and a
    // thin strip along the bottom for the progress bar. Columns/rows are spaced by the button
    // size plus a small gap, so the grid is only as large as the buttons themselves need.
    public static final int BUTTON_W = 20;
    public static final int BUTTON_H = 22;
    public static final int BUTTON_COLUMN_GAP = 4;
    public static final int BUTTON_ROW_GAP = 4;
    public static final int BUTTON_ROW_SPACING = BUTTON_H + BUTTON_ROW_GAP; // 26

    /** Total width of a {@code columns}-wide button grid. */
    public static int buttonGridWidth(int columns) {
        return columns * BUTTON_W + (columns - 1) * BUTTON_COLUMN_GAP;
    }

    /** Left edge of the button grid, centered in the panel so it stays balanced at any column count. */
    public static int buttonStartX(int columns) {
        return (IMAGE_WIDTH - buttonGridWidth(columns)) / 2;
    }

    private final ProductionKind kind;
    @Nullable
    private final ProductionBuilding building; // server-side only
    private final Container input;
    private final ContainerData data;
    private final ContainerLevelAccess access;
    private final int inputSlots;

    /**
     * Opens {@code building}'s card for {@code player}, writing the one thing the client needs to
     * rebuild the menu: which card it is.
     *
     * <p>This exists so that the write and the {@linkplain #ProductionMenu(int, Inventory,
     * RegistryFriendlyByteBuf) read} sit beside each other. Every producing building has to open its
     * menu this way — {@code MenuType} is registered through {@code IMenuTypeExtension.create}, so a
     * plain {@code player.openMenu(provider)} hands the client constructor a <b>null</b> buffer and
     * takes the game down with an NPE on right-click, which no test catches and every building would
     * otherwise have to remember on its own.
     */
    public static void open(Player player, ProductionBuilding building) {
        player.openMenu(building, buf -> buf.writeVarInt(building.kind().ordinal()));
    }

    /** Client constructor: rebuilds an empty mirror container/data from the open-menu buffer. */
    public ProductionMenu(int windowId, Inventory playerInv, RegistryFriendlyByteBuf buf) {
        this(windowId, playerInv, ProductionKind.byId(buf.readVarInt()));
    }

    private ProductionMenu(int windowId, Inventory playerInv, ProductionKind kind) {
        this(windowId, playerInv, kind, null,
                new SimpleContainer(kind.inputSlotCount()),
                new SimpleContainerData(DATA_COUNT),
                ContainerLevelAccess.NULL);
    }

    /** Server constructor: wires the real block-entity container and live data. */
    public ProductionMenu(int windowId, Inventory playerInv, ProductionBuilding building,
                          ContainerData data, ContainerLevelAccess access) {
        this(windowId, playerInv, building.kind(), building, building.inputContainer(), data, access);
    }

    private ProductionMenu(int windowId, Inventory playerInv, ProductionKind kind,
                           @Nullable ProductionBuilding building, Container input,
                           ContainerData data, ContainerLevelAccess access) {
        super(AsteriskCraft.PRODUCTION_MENU.get(), windowId);
        this.kind = kind;
        this.building = building;
        this.input = input;
        this.data = data;
        this.access = access;
        this.inputSlots = kind.inputSlotCount();

        for (int i = 0; i < inputSlots; i++) {
            int col = i % INPUT_COLUMNS;
            int row = i / INPUT_COLUMNS;
            addSlot(new Slot(input, i, SLOT_START_X + col * 18, INPUT_ROW_Y + row * 18));
        }
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(playerInv, col + row * 9 + 9, SLOT_START_X + col * 18, PLAYER_INV_Y + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(playerInv, col, SLOT_START_X + col * 18, HOTBAR_Y));
        }

        addDataSlots(data);
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (this.building == null || id < 0 || id >= this.kind.options().size()) {
            return false;
        }
        this.access.execute((level, pos) -> this.building.trainOption(id, player));
        return true;
    }

    @Override
    public boolean stillValid(Player player) {
        return AbstractContainerMenu.stillValid(this.access, player, this.kind.block());
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        ItemStack result = ItemStack.EMPTY;
        Slot slot = this.slots.get(index);
        if (slot != null && slot.hasItem()) {
            ItemStack stack = slot.getItem();
            result = stack.copy();
            int playerStart = this.inputSlots;
            int playerEnd = this.slots.size();
            if (index < this.inputSlots) {
                // building input -> player inventory
                if (!moveItemStackTo(stack, playerStart, playerEnd, true)) {
                    return ItemStack.EMPTY;
                }
            } else {
                // player inventory -> building input
                if (!moveItemStackTo(stack, 0, this.inputSlots, false)) {
                    return ItemStack.EMPTY;
                }
            }
            if (stack.isEmpty()) {
                slot.set(ItemStack.EMPTY);
            } else {
                slot.setChanged();
            }
        }
        return result;
    }

    public ProductionKind getKind() {
        return this.kind;
    }

    public int buildingOptionIndex() {
        return this.data.get(DATA_BUILDING_INDEX);
    }

    public int buildProgress() {
        return this.data.get(DATA_BUILD_PROGRESS);
    }

    public int buildTotal() {
        // Clamped because the screen divides by this and an idle building legitimately reports 0 —
        // build times are per-unit now, so there is no constant to fall back on. See UnitStat#buildTicks.
        return Math.max(1, this.data.get(DATA_BUILD_TOTAL));
    }

    public int warpTicks() {
        return this.data.get(DATA_WARP);
    }

    public int queuedCount(int option) {
        return this.data.get(DATA_QUEUE_BASE + option);
    }
}
