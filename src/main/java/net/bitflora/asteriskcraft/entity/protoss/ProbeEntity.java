package net.bitflora.asteriskcraft.entity.protoss;

import net.bitflora.asteriskcraft.AsteriskCraft;
import net.bitflora.asteriskcraft.building.DepletedNodeBlockEntity;
import net.bitflora.asteriskcraft.command.CommandAttachments;
import net.bitflora.asteriskcraft.command.CommandOrder;
import net.bitflora.asteriskcraft.entity.Shielded;
import net.bitflora.asteriskcraft.entity.ai.CommandableGoals;
import net.bitflora.asteriskcraft.entity.ai.CommandedMoveGoal;
import net.bitflora.asteriskcraft.faction.Faction;
import net.bitflora.asteriskcraft.faction.FactionAttachments;
import net.bitflora.asteriskcraft.game.GameAttachments;
import net.bitflora.asteriskcraft.stats.UnitAttributes;
import net.bitflora.asteriskcraft.stats.UnitStat;
import net.bitflora.asteriskcraft.stats.UnitStats;
import com.mojang.serialization.Codec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.jetbrains.annotations.Nullable;

/**
 * The Protoss worker. Finds a harvestable block near its home Nexus, mines it non-destructively
 * (the block is swapped for a regenerating depleted node), then delivers the yield straight into
 * the Nexus. It works one resource and one only: the type it was assigned — by an explicit MINE
 * order, or by whatever it first picked on its own — is the only kind it will mine, and once every
 * node of that type is depleted it waits at one for the regen instead of taking up another.
 *
 * <p>Its combat stats (health, speed, shield) live in
 * {@link net.bitflora.asteriskcraft.stats.UnitStats#PROBE} — not here. The harvest-loop constants
 * below are pure mining logic, not balance numbers, and stay put.
 */
public class ProbeEntity extends PathfinderMob implements Shielded {
    private static final UnitStat STAT = UnitStats.PROBE;

    public static final TagKey<Block> HARVESTABLE = BlockTags.create(AsteriskCraft.id("harvestable"));
    public static final int YIELD_PER_TRIP = 3;
    public static final int MINE_TICKS = 60;
    public static final int SEARCH_RADIUS = 24;
    public static final int SEARCH_VERTICAL = 8;

    /** Coarse resource category; a worker is assigned exactly one of these and mines nothing else. */
    enum ResourceType implements StringRepresentable {
        WOOD("wood"), IRON("iron"), STONE("stone"), COAL("coal"), COPPER("copper"),
        GOLD("gold"), REDSTONE("redstone"), LAPIS("lapis"), DIAMOND("diamond"), EMERALD("emerald");

        static final Codec<ResourceType> CODEC = StringRepresentable.fromEnum(ResourceType::values);
        private final String name;

        ResourceType(String name) {
            this.name = name;
        }

        static ResourceType of(BlockState state) {
            if (state.is(BlockTags.LOGS)) {
                return WOOD;
            }
            if (state.is(BlockTags.IRON_ORES)) {
                return IRON;
            }
            if (state.is(BlockTags.COAL_ORES)) {
                return COAL;
            }
            if (state.is(BlockTags.COPPER_ORES)) {
                return COPPER;
            }
            if (state.is(BlockTags.GOLD_ORES)) {
                return GOLD;
            }
            if (state.is(BlockTags.REDSTONE_ORES)) {
                return REDSTONE;
            }
            if (state.is(BlockTags.LAPIS_ORES)) {
                return LAPIS;
            }
            if (state.is(BlockTags.DIAMOND_ORES)) {
                return DIAMOND;
            }
            if (state.is(BlockTags.EMERALD_ORES)) {
                return EMERALD;
            }
            return STONE;
        }

        @Override
        public String getSerializedName() {
            return this.name;
        }
    }

    private BlockPos homePos = BlockPos.ZERO;
    private ItemStack carried = ItemStack.EMPTY;
    /**
     * The one resource this worker mines. Set by an explicit MINE order and, for a worker never told
     * otherwise, by whatever it first picked on its own. It is a filter, not a preference: with
     * nothing of it standing the worker waits for a node to regenerate rather than switching to a
     * resource it was not put on. Dropped only when that resource has left the base entirely (see
     * {@link HarvestGoal#findNearestHarvestable()}), so an assignment can never strand a worker.
     */
    @Nullable
    private ResourceType assignedType;
    /** Where this probe last mined; the search ranks candidates by proximity to it to keep the probe on one vein. */
    @Nullable
    private BlockPos lastMinedPos;
    /**
     * The depleted node {@link AwaitRegenGoal} stands over while the assigned resource regrows, or
     * {@code null} whenever the worker has something to mine. Written by {@link HarvestGoal}'s
     * search, which already sweeps the whole home box, so waiting costs no second scan of the world.
     * Deliberately not saved: the next search re-derives it within a couple of seconds of a reload.
     */
    @Nullable
    private BlockPos pendingNodePos;

    public ProbeEntity(EntityType<? extends ProbeEntity> type, Level level) {
        super(type, level);
        this.setPersistenceRequired();
    }

    public static AttributeSupplier.Builder createAttributes() {
        return UnitAttributes.apply(PathfinderMob.createMobAttributes(), UnitStats.PROBE);
    }

    @Override
    protected void registerGoals() {
        CommandableGoals.configureNavigation(this);
        this.goalSelector.addGoal(0, new FloatGoal(this));
        // A move order interrupts the economy (RTS move); a carried load is still delivered
        // before a fresh mine because DeliverGoal outranks HarvestGoal.
        this.goalSelector.addGoal(1, new CommandedMoveGoal(this, 1.1));
        this.goalSelector.addGoal(2, new DeliverGoal(this));
        this.goalSelector.addGoal(3, new HarvestGoal(this));
        // Below HarvestGoal, so the moment a node of the assigned resource regrows the worker is
        // pulled straight back to work; above the stroll, so waiting holds it at its own vein
        // rather than letting it wander off looking idle.
        this.goalSelector.addGoal(4, new AwaitRegenGoal(this));
        this.goalSelector.addGoal(6, new WaterAvoidingRandomStrollGoal(this, 0.8));
        this.goalSelector.addGoal(7, new LookAtPlayerGoal(this, Player.class, 6.0f));
        this.goalSelector.addGoal(8, new RandomLookAroundGoal(this));
    }

    @Override
    public int getAmbientSoundInterval() {
        return 1200; // ~10x the vanilla default (80): ambient barks average ~1/minute, not ~1/6s.
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return AsteriskCraft.PROBE_AMBIENT.get();
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return AsteriskCraft.PROBE_HURT.get();
    }

    @Override
    protected SoundEvent getDeathSound() {
        return AsteriskCraft.PROBE_DEATH.get();
    }

    public int getShield() {
        return STAT.shield();
    }

    /** Whether this worker will mine the given block. Overridable so a subtype can narrow the shared tag. */
    protected boolean canHarvest(BlockState state) {
        return state.is(HARVESTABLE);
    }

    public void setHomePos(BlockPos pos) {
        this.homePos = pos.immutable();
    }

    public BlockPos getHomePos() {
        return this.homePos;
    }

    public boolean isCarrying() {
        return !this.carried.isEmpty();
    }

    /**
     * Where this worker unloads its carried yield: home is always set to the owning Nexus/Hive
     * core, which is exactly the "nearest core building" a worker should deliver into. Returns
     * {@code null} if home hasn't been set yet.
     */
    @Nullable
    protected BlockPos findDeliveryTarget() {
        return this.homePos.equals(BlockPos.ZERO) ? null : this.homePos;
    }

    /**
     * Re-homes this worker onto the nearest surviving friendly core when its recorded home has
     * been razed, so a Drone whose Hive is destroyed keeps delivering into a sibling Hive (all
     * of a faction's cores share one {@code ArmyBank}) instead of carrying its load forever.
     * Faction-generic: candidate cores come from the level's saved game state (Hives for Zerg,
     * the Nexus for Protoss), and we keep only those that still expose an item capability.
     * Returns {@code null} if no friendly core survives — the worker then keeps its load.
     */
    @Nullable
    public BlockPos rehomeToNearestCore() {
        if (!(this.level() instanceof ServerLevel level)) {
            return null;
        }
        BlockPos best = null;
        double bestDistSqr = Double.MAX_VALUE;
        for (BlockPos pos : coreCandidates(level, FactionAttachments.get(this))) {
            if (level.getCapability(Capabilities.Item.BLOCK, pos, null) == null) {
                continue;
            }
            double distSqr = pos.distSqr(this.blockPosition());
            if (distSqr < bestDistSqr) {
                bestDistSqr = distSqr;
                best = pos;
            }
        }
        return best;
    }

    private static List<BlockPos> coreCandidates(ServerLevel level, Faction faction) {
        return switch (faction) {
            case ZERG -> level.getData(GameAttachments.HIVE_POSITIONS);
            case PROTOSS -> {
                BlockPos nexus = level.getData(GameAttachments.NEXUS_POS);
                yield nexus.equals(BlockPos.ZERO) ? List.of() : List.of(nexus);
            }
            case NEUTRAL -> List.of();
        };
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output);
        output.store("HomePos", BlockPos.CODEC, this.homePos);
        if (!this.carried.isEmpty()) {
            output.store("Carried", ItemStack.CODEC, this.carried);
        }
        if (this.assignedType != null) {
            output.store("AssignedResource", ResourceType.CODEC, this.assignedType);
        }
        if (this.lastMinedPos != null) {
            output.store("LastMinedPos", BlockPos.CODEC, this.lastMinedPos);
        }
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
        this.homePos = input.read("HomePos", BlockPos.CODEC).orElse(BlockPos.ZERO);
        this.carried = input.read("Carried", ItemStack.CODEC).orElse(ItemStack.EMPTY);
        this.assignedType = input.read("AssignedResource", ResourceType.CODEC).orElse(null);
        this.lastMinedPos = input.read("LastMinedPos", BlockPos.CODEC).orElse(null);
    }

    /**
     * The assignment rule: a worker that has never been put on a resource mines anything, an assigned
     * one only its own kind. The single place the "only mine what you were assigned" call is made.
     */
    static boolean mines(@Nullable ResourceType assigned, ResourceType candidate) {
        return assigned == null || assigned == candidate;
    }

    /**
     * Whether {@code pos} holds a depleted node that will regrow into {@code type} — i.e. a node a
     * worker assigned to that resource is worth waiting at. The blockstate is passed in and tested
     * first because the caller sweeps tens of thousands of positions, and a block-entity lookup at
     * each of them costs far more than this decision is worth.
     */
    static boolean regeneratesInto(Level level, BlockPos pos, BlockState state, ResourceType type) {
        return state.is(AsteriskCraft.DEPLETED_NODE.get())
                && level.getBlockEntity(pos) instanceof DepletedNodeBlockEntity node
                && ResourceType.of(node.getOriginalState()) == type;
    }

    /** What a harvested block yields: a flat {@link #YIELD_PER_TRIP} of the matching item. */
    static ItemStack yieldFor(BlockState state) {
        ResourceType type = ResourceType.of(state);
        return switch (type) {
            case WOOD -> new ItemStack(state.getBlock().asItem(), YIELD_PER_TRIP);
            case IRON -> new ItemStack(Items.IRON_INGOT, YIELD_PER_TRIP);
            case STONE -> new ItemStack(Items.COBBLESTONE, YIELD_PER_TRIP);
            case COAL -> new ItemStack(Items.COAL, YIELD_PER_TRIP);
            case COPPER -> new ItemStack(Items.COPPER_INGOT, YIELD_PER_TRIP);
            case GOLD -> new ItemStack(Items.GOLD_INGOT, YIELD_PER_TRIP);
            case REDSTONE -> new ItemStack(Items.REDSTONE, YIELD_PER_TRIP);
            case LAPIS -> new ItemStack(Items.LAPIS_LAZULI, YIELD_PER_TRIP);
            case DIAMOND -> new ItemStack(Items.DIAMOND, YIELD_PER_TRIP);
            case EMERALD -> new ItemStack(Items.EMERALD, YIELD_PER_TRIP);
        };
    }

    /**
     * Walks to a harvestable block near home, mines it for a few seconds, swaps it
     * for a depleted node, and loads the yield onto the probe.
     */
    static class HarvestGoal extends Goal {
        private static final double MINE_RANGE_SQR = 6.25;   // must be within 2.5 blocks to mine
        private static final int REACH_ACCURACY = 2;         // a valid path must end within 2 blocks of the node
        private static final int NO_PROGRESS_LIMIT = 60;     // ~3s of not getting closer → abandon this node
        private static final int UNREACHABLE_COOLDOWN = 200; // ~10s a rejected node is skipped by the search
        private static final int MAX_PATH_CHECKS = 8;        // cap A* validations per search so it stays cheap

        private final ProbeEntity probe;
        /** Nodes we recently failed to reach, mapped to the game time their cooldown expires. */
        private final Map<BlockPos, Long> unreachable = new HashMap<>();
        @Nullable
        private BlockPos target;
        private boolean commandedTarget;
        private int mineTicks;
        private int searchCooldown;
        private double lastDistSqr;
        private int noProgressTicks;

        HarvestGoal(ProbeEntity probe) {
            this.probe = probe;
            this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            if (this.probe.isCarrying()) {
                return false;
            }
            // A commanded MINE order targets a specific block, bypassing the home-radius search.
            CommandOrder order = CommandAttachments.getOrder(this.probe);
            if (order.kind() == CommandOrder.Kind.MINE && order.pos().isPresent()) {
                BlockPos commanded = order.pos().get();
                BlockState commandedState = this.probe.level().getBlockState(commanded);
                if (this.probe.canHarvest(commandedState)) {
                    // The order is the assignment: from here the worker mines this resource and no
                    // other, including after this particular node is spent.
                    this.probe.assignedType = ResourceType.of(commandedState);
                    this.target = commanded.immutable();
                    this.commandedTarget = true;
                    return true;
                }
                CommandAttachments.clearOrder(this.probe); // commanded block no longer harvestable
            }
            if (this.probe.homePos.equals(BlockPos.ZERO)) {
                return false;
            }
            if (--this.searchCooldown > 0) {
                return false;
            }
            this.searchCooldown = 40;
            this.target = findNearestHarvestable();
            this.commandedTarget = false;
            return this.target != null;
        }

        @Override
        public boolean canContinueToUse() {
            return this.target != null
                    && !this.probe.isCarrying()
                    && this.probe.canHarvest(this.probe.level().getBlockState(this.target));
        }

        @Override
        public void start() {
            this.mineTicks = 0;
            this.lastDistSqr = Double.MAX_VALUE;
            this.noProgressTicks = 0;
        }

        @Override
        public void stop() {
            this.target = null;
            this.probe.getNavigation().stop();
        }

        @Override
        public boolean requiresUpdateEveryTick() {
            return true;
        }

        @Override
        public void tick() {
            if (this.target == null) {
                return;
            }
            BlockPos pos = this.target;
            this.probe.getLookControl().setLookAt(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
            double dist = pos.distToCenterSqr(this.probe.position());
            if (dist > MINE_RANGE_SQR) {
                // Still travelling. Abandon this node if we stop making headway toward it, so a probe
                // that can't actually reach its pick (blocked path, unstable footing) frees itself to
                // choose another instead of grinding against the same block forever.
                if (dist < this.lastDistSqr - 0.25) {
                    this.lastDistSqr = dist;
                    this.noProgressTicks = 0;
                } else if (++this.noProgressTicks > NO_PROGRESS_LIMIT) {
                    abandonTarget(pos);
                    return;
                }
                if (this.probe.getNavigation().isDone()) {
                    this.probe.getNavigation().moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, 1.0);
                }
                this.mineTicks = 0;
                return;
            }
            this.probe.getNavigation().stop();
            this.probe.swing(net.minecraft.world.InteractionHand.MAIN_HAND);
            if (++this.mineTicks < MINE_TICKS) {
                if (this.mineTicks % 10 == 0) {
                    this.probe.level().playSound(null, pos, SoundEvents.STONE_HIT, SoundSource.BLOCKS, 0.5f, 1.2f);
                }
                spawnMiningParticles();
                return;
            }

            Level level = this.probe.level();
            BlockState state = level.getBlockState(pos);
            this.probe.carried = yieldFor(state);
            // A worker never given an order is put on a resource by its own first pick.
            this.probe.assignedType = ResourceType.of(state);
            this.probe.lastMinedPos = pos.immutable();
            level.levelEvent(2001, pos, Block.getId(state));
            level.setBlock(pos, AsteriskCraft.DEPLETED_NODE.get().defaultBlockState(), Block.UPDATE_ALL);
            if (level.getBlockEntity(pos) instanceof DepletedNodeBlockEntity node) {
                node.setOriginalState(state);
            }
            this.target = null;
            // If this was a commanded mine, the node is now depleted — drop the order so the
            // probe reverts to autonomous harvesting (a no-op when there was no order).
            CommandAttachments.clearOrder(this.probe);
        }

        /** Emits a burst of crit particles from the probe's front, where its mining beam would be. */
        private void spawnMiningParticles() {
            if (!(this.probe.level() instanceof ServerLevel serverLevel)) {
                return;
            }
            Vec3 look = this.probe.getLookAngle();
            Vec3 origin = this.probe.getEyePosition().add(look.scale(0.6));
            serverLevel.sendParticles(ParticleTypes.CRIT,
                    origin.x, origin.y, origin.z,
                    2, 0.05, 0.05, 0.05, 0.02);
        }

        /**
         * Gives up on the current node: stops navigating and, for an autonomous pick, blacklists
         * the block for a short cooldown so the next search skips it and chooses a different one.
         * A commanded mine is dropped back to autonomous behaviour instead.
         */
        private void abandonTarget(BlockPos pos) {
            this.probe.getNavigation().stop();
            if (this.commandedTarget) {
                CommandAttachments.clearOrder(this.probe);
            } else {
                this.unreachable.put(pos.immutable(), this.probe.level().getGameTime() + UNREACHABLE_COOLDOWN);
            }
            this.target = null;
            this.searchCooldown = 0; // re-search immediately so a fresh node is picked next tick
        }

        /**
         * Picks a node the probe can actually walk to, of its assigned resource and of nothing else.
         * Candidates are ranked nearest to the block it last mined — not to the probe's current spot,
         * which after a delivery is always back at home — and the top few are verified with a real
         * pathfind; the first that yields a reachable path wins. Recently-abandoned nodes are skipped
         * until their cooldown lapses.
         *
         * <p>Returns {@code null} when nothing of the assigned resource is standing, which parks the
         * worker on {@link AwaitRegenGoal} rather than letting it drift onto a different resource.
         * The one exception is a resource that has left the base altogether — none of it standing and
         * none of it regenerating — where keeping the assignment would leave the worker waiting on
         * something that is never coming back, so it is dropped and the search re-run unfiltered.
         */
        @Nullable
        private BlockPos findNearestHarvestable() {
            Scan scan = scan(this.probe.assignedType);
            BlockPos pick = firstPathable(scan.candidates());
            // Only a worker with nothing to mine waits, so the node is handed over only then.
            this.probe.pendingNodePos = pick == null ? scan.pendingNode() : null;
            if (pick != null || this.probe.assignedType == null) {
                return pick;
            }
            if (scan.sawAssignedResource()) {
                return null; // depleted (or briefly unreachable) — wait it out, do not switch resource
            }
            this.probe.assignedType = null;
            return firstPathable(scan(null).candidates());
        }

        /**
         * One sweep of the box around home, collecting everything the harvest decision needs: the
         * rankable candidates of {@code assigned} (of any resource when it is {@code null}), the
         * nearest node of it that is regenerating, and whether the box holds any of that resource at
         * all — standing or depleted, reachable or not. All three come off the same sweep, because
         * the sweep is the expensive part.
         */
        private Scan scan(@Nullable ResourceType assigned) {
            Level level = this.probe.level();
            BlockPos home = this.probe.homePos;
            // Rank by proximity to the last-mined block so the probe returns to its vein; a probe that
            // has never mined falls back to its current position.
            BlockPos ref = this.probe.lastMinedPos != null
                    ? this.probe.lastMinedPos : this.probe.blockPosition();
            long now = level.getGameTime();
            this.unreachable.entrySet().removeIf(e -> e.getValue() <= now);

            List<Candidate> candidates = new ArrayList<>();
            BlockPos pendingNode = null;
            double pendingDistSqr = Double.MAX_VALUE;
            boolean sawAssignedResource = false;
            for (BlockPos pos : BlockPos.betweenClosed(
                    home.offset(-SEARCH_RADIUS, -SEARCH_VERTICAL, -SEARCH_RADIUS),
                    home.offset(SEARCH_RADIUS, SEARCH_VERTICAL, SEARCH_RADIUS))) {
                BlockState state = level.getBlockState(pos);
                if (!this.probe.canHarvest(state)) {
                    if (assigned != null && regeneratesInto(level, pos, state, assigned)) {
                        sawAssignedResource = true;
                        double distSqr = pos.distSqr(ref);
                        if (distSqr < pendingDistSqr) {
                            pendingDistSqr = distSqr;
                            pendingNode = pos.immutable();
                        }
                    }
                    continue;
                }
                if (!mines(assigned, ResourceType.of(state))) {
                    continue;
                }
                sawAssignedResource = true;
                // Cheap pre-filter: some passable neighbor to stand at. A real pathfind (below)
                // then confirms the probe can actually get there.
                if (!isReachable(level, pos)) {
                    continue;
                }
                BlockPos immutable = pos.immutable();
                if (this.unreachable.containsKey(immutable)) {
                    continue;
                }
                candidates.add(new Candidate(immutable, pos.distSqr(ref)));
            }
            candidates.sort(Comparator.comparingDouble(Candidate::distSqr));
            return new Scan(candidates, pendingNode, sawAssignedResource);
        }

        /**
         * The nearest candidate a real pathfind reaches, blacklisting the ones it rejects on the way
         * so later searches do not keep re-checking them. Capped at {@link #MAX_PATH_CHECKS} A* runs.
         */
        @Nullable
        private BlockPos firstPathable(List<Candidate> candidates) {
            long now = this.probe.level().getGameTime();
            int checks = 0;
            for (Candidate candidate : candidates) {
                if (checks++ >= MAX_PATH_CHECKS) {
                    break;
                }
                if (canPathTo(candidate.pos())) {
                    return candidate.pos();
                }
                this.unreachable.put(candidate.pos(), now + UNREACHABLE_COOLDOWN);
            }
            return null;
        }

        /** Whether a real pathfind reaches within mining range of {@code pos}. */
        private boolean canPathTo(BlockPos pos) {
            Path path = this.probe.getNavigation().createPath(pos, REACH_ACCURACY);
            return path != null && path.canReach();
        }

        private static boolean isReachable(Level level, BlockPos pos) {
            for (BlockPos neighbor : new BlockPos[]{pos.north(), pos.south(), pos.east(), pos.west(), pos.above()}) {
                if (level.getBlockState(neighbor).isAir()) {
                    return true;
                }
            }
            return false;
        }

        /** A ranked harvest candidate: nearest to the last-mined block first. */
        private record Candidate(BlockPos pos, double distSqr) {
        }

        /** Everything one sweep of the home box turned up — see {@link #scan(ResourceType)}. */
        private record Scan(List<Candidate> candidates, @Nullable BlockPos pendingNode,
                            boolean sawAssignedResource) {
        }
    }

    /**
     * What a worker does when every node of its assigned resource is depleted: walk to the nearest
     * one and hold there until it regrows, instead of drifting onto a resource it was not put on.
     * Ranked below {@link HarvestGoal}, whose search is also what hands this goal the node to wait
     * at, so the moment that node is a real block again the harvest reclaims the worker.
     */
    static class AwaitRegenGoal extends Goal {
        private static final double WAIT_RANGE_SQR = 9.0; // close enough to read as holding the spot

        private final ProbeEntity probe;
        @Nullable
        private BlockPos node;

        AwaitRegenGoal(ProbeEntity probe) {
            this.probe = probe;
            this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            BlockPos pending = this.probe.pendingNodePos;
            if (pending == null || !stillWorthWaitingFor(pending)) {
                return false;
            }
            this.node = pending;
            return true;
        }

        @Override
        public boolean canContinueToUse() {
            return this.node != null && stillWorthWaitingFor(this.node);
        }

        /** Whether the worker is free, still assigned, and that node is still regrowing its resource. */
        private boolean stillWorthWaitingFor(BlockPos pos) {
            ResourceType assigned = this.probe.assignedType;
            return !this.probe.isCarrying()
                    && assigned != null
                    && regeneratesInto(this.probe.level(), pos, this.probe.level().getBlockState(pos), assigned);
        }

        @Override
        public void stop() {
            this.node = null;
            this.probe.getNavigation().stop();
        }

        @Override
        public boolean requiresUpdateEveryTick() {
            return true;
        }

        @Override
        public void tick() {
            if (this.node == null) {
                return;
            }
            BlockPos pos = this.node;
            this.probe.getLookControl().setLookAt(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
            if (pos.distToCenterSqr(this.probe.position()) > WAIT_RANGE_SQR) {
                if (this.probe.getNavigation().isDone()) {
                    this.probe.getNavigation().moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, 1.0);
                }
            } else {
                // Arrived: stand over the node rather than shuffling around it.
                this.probe.getNavigation().stop();
            }
        }
    }

    /** Carries the harvested yield to the nearest chest around home and unloads it. */
    static class DeliverGoal extends Goal {
        private final ProbeEntity probe;
        @Nullable
        private BlockPos chestPos;
        private int stuckTicks;

        DeliverGoal(ProbeEntity probe) {
            this.probe = probe;
            this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            if (!this.probe.isCarrying()) {
                return false;
            }
            this.chestPos = this.probe.findDeliveryTarget();
            return this.chestPos != null;
        }

        @Override
        public boolean canContinueToUse() {
            return this.probe.isCarrying() && this.chestPos != null && this.stuckTicks < 300;
        }

        @Override
        public void stop() {
            this.chestPos = null;
            this.stuckTicks = 0;
            this.probe.getNavigation().stop();
        }

        @Override
        public boolean requiresUpdateEveryTick() {
            return true;
        }

        @Override
        public void tick() {
            if (this.chestPos == null) {
                return;
            }
            this.stuckTicks++;
            BlockPos pos = this.chestPos;
            this.probe.getLookControl().setLookAt(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
            if (pos.distToCenterSqr(this.probe.position()) > 6.25) {
                this.probe.getNavigation().moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, 1.0);
                return;
            }
            ResourceHandler<ItemResource> handler = this.probe.level().getCapability(Capabilities.Item.BLOCK, pos, null);
            if (handler == null) {
                // Home core is gone (razed). Re-route to the nearest surviving friendly core so the
                // carried load still gets deposited, rather than idling here forever.
                BlockPos next = this.probe.rehomeToNearestCore();
                if (next != null) {
                    this.probe.setHomePos(next);
                }
                this.chestPos = null;
                return;
            }
            ItemResource resource = ItemResource.of(this.probe.carried);
            try (Transaction tx = Transaction.openRoot()) {
                int inserted = handler.insert(resource, this.probe.carried.getCount(), tx);
                tx.commit();
                this.probe.carried.shrink(inserted);
            }
            if (this.probe.carried.isEmpty()) {
                this.probe.carried = ItemStack.EMPTY;
                this.probe.level().playSound(null, pos, SoundEvents.ITEM_PICKUP, SoundSource.NEUTRAL, 0.7f, 1.0f);
            }
            // If the target is full the probe keeps what's left and retries after canUse fires again.
            this.chestPos = null;
        }
    }
}
