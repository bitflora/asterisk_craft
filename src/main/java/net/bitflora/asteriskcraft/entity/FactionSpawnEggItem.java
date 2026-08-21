package net.bitflora.asteriskcraft.entity;

import net.bitflora.asteriskcraft.faction.Faction;
import net.bitflora.asteriskcraft.faction.FactionAttachments;
import net.bitflora.asteriskcraft.game.MatchSetup;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import org.jetbrains.annotations.Nullable;

import java.util.function.Supplier;

/**
 * A spawn egg that stamps the spawned mob with a {@link Faction} rather than deferring to whatever
 * egg-type/faction convention the entity would otherwise pick up. Vanilla's {@code SpawnEggItem}
 * keys the entity type off a data component and offers no hook to run code on the freshly spawned
 * mob, so this reimplements its place-on-block/place-in-liquid behavior directly against a fixed
 * {@link EntityType} instead.
 *
 * <p>The faction is chosen by {@link Side} at use time, not fixed at registration. Every unit has
 * an "ally" egg and an "enemy" egg, and which actual faction each of those means is a fact about
 * the match ({@code game.MatchSetup}), not about the item — so a match where the human plays Zerg
 * hands out the same eggs and they still spawn things on the right sides.
 */
public class FactionSpawnEggItem extends Item {

    /** Whose side an egg's mob joins, resolved against the match when the egg is used. */
    public enum Side {
        /** The side the player commands. */
        ALLY,
        /** The side the computer plays. */
        ENEMY;

        Faction resolve(Level level) {
            MatchSetup setup = MatchSetup.of(level);
            return this == ALLY ? setup.playerFaction() : setup.aiFaction();
        }
    }

    private final Supplier<? extends EntityType<? extends Mob>> entityType;
    private final Side side;

    public FactionSpawnEggItem(Properties properties, Supplier<? extends EntityType<? extends Mob>> entityType, Side side) {
        super(properties);
        this.entityType = entityType;
        this.side = side;
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        if (!(level instanceof ServerLevel serverLevel)) {
            return InteractionResult.SUCCESS;
        }
        BlockPos pos = context.getClickedPos();
        Direction clickedFace = context.getClickedFace();
        BlockState blockState = level.getBlockState(pos);
        BlockPos spawnPos = blockState.getCollisionShape(level, pos).isEmpty() ? pos : pos.relative(clickedFace);
        return spawnMob(context.getPlayer(), context.getItemInHand(), serverLevel, spawnPos,
                true, !pos.equals(spawnPos) && clickedFace == Direction.UP);
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        BlockHitResult hitResult = getPlayerPOVHitResult(level, player, ClipContext.Fluid.SOURCE_ONLY);
        if (hitResult.getType() != HitResult.Type.BLOCK) {
            return InteractionResult.PASS;
        }
        if (!(level instanceof ServerLevel serverLevel)) {
            return InteractionResult.SUCCESS;
        }
        BlockPos pos = hitResult.getBlockPos();
        if (!(level.getBlockState(pos).getBlock() instanceof LiquidBlock)) {
            return InteractionResult.PASS;
        }
        if (!level.mayInteract(player, pos) || !player.mayUseItemAt(pos, hitResult.getDirection(), stack)) {
            return InteractionResult.FAIL;
        }
        InteractionResult result = spawnMob(player, stack, serverLevel, pos, false, false);
        if (result == InteractionResult.SUCCESS) {
            player.awardStat(Stats.ITEM_USED.get(this));
        }
        return result;
    }

    private InteractionResult spawnMob(@Nullable LivingEntity user, ItemStack stack, ServerLevel level,
                                        BlockPos pos, boolean tryMoveDown, boolean movedUp) {
        EntityType<? extends Mob> type = entityType.get();
        Mob mob = type.spawn(level, stack, user, pos, EntitySpawnReason.SPAWN_ITEM_USE, tryMoveDown, movedUp);
        if (mob != null) {
            FactionAttachments.set(mob, this.side.resolve(level));
            stack.consume(1, user);
            level.gameEvent(user, GameEvent.ENTITY_PLACE, pos);
        }
        return InteractionResult.SUCCESS;
    }
}
