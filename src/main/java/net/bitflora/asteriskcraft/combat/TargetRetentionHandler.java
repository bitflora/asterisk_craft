package net.bitflora.asteriskcraft.combat;

import net.bitflora.asteriskcraft.AsteriskCraft;
import net.bitflora.asteriskcraft.faction.Cloaked;
import net.bitflora.asteriskcraft.faction.Cloaking;
import net.bitflora.asteriskcraft.faction.FactionAttachments;
import net.bitflora.asteriskcraft.faction.Garrison;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingChangeTargetEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;

/**
 * Keeps an attacker from holding on to a target that has stopped being engageable — the
 * <em>retention</em> half of the two live-entity rules {@code FactionAttachments.isHostile} enforces
 * on acquisition: a unit that has disappeared behind a cloak, and a unit that has climbed inside a
 * {@link net.bitflora.asteriskcraft.faction.Garrison}.
 *
 * <p><b>Why acquisition alone isn't enough.</b> Verified against the decompiled 26.1.2 source:
 * {@code TargetGoal.canContinueToUse} — which every vanilla target goal inherits, ours included —
 * re-checks only {@code canAttack}, scoreboard teams, follow distance and line of sight. It never
 * consults the {@code TargetingConditions.Selector} that {@code canUse} acquired through, so the
 * cloak gate is asked once, when the target is first picked, and never again. A unit already being
 * attacked when its cloak comes up keeps being attacked, indefinitely.
 *
 * <p>That hole was invisible until the Lurker. The Dark Templar is cloaked permanently, so it can
 * never be acquired in the first place and there is never a target to hold on to; the Lurker is the
 * first unit whose cloak comes up <em>after</em> acquisition, which is exactly the case the vanilla
 * path doesn't handle. The Bunker is the second instance of the same shape and the reason this class
 * is named for the shape rather than for cloak: a Marine that boards while it is being shot at has
 * exactly the Lurker's problem, and needed exactly the Lurker's fix.
 *
 * <p><b>It takes two listeners, because there are two ways a target survives.</b>
 *
 * <ul>
 *   <li>{@link #onChangeTarget} rewrites an about-to-be-set target to null. This is the one that does
 *       the real work: {@code TargetGoal.canContinueToUse} re-asserts its target through
 *       {@code Mob.setTarget} on <em>every</em> tick it runs, so vetoing there empties the target
 *       every tick and every attack goal reading {@code getTarget()} stands down. It is also the only
 *       thing that beats {@code HurtByTargetGoal}, which is the one vanilla goal that caches the
 *       victim in {@code TargetGoal.targetMob} and so can resurrect a target cleared from outside —
 *       relevant because a Lurker shoots wild hostiles, and they hit back.</li>
 *   <li>{@link #onEntityTick} clears a target that is already set and that nothing re-asserts. Our own
 *       {@code entity.ai.RetaliateGoal} sets its target once in {@code start()} and never again, so
 *       the veto alone would never fire for it.</li>
 * </ul>
 *
 * <p>The veto <b>rewrites to null rather than cancelling</b>. Cancelling a
 * {@link LivingChangeTargetEvent} means "leave the target as it was", which for a mob already locked
 * on would preserve exactly the thing being fixed.
 *
 * <p><b>Deliberately scoped to these two rules, not to hostility in general.</b> The tempting version
 * of this — "drop any target {@code isHostile} would no longer allow" — quietly breaks the rest of
 * the world: a vanilla zombie chasing the player is a NEUTRAL attacker on a target
 * {@code isHostile} says nothing useful about, and the sweep would strip its target every tick and
 * leave zombies unable to attack anyone. So the guard stays narrow, and a target that is neither
 * {@link Cloaked} nor riding a {@link net.bitflora.asteriskcraft.faction.Garrison} is never touched.
 *
 * <p>Both listeners run for every mob, not just the mod's own units, because a cloak — and a bunker
 * wall — has to work against everything that can hold a grudge, including the wild hostiles
 * {@code isHostile} deliberately lets our units fight.
 *
 * <p><b>One residual, and it is deliberate.</b> A mob whose grudge came from {@code HurtByTargetGoal}
 * keeps that goal <em>running</em> after the veto empties its target, because that goal's
 * {@code canContinueToUse} is satisfied by its own cached {@code targetMob} and only gives up on
 * follow distance or 300 ticks without line of sight. So a wild hostile a Lurker shot at will stand
 * there attacking nothing for a few seconds rather than immediately picking a new fight. It cannot be
 * fixed from outside without reaching into a private goal field, it self-heals, and it is strictly
 * the lesser of the two behaviours — the alternative is the cloak simply not working.
 */
@EventBusSubscriber(modid = AsteriskCraft.MODID)
public final class TargetRetentionHandler {

    private TargetRetentionHandler() {
    }

    @SubscribeEvent
    public static void onChangeTarget(LivingChangeTargetEvent event) {
        LivingEntity target = event.getNewAboutToBeSetTarget();
        if (target == null || !hidden(event.getEntity(), target)) {
            return;
        }
        event.setNewAboutToBeSetTarget(null);
    }

    @SubscribeEvent
    public static void onEntityTick(EntityTickEvent.Post event) {
        if (event.getEntity().level().isClientSide() || !(event.getEntity() instanceof Mob mob)) {
            return;
        }
        LivingEntity target = mob.getTarget();
        if (target == null || !hidden(mob, target)) {
            return;
        }
        mob.setTarget(null);
    }

    /**
     * Whether {@code target} has stopped being engageable by {@code viewer}'s faction: either it is
     * cloaked and no detector of that faction currently reveals it, or it is sheltered inside a
     * garrison. Both early-outs are a single {@code instanceof} on the overwhelmingly common case, so
     * this costs almost nothing for every entity in the world that isn't part of either mechanic.
     *
     * <p>Written out rather than delegated to {@code FactionAttachments.isEngageable} on purpose:
     * that helper is the *acquisition* pair and answers "may this be fought", which is true of an
     * uncloaked, unsheltered entity of any faction — including the player a zombie is chasing. Here
     * the question has to be the narrower "was this taken away from an attacker that already had
     * it", so the two conditions are guarded by their own markers first.
     */
    private static boolean hidden(LivingEntity viewer, LivingEntity target) {
        if (Cloaked.isCloaked(target)) {
            return !Cloaking.isVisibleTo(target, FactionAttachments.get(viewer));
        }
        return Garrison.isGarrisoned(target);
    }
}
