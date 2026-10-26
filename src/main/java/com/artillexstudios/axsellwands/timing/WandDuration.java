package com.artillexstudios.axsellwands.timing;

import java.util.regex.Pattern;

/** Strict duration parsing; no minutes, fractions, zero, negatives or compound units. */
public final class WandDuration {
    private static final Pattern INPUT = Pattern.compile("[1-9][0-9]*[hd]");
    private WandDuration() {}

    public static long parseMillis(String input) {
        if (input == null || !INPUT.matcher(input).matches()) {
            throw new IllegalArgumentException("Use positive whole hours or days");
        }
        long value = Long.parseLong(input.substring(0, input.length() - 1));
        return Math.multiplyExact(value, input.endsWith("d") ? 86_400_000L : 3_600_000L);
    }

    public static String remaining(long expiresAt, long now) {
        if (expiresAt <= now) return "0s";
        long seconds = (expiresAt - now - 1) / 1000 + 1;
        return (seconds / 86400 > 0 ? seconds / 86400 + "d " : "")
                + (seconds / 3600 % 24) + "h " + (seconds / 60 % 60) + "m " + (seconds % 60) + "s";
    }
}
