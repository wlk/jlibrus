package io.github.wlk.jlibrus.client.model;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** School announcement ({@code SchoolNotices}); the id is an opaque string. */
public record Announcement(String id, String title, String content, LocalDate startDate, LocalDate endDate,
                           Long authorId, LocalDateTime createdAt, boolean read) {
}
