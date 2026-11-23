package io.github.wlk.jlibrus.server.domain.agenda;

import io.github.wlk.jlibrus.server.domain.EntryType;
import io.github.wlk.jlibrus.server.domain.ical.When;

import java.util.Objects;

/**
 * Something on a child's calendar: a test, homework due date, meeting, day off, teacher absence or
 * timetable change. The same items feed the dashboard, the calendar page, iCal feeds and e-mails.
 *
 * @param uid stable id, unique across children (used as iCal UID)
 */
public record AgendaItem(String uid, String childId, EntryType type, String title, String detail,
                         String subject, String location, When when) {

    public AgendaItem {
        Objects.requireNonNull(uid, "uid");
        Objects.requireNonNull(childId, "childId");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(title, "title");
        Objects.requireNonNull(when, "when");
    }
}
