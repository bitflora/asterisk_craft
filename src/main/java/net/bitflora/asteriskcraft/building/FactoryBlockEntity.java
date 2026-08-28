package net.bitflora.asteriskcraft.building;

import net.bitflora.asteriskcraft.AsteriskCraft;
import net.bitflora.asteriskcraft.race.RaceProfile;
import net.bitflora.asteriskcraft.race.Races;
import net.bitflora.asteriskcraft.race.UnitRoster;
import net.bitflora.asteriskcraft.stats.CostPayment;
import net.bitflora.asteriskcraft.stats.CostText;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * A <b>unit factory</b>: a {@link StructureBlockEntity} that also runs a production queue. The
 * Barracks is the only one today, and it is race-generic — everything it produces it looks up in its
 * own race's {@link UnitRoster} by the id its card names, so a second race's factory is a
 * {@link ProductionKind} entry and a registration, the way a second race's base always was.
 *
 * <p>It is deliberately <em>not</em> a merge with {@link GatewayBlockEntity}, which predates the
 * roster and dispatches its buttons positionally through an enum of Protoss units. Folding that in
 * is a separate job; nothing here is written twice for it.
 *
 * <p>Like every producing building it is a "linked chest" onto its army's {@link ArmyBank}
 * ({@link ArmyLinkedContainer}), so a Barracks spends the same pool the Command Center that sold its
 * kit does — a player loads resources once, anywhere.
 */
public class FactoryBlockEntity extends StructureBlockEntity
        implements ArmyLinkedContainer, ProductionBuilding {

    private final UnitQueue queue = new UnitQueue(() -> profile().roster());

    private final ContainerData dataAccess = new ContainerData() {
        @Override
        public int get(int index) {
            if (index >= ProductionMenu.DATA_QUEUE_BASE) {
                return queuedCountFor(index - ProductionMenu.DATA_QUEUE_BASE);
            }
            String head = queue.head();
            return switch (index) {
                case ProductionMenu.DATA_BUILDING_INDEX -> head == null ? -1 : optionIndexFor(head);
                case ProductionMenu.DATA_BUILD_PROGRESS ->
                        head == null ? 0 : queue.buildTicksOf(head) - queue.buildTicksRemaining();
                case ProductionMenu.DATA_BUILD_TOTAL -> head == null ? 0 : queue.buildTicksOf(head);
                case ProductionMenu.DATA_WARP -> defense().warpTicksRemaining();
                case ProductionMenu.DATA_LOCKED ->
                        TechCensus.lockedOptions(level, buildingFaction(), kind(), profile().roster());
                default -> 0;
            };
        }

        @Override
        public void set(int index, int value) {
            // Server state is authoritative; the client mirror uses a SimpleContainerData.
        }

        @Override
        public int getCount() {
            return ProductionMenu.DATA_COUNT;
        }
    };

    public FactoryBlockEntity(BlockPos pos, BlockState state) {
        super(AsteriskCraft.FACTORY_BLOCK_ENTITY.get(), pos, state);
    }

    private RaceProfile profile() {
        return Races.of(race());
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, FactoryBlockEntity factory) {
        // Ticked before the queue so shields recharge whether or not anything is in production.
        if (factory.defense().tickWarpIn(level, pos)) {
            factory.setChanged();
            return; // still going up: no production yet
        }
        if (factory.queue.isEmpty()) {
            return;
        }
        String finished = factory.queue.tick();
        factory.setChanged();
        if (finished != null) {
            factory.profile().roster().resolve(finished).ifPresent(def -> UnitSpawns.spawn(
                    (ServerLevel) level, pos, def.type(), factory.buildingFaction(), factory.race(), false));
        }
    }

    // --- ProductionBuilding ---

    /** This factory's command card, off the block it was placed as. */
    @Override
    public ProductionKind kind() {
        if (getBlockState().getBlock() instanceof FactoryBlock factory) {
            return factory.production();
        }
        throw new IllegalStateException("a factory block entity on " + getBlockState().getBlock()
                + ", which declares no command card");
    }

    @Override
    public Container inputContainer() {
        return this;
    }

    @Override
    public ContainerData dataAccess() {
        return this.dataAccess;
    }

    @Override
    public void trainOption(int optionIndex, Player player) {
        if (defense().isWarping()) {
            overlay(player, Component.translatable("message.asteriskcraft.base.warping", getDisplayName()));
            return;
        }
        List<ProductionKind.OptionView> options = kind().options();
        if (optionIndex < 0 || optionIndex >= options.size()) {
            return;
        }
        // A factory's card only ever trains units of its race's roster: a worker comes from a base
        // and a kit is sold at one, so the other actions are a mis-authored table rather than a
        // runtime case. FactoryCardTest is what says so before the game does.
        if (options.get(optionIndex).action() instanceof ProductionKind.Action.TrainUnit(String rosterId)) {
            profile().roster().resolve(rosterId).ifPresent(def -> queueUnit(player, def));
        }
    }

    private void queueUnit(Player player, UnitRoster.UnitDef def) {
        if (this.queue.isFull()) {
            overlay(player, Component.translatable("message.asteriskcraft.base.queue_full"));
            return;
        }
        // Above the payment, deliberately: a refused unit must not have been charged for.
        Block missing = this.level instanceof ServerLevel serverLevel
                ? TechCensus.missing(serverLevel, buildingFaction(), def)
                : null;
        if (missing != null) {
            overlay(player, Component.translatable("message.asteriskcraft.base.needs_building",
                    def.type().getDescription(), missing.getName()));
            return;
        }
        if (!CostPayment.payAny(this, def.cost())) {
            overlay(player, Component.translatable("message.asteriskcraft.base.cannot_afford_unit",
                    def.type().getDescription(), CostText.costOnly(def.cost(), 0)));
            return;
        }
        this.queue.add(def.id());
        this.setChanged();
        if (this.level != null) {
            this.level.playSound(null, this.worldPosition, SoundEvents.BEACON_ACTIVATE, SoundSource.BLOCKS, 0.6f, 1.4f);
        }
        overlay(player, Component.translatable("message.asteriskcraft.base.queued",
                def.type().getDescription(), this.queue.size()));
    }

    /** The button that produces {@code rosterId}, for the screen's "this is what is building" marker. */
    private int optionIndexFor(String rosterId) {
        List<ProductionKind.OptionView> options = kind().options();
        for (int i = 0; i < options.size(); i++) {
            if (options.get(i).action() instanceof ProductionKind.Action.TrainUnit(String id)
                    && rosterId.equals(id)) {
                return i;
            }
        }
        return -1;
    }

    private int queuedCountFor(int optionIndex) {
        List<ProductionKind.OptionView> options = kind().options();
        if (optionIndex < 0 || optionIndex >= options.size()) {
            return 0;
        }
        return options.get(optionIndex).action() instanceof ProductionKind.Action.TrainUnit(String rosterId)
                ? this.queue.countOf(rosterId)
                : 0;
    }

    private static void overlay(Player player, Component message) {
        if (player instanceof ServerPlayer serverPlayer) {
            serverPlayer.sendSystemMessage(message, true);
        }
    }

    // --- MenuProvider ---

    @Override
    public Component getDisplayName() {
        return getBlockState().getBlock().getName();
    }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory playerInventory, Player player) {
        return new ProductionMenu(containerId, playerInventory, this, this.dataAccess,
                ContainerLevelAccess.create(this.level, this.worldPosition));
    }

    // --- ArmyLinkedContainer ---

    @Override
    public NonNullList<ItemStack> armyItems() {
        return ArmyBank.of(this.level, buildingFaction(), race());
    }

    @Override
    public void markArmyBankChanged() {
        this.setChanged();
    }

    @Override
    public boolean stillValid(Player player) {
        return Container.stillValidBlockEntity(this, player);
    }

    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        // Deliberately skip StructureBlockEntity's super chain into BlockEntity's own drop logic for
        // the reason every producing building does: this Container is the shared army bank, and one
        // Barracks falling must not dump or clear what the rest of the army is still spending.
        if (this.level != null) {
            defense().collapseScaffold(this.level, pos);
        }
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        this.queue.save(output);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        this.queue.load(input, List::of);
    }
}
