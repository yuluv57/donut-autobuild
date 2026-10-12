package com.donutautobuild;

import com.donutautobuild.build.HeldPlacementGuard;

/** Ensures target changes and server inventory changes wait for a stable, correct hand. */
public final class HeldPlacementRegression {
    private static int checks;
    private static void check(boolean value, String message) {
        checks++;
        if (!value) throw new AssertionError(message);
    }

    public static void main(String[] args) { run(); }

    public static int run() {
        checks = 0;
        Object obsidian = new Object(), cobble = new Object(), food = new Object();
        HeldPlacementGuard guard = new HeldPlacementGuard();
        check(!guard.ready(cobble, obsidian, 0, 1, 1, 2), "stale cobble must not place at obsidian target");
        check(!guard.ready(cobble, obsidian, 0, 1, 100, 2), "waiting never makes wrong held item valid");
        check(!guard.ready(obsidian, obsidian, 0, 1, 101, 2), "correct replacement item starts stabilization");
        check(!guard.ready(obsidian, obsidian, 0, 1, 102, 2), "correct item still waits two ticks");
        check(guard.ready(obsidian, obsidian, 0, 1, 103, 2), "stable correct item permits click");
        check(guard.ready(obsidian, obsidian, 0, 1, 104, 2), "subsequent stable click permitted");

        check(!guard.ready(obsidian, cobble, 0, 1, 105, 2), "changed target cannot use previous target item");
        check(!guard.ready(cobble, cobble, 0, 1, 106, 2), "changed target item stabilizes separately");
        check(guard.ready(cobble, cobble, 0, 1, 108, 2), "new target stable item permitted");
        check(!guard.ready(cobble, cobble, 1, 1, 109, 2), "selected slot change invalidates hand confirmation");
        check(!guard.ready(cobble, cobble, 1, 1, 110, 2), "new selected slot waits");
        check(guard.ready(cobble, cobble, 1, 1, 111, 2), "new selected slot stable");
        check(guard.ready(cobble, cobble, 1, 2, 112, 2), "same material count revision does not delay next placement");
        check(guard.ready(cobble, cobble, 1, 3, 113, 2), "another count revision retains confirmed material");
        check(guard.ready(cobble, cobble, 1, 3, 114, 2), "stable selected material remains ready");
        check(guard.ready(cobble, cobble, 1, 3, 115, 2), "confirmed revision permits hand use");

        check(!guard.ready(null, cobble, 1, 3, 116, 2), "empty stack invalidates confirmation");
        check(!guard.ready(cobble, cobble, 1, 3, 1000, 2), "returning after empty stack starts fresh delay");
        check(guard.ready(cobble, cobble, 1, 3, 1002, 2), "restored stack stable");
        check(!guard.ready(food, obsidian, 1, 3, 1003, 2), "eating hand cannot place an obsidian block");
        check(!guard.ready(obsidian, obsidian, 1, 3, 1004, 2), "returning from eating starts fresh delay");
        check(guard.ready(obsidian, obsidian, 1, 3, 1006, 2), "restored building hand stable");
        guard.reset();
        check(!guard.ready(obsidian, obsidian, 1, 3, 2000, 2), "explicit reset cannot inherit previous readiness");
        check(guard.ready(obsidian, obsidian, 1, 3, 2002, 2), "after reset stabilization completes");

        guard.reset();
        check(!guard.ready(obsidian, obsidian, 0, 1, 3000, 8), "high latency starts longer delay");
        for (int tick = 3001; tick < 3008; tick++)
            check(!guard.ready(obsidian, obsidian, 0, 1, tick, 8), "full high latency delay enforced");
        check(guard.ready(obsidian, obsidian, 0, 1, 3008, 8), "high latency delay complete");
        guard.reset();
        check(!guard.ready(obsidian, obsidian, 0, 1, 4000, -100), "negative configured wait still starts delay");
        check(!guard.ready(obsidian, obsidian, 0, 1, 4001, -100), "minimum delay one tick not enough");
        check(guard.ready(obsidian, obsidian, 0, 1, 4002, -100), "minimum delay exactly two ticks");
        check(!guard.ready(obsidian, null, 0, 1, 4003, 2), "unknown required item cannot confirm hand");

        // Repeated inventory changes must never unlock a stale hand at a sensitive target.
        for (int round = 0; round < 16; round++) {
            int tick = 5000 + round * 10;
            check(!guard.ready(cobble, obsidian, round % 9, round, tick, 3), "repeated wrong material rejected");
            check(!guard.ready(obsidian, obsidian, round % 9, round, tick + 1, 3), "repeated correct material waits");
            check(!guard.ready(obsidian, obsidian, round % 9, round, tick + 3, 3), "full per-target delay retained");
            check(guard.ready(obsidian, obsidian, round % 9, round, tick + 4, 3), "repeated correct stable hand allowed");
        }
        System.out.println("PASS: " + checks + " held item stabilization assertions.");
        return checks;
    }
}
