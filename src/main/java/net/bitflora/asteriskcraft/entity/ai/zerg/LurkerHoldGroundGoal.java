package net.bitflora.asteriskcraft.entity.ai.zerg;

import net.bitflora.asteriskcraft.entity.zerg.LurkerEntity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;

import java.util.EnumSet;
import org.jetbrains.annotations.Nullable;

/**
 * Keeps a Lurker shooting what it can already reach instead of digging out to chase what it cannot.
 *
 * <p>Without it the unit is at the mercy of whatever last hit it: {@link
 * net.bitflora.asteriskcraft.entity.ai.RetaliateGoal} pins the target on an attacker anywhere inside
 * follow range, {@link LurkerApproachGoal} sees a target it cannot hit from here and takes the
 * {@code MOVE} flag, and a Lurker in the middle of a working ambush climbs out of the ground — losing
 * its cloak, its immobility and six seconds — to walk at a Hydralisk plinking it from across the
 * field, past the Zealots it was happily killing. A weapon that only works while buried must not be
 * talked out of being buried.
 *
 * <p>So this sits <em>above</em> retaliation in the target selector and holds the {@code TARGET} flag
 * for as long as anything strikeable stands in reach: while it runs, nothing else can retarget the
 * unit at all. Being shot from out of range is not ignored — it simply waits, and the moment the near
 * fight is over the goal lapses and retaliation acquires the sniper on the next tick.
 *
 * <p><b>The claim must never outlive the reach, and that is the whole of {@link #tick()}.</b> While
 * this goal runs nothing else can retarget the unit — which makes it responsible for the target being
 * a <em>shootable</em> one every tick, not just at the moment it claimed one. An earlier version held
 * its target out to a grace distance past the unit's range, hysteresis meant to stop the claim flapping
 * against retaliation at the range edge, and it broke the unit outright: a target drifting a block
 * outside the spines stayed claimed and unshootable while the goal went on blocking acquisition of the
 * enemy standing next to the Lurker. So a stale claim is re-picked rather than held, the goal keeps
 * running while <em>anything</em> is in reach, and the flapping it was guarding against cannot arise —
 * a claim is only ever dropped for another target already in range, or because there is nothing left
 * down here to shoot at all.
 *
 * <p>It also re-asserts a still-good claim each tick, the way vanilla's
 * {@code NearestAttackableTargetGoal} does: holding the flag stops other <em>goals</em> retargeting the
 * unit, but {@code setTarget} is public and the target selector is not its only caller. It re-asserts
 * the target it claimed rather than a freshly chosen nearest, because swapping between two enemies a
 * block apart would restart the volley cadence forever.
 *
 * <p>The reach question itself, and the deference to a player's order, live in {@link LurkerReach} —
 * shared with {@link LurkerApproachGoal}, which refuses to dig on the same answer.
 */
public class LurkerHoldGroundGoal extends Goal {
    private final LurkerEntity lurker;
    /** The enemy this goal is holding the unit on. Its own field, so {@link #tick()} can restate it. */
    @Nullable
    private LivingEntity claimed;

    public LurkerHoldGroundGoal(LurkerEntity lurker) {
        this.lurker = lurker;
        this.setFlags(EnumSet.of(Flag.TARGET));
    }

    @Override
    public boolean canUse() {
        return LurkerReach.holdingGround(this.lurker);
    }

    @Override
    public void start() {
        this.claimed = LurkerReach.nearestInReach(this.lurker);
        this.lurker.setTarget(this.claimed);
    }

    @Override
    public void stop() {
        this.claimed = null;
    }

    /**
     * Runs on the same question it started on — is anything in reach — rather than on the fate of the
     * particular enemy it claimed. Which one that is, is {@link #tick()}'s business.
     */
    @Override
    public boolean canContinueToUse() {
        return LurkerReach.holdingGround(this.lurker);
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public void tick() {
        if (!shootable(this.claimed)) {
            this.claimed = LurkerReach.nearestInReach(this.lurker);
        }
        if (this.claimed != null && this.lurker.getTarget() != this.claimed) {
            this.lurker.setTarget(this.claimed); // fresh claim, or something else moved it
        }
    }

    /** Whether the claim is still one the spines can land on — the same test the attack goal makes. */
    private boolean shootable(@Nullable LivingEntity target) {
        return target != null
                && LurkerReach.strikeable(this.lurker, target)
                && LurkerReach.within(this.lurker, target, LurkerReach.RANGE);
    }
}
