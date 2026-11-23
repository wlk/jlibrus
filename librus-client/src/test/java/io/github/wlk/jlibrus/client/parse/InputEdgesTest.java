package io.github.wlk.jlibrus.client.parse;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.wlk.jlibrus.client.grades.GradeAverages;
import io.github.wlk.jlibrus.client.grades.GradeScale;
import io.github.wlk.jlibrus.client.model.AttendanceKind;
import io.github.wlk.jlibrus.client.model.DescriptiveGrade;
import io.github.wlk.jlibrus.client.model.Grade;
import io.github.wlk.jlibrus.client.model.GradeCategory;
import io.github.wlk.jlibrus.client.model.GradeKind;
import io.github.wlk.jlibrus.client.model.LessonStatus;
import io.github.wlk.jlibrus.client.model.SchoolClass;
import io.github.wlk.jlibrus.client.model.TimetableEntry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Odd but legal Librus payloads: nulls, blanks, wrong node types and boundary values. */
class InputEdgesTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final LocalDate DAY = LocalDate.of(2026, 11, 23);

    private static JsonNode json(String text) throws Exception {
        return MAPPER.readTree(text);
    }

    @Test
    void jsonReadersTreatNullBlankAndWrongTypesAsMissing() throws Exception {
        JsonNode node = json("{\"n\":null,\"blank\":\" \",\"obj\":{\"Id\":1},\"T\":\"TRUE\",\"Y\":\"yes\",\"items\":{\"a\":1}}");

        assertThat(Json.text(node, "n")).isEmpty();
        assertThat(Json.refId(node, "blank")).isEmpty();
        assertThat(Json.refId(node, "n")).isEmpty();
        assertThat(Json.flag(node, "T")).isTrue();
        assertThat(Json.flag(node, "Y")).isFalse();
        assertThat(Json.optionalFlag(node, "missing")).isEmpty();
        assertThat(Json.optionalFlag(node, "T")).contains(true);
        assertThat(Json.list(node, "items", n -> 1)).isEmpty();
        assertThat(Json.date(node, "obj")).isEmpty();
    }

    @Test
    void timetableKeepsOriginalOnlyForSubstitutionsAndTargetOnlyForMovedOut() throws Exception {
        String org = "\"OrgSubject\":{\"Name\":\"Fizyka\"},\"OrgTeacher\":{\"FirstName\":\"Jan\",\"LastName\":\"Kowal\"},"
                + "\"NewDate\":\"2026-11-25\",\"NewLessonNo\":\"3\",\"LessonNo\":\"2\"";
        TimetableEntry normal = TimetableParser.entry(DAY, json("{" + org + "}"));
        TimetableEntry substitution = TimetableParser.entry(DAY, json("{" + org + ",\"IsSubstitutionClass\":true,"
                + "\"OrgDate\":\"2026-11-23\",\"OrgLessonNo\":\"2\"}"));
        TimetableEntry movedIn = TimetableParser.entry(DAY, json("{" + org + ",\"IsSubstitutionClass\":true,"
                + "\"OrgDate\":\"2026-11-20\"}"));
        TimetableEntry cancelled = TimetableParser.entry(DAY, json("{" + org + ",\"IsCanceled\":true}"));
        TimetableEntry blankTeacher = TimetableParser.entry(DAY, json("{\"IsSubstitutionClass\":true,\"OrgTeacher\":{}}"));

        assertThat(normal.originalSubject()).isNull();
        assertThat(normal.originalTeacher()).isNull();
        assertThat(normal.movedToDate()).isNull();
        assertThat(substitution.originalSubject()).isEqualTo("Fizyka");
        assertThat(substitution.originalTeacher()).isEqualTo("Jan Kowal");
        assertThat(substitution.movedToDate()).isNull();
        assertThat(movedIn.status()).isEqualTo(LessonStatus.MOVED_IN);
        assertThat(movedIn.originalSubject()).isEqualTo("Fizyka");
        assertThat(cancelled.movedToDate()).isNull();
        assertThat(cancelled.movedToLessonNo()).isNull();
        assertThat(blankTeacher.originalTeacher()).isNull();
    }

    @ParameterizedTest
    @CsvSource({"0, ''", "1, wz", "6, ng", "7, x"})
    void behaviourGradeIdsOutsideTheStandardRangeFallBack(int id, String expected) throws Exception {
        assertThat(RecordParser.behaviourDisplay(json("{\"BehaviourGrade\":{\"Id\":" + id + "},\"ShortName\":\""
                + (id == 0 ? "" : "x") + "\"}"))).isEqualTo(expected);
    }

    @Test
    void luckyNumberWithNullObjectIsEmpty() throws Exception {
        assertThat(RecordParser.luckyNumber(json("{\"LuckyNumber\":null}"))).isEmpty();
    }

    @Test
    void calendarFallbacks() throws Exception {
        assertThat(CalendarParser.parentMeetings(json("{\"ParentTeacherConferences\":[{\"Id\":1,\"Time\":\"00:00:00\"},"
                + "{\"Id\":2,\"Time\":\"17:30:00\"}]}"))).extracting(m -> m.time()).containsExactly(null, LocalTime.of(17, 30));
        assertThat(CalendarParser.schoolFreeDays(json("{\"SchoolFreeDays\":[{\"Id\":1,\"DateFrom\":\"2026-12-01\"}]}")).getFirst().to())
                .isEqualTo(LocalDate.of(2026, 12, 1));
        assertThat(CalendarParser.teacherAbsences(json("{\"TeacherFreeDays\":[{\"Id\":1,\"DateFrom\":\"2026-12-02\"}]}")).getFirst().to())
                .isEqualTo(LocalDate.of(2026, 12, 2));
    }

    @Test
    void presenceKindWinsOverAbsentStandardAndNoteCategoryFallsBackToName() throws Exception {
        assertThat(DictionaryParser.attendanceKind(1, true)).isEqualTo(AttendanceKind.PRESENT);
        assertThat(DictionaryParser.noteCategories(json("{\"Categories\":[{\"Id\":1,\"Name\":\"Inne\"}]}")).getFirst().name())
                .isEqualTo("Inne");
    }

    @Test
    void semesterBoundaryIsInclusiveEndOfFirstSemester() {
        SchoolClass known = new SchoolClass(1, "4b", DAY, DAY.plusDays(10), DAY.plusDays(100), null);
        SchoolClass unknown = new SchoolClass(1, "4b", null, null, null, null);

        assertThat(known.semesterOf(DAY.plusDays(10))).isEqualTo(1);
        assertThat(known.semesterOf(DAY.plusDays(11))).isEqualTo(2);
        assertThat(unknown.semesterOf(DAY.plusDays(500))).isEqualTo(1);
    }

    @Test
    void gradeScaleBoundaries() {
        assertThat(new GradeScale(0, 0).numeric("4+")).hasValue(4.0);
        assertThat(new GradeScale(0.99, 0.99).numeric("4-")).hasValue(3.01);
        assertThatThrownBy(() -> new GradeScale(1, 0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new GradeScale(0, 1)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new GradeScale(0, -0.01)).isInstanceOf(IllegalArgumentException.class);
        assertThat(GradeScale.DEFAULT.numeric("+4-")).isEmpty();
        assertThat(GradeScale.DEFAULT.numeric("-4+")).isEmpty();
    }

    @Test
    void gradesWithoutCategoryOrWithZeroWeightDoNotCount() {
        Map<Long, GradeCategory> categories = Map.of(1L, new GradeCategory(1, "Zero", 0, true, null),
                2L, new GradeCategory(2, "Sprawdzian", 2, true, null));
        Grade noCategory = new Grade(1, "1", null, 1, null, 1, null, GradeKind.REGULAR, List.of(), null);
        Grade zero = new Grade(2, "1", 1L, 1, null, 1, null, GradeKind.REGULAR, List.of(), null);
        Grade counted = new Grade(3, "5", 2L, 1, null, 1, null, GradeKind.REGULAR, List.of(), null);

        assertThat(GradeAverages.weighted(List.of(noCategory, zero, counted), categories, GradeScale.DEFAULT)).hasValue(5.0);
    }

    @Test
    void extraParserFiltersBlankNamesAndResolvesFallbacks() throws Exception {
        var justification = ExtraParser.justifications(json("{\"data\":[{\"id\":1,\"notifiedTeachers\":[{\"name\":\"\"},"
                + "{\"x\":1},{\"name\":\"Anna\"}]}]}")).getFirst();
        var trip = ExtraParser.schoolTrips(json("{\"Data\":[{\"id\":1,\"destination\":\"Kino\"}]}")).getFirst();
        var classic = ExtraParser.descriptiveGrades(json("{\"Grades\":[{\"Id\":1,\"Comments\":[{\"Id\":7},{\"Id\":8}]}]}"),
                Map.of(), Map.of(8L, " ")).getFirst();

        assertThat(justification.notifiedTeachers()).containsExactly("Anna");
        assertThat(trip.coordinator()).isNull();
        assertThat(classic.comments()).isEmpty();
        assertThat(classic.skill()).isNull();
    }

    @Test
    void partialGradeFieldVariants() throws Exception {
        List<DescriptiveGrade> grades = ExtraParser.partialGrades(json("""
                {"data":[
                 {"gradeId":1,"subjectId":"12","scaleValue":{"value":3},"comments":{"text":"Jeden"},
                  "implementedRequirements":[{"name":""},{"name":"Liczy"}]},
                 {"gradeId":2,"subjectId":"LID-UNKNOWN","comments":["  ",{"content":"Dwa"}]},
                 {"gradeId":3,"subjectId":true,"comments":7}]}
                """), Map.of());

        assertThat(grades.getFirst().subjectId()).isEqualTo(12L);
        assertThat(grades.getFirst().value()).isEqualTo("3");
        assertThat(grades.getFirst().comments()).containsExactly("Jeden");
        assertThat(grades.getFirst().requirements()).containsExactly("Liczy");
        assertThat(grades.get(1).subjectId()).isNull();
        assertThat(grades.get(1).comments()).containsExactly("Dwa");
        assertThat(grades.get(2).subjectId()).isNull();
        assertThat(grades.get(2).comments()).isEmpty();
    }
}
