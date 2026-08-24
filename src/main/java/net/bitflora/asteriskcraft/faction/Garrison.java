package net.bitflora.asteriskcraft.faction;

import net.minecraft.world.entity.Entity;

/**
 * Marks a unit that <em>shelters its passengers</em>: the Terran Bunker, and anything later that
 * holds units the same way. A garrisoned unit cannot be acquired, retaliated against, splashed or
 * hurt at all — everything aimed at it has to go through the shell instead.
 *
 * <p>The marker is on the <b>vehicle</b>; {@link #isGarrisoned(Entity)} is the runtime question
 * asked about a <em>candidate</em>. That is the {@link Cloaked}/{@link Cloaking} split folded into
 * one interface, and it is the same shape for the same reason: consumers never name a concrete
 * entity class, so a second transport is one {@code implements}.
 *
 * <p><b>Why this sits in {@code faction} rather than beside {@code entity.Rooted} and
 * {@code entity.Organic}:</b> the shelter gate has to be inside
 * {@link FactionAttachments#isHostile(Entity, Entity)}, the single choke point every targeting path
 * already goes through — and {@code faction} is deliberately a leaf package (nothing in it imports
 * another of the mod's packages), which is what keeps that choke point free of layering cycles.
 * {@link Cloaked} lives here for exactly this reason and this is the second case of it. Nothing
 * about sheltering needs the {@code entity} package.
 *
 * <p><b>The gate is only half of it.</b> A {@code TargetingConditions.Selector} is consulted once,
 * when a target is acquired, and never again — so a unit that boards <em>while</em> something has
 * it targeted stays targeted forever unless the held target is cleared from outside. That is
 * {@code combat.TargetRetentionHandler}, shared with the Lurker's mid-fight cloak. And because
 * hostility is not the only way damage arrives (a suicide blast resolves none at all, and a
 * vehicle propagates its fall to its riders), {@code combat.GarrisonDamageHandler} is the floor
 * underneath both.
 */
public interface Garrison {
    /** How many units may be inside at once. */
    int capacity();

    /**
     * Extra attack range handed to whatever is shooting from inside, in blocks. A firing slit lets a
     * rifle reach a little further than the soldier holding it could standing in the open, which is
     * the whole reason to be in there rather than beside it. Defaults to none, so a transport that
     * is only a transport opts out by saying nothing.
     */
    default float rangeBonus() {
        return 0.0f;
    }

    /** Whether {@code entity} is currently sheltered inside a garrison. */
    static boolean isGarrisoned(Entity entity) {
        return entity.getVehicle() instanceof Garrison;
    }

    /** The range bonus {@code entity} currently enjoys, or zero if it is not garrisoned. */
    static float rangeBonusFor(Entity entity) {
        return entity.getVehicle() instanceof Garrison garrison ? garrison.rangeBonus() : 0.0f;
    }
}
