package net.bitflora.asteriskcraft.entity.terran;

import net.bitflora.asteriskcraft.building.ConstructionSite;
import net.bitflora.asteriskcraft.building.PrePlaced;
import net.bitflora.asteriskcraft.building.SpawnSpots;
import net.bitflora.asteriskcraft.building.WarpInVulnerability;
import net.bitflora.asteriskcraft.entity.Organic;
import net.bitflora.asteriskcraft.entity.Rooted;
import net.bitflora.asteriskcraft.faction.FactionAttachments;
import net.bitflora.asteriskcraft.faction.Garrison;
import net.bitflora.asteriskcraft.stats.UnitAttributes;
import net.bitflora.asteriskcraft.stats.UnitStats;
import net.minecraft.core.BlockPos;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * The Terran Bunker: a rooted structure with no weapon at all, whose entire contribution is the four
 * units it can hold. It is the Terran answer to the Photon Cannon and the Sunken Colony, and it is
 * deliberately a different <em>kind</em> of answer — a Cannon <em>is</em> a gun, while a Bunker is a
 * container for guns the player already owns.
 *
 * <p>Like {@code entity.protoss.PhotonCannonEntity} it is a {@link Mob} rather than a block entity,
 * for the reason stated there: being a {@link LivingEntity} it reuses the whole combat stack — HP as
 * an attribute, and automatic retaliation, since attacking units acquire and hit it back through the
 * ordinary {@code RetaliateGoal}/{@code FactionTargetGoal} path. It registers <b>no goals</b>: with
 * nothing to shoot and nowhere to walk, a Bunker has no behaviour but standing there being hit.
 *
 * <p><b>The garrison is vanilla passengers, and most of it is free.</b> A riding {@code Mob} runs its
 * full goal and target selectors ({@code Mob.serverAiStep} has no passenger check, and
 * {@code LivingEntity.isImmobile} is {@code isDeadOrDying()} and nothing else), so a Marine inside
 * acquires and fires with no code written for it. Passengers are persisted nested under the
 * vehicle's own save data and re-seated on load, so a loaded Bunker comes back loaded. And
 * {@code Entity.setRemoved} dismounts everyone, so a destroyed Bunker spills its garrison without
 * being asked — {@link #getDismountLocationForPassenger} is only there to say <em>where</em>.
 *
 * <p><b>Three things are not free</b>, and two of them are handled here. {@link #getControllingPassenger}
 * must return null: by default the first {@code Mob} passenger becomes the vehicle's <em>driver</em>,
 * which switches off the vehicle's own goal flags and re-routes the rider's navigation to the
 * vehicle, so a garrisoned Marine's {@code RangedAttackGoal} would try to steer the Bunker at its
 * target. And {@link #canAddPassenger} must cap the list, since vanilla's default is one rider. The
 * third — that riders stay targetable and hittable — is not an entity concern at all and lives at
 * the choke points: {@code FactionAttachments.isHostile}, {@code combat.TargetRetentionHandler} and
 * {@code combat.GarrisonDamageHandler}.
 *
 * <p>Its numbers live in {@link UnitStats#BUNKER} — not here. The construction countdown does live
 * here, exactly as the Photon Cannon's warp-in does: it is a building mechanic rather than a combat
 * stat. Half HP throughout ({@link WarpInVulnerability}), scaled back up on completion, so damage
 * landed on a half-built Bunker is sustained twice over.
 */
public class BunkerEntity extends Mob implements Rooted, Garrison, PrePlaced {
    /** Four units, the StarCraft capacity. */
    public static final int CAPACITY = 4;

    /**
     * A firing slit is worth one block of reach. Small on purpose: it is the difference between a
     * Marine in cover and a Marine standing beside it, not a weapon upgrade.
     */
    public static final float RANGE_BONUS = 1.0f;

    /** 30 seconds to put up, three times a Photon Cannon's warp. */
    public static final int BUILD_TICKS = 600;

    /** How close a boarding unit has to get before it can climb in. */
    public static final double BOARDING_REACH = 2.5;

    // Synced for the same reason the Photon Cannon's warp counter is: the renderer reads it, and a
    // plain field is never restored client-side, so every Bunker on screen would look half-built.
    private static final EntityDataAccessor<Integer> BUILD_TICKS_REMAINING =
            SynchedEntityData.defineId(BunkerEntity.class, EntityDataSerializers.INT);

    /** The SCV welding it together. Empty for one world generation stamped, which builds itself. */
    private final ConstructionSite site = new ConstructionSite();

    public BunkerEntity(EntityType<? extends BunkerEntity> type, Level level) {
        super(type, level);
        this.setPersistenceRequired();
        // A fresh Bunker starts building, so it starts on the halved pool. (Loading from disk
        // overwrites this with the saved health afterwards, half-built or not.)
        this.setHealth(WarpInVulnerability.warpPool(this.getMaxHealth()));
    }

    public static AttributeSupplier.Builder createAttributes() {
        return UnitAttributes.apply(Mob.createMobAttributes(), UnitStats.BUNKER);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(BUILD_TICKS_REMAINING, BUILD_TICKS);
    }

    @Override
    protected void registerGoals() {
        // Deliberately none. A Bunker has no gun and cannot move; everything it does to an enemy is
        // done by whatever is riding it, out of that unit's own goals.
    }

    // --- Construction ---

    public boolean isUnderConstruction() {
        return this.buildTicksRemaining() > 0;
    }

    private int buildTicksRemaining() {
        return this.entityData.get(BUILD_TICKS_REMAINING);
    }

    /** How far along the build is: 0 on the first tick, 1 once it is standing. Read by the renderer. */
    public float buildProgress() {
        return 1.0f - (float) this.buildTicksRemaining() / BUILD_TICKS;
    }

    @Override
    public ConstructionSite constructionSite() {
        return this.site;
    }

    @Override
    public void tick() {
        super.tick();
        int buildTicks = this.buildTicksRemaining();
        if (this.level() instanceof ServerLevel level && buildTicks > 0) {
            switch (this.site.tick(level, this.position())) {
                case ABANDONED -> {
                    ConstructionSite.razeUnbuilt(this, level);
                    return;
                }
                // Nothing has been built yet: the SCV is still on its way over.
                case WAITING -> {
                    return;
                }
                case BUILDING -> {
                }
            }
            this.entityData.set(BUILD_TICKS_REMAINING, buildTicks - 1);
            ConstructionSite.plume(level, this, 4, 0.6, 0.5);
            if (buildTicks == 1) {
                this.finishBuild(level);
                this.site.release(level, this.blockPosition());
            }
        }
    }

    /**
     * Lets the builder go if this Bunker is destroyed before it was finished — otherwise the SCV
     * would stand welding a hole in the ground for the rest of the match.
     */
    @Override
    public void remove(RemovalReason reason) {
        if (this.level() instanceof ServerLevel level) {
            this.site.release(level, this.blockPosition());
        }
        super.remove(reason);
    }

    /**
     * Scales the halved construction pool back up. {@link #isUnderConstruction()} is already false by
     * now, so the setter clamps against the full maximum — and because it is what <em>survived</em>
     * that gets doubled, whatever damage the Bunker took while going up is sustained twice over.
     */
    private void finishBuild(ServerLevel level) {
        this.setHealth(WarpInVulnerability.onWarpComplete(this.getHealth()));
        level.playSound(null, this.blockPosition(), SoundEvents.ANVIL_LAND, SoundSource.HOSTILE, 0.8f, 0.6f);
    }

    /**
     * Stands the Bunker up at once, at full HP. The inverse of the constructor's halved pool, and the
     * exact counterpart of {@code BaseBlockEntity.skipWarpIn}: a Bunker placed by world generation was
     * never built by anyone, so it must not open the match half-built and refusing its own garrison.
     */
    @Override
    public void skipConstruction() {
        if (this.isUnderConstruction()) {
            this.entityData.set(BUILD_TICKS_REMAINING, 0);
            this.setHealth(WarpInVulnerability.onWarpComplete(this.getHealth()));
        }
    }

    // --- Garrison ---

    @Override
    public int capacity() {
        return CAPACITY;
    }

    @Override
    public float rangeBonus() {
        return RANGE_BONUS;
    }

    /** How many units are inside. Answerable on the client too — vanilla syncs the passenger link. */
    public int garrisonSize() {
        return this.getPassengers().size();
    }

    /**
     * Whether {@code unit} may climb in right now. The whole boarding rule, in one place: it has to
     * be alive, on this Bunker's side, {@link Organic} (which is what makes it Terran infantry
     * without anything here naming a race), not already riding something, and there has to be room
     * in a Bunker that has finished going up.
     */
    public boolean boardable(Mob unit) {
        return unit.isAlive()
                && unit != this
                && Organic.isOrganic(unit)
                && !unit.isPassenger()
                && FactionAttachments.get(unit) == FactionAttachments.get(this)
                && this.canAddPassenger(unit);
    }

    /** Puts {@code unit} inside, if it may go. Returns whether it did. */
    public boolean board(Mob unit) {
        return this.boardable(unit) && unit.startRiding(this);
    }

    /** Turns everyone out, and hands back the units that were inside so a caller can order them. */
    public List<Mob> unload() {
        List<Mob> ejected = new ArrayList<>();
        for (Entity passenger : List.copyOf(this.getPassengers())) {
            if (passenger instanceof Mob mob) {
                ejected.add(mob);
            }
            passenger.stopRiding();
        }
        return ejected;
    }

    @Override
    protected boolean canAddPassenger(Entity passenger) {
        return this.getPassengers().size() < CAPACITY && !this.isUnderConstruction();
    }

    /**
     * Nobody drives a building. Vanilla's default hands the wheel to the first {@code Mob} passenger
     * — which would switch this Bunker's own goal flags off and, worse, re-route the rider's
     * {@code getNavigation()}/{@code getMoveControl()} onto the Bunker, so a garrisoned Marine's
     * {@code RangedAttackGoal} would spend its life trying to walk a building at its target.
     */
    @Override
    public @Nullable LivingEntity getControllingPassenger() {
        return null;
    }

    /**
     * Where a unit stands when it comes out — whether it was ordered out or the Bunker was killed out
     * from under it. Vanilla's default is the vehicle's roof, which for a building means the garrison
     * ends up standing on top of it.
     *
     * <p>{@code SpawnSpots} is deterministic, so several units leaving at once are handed the same
     * block; that is fine and is its documented behaviour, since mobile units shove each other apart
     * within a tick. It is also why {@link Rooted} matters here: a Bunker's own square counts as
     * taken, so nobody is placed inside the wall.
     */
    @Override
    public Vec3 getDismountLocationForPassenger(LivingEntity passenger) {
        if (this.level() instanceof ServerLevel level) {
            BlockPos spot = SpawnSpots.findGroundSpot(level, this.blockPosition(), passenger.getType());
            return new Vec3(spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5);
        }
        return super.getDismountLocationForPassenger(passenger);
    }

    // --- Immovable structure ---

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output);
        output.putInt("BuildTicks", this.buildTicksRemaining());
        this.site.save(output);
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
        this.entityData.set(BUILD_TICKS_REMAINING, input.getIntOr("BuildTicks", 0));
        this.site.load(input);
    }
}
