package io.github.wlk.jlibrus.client.parse;

import com.fasterxml.jackson.databind.JsonNode;
import io.github.wlk.jlibrus.client.model.CalendarEvent;
import io.github.wlk.jlibrus.client.model.FreeDay;
import io.github.wlk.jlibrus.client.model.Homework;
import io.github.wlk.jlibrus.client.model.ParentMeeting;
import io.github.wlk.jlibrus.client.model.TeacherAbsence;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

/** Parses dated items: calendar events, homework, meetings, free days and teacher absences. */
public final class CalendarParser {

    private CalendarParser() {
    }

    public static List<CalendarEvent> events(JsonNode root) {
        return Json.list(root, "HomeWorks", e -> new CalendarEvent(
                Json.requiredLong(e, "Id"),
                Json.textOr(e, "Content", ""),
                Json.date(e, "Date").orElse(null),
                Json.refLong(e, "Category").orElse(null),
                Json.refLong(e, "Subject").orElse(null),
                Json.refLong(e, "CreatedBy").orElse(null),
                Json.integer(e, "LessonNo").orElse(null),
                Json.time(e, "TimeFrom").orElse(null),
                Json.time(e, "TimeTo").orElse(null),
                Json.dateTime(e, "AddDate").orElse(null),
                Json.text(e.path("onlineLessonUrl"), "url").orElse(null)));
    }

    public static List<Homework> homework(JsonNode root) {
        return Json.list(root, "HomeWorkAssignments", h -> new Homework(
                Json.requiredLong(h, "Id"),
                Json.textOr(h, "Topic", ""),
                Json.textOr(h, "Text", ""),
                Json.refLong(h, "Teacher").orElse(null),
                Json.date(h, "Date").orElse(null),
                Json.date(h, "DueDate").orElse(null),
                Json.list(h, "HomeworkAssigmentFiles", f -> Json.textOr(f, "Name", "plik"))));
    }

    public static List<ParentMeeting> parentMeetings(JsonNode root) {
        return Json.list(root, "ParentTeacherConferences", m -> new ParentMeeting(
                Json.requiredLong(m, "Id"),
                Json.textOr(m, "Name", ""),
                Json.textOr(m, "Topic", ""),
                Json.textOr(m, "Room", ""),
                Json.date(m, "Date").orElse(null),
                Json.time(m, "Time").filter(t -> !LocalTime.MIDNIGHT.equals(t)).orElse(null),
                Json.refLong(m, "Teacher").orElse(null)));
    }

    public static List<FreeDay> schoolFreeDays(JsonNode root) {
        return Json.list(root, "SchoolFreeDays", d -> new FreeDay(
                Json.requiredLong(d, "Id"),
                Json.textOr(d, "Name", ""),
                Json.date(d, "DateFrom").orElse(null),
                dateTo(d)));
    }

    public static List<TeacherAbsence> teacherAbsences(JsonNode root) {
        return Json.list(root, "TeacherFreeDays", d -> new TeacherAbsence(
                Json.requiredLong(d, "Id"),
                Json.refLong(d, "Teacher").orElse(null),
                Json.date(d, "DateFrom").orElse(null),
                dateTo(d),
                Json.time(d, "TimeFrom").orElse(null),
                Json.time(d, "TimeTo").orElse(null)));
    }

    /** {@code DateTo}; one-day entries may leave it out, then it is {@code DateFrom}. */
    private static LocalDate dateTo(JsonNode d) {
        return Json.date(d, "DateTo").or(() -> Json.date(d, "DateFrom")).orElse(null);
    }
}
