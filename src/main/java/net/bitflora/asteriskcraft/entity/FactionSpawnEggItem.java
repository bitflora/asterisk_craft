package net.bitflora.asteriskcraft.entity;

import net.bitflora.asteriskcraft.building.CreepDependent;
import net.bitflora.asteriskcraft.building.CreepField;
import net.bitflora.asteriskcraft.building.PsiDependent;
import net.bitflora.asteriskcraft.building.PsiField;
import net.bitflora.asteriskcraft.command.ControlledRace;
import net.bitflora.asteriskcraft.faction.Faction;
import net.bitflora.asteriskcraft.faction.FactionAttachments;
import net.bitflora.asteriskcraft.faction.Race;
import net.bitflora.asteriskcraft.game.GameBootstrap;
import net.bitflora.asteriskcraft.game.MatchSetup;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;
import net.minecraft.tags.BlockTags;
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
 *
 * <p>The Photon Cannon is a building that happens to be an entity, so its "kit" is one of these
 * rather than a {@code building/BuildingKitItem} — which makes this the second of the mod's two
 * placement call sites, and the reason for {@code requiresPylon}/{@code requiresCreep}. Both sites
 * ask {@code building/PsiField} and {@code building/CreepField} and nothing else.
 */
public class FactionSpawnEggItem extends Item implements PsiDependent, CreepDependent {

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

        /** The race that side drew in this match — what the mob's army <em>is</em>. */
        Race resolveRace(Level level) {
            MatchSetup setup = MatchSetup.of(level);
            return this == ALLY ? setup.playerRace() : setup.aiRace();
        }
    }

    private final Supplier<? extends EntityType<? extends Mob>> entityType;
    private final Side side;
    private final boolean requiresPylon;
    private final boolean requiresCreep;
    private final boolean spreadsCreep;

    public FactionSpawnEggItem(Properties properties, Supplier<? extends EntityType<? extends Mob>> entityType, Side side) {
        this(properties, entityType, side, false);
    }

    /**
     * @param requiresPylon whether this egg places a <em>building</em> that needs a Pylon in range.
     *                      True only for the Photon Cannon kit; a spawn egg for an actual unit is
     *                      never gated.
     */
    public FactionSpawnEggItem(Properties properties, Supplier<? extends EntityType<? extends Mob>> entityType,
            Side side, boolean requiresPylon) {
        this(properties, entityType, side, requiresPylon, false);
    }

    /**
     * @param spreadsCreep whether this egg's mob should spread its race's ground cover (Zerg creep)
     *                     out from itself once placed — true only for the Sunken/Spore Colony eggs.
     *                     Resolved against the spawned mob's faction at use time, so this class still
     *                     names no race: a race with no creep (Protoss) is a no-op.
     */
    public FactionSpawnEggItem(Properties properties, Supplier<? extends EntityType<? extends Mob>> entityType,
            Side side, boolean requiresPylon, boolean spreadsCreep) {
        this(properties, entityType, side, requiresPylon, false, spreadsCreep);
    }

    /**
     * @param requiresCreep whether this egg places a <em>building</em> that needs Zerg creep in
     *                       range — the sibling of {@code requiresPylon}, and never true at the same
     *                       time as it. True only for the Sunken/Spore Colony eggs, which is also
     *                       why it doubles as the gate for refusing a spot standing on leaves — a
     *                       colony rooted in foliage that can be chopped out from under it.
     */
    public FactionSpawnEggItem(Properties properties, Supplier<? extends EntityType<? extends Mob>> entityType,
            Side side, boolean requiresPylon, boolean requiresCreep, boolean spreadsCreep) {
        super(properties);
        this.entityType = entityType;
        this.side = side;
        this.requiresPylon = requiresPylon;
        this.requiresCreep = requiresCreep;
        this.spreadsCreep = spreadsCreep;
    }

    @Override
    public boolean requiresPylon() {
        return this.requiresPylon;
    }

    @Override
    public boolean requiresCreep() {
        return this.requiresCreep;
    }

    /**
     * Resolves the race for {@link CreepDependent}'s client-side overlay — deliberately <em>not</em>
     * {@code this.side.resolveRace(level)}: {@code MatchSetup.of} answers with its hardcoded default
     * on the client (the level-scoped match setup isn't synced), which is fine for {@link #spawnMob}'s
     * authoritative server-side check but would silently paint the wrong army's creep whenever the
     * human isn't playing the default's race. The player's own race is instead read off
     * {@code command.ControlledRace}, a synced per-player attachment that answers correctly on both
     * sides.
     *
     * <p>The ENEMY case has no synced source of its own and answers with the player's race too. That
     * is exact in a mirror match and an approximation otherwise — and it only decides which ground
     * block the overlay paints as creep for an enemy-side testing egg, never what the server will
     * accept.
     */
    @Override
    public @Nullable Race placingRace(Level level, Player player) {
        return ControlledRace.of(player);
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
        // The army the mob is about to join: the side that has to own the Pylon powering it, and
        // the race whose creep it may be rooted in.
        Faction faction = this.side.resolve(level);
        Race race = this.side.resolveRace(level);
        if (this.requiresPylon && !PsiField.covered(level, pos, faction)) {
            if (user instanceof ServerPlayer player) {
                player.sendSystemMessage(
                        Component.translatable("message.asteriskcraft.kit.no_pylon", PsiField.RADIUS), true);
            }
            return InteractionResult.FAIL;
        }
        if (this.requiresCreep && !CreepField.covered(level, pos, race)) {
            if (user instanceof ServerPlayer player) {
                player.sendSystemMessage(
                        Component.translatable("message.asteriskcraft.kit.no_creep", CreepField.RADIUS), true);
            }
            return InteractionResult.FAIL;
        }
        if (this.requiresCreep && level.getBlockState(pos.below()).is(BlockTags.LEAVES)) {
            if (user instanceof ServerPlayer player) {
                player.sendSystemMessage(Component.translatable("message.asteriskcraft.kit.on_leaves"), true);
            }
            return InteractionResult.FAIL;
        }
        EntityType<? extends Mob> type = entityType.get();
        Mob mob = type.spawn(level, stack, user, pos, EntitySpawnReason.SPAWN_ITEM_USE, tryMoveDown, movedUp);
        if (mob != null) {
            FactionAttachments.set(mob, faction, race);
            if (this.spreadsCreep) {
                GameBootstrap.spreadCreep(level, mob.blockPosition(), race);
            }
            stack.consume(1, user);
            level.gameEvent(user, GameEvent.ENTITY_PLACE, pos);
        }
        return InteractionResult.SUCCESS;
    }
}
