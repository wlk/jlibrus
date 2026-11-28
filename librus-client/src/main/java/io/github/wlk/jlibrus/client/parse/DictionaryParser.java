package io.github.wlk.jlibrus.client.parse;

import com.fasterxml.jackson.databind.JsonNode;
import io.github.wlk.jlibrus.client.model.AccountInfo;
import io.github.wlk.jlibrus.client.model.AttendanceKind;
import io.github.wlk.jlibrus.client.model.AttendanceType;
import io.github.wlk.jlibrus.client.model.Classroom;
import io.github.wlk.jlibrus.client.model.EventCategory;
import io.github.wlk.jlibrus.client.model.GradeCategory;
import io.github.wlk.jlibrus.client.model.GradeComment;
import io.github.wlk.jlibrus.client.model.LessonAssignment;
import io.github.wlk.jlibrus.client.model.LessonSlot;
import io.github.wlk.jlibrus.client.model.NoteCategory;
import io.github.wlk.jlibrus.client.model.Person;
import io.github.wlk.jlibrus.client.model.School;
import io.github.wlk.jlibrus.client.model.SchoolClass;
import io.github.wlk.jlibrus.client.model.Subject;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/** Parses rarely-changing reference data: account, school, class, people, subjects and categories. */
public final class DictionaryParser {

    /** {@code Me.Account.GroupId} values of parent accounts. */
    private static final Set<Integer> PARENT_GROUPS = Set.of(5, 6);

    private DictionaryParser() {
    }

    public static AccountInfo me(JsonNode root) {
        JsonNode me = Json.object(root, "Me");
        JsonNode account = me.path("Account");
        JsonNode user = me.path("User");
        return new AccountInfo(
                Json.requiredLong(account, "Id"),
                Json.textOr(account, "Login", ""),
                Json.textOr(account, "FirstName", ""),
                Json.textOr(account, "LastName", ""),
                Json.integer(account, "GroupId").map(PARENT_GROUPS::contains).orElse(false),
                Json.textOr(user, "FirstName", ""),
                Json.textOr(user, "LastName", ""),
                Json.longValue(account, "UserId").orElse(null));
    }

    public static List<Person> users(JsonNode root) {
        return Json.list(root, "Users", u -> new Person(Json.requiredLong(u, "Id"),
                Json.textOr(u, "FirstName", ""), Json.textOr(u, "LastName", "")));
    }

    public static List<Subject> subjects(JsonNode root) {
        return Json.list(root, "Subjects", s -> new Subject(Json.requiredLong(s, "Id"),
                Json.textOr(s, "Name", "?"), Json.textOr(s, "Short", "")));
    }

    public static SchoolClass schoolClass(JsonNode root) {
        JsonNode c = Json.object(root, "Class");
        String name = Json.textOr(c, "Number", "") + Json.textOr(c, "Symbol", "");
        return new SchoolClass(Json.requiredLong(c, "Id"), name,
                Json.date(c, "BeginSchoolYear").orElse(null),
                Json.date(c, "EndFirstSemester").orElse(null),
                Json.date(c, "EndSchoolYear").orElse(null),
                Json.refLong(c, "ClassTutor").orElse(null));
    }

    public static School school(JsonNode root) {
        JsonNode s = Json.object(root, "School");
        List<LessonSlot> slots = new ArrayList<>();
        JsonNode ranges = s.path("LessonsRange");
        for (int i = 0; i < ranges.size(); i++) {
            JsonNode range = ranges.get(i);
            int number = i;
            Json.time(range, "From").ifPresent(from -> Json.time(range, "To")
                    .ifPresent(to -> slots.add(new LessonSlot(number, from, to))));
        }
        return new School(Json.requiredLong(s, "Id"), Json.textOr(s, "Name", ""), Json.textOr(s, "Town", ""), slots);
    }

    public static List<Classroom> classrooms(JsonNode root) {
        return Json.list(root, "Classrooms", c -> new Classroom(Json.requiredLong(c, "Id"),
                Json.textOr(c, "Name", ""), Json.textOr(c, "Symbol", "")));
    }

    public static List<LessonAssignment> lessons(JsonNode root) {
        return Json.list(root, "Lessons", l -> new LessonAssignment(Json.requiredLong(l, "Id"),
                Json.refLong(l, "Subject").orElse(0L), Json.refLong(l, "Teacher").orElse(null)));
    }

    public static List<GradeCategory> gradeCategories(JsonNode root) {
        return Json.list(root, "Categories", c -> new GradeCategory(Json.requiredLong(c, "Id"),
                Json.textOr(c, "Name", ""),
                Json.integer(c, "Weight").orElse(null),
                Json.optionalFlag(c, "CountToTheAverage").orElse(null),
                Json.refInt(c, "Color").orElse(null)));
    }

    public static List<GradeComment> gradeComments(JsonNode root) {
        return Json.list(root, "Comments", c -> new GradeComment(Json.requiredLong(c, "Id"),
                Json.textOr(c, "Text", "")));
    }

    public static List<AttendanceType> attendanceTypes(JsonNode root) {
        return Json.list(root, "Types", t -> {
            long id = Json.requiredLong(t, "Id");
            long base = Json.refLong(t, "StandardType").orElse(id);
            return new AttendanceType(id, Json.textOr(t, "Name", ""), Json.textOr(t, "Short", ""),
                    attendanceKind(base, Json.flag(t, "IsPresenceKind")), Json.textOr(t, "ColorRGB", ""));
        });
    }

    /** Standard type ids are fixed across schools; custom types point at one via {@code StandardType}. */
    static AttendanceKind attendanceKind(long standardId, boolean presenceKind) {
        if (presenceKind) {
            return standardId == 2 ? AttendanceKind.LATE : AttendanceKind.PRESENT;
        }
        return switch ((int) standardId) {
            case 1 -> AttendanceKind.ABSENT;
            case 2 -> AttendanceKind.LATE;
            case 3 -> AttendanceKind.EXCUSED;
            case 4 -> AttendanceKind.RELEASED;
            default -> AttendanceKind.PRESENT;
        };
    }

    public static List<EventCategory> eventCategories(JsonNode root) {
        return Json.list(root, "Categories", c -> new EventCategory(Json.requiredLong(c, "Id"),
                Json.textOr(c, "Name", ""), Json.refInt(c, "Color").orElse(null)));
    }

    public static List<NoteCategory> noteCategories(JsonNode root) {
        return Json.list(root, "Categories", c -> new NoteCategory(Json.requiredLong(c, "Id"),
                Json.text(c, "CategoryName").or(() -> Json.text(c, "Name")).orElse("")));
    }
}
