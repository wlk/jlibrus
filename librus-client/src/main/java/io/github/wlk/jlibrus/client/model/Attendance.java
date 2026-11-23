package io.github.wlk.jlibrus.client.model;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * One attendance record for one lesson. Ids may be non-numeric (e.g. {@code t41685}).
 *
 * @param lessonId teaching assignment, the only link to subject and teacher
 */
public record Attendance(String id, Long lessonId, LocalDate date, Integer lessonNo, Integer semester,
                         long typeId, Long teacherId, LocalDateTime addedAt) {
}
