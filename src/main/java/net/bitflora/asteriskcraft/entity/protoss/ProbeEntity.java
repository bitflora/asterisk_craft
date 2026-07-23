package net.bitflora.asteriskcraft.entity.protoss;

import net.bitflora.asteriskcraft.AsteriskCraft;
import net.bitflora.asteriskcraft.building.DepletedNodeBlockEntity;
import net.bitflora.asteriskcraft.command.CommandAttachments;
import net.bitflora.asteriskcraft.command.CommandOrder;
import net.bitflora.asteriskcraft.entity.Shielded;
import net.bitflora.asteriskcraft.entity.ai.CommandedMoveGoal;
import net.bitflora.asteriskcraft.faction.Faction;
import net.bitflora.asteriskcraft.faction.FactionAttachments;
import net.bitflora.asteriskcraft.game.GameAttachments;
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
import net.minecraft.world.entity.ai.attributes.Attributes;
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
 * The Protoss worker. Finds a harvestable block near its home Nexus, preferring the same
 * resource type it last mined, mines it non-destructively (the block is swapped for a
 * regenerating depleted node), then delivers the yield straight into the Nexus.
 */
public class ProbeEntity extends PathfinderMob implements Shielded {
    public static final TagKey<Block> HARVESTABLE = BlockTags.create(AsteriskCraft.id("harvestable"));
    public static final int YIELD_PER_TRIP = 3;
    public static final int MINE_TICKS = 60;
    public static final int SEARCH_RADIUS = 24;
    public static final int SEARCH_VERTICAL = 8;
    public static final int SHIELD = 10;

    /** Coarse resource category, used to prefer re-mining the same kind of node on the next trip. */
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
    @Nullable
    private ResourceType lastResourceType;

    public ProbeEntity(EntityType<? extends ProbeEntity> type, Level level) {
        super(type, level);
        this.setPersistenceRequired();
    }

    public static AttributeSupplier.Builder createAttributes() {
        return PathfinderMob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 10.0)
                .add(Attributes.MOVEMENT_SPEED, 0.35)
                .add(Attributes.FOLLOW_RANGE, 48.0);
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        // A move order interrupts the economy (RTS move); a carried load is still delivered
        // before a fresh mine because DeliverGoal outranks HarvestGoal.
        this.goalSelector.addGoal(1, new CommandedMoveGoal(this, 1.1));
        this.goalSelector.addGoal(2, new DeliverGoal(this));
        this.goalSelector.addGoal(3, new HarvestGoal(this));
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
        return SHIELD;
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
        if (this.lastResourceType != null) {
            output.store("LastResourceType", ResourceType.CODEC, this.lastResourceType);
        }
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
        this.homePos = input.read("HomePos", BlockPos.CODEC).orElse(BlockPos.ZERO);
        this.carried = input.read("Carried", ItemStack.CODEC).orElse(ItemStack.EMPTY);
        this.lastResourceType = input.read("LastResourceType", ResourceType.CODEC).orElse(null);
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
                if (this.probe.level().getBlockState(commanded).is(HARVESTABLE)) {
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
                    && this.probe.level().getBlockState(this.target).is(HARVESTABLE);
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
            this.probe.lastResourceType = ResourceType.of(state);
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
         * Picks a node the probe can actually walk to. Candidates are ranked (same-type-as-last
         * first so a probe keeps working one vein, then nearest), and the top few are verified with
         * a real pathfind — the first that yields a reachable path wins. Recently-abandoned nodes
         * are skipped until their cooldown lapses. Returns {@code null} if nothing reachable is in range.
         */
        @Nullable
        private BlockPos findNearestHarvestable() {
            Level level = this.probe.level();
            BlockPos home = this.probe.homePos;
            ResourceType preferred = this.probe.lastResourceType;
            long now = level.getGameTime();
            this.unreachable.entrySet().removeIf(e -> e.getValue() <= now);

            List<Candidate> candidates = new ArrayList<>();
            for (BlockPos pos : BlockPos.betweenClosed(
                    home.offset(-SEARCH_RADIUS, -SEARCH_VERTICAL, -SEARCH_RADIUS),
                    home.offset(SEARCH_RADIUS, SEARCH_VERTICAL, SEARCH_RADIUS))) {
                BlockState state = level.getBlockState(pos);
                if (!state.is(HARVESTABLE)) {
                    continue;
                }
                // Cheap pre-filter: some passable neighbor to stand at. A real pathfind (below)
                // then confirms the probe can actually get there.
                if (!isReachable(level, pos)) {
                    continue;
                }
                BlockPos immutable = pos.immutable();
                if (this.unreachable.containsKey(immutable)) {
                    continue;
                }
                double dist = pos.distSqr(this.probe.blockPosition());
                boolean sameType = preferred != null && ResourceType.of(state) == preferred;
                candidates.add(new Candidate(immutable, dist, sameType));
            }
            candidates.sort(Comparator.comparing((Candidate c) -> c.sameType).reversed()
                    .thenComparingDouble(c -> c.distSqr));

            int checks = 0;
            for (Candidate candidate : candidates) {
                if (checks++ >= MAX_PATH_CHECKS) {
                    break;
                }
                if (canPathTo(candidate.pos)) {
                    return candidate.pos;
                }
                // No path this search: blacklist so subsequent searches don't keep re-checking it.
                this.unreachable.put(candidate.pos, now + UNREACHABLE_COOLDOWN);
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

        /** A ranked harvest candidate: same-type nodes sort ahead of others, then nearest first. */
        private record Candidate(BlockPos pos, double distSqr, boolean sameType) {
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
