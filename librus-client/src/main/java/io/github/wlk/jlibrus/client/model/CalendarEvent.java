package io.github.wlk.jlibrus.client.model;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

/** Calendar ("terminarz") entry: test, quiz, trip, meeting ({@code HomeWorks} in the API). */
public record CalendarEvent(long id, String content, LocalDate date, Long categoryId, Long subjectId,
                            Long teacherId, Integer lessonNo, LocalTime timeFrom, LocalTime timeTo,
                            LocalDateTime addedAt, String onlineUrl) {
}
