package net.bitflora.asteriskcraft.entity.zerg;

import net.bitflora.asteriskcraft.AsteriskCraft;
import net.bitflora.asteriskcraft.combat.AsteriskCraftDamageTypes;
import net.bitflora.asteriskcraft.combat.SuicideBlast;
import net.bitflora.asteriskcraft.entity.ai.CommandableGoals;
import net.bitflora.asteriskcraft.entity.ai.FactionTargetGoal;
import net.bitflora.asteriskcraft.entity.ai.RetaliateGoal;
import net.bitflora.asteriskcraft.entity.ai.SiegeBlockGoal;
import net.bitflora.asteriskcraft.entity.ai.StuckWanderGoal;
import net.bitflora.asteriskcraft.entity.ai.zerg.InfestedSwellGoal;
import net.bitflora.asteriskcraft.faction.FactionAttachments;
import net.bitflora.asteriskcraft.stats.UnitAttributes;
import net.bitflora.asteriskcraft.stats.UnitStat;
import net.bitflora.asteriskcraft.stats.UnitStats;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.gameevent.GameEvent;

/**
 * What a villager gets back up as. Raised only by {@code combat.InfestationHandler} when a Zerg kill
 * lands on one — never trained, which is why {@link UnitStats#INFESTED_VILLAGER} costs nothing — and
 * pointed at the nearest Protoss core the moment it stands up.
 *
 * <p>It is the roster's first <b>suicide</b> unit. It has no swing: {@link #doHurtTarget} deals
 * nothing and exists only so {@code MeleeAttackGoal} has something to call while it closes the gap.
 * Its whole output is one detonation ({@link SuicideBlast}), and the fuse is reimplemented here rather
 * than inherited, because vanilla's {@code SwellGoal} takes a {@code Creeper} in its constructor — the
 * same trap {@code ZombieAttackGoal} carries — and extending {@code Creeper} would drag in powered
 * state, lightning conversion, flint-and-steel priming and lingering effect clouds, none of which this
 * unit wants. What is kept is the <em>shape</em>: a synced swell direction, a server-side counter, and
 * {@link #getSwelling} for the renderer to bulge and flash against.
 *
 * <p><b>Its targeting is the point of the unit, and it is deliberately asymmetric.</b> Acquisition is
 * narrowed to {@link FactionAttachments#areEnemies} — strict cross-faction, which is exactly "units
 * allied with the player" once you know {@code GameBootstrap} tags the player PROTOSS on every login.
 * That subtracts the villagers and golems {@code Race.attacksWild(CIVILIAN)} would otherwise hand
 * any Zerg unit, so unlike the rest of the swarm this one walks past the village it was born in.
 * Retaliation is left <em>un</em>narrowed, which is how "unless attacked" is implemented. That is a
 * deliberate departure from the rule that a unit narrowing one narrows both (see CLAUDE.md): that rule
 * exists for units narrowed by <em>capability</em> — a Spore Colony pinned by a ground attacker it can
 * never reach stops fighting the flyers it can — and this unit's narrowing is <em>doctrine</em>. It
 * can blow up an iron golem perfectly well; it just does not go looking for one.
 *
 * <p>Its numbers live in {@link UnitStats#INFESTED_VILLAGER} — not here.
 */
public class InfestedVillagerEntity extends Monster {
    private static final UnitStat STAT = UnitStats.INFESTED_VILLAGER;

    /**
     * Which way the fuse is running: +1 burning down, -1 backing off. Synced rather than derived
     * because the client needs it to size and flash the model, and it is the <em>direction</em> that
     * is synced (as vanilla does) so the count itself stays server-side and costs no traffic.
     */
    private static final EntityDataAccessor<Integer> DATA_SWELL_DIR =
            SynchedEntityData.defineId(InfestedVillagerEntity.class, EntityDataSerializers.INT);

    private int swell;
    private int oldSwell;

    public InfestedVillagerEntity(EntityType<? extends InfestedVillagerEntity> type, Level level) {
        super(type, level);
        this.setPersistenceRequired();
    }

    public static AttributeSupplier.Builder createAttributes() {
        return UnitAttributes.apply(Monster.createMonsterAttributes(), UnitStats.INFESTED_VILLAGER);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder entityData) {
        super.defineSynchedData(entityData);
        entityData.define(DATA_SWELL_DIR, -1);
    }

    @Override
    protected void registerGoals() {
        // Above everything, StuckWanderGoal included: a lit fuse must not be preemptable. The goal
        // holds MOVE, and GoalSelector only hands a held flag to a strictly lower priority number, so
        // nothing below can walk an armed bomber off its target.
        this.goalSelector.addGoal(-2, new InfestedSwellGoal(this));
        this.goalSelector.addGoal(-1, new StuckWanderGoal(this));
        this.goalSelector.addGoal(0, new FloatGoal(this));
        // Kept for its dig branch: a bomber marched across broken ground has to get through a lip of
        // terrain to reach anything. Its assault branch is near-vacuous here — the swell goal arms on
        // the same buildings from a strictly higher priority, so it detonates rather than punching.
        this.goalSelector.addGoal(0, new SiegeBlockGoal(this));
        this.goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.0, false));
        this.goalSelector.addGoal(8, new LookAtPlayerGoal(this, Player.class, 8.0f));
        this.goalSelector.addGoal(8, new RandomLookAroundGoal(this));
        // Unfiltered on purpose — see the class javadoc. This is what "unless attacked" means.
        this.targetSelector.addGoal(-1, new RetaliateGoal(this));
        this.targetSelector.addGoal(1,
                new FactionTargetGoal(this, candidate -> FactionAttachments.areEnemies(this, candidate)));
        CommandableGoals.install(this, this.goalSelector, this.targetSelector);
    }

    @Override
    public void tick() {
        if (this.isAlive()) {
            this.oldSwell = this.swell;
            int dir = this.getSwellDir();
            if (dir > 0 && this.swell == 0) {
                this.playSound(SoundEvents.CREEPER_PRIMED, 1.0f, 0.5f);
                this.gameEvent(GameEvent.PRIME_FUSE);
            }
            this.swell = Math.max(0, this.swell + dir);
            if (this.swell >= this.fuseTicks()) {
                this.swell = this.fuseTicks();
                this.detonate();
            }
        }
        super.tick();
    }

    private void detonate() {
        if (!(this.level() instanceof ServerLevel level)) {
            return;
        }
        // Marked dead before the blast so its own explosion cannot re-enter this through a damage
        // path, and discarded rather than killed: the unit is spent, not slain, and a death message
        // for a bomb that went off exactly as designed would be noise.
        this.dead = true;
        SuicideBlast.detonate(this, level, STAT, AsteriskCraftDamageTypes.INFESTED_BLAST);
        this.triggerOnDeathMobEffects(level, Entity.RemovalReason.KILLED);
        this.discard();
    }

    /** Ticks from arming to detonation. */
    public int fuseTicks() {
        return STAT.blastOrThrow().fuseTicks();
    }

    public int getSwellDir() {
        return this.entityData.get(DATA_SWELL_DIR);
    }

    public void setSwellDir(int dir) {
        this.entityData.set(DATA_SWELL_DIR, dir);
    }

    /** 0 (idle) to 1 (about to go), interpolated — what the renderer bulges and flashes against. */
    public float getSwelling(float partialTicks) {
        return Mth.lerp(partialTicks, this.oldSwell, this.swell) / (this.fuseTicks() - 2);
    }

    /**
     * No swing. {@code MeleeAttackGoal} exists on this unit only to close the distance, and vanilla's
     * {@code Creeper} returns true here for the same reason: reporting a miss would have the goal back
     * off and re-approach forever.
     */
    @Override
    public boolean doHurtTarget(ServerLevel level, Entity target) {
        return true;
    }

    @Override
    public int getAmbientSoundInterval() {
        return 1200; // ~10x the vanilla default, as every unit in this mod does.
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return AsteriskCraft.INFESTED_VILLAGER_AMBIENT.get();
    }
}
