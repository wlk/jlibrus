package io.github.wlk.jlibrus.client.model;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Absence justification ("usprawiedliwienie") a parent submitted, with the school's decision.
 *
 * @param justifiedAbsences number of absences the school marked as excused because of it
 */
public record Justification(long id, JustificationStatus status, String message, LocalDateTime postedAt,
                            LocalDate from, LocalDate to, List<JustifiedLesson> lessons, int justifiedAbsences,
                            boolean hasAttachment, List<String> notifiedTeachers) {

    public Justification {
        lessons = List.copyOf(lessons);
        notifiedTeachers = List.copyOf(notifiedTeachers);
    }
}
