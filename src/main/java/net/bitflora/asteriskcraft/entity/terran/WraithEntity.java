package net.bitflora.asteriskcraft.entity.terran;

import net.bitflora.asteriskcraft.AsteriskCraft;
import net.bitflora.asteriskcraft.combat.AsteriskCraftDamageTypes;
import net.bitflora.asteriskcraft.entity.Flyer;
import net.bitflora.asteriskcraft.entity.ai.CommandableGoals;
import net.bitflora.asteriskcraft.entity.ai.FactionTargetGoal;
import net.bitflora.asteriskcraft.entity.ai.HitscanAttacks;
import net.bitflora.asteriskcraft.entity.ai.HoverFlyingNavigation;
import net.bitflora.asteriskcraft.entity.ai.HoverGoal;
import net.bitflora.asteriskcraft.entity.ai.RetaliateGoal;
import net.bitflora.asteriskcraft.entity.ai.SiegeBlockGoal;
import net.bitflora.asteriskcraft.faction.Cloaked;
import net.bitflora.asteriskcraft.stats.UnitAttributes;
import net.bitflora.asteriskcraft.stats.UnitStat;
import net.bitflora.asteriskcraft.stats.UnitStats;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
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
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * The Terran fighter, and the first thing the race puts in the air. It is assembled out of two units
 * the mod already had, and writes almost nothing of its own:
 *
 * <ul>
 *   <li><b>The {@link net.bitflora.asteriskcraft.entity.protoss.ScoutEntity Scout}'s flight and its
 *       split attack.</b> A vanilla {@link FlyingMoveControl} plus a {@link HoverFlyingNavigation}
 *       that lifts every destination to cruising altitude <em>before</em> the path is computed, so no
 *       goal in the ladder below knows it is flying; and an anti-air bonus that rides on top of the
 *       attack attribute rather than replacing it, so one attribute still describes the shot.</li>
 *   <li><b>The {@link GhostEntity}'s reactive cloak.</b> The same {@link CloakClock} — a minute of
 *       cloak on the hit that triggers it, then two minutes locked out — as one synced signed int.</li>
 * </ul>
 *
 * <p><b>Neither half needed combat code.</b> {@link Cloaked#isCloakActive()} is the single seam every
 * consumer already reaches cloak through ({@code faction.FactionAttachments.isHostile} for
 * acquisition, {@code combat.TargetRetentionHandler} for a target already held when the cloak comes
 * up, {@code combat.DetectionHandler} for the sweep, {@code client.DetectionRenderStateModifier} for
 * what each viewer sees), and the anti-air bonus lives in the attack rather than in targeting — a
 * Wraith still engages whatever {@code FactionTargetGoal} hands it, it simply does very little to it
 * unless it is off the ground. Air-ness is the {@link Flyer} marker, never a concrete entity class,
 * and never {@code entity.Altitude} — a grounded Mutalisk is still a flyer (see CLAUDE.md).
 *
 * <p>The clock is synced rather than server-only because that seam is consulted on both sides: the
 * gate runs on the server and the render decision on the client.
 *
 * <p>Deliberately <b>not</b> {@code entity.Organic}: a Wraith is a craft, so it cannot be crammed
 * into a Bunker the way the race's infantry can.
 *
 * <p><b>It is the first unit in the mod with two attack sounds</b>, and they are not decoration: the
 * archive ships a separate ground and air firing clip for a Wraith, which is the same split its
 * damage already makes, so {@link #performRangedAttack} branches once on {@code Flyer.isAir} and
 * that one test picks both. A player therefore hears whether a Wraith is doing its job.
 *
 * <p>Ambient bark and death line but <b>no hurt sound</b>, as with the Marine, the Firebat, the SCV
 * and the Ghost: the archive holds no Wraith pain line, so {@code Mob}'s default stands in.
 *
 * <p>Its numbers live in {@link UnitStats#WRAITH} — not here.
 */
public class WraithEntity extends Monster implements Flyer, RangedAttackMob, Cloaked {
    private static final UnitStat STAT = UnitStats.WRAITH;
    private static final UnitStat.Ranged RANGED = STAT.rangedOrThrow();
    private static final UnitStat.Flight FLIGHT = STAT.flightOrThrow();

    /**
     * The whole cloak state, as {@link CloakClock#value()}'s single signed int. Synced because
     * {@link #isCloakActive()} is asked on both sides — see the class doc.
     */
    private static final EntityDataAccessor<Integer> CLOAK_STATE =
            SynchedEntityData.defineId(WraithEntity.class, EntityDataSerializers.INT);

    private final CloakClock cloak = new CloakClock();

    public WraithEntity(EntityType<? extends WraithEntity> type, Level level) {
        super(type, level);
        // hoversInPlace = true: the move control switches gravity off for us and, unlike the
        // ground-falling variant, never switches it back on when it runs out of somewhere to go.
        this.moveControl = new FlyingMoveControl(this, 20, true);
        this.setPersistenceRequired();
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(CLOAK_STATE, 0);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return UnitAttributes.apply(Monster.createMonsterAttributes(), UnitStats.WRAITH);
    }

    @Override
    protected PathNavigation createNavigation(Level level) {
        HoverFlyingNavigation navigation = new HoverFlyingNavigation(this, level, FLIGHT.hoverHeight());
        navigation.setCanOpenDoors(false);
        navigation.setCanFloat(true);
        // The default path length is far shorter than this unit's follow range, so a Wraith would
        // give up on distant targets mid-approach. (Vanilla's Bee raises it for the same reason.)
        navigation.setRequiredPathLength(FLIGHT.requiredPathLength());
        return navigation;
    }

    @Override
    protected void registerGoals() {
        // The Scout's ladder verbatim — see ScoutEntity for why each priority is where it is. No
        // FloatGoal and no StuckWanderGoal: it flies, and the navigation floats across water anyway.
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

    // --- Cloak ---

    /**
     * The entire implementation of the cloak, read off the synced value rather than off the clock so
     * the client answers it identically — the clock itself only ticks on the server.
     */
    @Override
    public boolean isCloakActive() {
        return this.entityData.get(CLOAK_STATE) > 0;
    }

    /**
     * The one trigger, and the Ghost's verbatim. {@code super} is asked first so a hit that was
     * blocked, absorbed or landed on an already-dead Wraith doesn't spend the cloak, and one that
     * kills it is left alone for the same reason.
     */
    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float damage) {
        boolean hurt = super.hurtServer(level, source, damage);
        if (hurt && this.isAlive() && this.cloak.onDamaged()) {
            this.entityData.set(CLOAK_STATE, this.cloak.value());
        }
        return hurt;
    }

    @Override
    public void performRangedAttack(LivingEntity target, float power) {
        // One test, two consequences. The anti-air bonus rides on top of the attribute rather than
        // replacing it, exactly as the Scout's does, so a Wraith's ground damage stays what the
        // attribute says while an intercepted flyer takes the whole burst — and the same branch
        // picks which of the two firing clips plays, so the shot sounds like what it is worth.
        // Air-ness is the Flyer unit-type marker, never a concrete class and never Altitude.
        boolean air = Flyer.isAir(target);
        double damage = this.getAttributeValue(Attributes.ATTACK_DAMAGE)
                + (air ? STAT.antiAirBonus() : 0.0);
        // Firing does not break the cloak — the Dark Templar and the Lurker both shoot from under
        // theirs, and an interceptor that uncloaked to shoot would never get to.
        HitscanAttacks.fire(this, target, damage, AsteriskCraftDamageTypes.BURST_LASERS,
                ParticleTypes.ELECTRIC_SPARK,
                (air ? AsteriskCraft.WRAITH_ATTACK_AIR : AsteriskCraft.WRAITH_ATTACK_GROUND).get());
    }

    @Override
    public void tick() {
        super.tick();
        if (!this.level().isClientSide()) {
            // Writes synced data only on the ticks the clock actually moved, which is none of them
            // for a Wraith that has never been shot at.
            if (this.cloak.tick()) {
                this.entityData.set(CLOAK_STATE, this.cloak.value());
            }
        }
    }

    @Override
    public boolean causeFallDamage(double distance, float multiplier, DamageSource source) {
        // Gravity is only switched off once the move control has somewhere to fly, so a Wraith
        // produced over a drop would otherwise take fall damage before it ever got airborne.
        return false;
    }

    // --- Persistence ---

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output);
        output.putInt("CloakState", this.cloak.value());
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
        this.cloak.restore(input.getIntOr("CloakState", 0));
        // The synced value is the client's only source for the cloak, so it has to be written here
        // too — a reloaded Wraith that was hidden must come back hidden, not sitting in the open.
        this.entityData.set(CLOAK_STATE, this.cloak.value());
    }

    // --- Sounds ---

    @Override
    public int getAmbientSoundInterval() {
        return 1200; // ~10x the vanilla default (80): ambient barks average ~1/minute, not ~1/6s.
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return AsteriskCraft.WRAITH_AMBIENT.get();
    }

    @Override
    protected SoundEvent getDeathSound() {
        return AsteriskCraft.WRAITH_DEATH.get();
    }
}
