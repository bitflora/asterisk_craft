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
import net.bitflora.asteriskcraft.stats.CostText;
import net.bitflora.asteriskcraft.stats.Resource;
import net.bitflora.asteriskcraft.stats.UnitCost;
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
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BeaconBeamOwner;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
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
 * {@link RaceProfile#production()} is empty simply never queues; the computer never opens a card at
 * all, since {@code director.AiDirector} drives its production centrally either way.
 *
 * <p>The queue is a mixed one, of roster ids rather than of workers. That is what a race with no
 * separate factory building needs: the Hive morphs its combat units itself, so a Mutalisk and a
 * Drone sit in the same line and each takes its own build time.
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

    /**
     * What the buildings sold at a command card cost. These are {@link UnitCost}s and not plain
     * amounts because a building's price is the same shape a unit's is: several lines that must all
     * be paid ({@link #BARRACKS_COST}), or several interchangeable ones the player picks between
     * ({@link #BUILDING_COST}). A card gives one button per alternative, exactly as it does for a
     * worker, so alternative order is button order here too — Wood first, then Stone.
     */
    public static final UnitCost BUILDING_COST = UnitCost.either(
            List.of(UnitCost.line(Resource.WOOD, 150)),
            List.of(UnitCost.line(Resource.STONE, 150)));
    /** Cobblestone-only cost for a second/expansion base — deliberately pricier and not wood-alternative. */
    public static final UnitCost BASE_KIT_COST = UnitCost.of(Resource.STONE, 400);
    /** All-wood or all-cobblestone, and cheaper than what it unlocks: a Pylon is a prerequisite, not a prize. */
    public static final UnitCost PYLON_COST = UnitCost.either(
            List.of(UnitCost.line(Resource.WOOD, 100)),
            List.of(UnitCost.line(Resource.STONE, 100)));
    /**
     * Cobblestone-only, and the cheapest structure in the mod: a Bunker is worth nothing empty, so
     * what a player really pays for it is the four units they have to put inside.
     */
    public static final UnitCost BUNKER_COST = UnitCost.of(Resource.STONE, 100);
    /**
     * Cobblestone-only, and cheaper than a Bunker on purpose: unlike a Bunker, a Missile Turret is
     * worth its price the moment it is standing, so what a player is buying is the whole answer to
     * the air rather than a shell they still have to crew.
     */
    public static final UnitCost MISSILE_TURRET_COST = UnitCost.of(Resource.STONE, 75);
    /**
     * The Terran expansion base, and the one price that is <em>not</em> {@link #BASE_KIT_COST}:
     * a Command Center is the same 400 as a Nexus or a Hive, but payable in either resource rather
     * than in cobblestone alone, which is the same Wood/Stone choice the race's SCV button offers.
     */
    public static final UnitCost COMMAND_CENTER_COST = UnitCost.either(
            List.of(UnitCost.line(Resource.WOOD, 400)),
            List.of(UnitCost.line(Resource.STONE, 400)));
    /**
     * The Terran infantry building: half a Gateway's price in each resource rather than a choice
     * between them, so it is bought out of a balanced bank rather than out of whichever pile is
     * deepest.
     */
    public static final UnitCost BARRACKS_COST = UnitCost.all(
            UnitCost.line(Resource.WOOD, 75), UnitCost.line(Resource.STONE, 75));
    /**
     * The Protoss air building, and the most expensive structure on any card: it is bought on top of
     * the Gateway rather than instead of it, and the iron is what keeps it out of an opening.
     */
    public static final UnitCost STARGATE_COST = UnitCost.all(
            UnitCost.line(Resource.STONE, 150), UnitCost.line(Resource.WOOD, 150),
            UnitCost.line(Resource.IRON, 10));
    /** The swarm's first tech building — one flat pile of anything, the way every Zerg cost is. */
    public static final UnitCost SPAWNING_POOL_COST = UnitCost.of(Resource.ANY, 200);
    /**
     * The swarm's air building. The one Zerg cost that names a specific resource alongside
     * {@link Resource#ANY} — see {@code UnitCost.bankCosts}, which is what keeps the flat pile from
     * eating the iron before the iron line is paid.
     */
    public static final UnitCost SPIRE_COST = UnitCost.all(
            UnitCost.line(Resource.ANY, 250), UnitCost.line(Resource.IRON, 10));
    /** Pay with whichever cost alternative the army can afford - see {@link #queueUnit}. */
    private static final int ANY_COST = -1;

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
    /** What is in production, oldest first — shared with every other producing building. */
    private final UnitQueue queue;
    /** Whether this base has enrolled in the {@link CoreCensus} since loading. Not saved — the census is. */
    private boolean enrolled = false;

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
                case ProductionMenu.DATA_WARP -> defense.warpTicksRemaining();
                case ProductionMenu.DATA_LOCKED ->
                        TechCensus.lockedOptions(level, buildingFaction(), kind(), profile.roster());
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
        this.queue = new UnitQueue(this.profile::roster);
    }

    /** The race a base block declares, defaulting defensively for a state that isn't one. */
    private static Race raceOf(BlockState state) {
        return state.getBlock() instanceof BaseBlock base ? base.race() : Race.PROTOSS;
    }

    /**
     * The button that produces {@code rosterId}, for the screen's "this is what is building"
     * marker. A worker has two buttons (one per cost alternative) and the first stands for both -
     * they produce the same unit, so there is nothing to tell apart once it is queued.
     */
    private int optionIndexFor(String rosterId) {
        List<ProductionKind.OptionView> options = kind().options();
        for (int i = 0; i < options.size(); i++) {
            if (rosterId.equals(producedBy(options.get(i).action()))) {
                return i;
            }
        }
        return -1;
    }

    /** How many queued units a button has waiting; both of a worker's buttons report the same. */
    private int queuedCountFor(int optionIndex) {
        List<ProductionKind.OptionView> options = kind().options();
        if (optionIndex < 0 || optionIndex >= options.size()) {
            return 0;
        }
        String rosterId = producedBy(options.get(optionIndex).action());
        return rosterId == null ? 0 : this.queue.countOf(rosterId);
    }

    /**
     * Which unit a button produces, or null for one that produces none (a kit). A worker button
     * names no unit on the card - <em>which</em> worker is the base's race's, not the option's - so
     * this is where that indirection is resolved.
     */
    private @Nullable String producedBy(ProductionKind.Action action) {
        return switch (action) {
            case ProductionKind.Action.TrainWorker ignored -> this.profile.worker().id();
            case ProductionKind.Action.TrainUnit(String rosterId) -> rosterId;
            case ProductionKind.Action.GiveKit ignored -> null;
            case ProductionKind.Action.Factory ignored -> null;
        };
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, BaseBlockEntity base) {
        if (!base.enrolled) {
            CoreCensus.ensureRegistered(level, base.buildingFaction(), pos);
            base.enrolled = true;
        }
        // Kept in step from the tick rather than only from setFaction, so a base that resolved its
        // side from the match — or one loaded from a world saved before the property existed —
        // still tells the client who owns it. A no-op once the two agree.
        base.publishFaction(base.buildingFaction());
        // Ticked ahead of the sky/queue gates so shields recharge even while the base is buried.
        if (base.defense.tickWarpIn(level, pos)) {
            base.setChanged();
            return; // still warping in: no production yet
        }
        if (!base.skyGate.update(level, pos, base::cancelQueueOnDormant)) {
            return; // dormant: no clear path to the sky, production is frozen
        }
        if (base.queue.isEmpty()) {
            return;
        }
        String finished = base.queue.tick();
        base.setChanged();
        if (finished != null) {
            base.spawnUnit((ServerLevel) level, pos, finished);
        }
    }

    /** True when the base has a clear path to the sky and can produce; false while buried (dormant). */
    public boolean isAwake() {
        return this.level != null && this.level.canSeeSky(this.worldPosition.above());
    }

    /** Clears any in-progress production when the base goes dormant (loses its sky). */
    private void cancelQueueOnDormant() {
        if (this.queue.clear()) {
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
            case ProductionKind.Action.TrainWorker(int alternative) ->
                    queueUnit(player, this.profile.worker(), alternative);
            case ProductionKind.Action.TrainUnit(String rosterId) ->
                    this.profile.roster().resolve(rosterId).ifPresent(def -> queueUnit(player, def, ANY_COST));
            case ProductionKind.Action.GiveKit(var kit, UnitCost cost, int alternative, String readyKey) ->
                    warpInKit(player, kit.get(), cost, alternative, readyKey);
            case ProductionKind.Action.Factory ignored -> {
                // A factory button on a base's card is a mis-authored table, not a runtime case.
            }
        }
    }

    /**
     * Queues one unit, paying either a named cost alternative (a worker's Wood button or its Stone
     * one) or {@link #ANY_COST} for whichever the army can currently afford - which is what a
     * combat unit's single button wants, and matches how the Gateway pays.
     */
    private void queueUnit(Player player, UnitRoster.UnitDef def, int alternative) {
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
        boolean paid = alternative == ANY_COST
                ? CostPayment.payAny(this, def.cost())
                : CostPayment.pay(this, def.cost(), alternative);
        if (!paid) {
            overlay(player, cannotAfford(def, alternative));
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

    /**
     * Why the purchase failed. A named alternative can say exactly what it wanted; a unit paid for
     * with whichever resource is to hand can only quote the bill, the way the Gateway does.
     */
    private static Component cannotAfford(UnitRoster.UnitDef def, int alternative) {
        if (alternative == ANY_COST) {
            return Component.translatable("message.asteriskcraft.base.cannot_afford_unit",
                    def.type().getDescription(), CostText.costOnly(def.cost(), 0));
        }
        Resource resource = def.cost().alternatives().get(alternative).get(0).resource();
        return Component.translatable("message.asteriskcraft.base.cannot_afford",
                def.cost().amountOf(alternative, resource), resourceName(resource));
    }

    /**
     * Warp-in kits are instant: pay the button's own cost alternative and hand the player the kit
     * item. The building's own build time is spent afterwards, once the kit is placed — a kit's
     * countdown belongs to the structure it stamps, not to the base that sold it.
     */
    private void warpInKit(Player player, Item kit, UnitCost cost, int alternative, String readyKey) {
        if (!CostPayment.pay(this, cost, alternative)) {
            overlay(player, Component.translatable("message.asteriskcraft.base.cannot_afford_building",
                    CostText.costOnly(cost, alternative)));
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

    private void spawnUnit(ServerLevel level, BlockPos pos, String rosterId) {
        this.profile.roster().resolve(rosterId).ifPresent(def -> {
            Mob unit = UnitSpawns.spawn(level, pos, def.type(), buildingFaction(), this.profile.race(), false);
            if (unit instanceof WorkerEntity worker) {
                worker.setHomePos(pos);
            }
        });
    }

    // --- SiegeTarget / FactionCore ---

    @Override
    public Faction buildingFaction() {
        if (this.faction != null) {
            return this.faction;
        }
        // The blockstate carries the same answer and is the half of it the client ever sees, so it
        // is consulted before anything is derived: a client that guessed from MatchSetup would be
        // guessing from its hardcoded default (the match itself is a level attachment and is not
        // synced), which is wrong for every matchup but that default's.
        Faction shown = getBlockState().getValue(BaseBlock.FACTION);
        if (shown != Faction.NEUTRAL) {
            return shown;
        }
        if (this.level == null || this.level.isClientSide()) {
            // Asked before the block entity has a level, or on a client that has not been told:
            // answer, but don't cache it, or the base would be stuck belonging to nobody for the
            // rest of its life.
            return Faction.NEUTRAL;
        }
        this.faction = resolveFaction();
        return this.faction;
    }

    @Override
    public boolean projectsCreep() {
        return this.profile.creep() != null;
    }

    /**
     * Whichever side of this match plays this base's race — the fallback for a base placed by hand
     * rather than warped in by an army. NEUTRAL when nobody plays it, which is the honest answer
     * for a base of a race sitting out the match and keeps it out of both armies; in a mirror,
     * where both sides play it, {@code MatchSetup.sidePlaying} hands it to the human.
     */
    private Faction resolveFaction() {
        return MatchSetup.of(this.level).sidePlaying(this.profile.race());
    }

    @Override
    public void setFaction(Faction faction) {
        this.faction = faction;
        this.setChanged();
        publishFaction(faction);
    }

    /**
     * Mirrors the owning side onto the blockstate, which is how it reaches the client at all — see
     * {@link BaseBlock#FACTION}. Written whenever it differs rather than only at placement, for the
     * same reason {@code PylonBlock.ONLINE} is: a base stamped from a structure template arrives
     * carrying the blockstate captured in the design world, and a base saved before this property
     * existed comes back neutral with its real side still on the block entity.
     */
    private void publishFaction(Faction faction) {
        if (this.level == null) {
            return;
        }
        BlockState state = getBlockState();
        if (state.hasProperty(BaseBlock.FACTION) && state.getValue(BaseBlock.FACTION) != faction) {
            this.level.setBlock(this.worldPosition, state.setValue(BaseBlock.FACTION, faction), Block.UPDATE_ALL);
        }
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
            this.defense.collapseScaffold(this.level, pos);
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
        return ArmyBank.of(this.level, buildingFaction(), this.profile.race());
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
        this.queue.save(output);
        this.defense.save(output);
    }

    /**
     * A queue saved before a base could build anything but its worker: a bare count under
     * {@code Queued}. Read back as that many workers, so a world made then keeps what it had in
     * production instead of losing it.
     */
    private List<String> legacyQueue(ValueInput input) {
        return Collections.nCopies(Math.min(input.getIntOr("Queued", 0), UnitQueue.MAX),
                this.profile.worker().id());
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        Optional<Faction> saved = input.read("Faction", Faction.CODEC);
        this.faction = saved.orElse(null);
        this.queue.load(input, () -> legacyQueue(input));
        this.defense.load(input);
    }
}
