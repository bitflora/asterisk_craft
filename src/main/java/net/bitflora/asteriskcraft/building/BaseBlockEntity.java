package net.bitflora.asteriskcraft.building;

import net.bitflora.asteriskcraft.AsteriskCraft;
import net.bitflora.asteriskcraft.entity.TeamColors;
import net.bitflora.asteriskcraft.entity.WorkerEntity;
import net.bitflora.asteriskcraft.faction.Faction;
import net.bitflora.asteriskcraft.faction.Race;
import net.bitflora.asteriskcraft.game.GameOutcome;
import net.bitflora.asteriskcraft.game.MatchSetup;
import net.bitflora.asteriskcraft.race.RaceProfile;
import net.bitflora.asteriskcraft.race.Races;
import net.bitflora.asteriskcraft.race.UnitRoster;
import net.bitflora.asteriskcraft.stats.CostPayment;
import net.bitflora.asteriskcraft.stats.Resource;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Container;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BeaconBeamOwner;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Optional;

/**
 * A <b>base</b>: the building an army is built around and loses the game without — a Protoss Nexus,
 * a Zerg Hive, whatever a later race calls its own. One class for every race, because which race a
 * base belongs to changes its numbers and its command card, not what it <em>is</em>: it banks the
 * army's resources, produces that race's worker, stands as a {@link FactionCore} the enemy has to
 * raze, and lights a beacon beam in its owner's colour.
 *
 * <p>That merge is what makes the human and the computer interchangeable. The old split had the
 * production queue and GUI on the Nexus and nothing at all on the Hive, so "the player plays Zerg"
 * would have meant reimplementing production on the Hive and "the computer plays Protoss" would
 * have meant a Nexus the director could drive. Here both are the same building, and whether it
 * shows a command card is a question about <em>who is holding it</em> — {@link BaseBlock} asks
 * {@code ControlledFaction} — over a race that has one to show. A race whose
 * {@link RaceProfile#production()} is empty simply never queues, which is exactly the Hive's
 * long-standing behaviour, driven centrally by {@code director.AiDirector} instead.
 *
 * <p>Everything race-varying is read from the {@link RaceProfile} its block declares: staying power
 * ({@link BuildingDefense}), bank size, which worker it trains, and its command card. Nothing here
 * names a Nexus or a Hive.
 *
 * <p>Acts as a "linked chest" onto its race's {@link ArmyBank}: every base and factory of one army
 * reads and writes the same underlying data, so they all draw from one pool. See
 * {@link ArmyLinkedContainer}.
 */
public class BaseBlockEntity extends BlockEntity
        implements ArmyLinkedContainer, ProductionBuilding, FactionCore, BeaconBeamOwner, WarpInBuilding {

    /** All-wood or all-cobblestone (player's choice) to warp out a building kit. */
    public static final int BUILDING_COST = 150;
    /** Cobblestone-only cost for a second/expansion base — deliberately pricier and not wood-alternative. */
    public static final int BASE_KIT_COST = 400;
    /** All-wood or all-cobblestone, and cheaper than what it unlocks: a Pylon is a prerequisite, not a prize. */
    public static final int PYLON_COST = 100;
    public static final int MAX_QUEUE = 5;

    private final RaceProfile profile;
    private final BuildingDefense defense;
    private final UnderAttackAlert alert = new UnderAttackAlert();
    /** Tracks whether the base can see the sky (dormant when buried); cancels the queue on going dark. */
    private final SkyGate skyGate = new SkyGate();

    /**
     * Which side owns this base. Null until it is set at placement or resolved from the match on
     * first use — a base placed by hand (creative, {@code /setblock}) belongs to whichever side is
     * playing its race, which is the only answer that stays right once the human can pick one.
     */
    private @Nullable Faction faction;
    private int queued = 0;
    private int buildTicksRemaining;
    /** Whether this base has enrolled in the {@link CoreCensus} since loading. Not saved — the census is. */
    private boolean enrolled = false;

    private final ContainerData dataAccess = new ContainerData() {
        @Override
        public int get(int index) {
            return switch (index) {
                case ProductionMenu.DATA_BUILDING_INDEX -> queued > 0 ? 0 : -1;
                case ProductionMenu.DATA_BUILD_PROGRESS -> queued > 0 ? buildTicks() - buildTicksRemaining : 0;
                case ProductionMenu.DATA_BUILD_TOTAL -> buildTicks();
                case ProductionMenu.DATA_WARP -> defense.warpTicksRemaining();
                case ProductionMenu.DATA_QUEUE_BASE, ProductionMenu.DATA_QUEUE_BASE + 1 -> queued;
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

    public BaseBlockEntity(BlockPos pos, BlockState state) {
        super(AsteriskCraft.BASE_BLOCK_ENTITY.get(), pos, state);
        this.profile = Races.of(raceOf(state));
        this.defense = new BuildingDefense(
                this.profile.baseHealth(), this.profile.baseShield(), this.profile.baseWarpTicks());
        this.buildTicksRemaining = buildTicks();
    }

    /** The race a base block declares, defaulting defensively for a state that isn't one. */
    private static Race raceOf(BlockState state) {
        return state.getBlock() instanceof BaseBlock base ? base.race() : Race.PROTOSS;
    }

    /** A base only ever builds its race's worker, so its one build time comes off the balance table. */
    private int buildTicks() {
        return this.profile.worker().buildTicks();
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, BaseBlockEntity base) {
        if (!base.enrolled) {
            CoreCensus.ensureRegistered(level, base.buildingFaction(), pos);
            base.enrolled = true;
        }
        // Ticked ahead of the sky/queue gates so shields recharge even while the base is buried.
        if (base.defense.tickWarpIn(level, pos)) {
            base.setChanged();
            return; // still warping in: no production yet
        }
        if (!base.skyGate.update(level, pos, base::cancelQueueOnDormant)) {
            return; // dormant: no clear path to the sky, production is frozen
        }
        if (base.queued <= 0) {
            return;
        }
        if (--base.buildTicksRemaining > 0) {
            base.setChanged();
            return;
        }
        base.queued--;
        base.buildTicksRemaining = base.buildTicks();
        base.setChanged();
        base.spawnWorker((ServerLevel) level, pos);
    }

    /** True when the base has a clear path to the sky and can produce; false while buried (dormant). */
    public boolean isAwake() {
        return this.level != null && this.level.canSeeSky(this.worldPosition.above());
    }

    /** Clears any in-progress production when the base goes dormant (loses its sky). */
    private void cancelQueueOnDormant() {
        if (this.queued > 0) {
            this.queued = 0;
            this.buildTicksRemaining = buildTicks();
            this.setChanged();
        }
    }

    // --- BeaconBeamOwner (client-side beam locator) ---

    @Override
    public List<BeaconBeamOwner.Section> getBeamSections() {
        int color = TeamColors.factionColor(buildingFaction());
        if (color < 0 || this.level == null || !this.level.canSeeSky(this.worldPosition.above())) {
            return List.of();
        }
        return List.of(new BeaconBeamOwner.Section(color));
    }

    // --- ProductionBuilding ---

    /** This race's command card. Only ever asked for once {@link BaseBlock} has confirmed there is one. */
    @Override
    public ProductionKind kind() {
        return this.profile.production().orElseThrow(
                () -> new IllegalStateException(this.profile.race() + " has no base command card"));
    }

    /** Whether this base has a command card to open at all. */
    public boolean hasProduction() {
        return this.profile.production().isPresent();
    }

    @Override
    public Container inputContainer() {
        return this;
    }

    @Override
    public ContainerData dataAccess() {
        return this.dataAccess;
    }

    /**
     * Runs the pressed button's {@link ProductionKind.Action}. Generic over the card: what the
     * button does is data on the race's own card, so a second race's base needs a new entry there
     * and no code here.
     */
    @Override
    public void trainOption(int optionIndex, Player player) {
        if (this.defense.isWarping()) {
            overlay(player, Component.translatable("message.asteriskcraft.base.warping", displayName()));
            return;
        }
        if (this.skyGate.lit() == Boolean.FALSE) {
            overlay(player, Component.translatable("message.asteriskcraft.base.dormant", displayName()));
            return;
        }
        List<ProductionKind.OptionView> options = kind().options();
        if (optionIndex < 0 || optionIndex >= options.size()) {
            return;
        }
        switch (options.get(optionIndex).action()) {
            case ProductionKind.Action.TrainWorker(int alternative) -> trainWorker(player, alternative);
            case ProductionKind.Action.GiveKit(var kit, Resource resource, int cost, String readyKey) ->
                    warpInKit(player, kit.get(), resource, cost, readyKey);
            case ProductionKind.Action.Factory ignored -> {
                // A factory button on a base's card is a mis-authored table, not a runtime case.
            }
        }
    }

    private void trainWorker(Player player, int alternative) {
        if (this.queued >= MAX_QUEUE) {
            overlay(player, Component.translatable("message.asteriskcraft.base.queue_full"));
            return;
        }
        UnitRoster.UnitDef worker = this.profile.worker();
        if (!CostPayment.pay(this, worker.cost(), alternative)) {
            Resource resource = worker.cost().alternatives().get(alternative).get(0).resource();
            int amount = worker.cost().amountOf(alternative, resource);
            overlay(player, Component.translatable("message.asteriskcraft.base.cannot_afford",
                    amount, resourceName(resource)));
            return;
        }
        this.queued++;
        this.setChanged();
        if (this.level != null) {
            this.level.playSound(null, this.worldPosition, SoundEvents.BEACON_ACTIVATE, SoundSource.BLOCKS, 0.6f, 1.4f);
        }
        overlay(player, Component.translatable("message.asteriskcraft.base.queued",
                worker.type().getDescription(), this.queued));
    }

    /** Warp-in kits are instant: pay the chosen resource's share and hand the player the kit item. */
    private void warpInKit(Player player, Item kit, Resource resource, int cost, String readyKey) {
        if (!ResourceBank.extract(this, resource.matches(), cost)) {
            overlay(player, Component.translatable("message.asteriskcraft.base.cannot_afford_building",
                    cost, resourceName(resource)));
            return;
        }
        giveOrDrop(player, new ItemStack(kit));
        if (this.level != null) {
            this.level.playSound(null, this.worldPosition, SoundEvents.BEACON_ACTIVATE, SoundSource.BLOCKS, 0.6f, 1.0f);
        }
        overlay(player, Component.translatable(readyKey));
    }

    private static void giveOrDrop(Player player, ItemStack stack) {
        if (!player.getInventory().add(stack)) {
            player.drop(stack, false);
        }
    }

    private static void overlay(Player player, Component message) {
        if (player instanceof ServerPlayer serverPlayer) {
            serverPlayer.sendSystemMessage(message, true);
        }
    }

    /** Translatable player-facing name for a resource, keyed by its serialized name (see {@link Resource}). */
    private static Component resourceName(Resource resource) {
        return Component.translatable("gui.asteriskcraft.resource." + resource.getSerializedName());
    }

    private void spawnWorker(ServerLevel level, BlockPos pos) {
        Mob unit = UnitSpawns.spawn(level, pos, this.profile.worker().type(), buildingFaction(), false);
        if (unit instanceof WorkerEntity worker) {
            worker.setHomePos(pos);
        }
    }

    // --- SiegeTarget / FactionCore ---

    @Override
    public Faction buildingFaction() {
        if (this.faction == null) {
            this.faction = resolveFaction();
        }
        return this.faction;
    }

    /**
     * Whichever side of this match plays this base's race. NEUTRAL when nobody does, which is the
     * honest answer for a base of a race sitting out the match and keeps it out of both armies.
     */
    private Faction resolveFaction() {
        if (this.level == null) {
            return Faction.NEUTRAL;
        }
        MatchSetup setup = MatchSetup.of(this.level);
        if (setup.playerProfile().race() == this.profile.race()) {
            return setup.playerFaction();
        }
        if (setup.aiProfile().race() == this.profile.race()) {
            return setup.aiFaction();
        }
        return Faction.NEUTRAL;
    }

    @Override
    public void setFaction(Faction faction) {
        this.faction = faction;
        this.setChanged();
    }

    @Override
    public BuildingDefense defense() {
        return this.defense;
    }

    @Override
    public void damageBuilding(int amount, ServerLevel level, BlockPos pos) {
        this.defense.damage(amount, level, pos);
        this.setChanged();
        this.alert.ping(level, buildingFaction(), "message.asteriskcraft.under_attack");
    }

    /**
     * Stands the base up fully warped in — for a starting base, which the world begins with rather
     * than warping in from a kit (see {@code game.GameBootstrap}).
     */
    public void skipWarpIn() {
        this.defense.skipWarpIn();
        this.setChanged();
    }

    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        // Deliberately skip super: vanilla would drop+clear this Container's contents, but that
        // Container is the shared army bank (ArmyLinkedContainer) — one base breaking must not
        // dump/clear resources the army's other buildings still depend on. CoreSpoils knocks a
        // measured share of that pool loose instead.
        if (this.level != null) {
            this.defense.collapseScaffold(this.level);
        }
        CoreSpoils.spill(this.level, buildingFaction(), pos, this);
        GameOutcome.onCoreDestroyed(this.level, pos);
    }

    // --- MenuProvider ---

    @Override
    public Component getDisplayName() {
        return displayName();
    }

    /** The base's own block name — "Nexus", "Hive" — so generic messages still read in race voice. */
    private Component displayName() {
        return this.profile.coreBlock().get().getName();
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
        return ArmyBank.of(this.level, buildingFaction());
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
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        if (this.faction != null) {
            output.store("Faction", Faction.CODEC, this.faction);
        }
        output.putInt("Queued", this.queued);
        output.putInt("BuildTicks", this.buildTicksRemaining);
        this.defense.save(output);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        Optional<Faction> saved = input.read("Faction", Faction.CODEC);
        this.faction = saved.orElse(null);
        this.queued = input.getIntOr("Queued", 0);
        this.buildTicksRemaining = input.getIntOr("BuildTicks", buildTicks());
        this.defense.load(input);
    }
}
