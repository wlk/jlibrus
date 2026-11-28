package io.github.wlk.jlibrus.server.domain.timeline;

import io.github.wlk.jlibrus.server.domain.TimelineEntry;

import java.time.Instant;
import java.util.List;
import java.util.Set;

/**
 * Decides which entries are new and when they count as new. On the very first sync of a child (nothing known
 * yet) everything already exists, so entries are dated by Librus' own timestamp (or the epoch when Librus has
 * none) instead of flooding the next summary; afterwards an entry is new when the sync first sees it.
 */
public final class NoticePolicy {

    private NoticePolicy() {
    }

    /** True when no entry of the child has been recorded yet. */
    public static boolean isFirstSync(Set<String> knownKeys) {
        return knownKeys.isEmpty();
    }

    /** Entries whose key is not in {@code knownKeys}, stamped with the time they count as new. */
    public static List<TimelineEntry> unseen(List<TimelineEntry> entries, Set<String> knownKeys, Instant now) {
        boolean firstSync = isFirstSync(knownKeys);
        return entries.stream()
                .filter(e -> !knownKeys.contains(e.key()))
                .map(e -> e.withNoticedAt(firstSync ? baseline(e, now) : now))
                .toList();
    }

    private static Instant baseline(TimelineEntry entry, Instant now) {
        if (entry.addedAt() == null) {
            return Instant.EPOCH;
        }
        return entry.addedAt().isAfter(now) ? now : entry.addedAt();
    }
}
