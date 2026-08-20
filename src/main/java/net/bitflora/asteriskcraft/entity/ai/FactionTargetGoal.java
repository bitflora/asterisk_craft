package net.bitflora.asteriskcraft.entity.ai;

import net.bitflora.asteriskcraft.command.CommandAttachments;
import net.bitflora.asteriskcraft.faction.FactionAttachments;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;

import java.util.function.Predicate;

/**
 * Targets the nearest hostile entity, full stop: entity type is not a criterion, so an enemy-faction
 * Probe is as valid a target as an enemy soldier. Same-faction entities are spared, and so are
 * peaceful NEUTRALs — the player and wild animals — purely because
 * {@link net.bitflora.asteriskcraft.faction.FactionAttachments#isHostile} says so. Wild <em>hostile</em>
 * mobs are fair game, so a unit defends itself against a zombie that wanders into the base instead of
 * standing and watching. Faction-generic: works for any owner/target faction pairing.
 *
 * <p>It goes quiet while a fresh move order is in force ({@link CommandAttachments#isMoveFocused}):
 * a unit told to walk somewhere must actually leave, and autonomous acquisition would otherwise
 * re-target the enemy it is standing next to on the tick after the order cleared its target.
 *
 * <p>A unit that only engages part of the battlefield passes an extra filter — the Spore Colony's
 * {@link net.bitflora.asteriskcraft.entity.Altitude#isAirborne}. That filter may only ever
 * <em>narrow</em> what {@code isHostile} already allowed, never widen it: hostility (and with it the
 * cloak gate) stays decided in the one choke point, and this constructor merely subtracts from its
 * answer.
 */
public class FactionTargetGoal extends NearestAttackableTargetGoal<LivingEntity> {

    public FactionTargetGoal(Mob owner) {
        this(owner, target -> true);
    }

    public FactionTargetGoal(Mob owner, Predicate<LivingEntity> filter) {
        // mustSee = true: units react to what they can actually see, not to anything in follow range.
        super(owner, LivingEntity.class, 10, true, false,
                (target, level) -> FactionAttachments.isHostile(owner, target) && filter.test(target));
    }

    @Override
    public boolean canUse() {
        return !CommandAttachments.isMoveFocused(this.mob) && super.canUse();
    }
}
