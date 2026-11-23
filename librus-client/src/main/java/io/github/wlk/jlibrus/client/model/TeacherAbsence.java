package io.github.wlk.jlibrus.client.model;

import java.time.LocalDate;
import java.time.LocalTime;

/** Teacher absence; times are {@code null} for whole days. */
public record TeacherAbsence(long id, Long teacherId, LocalDate from, LocalDate to, LocalTime timeFrom,
                             LocalTime timeTo) {
}
