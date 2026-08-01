package net.bitflora.asteriskcraft.entity.ai;

import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.Vec3;

/**
 * The melee counterpart to {@link HitscanAttacks}: one swing, dealt with the attacker's own damage
 * type from {@link net.bitflora.asteriskcraft.combat.AsteriskCraftDamageTypes}. Melee units call this
 * from their {@code doHurtTarget} override so a Zealot kill reads as psi blades rather than the
 * generic {@code minecraft:mob_attack} vanilla would otherwise pick.
 *
 * <p>This reimplements {@code Mob.doHurtTarget} rather than wrapping it because vanilla builds its
 * damage source inline — {@code weaponItem.getDamageSource(this, () -> damageSources().mobAttack(this))}
 * — with no seam to substitute a type. Two of vanilla's steps are dropped, both verified no-ops here
 * (see docs/neoforge-api-notes.md):
 *
 * <ul>
 *   <li>the weapon/enchantment branches — this mod's units carry no main-hand item, only a dyed
 *       leather chestplate for team colour, so {@code getWeaponItem()} is always empty;</li>
 *   <li>{@code playAttackSound()} — an empty method on {@code LivingEntity} that neither melee unit
 *       overrides.</li>
 * </ul>
 *
 * <p>{@code causeExtraKnockback} is kept but is passed 0: it reads {@code ATTACK_KNOCKBACK}, which
 * {@code stats/UnitAttributes} never registers, so vanilla was already passing 0 here. This is the
 * <em>bonus</em> knockback only — the base 0.4 shove every hit now applies comes from
 * {@code LivingEntity.hurtServer}, because these damage types are outside
 * {@code #minecraft:no_knockback}.
 */
public final class MeleeAttacks {

    private MeleeAttacks() {
    }

    /**
     * Drop-in body for a {@code Mob.doHurtTarget(ServerLevel, Entity)} override.
     *
     * @return whether the target was actually hurt, which is what the overridden method must return
     */
    public static boolean doHurtTarget(Mob attacker, ServerLevel level, Entity target,
            ResourceKey<DamageType> damageType) {
        DamageSource source = level.damageSources().source(damageType, attacker);
        float damage = (float) attacker.getAttributeValue(Attributes.ATTACK_DAMAGE);
        // Captured before the hit: causeExtraKnockback compares against the movement the target had
        // before hurtServer's own knockback was folded in.
        Vec3 oldMovement = target.getDeltaMovement();

        boolean hurt = target.hurtServer(level, source, damage);
        if (hurt) {
            attacker.causeExtraKnockback(target, 0.0f, oldMovement);
            attacker.setLastHurtMob(target);
        }
        attacker.postPiercingAttack();
        return hurt;
    }
}
