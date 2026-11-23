package io.github.wlk.jlibrus.server.domain;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;

/**
 * Something that happened for one child, as remembered by the server.
 *
 * @param key        stable identity within the child, e.g. {@code GRADE:12345}
 * @param occursOn   the school day it concerns (may be {@code null})
 * @param addedAt    when Librus says it was added (may be {@code null})
 * @param noticedAt  when it should count as "new": first sync uses {@code addedAt}, later syncs use the sync time
 */
public record TimelineEntry(String childId, String key, EntryType type, String title, String detail,
                            String subject, String author, LocalDate occursOn, Instant addedAt,
                            Instant noticedAt) {

    public TimelineEntry {
        Objects.requireNonNull(childId, "childId");
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(title, "title");
        Objects.requireNonNull(noticedAt, "noticedAt");
    }

    public TimelineEntry withNoticedAt(Instant instant) {
        return new TimelineEntry(childId, key, type, title, detail, subject, author, occursOn, addedAt, instant);
    }
}
