package net.bitflora.asteriskcraft.combat;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Pins the shape of a detonation's damage curve, which is deliberately not a curve at all.
 *
 * <p>Two separate things make this worth its own test.
 *
 * <p>First, **the blast is flat on purpose**, and the obvious "improvement" is to taper it. A taper
 * was tried and was the bug: at the edge of a three-block blast a linear falloff left a Zealot taking
 * about 42 against 80 effective HP, so it survived a bomb it was standing next to and the unit did
 * nothing. Anything inside the radius must take the whole amount.
 *
 * <p>Second, **the outer cull is not redundant with vanilla's**. {@code ServerExplosion.hurtEntities}
 * considers entities out to <em>twice</em> the radius and leans on its own curve having decayed to
 * nothing by then. A flat curve has no decay, so dropping the clamp would extend a radius-3 blast to
 * 6 blocks with no visible sign — which is exactly the kind of silent doubling a test should hold
 * down.
 *
 * <p>The explosion itself, its knockback, and the separate sweep that charges enemy buildings all
 * need a live {@code ServerLevel} and so are out of scope here; they are verified in {@code
 * runClient}. This is the half that is pure arithmetic, split out for exactly that reason — the same
 * split {@code BounceChainTest} makes.
 */
class SuicideBlastTest {

    private static final float RADIUS = 3.0f;
    private static final double DAMAGE = 250.0;
    /** Nothing between the blast and the target: isolates the distance rule from the cover rule. */
    private static final float CLEAR = 1.0f;

    @Test
    void everythingInsideTheRadiusTakesTheFullAmount() {
        for (double distance : new double[]{0.0, 0.5, 1.5, 2.5, 2.99, RADIUS}) {
            assertEquals(DAMAGE, SuicideBlast.damageAt(DAMAGE, RADIUS, distance, CLEAR), 0.001,
                    "the blast is flat inside its radius; backing off must not soften it — at "
                            + distance);
        }
    }

    @Test
    void nothingOutsideTheRadiusIsTouched() {
        assertEquals(0.0f, SuicideBlast.damageAt(DAMAGE, RADIUS, RADIUS + 0.01, CLEAR), 0.001,
                "just past the rim is already out of the blast");
        // 1.5x and 2x the radius are both still inside the range vanilla hands to the calculator,
        // so these are the cases that would silently take full damage if the clamp were dropped.
        assertEquals(0.0f, SuicideBlast.damageAt(DAMAGE, RADIUS, RADIUS * 1.5, CLEAR), 0.001,
                "vanilla still offers this entity to the calculator — the clamp is what excludes it");
        assertEquals(0.0f, SuicideBlast.damageAt(DAMAGE, RADIUS, RADIUS * 2.0, CLEAR), 0.001,
                "the outer edge of vanilla's gather range must take nothing");
    }

    @Test
    void coverIsTheOnlyThingThatSoftensTheBlast() {
        assertEquals(0.0f, SuicideBlast.damageAt(DAMAGE, RADIUS, 0.0, 0.0f), 0.001,
                "a fully occluded target takes nothing even standing on the bomber");
        assertEquals(DAMAGE / 2.0, SuicideBlast.damageAt(DAMAGE, RADIUS, 1.0, 0.5f), 0.001,
                "exposure scales the result linearly, so half-covered is half-damage");
    }
}
