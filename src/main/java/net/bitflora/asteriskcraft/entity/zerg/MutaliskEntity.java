package net.bitflora.asteriskcraft.entity.zerg;

import net.bitflora.asteriskcraft.combat.AsteriskCraftDamageTypes;
import net.bitflora.asteriskcraft.entity.ai.CommandableGoals;
import net.bitflora.asteriskcraft.entity.ai.FactionTargetGoal;
import net.bitflora.asteriskcraft.entity.ai.HitscanAttacks;
import net.bitflora.asteriskcraft.entity.ai.HoverFlyingNavigation;
import net.bitflora.asteriskcraft.entity.ai.HoverGoal;
import net.bitflora.asteriskcraft.entity.ai.RetaliateGoal;
import net.bitflora.asteriskcraft.entity.ai.SiegeBlockGoal;
import net.bitflora.asteriskcraft.stats.UnitAttributes;
import net.bitflora.asteriskcraft.stats.UnitStat;
import net.bitflora.asteriskcraft.stats.UnitStats;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.FlyingMoveControl;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.RangedAttackGoal;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.monster.RangedAttackMob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

/**
 * The Zerg air unit, and the mod's first flyer. It cruises at a fixed height above the
 * terrain and spits glaves at anything of an enemy faction below, which means melee ground units
 * (Zealot, Zergling) simply cannot answer it — only ranged units and static defence can. That is
 * deliberate, and matches StarCraft.
 *
 * <p>Flight comes from two pieces, neither of which any combat goal knows about: a vanilla
 * {@link FlyingMoveControl} for the actual airborne motion, and a {@link HoverFlyingNavigation} that
 * lifts every destination to cruising altitude before pathing (see that class for why the altitude has
 * to be baked into the path rather than applied at the move control). A {@link HoverGoal} at the
 * bottom of the goal list holds the unit up when nothing else is driving it.
 *
 * <p>Its attack radius is a 3D distance, so a Mutalisk sitting at cruising altitude still has
 * {@code sqrt(9² − 6²) ≈ 6.7} blocks of horizontal reach on a ground target — a genuine ranged
 * engagement, not a near-melee one. Its shot is a bouncing glave: {@link HitscanAttacks#fireChained}
 * hits the primary target then chains to up to two more nearby enemies (5 blocks from the enemy
 * just hit, not from the Mutalisk itself — see {@link net.bitflora.asteriskcraft.combat.BounceChain}),
 * each hop dealing half the previous one's damage. That makes it the anti-clump unit: full damage
 * against a lone target is unchanged from a plain hitscan shot, but a massed group takes far more.
 *
 * <p>Its numbers live in {@link net.bitflora.asteriskcraft.stats.UnitStats#MUTALISK} — not here.
 */
public class MutaliskEntity extends Monster implements RangedAttackMob {
    private static final UnitStat STAT = UnitStats.MUTALISK;
    private static final UnitStat.Ranged RANGED = STAT.rangedOrThrow();
    private static final UnitStat.Flight FLIGHT = STAT.flightOrThrow();
    private static final UnitStat.Bounce BOUNCE = STAT.bounceOrThrow();

    public MutaliskEntity(EntityType<? extends MutaliskEntity> type, Level level) {
        super(type, level);
        // hoversInPlace = true: the move control switches gravity off for us and, unlike the
        // ground-falling variant, never switches it back on when it runs out of somewhere to go.
        this.moveControl = new FlyingMoveControl(this, 20, true);
        this.setPersistenceRequired();
    }

    public static AttributeSupplier.Builder createAttributes() {
        return UnitAttributes.apply(Monster.createMonsterAttributes(), UnitStats.MUTALISK);
    }

    @Override
    protected PathNavigation createNavigation(Level level) {
        HoverFlyingNavigation navigation = new HoverFlyingNavigation(this, level, FLIGHT.hoverHeight());
        navigation.setCanOpenDoors(false);
        navigation.setCanFloat(true);
        // The default path length is far shorter than this unit's follow range, so a Mutalisk would
        // give up on distant targets mid-approach. (Vanilla's Bee raises it for the same reason.)
        navigation.setRequiredPathLength(FLIGHT.requiredPathLength());
        return navigation;
    }

    @Override
    protected void registerGoals() {
        // No FloatGoal: it flies, and the navigation is allowed to float across water anyway.
        // Priority 0, as on the ground units: the siege goal must be able to preempt movement.
        this.goalSelector.addGoal(0, new SiegeBlockGoal(this, RANGED.range()));
        this.goalSelector.addGoal(4, new RangedAttackGoal(this, 1.0, RANGED.cooldown(), RANGED.range()));
        this.goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 12.0f));
        this.goalSelector.addGoal(6, new RandomLookAroundGoal(this));
        // Last: only holds altitude in the gaps between real orders.
        this.goalSelector.addGoal(8, new HoverGoal(this, FLIGHT.hoverHeight(), 1.0));
        this.targetSelector.addGoal(-1, new RetaliateGoal(this));
        this.targetSelector.addGoal(1, new FactionTargetGoal(this));
        CommandableGoals.install(this, this.goalSelector, this.targetSelector);
    }

    @Override
    public void performRangedAttack(LivingEntity target, float power) {
        HitscanAttacks.fireChained(this, target, this.getAttributeValue(Attributes.ATTACK_DAMAGE),
                AsteriskCraftDamageTypes.GLAVE_WURM, ParticleTypes.ITEM_SLIME, SoundEvents.SHULKER_SHOOT, BOUNCE);
    }

    @Override
    public boolean causeFallDamage(double distance, float multiplier, DamageSource source) {
        // Gravity is only switched off once the move control has somewhere to fly, so a Mutalisk
        // warped in over a drop would otherwise take fall damage before it ever got airborne.
        return false;
    }

    // No sound overrides: there are no Mutalisk .ogg assets to port, so it stays silent like the
    // Dragoon rather than borrowing another unit's voice.
}
