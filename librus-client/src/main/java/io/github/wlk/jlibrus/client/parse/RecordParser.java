package io.github.wlk.jlibrus.client.parse;

import com.fasterxml.jackson.databind.JsonNode;
import io.github.wlk.jlibrus.client.model.Announcement;
import io.github.wlk.jlibrus.client.model.Attendance;
import io.github.wlk.jlibrus.client.model.BehaviourPoint;
import io.github.wlk.jlibrus.client.model.Grade;
import io.github.wlk.jlibrus.client.model.GradeKind;
import io.github.wlk.jlibrus.client.model.LuckyNumber;
import io.github.wlk.jlibrus.client.model.Note;
import io.github.wlk.jlibrus.client.model.NotePolarity;

import java.util.List;
import java.util.Optional;

/** Parses the student's records: grades, behaviour, attendance, notes, announcements, lucky number. */
public final class RecordParser {

    private RecordParser() {
    }

    public static List<Grade> grades(JsonNode root) {
        return Json.list(root, "Grades", g -> new Grade(
                Json.requiredLong(g, "Id"),
                Json.textOr(g, "Grade", "?"),
                Json.refLong(g, "Category").orElse(null),
                Json.refLong(g, "Subject").orElse(0L),
                Json.refLong(g, "AddedBy").orElse(null),
                Json.integer(g, "Semester").orElse(1),
                Json.dateTime(g, "AddDate").orElse(null),
                gradeKind(g),
                Json.refLongs(g.path("Comments")),
                Json.refLong(g, "Improvement").orElse(null)));
    }

    static GradeKind gradeKind(JsonNode g) {
        if (Json.flag(g, "IsConstituent")) {
            return GradeKind.REGULAR;
        }
        if (Json.flag(g, "IsSemester")) {
            return GradeKind.SEMESTER;
        }
        if (Json.flag(g, "IsSemesterProposition")) {
            return GradeKind.SEMESTER_PROPOSAL;
        }
        if (Json.flag(g, "IsFinal")) {
            return GradeKind.FINAL;
        }
        return Json.flag(g, "IsFinalProposition") ? GradeKind.FINAL_PROPOSAL : GradeKind.REGULAR;
    }

    public static List<BehaviourPoint> behaviourPoints(JsonNode root) {
        return Json.list(root, "Grades", g -> new BehaviourPoint(
                Json.requiredLong(g, "Id"),
                behaviourDisplay(g),
                Json.textOr(g, "Text", ""),
                Json.refLong(g, "Category").orElse(null),
                Json.refLong(g, "AddedBy").orElse(null),
                Json.integer(g, "Semester").orElse(null),
                Json.dateTime(g, "AddDate").orElse(null)));
    }

    private static final String[] BEHAVIOUR_SHORT = {"", "wz", "bdb", "db", "popr", "ndp", "ng"};

    static String behaviourDisplay(JsonNode g) {
        Optional<Long> standard = Json.refLong(g, "BehaviourGrade");
        if (standard.isPresent() && standard.get() > 0 && standard.get() < BEHAVIOUR_SHORT.length) {
            return BEHAVIOUR_SHORT[standard.get().intValue()];
        }
        Optional<String> value = Json.text(g, "Value");
        if (value.isPresent()) {
            return value.get().startsWith("-") ? value.get() : "+" + value.get();
        }
        return Json.textOr(g, "ShortName", "");
    }

    public static List<Attendance> attendances(JsonNode root) {
        return Json.list(root, "Attendances", a -> new Attendance(
                Json.text(a, "Id").orElse(""),
                Json.refLong(a, "Lesson").orElse(null),
                Json.date(a, "Date").orElse(null),
                Json.integer(a, "LessonNo").orElse(null),
                Json.integer(a, "Semester").orElse(null),
                Json.refLong(a, "Type").orElse(0L),
                Json.refLong(a, "AddedBy").orElse(null),
                Json.dateTime(a, "AddDate").orElse(null)));
    }

    public static List<Note> notes(JsonNode root) {
        return Json.list(root, "Notes", n -> new Note(
                Json.requiredLong(n, "Id"),
                Json.textOr(n, "Text", ""),
                Json.refLong(n, "Category").orElse(null),
                Json.refLong(n, "Teacher").orElse(null),
                Json.date(n, "Date").orElse(null),
                polarity(Json.integer(n, "Positive").orElse(2))));
    }

    static NotePolarity polarity(int positive) {
        return switch (positive) {
            case 0 -> NotePolarity.NEGATIVE;
            case 1 -> NotePolarity.POSITIVE;
            default -> NotePolarity.NEUTRAL;
        };
    }

    public static List<Announcement> announcements(JsonNode root) {
        return Json.list(root, "SchoolNotices", n -> new Announcement(
                Json.text(n, "Id").orElse(""),
                Json.textOr(n, "Subject", ""),
                Json.textOr(n, "Content", ""),
                Json.date(n, "StartDate").orElse(null),
                Json.date(n, "EndDate").orElse(null),
                Json.refLong(n, "AddedBy").orElse(null),
                Json.dateTime(n, "CreationDate").orElse(null),
                Json.flag(n, "WasRead")));
    }

    /** Empty when the school does not use lucky numbers. */
    public static Optional<LuckyNumber> luckyNumber(JsonNode root) {
        JsonNode lucky = root.path("LuckyNumber");
        if (!lucky.isObject()) {
            return Optional.empty();
        }
        return Json.integer(lucky, "LuckyNumber").flatMap(number ->
                Json.date(lucky, "LuckyNumberDay").map(day -> new LuckyNumber(number, day)));
    }
}
