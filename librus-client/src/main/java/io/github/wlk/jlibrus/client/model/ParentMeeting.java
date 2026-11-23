package io.github.wlk.jlibrus.client.model;

import java.time.LocalDate;
import java.time.LocalTime;

/** Parent-teacher meeting. {@code time} is {@code null} when Librus has no time. */
public record ParentMeeting(long id, String name, String topic, String room, LocalDate date, LocalTime time,
                            Long teacherId) {
}
