package net.bitflora.asteriskcraft.entity.ai;

import net.bitflora.asteriskcraft.combat.FlameCone;
import net.bitflora.asteriskcraft.faction.FactionAttachments;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * The third of the attack helpers, beside {@link HitscanAttacks} (one target at range) and
 * {@link MeleeAttacks} (one target at arm's length): <b>a wash of fire over everything standing in a
 * cone</b>. As with those two, the attacker names its own damage type, particle and sound, so a kill
 * reads as the weapon that made it.
 *
 * <p><b>It resolves hostility, and that is the point.</b> Candidates are filtered through
 * {@link FactionAttachments#isHostile}, the one choke point — so cloak, garrison and the per-race
 * wild carve-out all apply here with nothing written for them, and a Firebat cannot burn its own
 * side. That is deliberately <em>unlike</em> {@code combat/SuicideBlast}, the mod's one attack that
 * resolves no hostility at all: a detonation is not targeting and catches everyone, while a
 * flamethrower is aimed and does not.
 *
 * <p><b>Buildings are untouched</b>, as they are by every other attack helper. A building's health
 * lives on a {@code building/SiegeTarget} block entity rather than on a {@code LivingEntity}, and
 * {@link SiegeBlockGoal} is how any unit brings one down.
 *
 * <p>The damage source is built with the attacker as its cause — attributed, which is what sets
 * {@code getLastHurtByMob()} and lets {@link RetaliateGoal} fire. Each victim takes the full amount:
 * a flamethrower has no falloff across its spread, which is what makes it the answer to a clump.
 */
public final class FlameAttacks {

    private FlameAttacks() {
    }

    /**
     * Washes {@code shape}'s cone out from {@code attacker} along {@code bearing}, hurting and
     * igniting every hostile living entity standing in it.
     *
     * @param bearing     direction to fire in; flattened onto the horizontal by {@link FlameCone}
     * @param igniteTicks how long a victim burns afterwards. {@code Entity.igniteForTicks} only ever
     *                    raises the counter, so overlapping sweeps top a victim up rather than
     *                    cutting an existing burn short. Fire-immune targets are skipped, matching
     *                    how vanilla's own fire damage gates itself.
     */
    public static void sweep(Mob attacker, Vec3 bearing, FlameCone.Shape shape, double damage,
            int igniteTicks, ResourceKey<DamageType> damageType, ParticleOptions particle,
            SoundEvent sound) {
        if (!(attacker.level() instanceof ServerLevel level)) {
            return;
        }
        // Fired from the chest rather than the eyes: the nozzle is cradled in the unit's folded arms,
        // and a cone launched from eye height washes over the head of anything short.
        Vec3 origin = attacker.position().add(0.0, attacker.getBbHeight() * 0.5, 0.0);

        // One broad-phase query for the whole cone, then the per-slice test. Hostility is resolved in
        // the query's own predicate so a candidate that is cloaked, garrisoned or friendly is never
        // even considered.
        List<LivingEntity> candidates = level.getEntitiesOfClass(LivingEntity.class,
                FlameCone.envelope(origin, bearing, shape),
                e -> e.isAlive() && e != attacker && FactionAttachments.isHostile(attacker, e));

        DamageSource source = level.damageSources().source(damageType, attacker);
        for (LivingEntity candidate : candidates) {
            if (!FlameCone.catches(origin, bearing, shape, candidate.getBoundingBox())) {
                continue;
            }
            if (candidate.hurtServer(level, source, (float) damage)) {
                attacker.setLastHurtMob(candidate);
                if (!candidate.fireImmune()) {
                    candidate.igniteForTicks(igniteTicks);
                }
            }
        }

        flames(level, origin, bearing, shape, particle);
        level.playSound(null, attacker.blockPosition(), sound, SoundSource.HOSTILE, 1.0f, 1.0f);
    }

    /**
     * Draws the cone, as puffs down its axis spread over the local half-width — which is what makes
     * the widening visible. {@link FlameCone#catches} decides what actually burns, but both read the
     * same {@link FlameCone.Shape}, so the fire cannot end up reaching further or opening wider than
     * the volume it represents.
     */
    private static void flames(ServerLevel level, Vec3 origin, Vec3 bearing, FlameCone.Shape shape,
            ParticleOptions particle) {
        for (FlameCone.Slice slice : FlameCone.samples(origin, bearing, shape)) {
            Vec3 center = slice.center();
            level.sendParticles(particle, center.x, center.y, center.z, 4,
                    slice.halfWidth() * 0.5, shape.halfHeight() * 0.25, slice.halfWidth() * 0.5, 0.0);
        }
    }
}
