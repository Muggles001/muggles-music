package top.boluofan.musictv.player.settings;

import java.util.HashSet;
import java.util.Set;

public final class FailedTrackRecovery {
    public static final int INDEX_UNSET = -1;

    private final Set<Integer> failedIndices = new HashSet<>();

    public int recordFailureAndFindNext(int failedIndex, int queueSize) {
        if (failedIndex >= 0 && failedIndex < queueSize) failedIndices.add(failedIndex);
        if (queueSize <= 1 || failedIndex < 0 || failedIndex >= queueSize) return INDEX_UNSET;
        for (int offset = 1; offset < queueSize; offset++) {
            int candidate = (failedIndex + offset) % queueSize;
            if (!failedIndices.contains(candidate)) return candidate;
        }
        return INDEX_UNSET;
    }

    public void reset() {
        failedIndices.clear();
    }

    int failedCount() {
        return failedIndices.size();
    }
}
