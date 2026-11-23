package io.github.wlk.jlibrus.server.domain.ical;

import java.util.Objects;

/**
 * One entry exported to an iCalendar feed.
 *
 * @param uid         globally unique, stable id (so calendar apps update instead of duplicating)
 * @param summary     title
 * @param description optional free text, may be {@code null}
 * @param location    optional location, may be {@code null}
 * @param category    entry type name used as CATEGORIES
 * @param when        timing
 */
public record CalendarItem(String uid, String summary, String description, String location,
                           String category, When when) {

    public CalendarItem {
        Objects.requireNonNull(uid, "uid");
        Objects.requireNonNull(summary, "summary");
        Objects.requireNonNull(category, "category");
        Objects.requireNonNull(when, "when");
    }
}
