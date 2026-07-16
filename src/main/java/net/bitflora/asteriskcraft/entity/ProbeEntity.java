package net.bitflora.asteriskcraft.entity;

import net.bitflora.asteriskcraft.AsteriskCraft;
import net.bitflora.asteriskcraft.building.DepletedNodeBlockEntity;
import net.bitflora.asteriskcraft.command.CommandAttachments;
import net.bitflora.asteriskcraft.command.CommandOrder;
import net.bitflora.asteriskcraft.entity.ai.CommandedMoveGoal;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
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
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;

import java.util.EnumSet;
import org.jetbrains.annotations.Nullable;

/**
 * The Protoss worker. Finds a harvestable block near its home Nexus, mines it
 * non-destructively (the block is swapped for a regenerating depleted node),
 * then delivers the yield to the nearest chest.
 */
public class ProbeEntity extends PathfinderMob {
    public static final TagKey<Block> HARVESTABLE = BlockTags.create(AsteriskCraft.id("harvestable"));
    public static final int YIELD_PER_TRIP = 10;
    public static final int MINE_TICKS = 60;
    public static final int SEARCH_RADIUS = 24;
    public static final int SEARCH_VERTICAL = 8;

    private BlockPos homePos = BlockPos.ZERO;
    private ItemStack carried = ItemStack.EMPTY;

    public ProbeEntity(EntityType<? extends ProbeEntity> type, Level level) {
        super(type, level);
        this.setPersistenceRequired();
    }

    public static AttributeSupplier.Builder createAttributes() {
        return PathfinderMob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 20.0)
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

    public void setHomePos(BlockPos pos) {
        this.homePos = pos.immutable();
    }

    public BlockPos getHomePos() {
        return this.homePos;
    }

    public boolean isCarrying() {
        return !this.carried.isEmpty();
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output);
        output.store("HomePos", BlockPos.CODEC, this.homePos);
        if (!this.carried.isEmpty()) {
            output.store("Carried", ItemStack.CODEC, this.carried);
        }
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
        this.homePos = input.read("HomePos", BlockPos.CODEC).orElse(BlockPos.ZERO);
        this.carried = input.read("Carried", ItemStack.CODEC).orElse(ItemStack.EMPTY);
    }

    /** What a harvested block yields. Iron smelts on the way home; stone chips to cobble. */
    static ItemStack yieldFor(BlockState state) {
        if (state.is(BlockTags.LOGS)) {
            return new ItemStack(state.getBlock().asItem(), YIELD_PER_TRIP);
        }
        if (state.is(BlockTags.IRON_ORES)) {
            return new ItemStack(Items.IRON_INGOT, YIELD_PER_TRIP / 2);
        }
        return new ItemStack(Items.COBBLESTONE, YIELD_PER_TRIP);
    }

    /**
     * Walks to a harvestable block near home, mines it for a few seconds, swaps it
     * for a depleted node, and loads the yield onto the probe.
     */
    static class HarvestGoal extends Goal {
        private final ProbeEntity probe;
        @Nullable
        private BlockPos target;
        private int mineTicks;
        private int searchCooldown;

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
                    this.target = commanded;
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
            if (pos.distToCenterSqr(this.probe.position()) > 6.25) {
                this.probe.getNavigation().moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, 1.0);
                this.mineTicks = 0;
                return;
            }
            this.probe.getNavigation().stop();
            this.probe.swing(net.minecraft.world.InteractionHand.MAIN_HAND);
            if (++this.mineTicks < MINE_TICKS) {
                if (this.mineTicks % 10 == 0) {
                    this.probe.level().playSound(null, pos, SoundEvents.STONE_HIT, SoundSource.BLOCKS, 0.5f, 1.2f);
                }
                return;
            }

            Level level = this.probe.level();
            BlockState state = level.getBlockState(pos);
            this.probe.carried = yieldFor(state);
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

        @Nullable
        private BlockPos findNearestHarvestable() {
            Level level = this.probe.level();
            BlockPos home = this.probe.homePos;
            BlockPos best = null;
            double bestDist = Double.MAX_VALUE;
            for (BlockPos pos : BlockPos.betweenClosed(
                    home.offset(-SEARCH_RADIUS, -SEARCH_VERTICAL, -SEARCH_RADIUS),
                    home.offset(SEARCH_RADIUS, SEARCH_VERTICAL, SEARCH_RADIUS))) {
                if (!level.getBlockState(pos).is(HARVESTABLE)) {
                    continue;
                }
                // Only blocks a probe can stand next to: some horizontal neighbor is passable.
                if (!isReachable(level, pos)) {
                    continue;
                }
                double dist = pos.distSqr(this.probe.blockPosition());
                if (dist < bestDist) {
                    bestDist = dist;
                    best = pos.immutable();
                }
            }
            return best;
        }

        private static boolean isReachable(Level level, BlockPos pos) {
            for (BlockPos neighbor : new BlockPos[]{pos.north(), pos.south(), pos.east(), pos.west(), pos.above()}) {
                if (level.getBlockState(neighbor).isAir()) {
                    return true;
                }
            }
            return false;
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
            this.chestPos = findNearestChest();
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
            // If the chest is full the probe keeps what's left and retries after canUse fires again.
            this.chestPos = null;
        }

        @Nullable
        private BlockPos findNearestChest() {
            Level level = this.probe.level();
            BlockPos home = this.probe.homePos.equals(BlockPos.ZERO) ? this.probe.blockPosition() : this.probe.homePos;
            BlockPos best = null;
            double bestDist = Double.MAX_VALUE;
            for (BlockPos pos : BlockPos.betweenClosed(home.offset(-12, -4, -12), home.offset(12, 4, 12))) {
                BlockState state = level.getBlockState(pos);
                if (!state.is(Blocks.CHEST) && !state.is(Blocks.TRAPPED_CHEST) && !state.is(Blocks.BARREL)) {
                    continue;
                }
                double dist = pos.distSqr(this.probe.blockPosition());
                if (dist < bestDist) {
                    bestDist = dist;
                    best = pos.immutable();
                }
            }
            return best;
        }
    }
}
