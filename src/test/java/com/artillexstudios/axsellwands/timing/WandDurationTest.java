package com.artillexstudios.axsellwands.timing;

/** Dependency-free regression checks: run with the commands in TIMED-WANDS.md. */
public final class WandDurationTest {
    public static void main(String[] args) {
        check(WandDuration.parseMillis("1h") == 3_600_000L);
        check(WandDuration.parseMillis("24h") == WandDuration.parseMillis("1d"));
        check(WandDuration.parseMillis("2h") == 7_200_000L);
        check(WandDuration.parseMillis("2d") == 172_800_000L);
        for (String invalid : new String[]{"0h", "-1d", "1m", "1.5h", "1h1d", "h", "1", "01h", " 1h", "1H", "999999999999999999999d", "9223372036854775807h"}) {
            boolean rejected = false;
            try { WandDuration.parseMillis(invalid); }
            catch (IllegalArgumentException | ArithmeticException expected) { rejected = true; }
            check(rejected);
        }
        check(WandDuration.remaining(1000, 1000).equals("0s"));
        check(WandDuration.remaining(1000, 2000).equals("0s"));
        check(WandDuration.remaining(1001, 1000).equals("0h 0m 1s"));
        check(WandDuration.remaining(86_400_000, 0).equals("1d 0h 0m 0s"));
        // Time in a closed chest/offline requires no ticking state: just compare the saved deadline.
        long deadline = 1_000L + WandDuration.parseMillis("1h");
        check(WandDuration.remaining(deadline, 1_000L + 1_800_000L).equals("0h 30m 0s"));
        check(WandDuration.remaining(deadline, deadline + 1).equals("0s"));
        System.out.println("All duration and deadline regression checks passed.");
    }
    private static void check(boolean passed) {
        if (!passed) throw new AssertionError("Duration regression");
    }
}
