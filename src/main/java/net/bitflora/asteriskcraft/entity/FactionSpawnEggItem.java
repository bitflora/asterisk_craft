package net.bitflora.asteriskcraft.entity;

import net.bitflora.asteriskcraft.faction.Faction;
import net.bitflora.asteriskcraft.faction.FactionAttachments;
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
 * A spawn egg that stamps the spawned mob with a fixed {@link Faction} rather than deferring
 * to whatever egg-type/faction convention the entity would otherwise pick up. Vanilla's
 * {@code SpawnEggItem} keys the entity type off a data component and offers no hook to run code
 * on the freshly spawned mob, so this reimplements its place-on-block/place-in-liquid behavior
 * directly against a fixed {@link EntityType} instead.
 */
public class FactionSpawnEggItem extends Item {
    private final Supplier<? extends EntityType<? extends Mob>> entityType;
    private final Faction faction;

    public FactionSpawnEggItem(Properties properties, Supplier<? extends EntityType<? extends Mob>> entityType, Faction faction) {
        super(properties);
        this.entityType = entityType;
        this.faction = faction;
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
            FactionAttachments.set(mob, faction);
            stack.consume(1, user);
            level.gameEvent(user, GameEvent.ENTITY_PLACE, pos);
        }
        return InteractionResult.SUCCESS;
    }
}
