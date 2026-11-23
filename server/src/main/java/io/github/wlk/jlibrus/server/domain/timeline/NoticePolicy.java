package io.github.wlk.jlibrus.server.domain.timeline;

import io.github.wlk.jlibrus.server.domain.TimelineEntry;

import java.time.Instant;
import java.util.List;

/**
 * Decides when an entry counts as "new". On the very first sync of a child everything already exists, so
 * entries are dated by Librus' own timestamp (or the epoch when Librus has none) instead of flooding the next
 * summary; afterwards an entry is new when the sync first sees it.
 */
public final class NoticePolicy {

    private NoticePolicy() {
    }

    public static List<TimelineEntry> stamp(List<TimelineEntry> entries, boolean firstSync, Instant now) {
        return entries.stream().map(e -> e.withNoticedAt(firstSync ? baseline(e, now) : now)).toList();
    }

    private static Instant baseline(TimelineEntry entry, Instant now) {
        if (entry.addedAt() == null) {
            return Instant.EPOCH;
        }
        return entry.addedAt().isAfter(now) ? now : entry.addedAt();
    }
}
