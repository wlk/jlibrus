package io.github.wlk.jlibrus.client.parse;

import com.fasterxml.jackson.databind.JsonNode;
import io.github.wlk.jlibrus.client.model.LessonStatus;
import io.github.wlk.jlibrus.client.model.TimetableEntry;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Parses {@code Timetables?weekStart=}: an object keyed by date, each day a list of slots, each slot a list of
 * (usually one) lessons. Ids inside are strings; flags are {@code IsCanceled} and {@code IsSubstitutionClass}.
 */
public final class TimetableParser {

    private TimetableParser() {
    }

    public static List<TimetableEntry> week(JsonNode root) {
        JsonNode timetable = root.path("Timetable");
        List<TimetableEntry> entries = new ArrayList<>();
        for (Map.Entry<String, JsonNode> day : timetable.properties()) {
            Optional<LocalDate> date = Json.parseDate(day.getKey());
            date.ifPresent(d -> day.getValue().forEach(slot ->
                    slot.forEach(lesson -> entries.add(entry(d, lesson)))));
        }
        entries.sort(Comparator.comparing(TimetableEntry::date).thenComparingInt(TimetableEntry::lessonNo));
        return List.copyOf(entries);
    }

    static TimetableEntry entry(LocalDate date, JsonNode lesson) {
        LessonStatus status = status(date, lesson);
        boolean substitution = status == LessonStatus.SUBSTITUTION || status == LessonStatus.MOVED_IN;
        return new TimetableEntry(
                date,
                Json.integer(lesson, "LessonNo").orElse(0),
                Json.time(lesson, "HourFrom").orElse(null),
                Json.time(lesson, "HourTo").orElse(null),
                Json.refLong(lesson, "Subject").orElse(null),
                Json.textOr(lesson.path("Subject"), "Name", ""),
                Json.refLong(lesson, "Teacher").orElse(null),
                teacherName(lesson.path("Teacher")),
                Json.refLong(lesson, "Classroom").orElse(null),
                status,
                substitution ? Json.text(lesson.path("OrgSubject"), "Name").orElse(null) : null,
                substitution ? blankToNull(teacherName(lesson.path("OrgTeacher"))) : null,
                status == LessonStatus.MOVED_OUT ? Json.date(lesson, "NewDate").orElse(null) : null,
                status == LessonStatus.MOVED_OUT ? Json.integer(lesson, "NewLessonNo").orElse(null) : null);
    }

    static LessonStatus status(LocalDate date, JsonNode lesson) {
        boolean canceled = Json.flag(lesson, "IsCanceled");
        boolean substitution = Json.flag(lesson, "IsSubstitutionClass");
        if (canceled) {
            return substitution ? LessonStatus.MOVED_OUT : LessonStatus.CANCELLED;
        }
        if (!substitution) {
            return LessonStatus.NORMAL;
        }
        boolean sameDay = Json.date(lesson, "OrgDate").map(date::equals).orElse(true);
        boolean sameSlot = Json.text(lesson, "OrgLessonNo")
                .map(org -> org.equals(Json.textOr(lesson, "LessonNo", ""))).orElse(true);
        return sameDay && sameSlot ? LessonStatus.SUBSTITUTION : LessonStatus.MOVED_IN;
    }

    private static String teacherName(JsonNode teacher) {
        return (Json.textOr(teacher, "FirstName", "") + " " + Json.textOr(teacher, "LastName", "")).trim();
    }

    private static String blankToNull(String text) {
        return text.isBlank() ? null : text;
    }
}
