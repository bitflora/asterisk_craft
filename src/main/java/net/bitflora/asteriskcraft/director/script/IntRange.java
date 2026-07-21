package net.bitflora.asteriskcraft.director.script;

import net.minecraft.util.RandomSource;

/**
 * An inclusive integer range parsed from a build-script quantity — either a single number
 * ({@code 3}) or a span ({@code 3-5}). {@link #roll(RandomSource)} picks a value when a command
 * begins executing, so ranged quantities vary from wave to wave.
 */
public record IntRange(int min, int max) {
    public IntRange {
        if (max < min) {
            int swap = min;
            min = max;
            max = swap;
        }
    }

    public static IntRange single(int value) {
        return new IntRange(value, value);
    }

    /** A uniformly random value in [min, max]. */
    public int roll(RandomSource random) {
        return min == max ? min : min + random.nextInt(max - min + 1);
    }
}
