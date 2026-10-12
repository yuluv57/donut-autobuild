package com.donutautobuild.build;

import java.util.HashMap;
import java.util.Map;

/** Failed real clicks remain counted until that cell is verified or we rejoin. */
public final class PlacementRecoveryBudget {
    public static final int LIMIT = 25;
    private final Map<Long, Integer> attempts = new HashMap<>();
    private final Map<Long, Integer> counted = new HashMap<>();
    private final Map<Long, Integer> failures = new HashMap<>();
    private int outstanding;

    public void clicked(long pos, int tick) {
        attempts.put(pos, tick);
    }

    public boolean failed(long pos, int tick) {
        Integer attempt = attempts.get(pos);
        // Multiple acknowledgement paths can report the same click. Count it once.
        if (attempt == null) return false;
        if (attempt.equals(counted.get(pos))) return false;
        counted.put(pos, attempt);
        failures.merge(pos, 1, Integer::sum);
        outstanding++;
        return true;
    }

    public void verified(long pos) {
        Integer count = failures.remove(pos);
        if (count != null) outstanding -= count;
        // Keep the attempt stamp for deduplication of late reports.
    }

    public int outstanding() { return outstanding; }
    public boolean reconnectDue() { return outstanding >= LIMIT; }

    public void reset() {
        attempts.clear(); counted.clear(); failures.clear(); outstanding = 0;
    }
}
