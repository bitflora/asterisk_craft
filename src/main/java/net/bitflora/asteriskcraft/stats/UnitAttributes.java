package net.bitflora.asteriskcraft.stats;

import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;

/**
 * Turns a {@link UnitStat} into registered attributes. The single place attributes get set from
 * the stats table — every entity's {@code createAttributes()} should be a one-line call here.
 *
 * <p>The base builder is the caller's, because which one to use is a Java-class fact, not a
 * balance number: {@code Monster.createMonsterAttributes()} for the combat units,
 * {@code Mob.createMobAttributes()} for the Photon Cannon, {@code PathfinderMob.createMobAttributes()}
 * for the two workers.
 *
 * <p>Every attribute this mod tunes is set <em>explicitly</em>, never left to a vanilla default,
 * because the defaults are wrong for RTS units: {@code MOVEMENT_SPEED} defaults to 0.7, and
 * {@code Mob.createMobAttributes()} pins {@code FOLLOW_RANGE} at 16 even though the attribute's own
 * default is 32 (see docs/neoforge-api-notes.md). Re-adding an attribute the base builder already
 * added is safe — {@code AttributeSupplier.Builder.add} keys a {@code HashMap}, so the later value
 * wins.
 *
 * <p>An empty {@link UnitStat#attackDamage()} does not <em>remove</em> {@code ATTACK_DAMAGE}: a
 * {@code Monster} base already registered it at its 2.0 default. Empty only means this mod doesn't
 * tune it — true of the two workers, which are {@code PathfinderMob}s and so never had it to begin
 * with.
 */
public final class UnitAttributes {

    private UnitAttributes() {
    }

    public static AttributeSupplier.Builder apply(AttributeSupplier.Builder base, UnitStat stat) {
        base.add(Attributes.MAX_HEALTH, stat.maxHealth())
                .add(Attributes.ARMOR, stat.armor())
                .add(Attributes.MOVEMENT_SPEED, stat.movementSpeed())
                .add(Attributes.KNOCKBACK_RESISTANCE, stat.knockbackResistance())
                .add(Attributes.FOLLOW_RANGE, stat.followRange());
        stat.attackDamage().ifPresent(damage -> base.add(Attributes.ATTACK_DAMAGE, damage));
        stat.flight().ifPresent(flight -> base.add(Attributes.FLYING_SPEED, flight.flyingSpeed()));
        return base;
    }
}
