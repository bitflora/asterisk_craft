package net.bitflora.asteriskcraft.combat;

import net.bitflora.asteriskcraft.building.SiegeTarget;
import net.bitflora.asteriskcraft.faction.FactionAttachments;
import net.bitflora.asteriskcraft.stats.UnitStat;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.ExplosionDamageCalculator;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * One unit blowing itself up — the third way this mod deals damage, alongside
 * {@link net.bitflora.asteriskcraft.entity.ai.HitscanAttacks} (ranged) and
 * {@link net.bitflora.asteriskcraft.entity.ai.MeleeAttacks} (melee), and like both of those it takes
 * the attacker's own damage type rather than owning one. The Infested Villager is its only caller
 * today; a second bomber needs nothing new here.
 *
 * <p>Unlike the fanged attackers, this needs no cancel-and-re-deal dance
 * ({@link FangStrikeDamageHandler}): {@code Level.explode} has an overload taking both a
 * {@code DamageSource} and an {@link ExplosionDamageCalculator}, so the type and the amount are both
 * substitutable up front. The source is built <b>attributed</b> — {@code damageSources().source(key,
 * bomber)} — for the same reason every other attack here is: that is what sets
 * {@code getLastHurtByMob}, so anything that survives the blast fights back.
 *
 * <p>Vanilla's damage curve cannot express these numbers at all. Its formula peaks near 49 at radius
 * 3, and the balance table asks for 250, so {@link #damageAt} replaces it outright: full damage at the
 * centre falling linearly to nothing at the edge, times vanilla's own line-of-sight {@code exposure}
 * so cover still matters. {@code shouldDamageEntity} is deliberately <em>not</em> overridden — this
 * blast is indiscriminate, and friendly Zerg standing in it (including other bombers, which chain)
 * die exactly as an enemy would. That is the one rule in the mod that does not resolve through
 * {@code FactionAttachments.isHostile}, because it is not targeting: nothing is being <em>chosen</em>
 * here, a bomb simply goes off. Hostility still decides everything up to that point — what the unit
 * walked towards, and which buildings it charges below.
 *
 * <p>Buildings are the other half, and they cannot ride on the explosion: a building's health lives on
 * its block entity's {@code BuildingDefense}, not on its blocks, so an explosion that chews the
 * stonework does nothing to the building as a <em>game object</em>. They are charged explicitly
 * through {@link SiegeTarget#damageBuilding}, the same door {@code SiegeBlockGoal} knocks on, so
 * shields, warp-in vulnerability and collapse all behave as they do under any other assault.
 */
public final class SuicideBlast {

    private SuicideBlast() {
    }

    /**
     * Detonates {@code bomber} where it stands. Does not remove it — the caller owns its own death, so
     * that a bomber can play its own effects and discard on its own terms.
     */
    public static void detonate(Mob bomber, ServerLevel level, UnitStat stat,
            ResourceKey<DamageType> damageType) {
        UnitStat.Blast blast = stat.blastOrThrow();
        double damage = stat.attackDamageOrThrow();
        DamageSource source = level.damageSources().source(damageType, bomber);

        chargeBuildings(bomber, level, blast.radius(), (int) damage);

        // MOB rather than NONE: the crater is wanted, and this route is already gated on the
        // mobGriefing gamerule by EventHooks.canEntityGrief. The core blocks of every building carry
        // 1200 blast resistance, so no explosion at this scale can delete one out from under its
        // BuildingDefense and hand GameOutcome a free win.
        level.explode(bomber, source, calculator(damage, blast.radius()),
                bomber.getX(), bomber.getY(), bomber.getZ(),
                blast.radius(), false, Level.ExplosionInteraction.MOB);
    }

    /**
     * Damage dealt to something {@code distance} away, given how much of it the blast could see.
     *
     * <p>Pure, so the curve is testable without a world — the same split {@link BounceChain} makes.
     *
     * <p><b>Flat inside the radius, nothing outside it.</b> A distance falloff was tried and was
     * wrong for this unit: at the edge of a three-block blast it left a Zealot taking about 42
     * against 80 effective HP, so the thing walked out of a detonation it was standing next to. A
     * suicide bomber that can be survived by backing up one block is not a threat, it is a
     * formality — the whole cost of the unit is that it only ever goes off once, and what it buys
     * for that is that everything inside the radius dies.
     *
     * <p><b>The {@code distance > radius} test is load-bearing, not defensive.</b> Vanilla's
     * {@code ServerExplosion.hurtEntities} gathers and considers entities out to <em>twice</em> the
     * radius — it skips one only when {@code sqrt(distSq) / (radius * 2) > 1.0} — and relies on its
     * own curve having tapered to nothing long before that. A flat curve has no such taper, so
     * without this clamp a blast declaring a radius of 3 would quietly deal its full damage out to
     * 6 blocks. See docs/neoforge-api-notes.md.
     *
     * <p>{@code exposure} survives because it is not a distance term at all: vanilla's
     * {@code getSeenPercent} is a grid of rays clipped against solid blocks, so it is purely cover.
     * It is what still makes a wall worth standing behind now that backing away is not.
     */
    public static float damageAt(double baseDamage, float radius, double distance, float exposure) {
        return distance > radius ? 0.0f : (float) baseDamage * exposure;
    }

    private static ExplosionDamageCalculator calculator(double damage, float radius) {
        return new ExplosionDamageCalculator() {
            @Override
            public float getEntityDamageAmount(Explosion explosion, Entity entity, float exposure) {
                Vec3 centre = explosion.center();
                return damageAt(damage, radius, Math.sqrt(entity.distanceToSqr(centre)), exposure);
            }
        };
    }

    /**
     * Charges every enemy building whose core block sits inside the blast, once each. Faction-generic:
     * it asks {@link SiegeTarget#buildingFaction()} and never names a Nexus, Gateway or Hive.
     */
    private static void chargeBuildings(Mob bomber, ServerLevel level, float radius, int damage) {
        int reach = Mth.ceil(radius);
        BlockPos origin = bomber.blockPosition();
        for (BlockPos pos : BlockPos.betweenClosed(
                origin.offset(-reach, -reach, -reach), origin.offset(reach, reach, reach))) {
            if (!(level.getBlockEntity(pos) instanceof SiegeTarget target)) {
                continue;
            }
            if (!FactionAttachments.get(bomber).isEnemy(target.buildingFaction())) {
                continue;
            }
            target.damageBuilding(damage, level, pos.immutable());
        }
    }
}
