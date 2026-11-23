package io.github.wlk.jlibrus.client.parse;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.wlk.jlibrus.client.model.AttendanceKind;
import io.github.wlk.jlibrus.client.model.GradeKind;
import io.github.wlk.jlibrus.client.model.LessonStatus;
import io.github.wlk.jlibrus.client.model.NotePolarity;
import io.github.wlk.jlibrus.client.model.TimetableEntry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Interpretation rules for Librus flags and codes, on hand-written payloads shaped like the real API. */
class ParserRulesTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final LocalDate DAY = LocalDate.of(2026, 11, 23);

    private static JsonNode json(String text) throws Exception {
        return MAPPER.readTree(text);
    }

    @ParameterizedTest
    @CsvSource({
            "IsConstituent, REGULAR", "IsSemester, SEMESTER", "IsSemesterProposition, SEMESTER_PROPOSAL",
            "IsFinal, FINAL", "IsFinalProposition, FINAL_PROPOSAL", "Unknown, REGULAR"})
    void gradeKindFollowsFlags(String flag, GradeKind expected) throws Exception {
        assertThat(RecordParser.gradeKind(json("{\"" + flag + "\":true}"))).isEqualTo(expected);
    }

    @Test
    void constituentWinsOverSummaryFlags() throws Exception {
        assertThat(RecordParser.gradeKind(json("{\"IsConstituent\":true,\"IsFinal\":true}"))).isEqualTo(GradeKind.REGULAR);
        assertThat(GradeKind.FINAL.isSummary()).isTrue();
        assertThat(GradeKind.REGULAR.isSummary()).isFalse();
    }

    @Test
    void commentReferencesAcceptObjectsAndNumbers() throws Exception {
        assertThat(RecordParser.refIds(json("[{\"Id\":5},601,\"x\",{\"Id\":\"7\"}]"))).containsExactly(5L, 601L, 7L);
    }

    @ParameterizedTest
    @CsvSource({"1, false, ABSENT", "2, false, LATE", "3, false, EXCUSED", "4, false, RELEASED", "100, false, PRESENT",
            "1266, true, PRESENT", "2, true, LATE", "999, false, PRESENT"})
    void attendanceKindFromStandardType(long standard, boolean presence, AttendanceKind expected) {
        assertThat(DictionaryParser.attendanceKind(standard, presence)).isEqualTo(expected);
    }

    @Test
    void attendanceAbsenceKinds() {
        assertThat(AttendanceKind.ABSENT.isAbsence()).isTrue();
        assertThat(AttendanceKind.EXCUSED.isAbsence()).isTrue();
        assertThat(AttendanceKind.LATE.isAbsence()).isFalse();
    }

    @ParameterizedTest
    @CsvSource({"0, NEGATIVE", "1, POSITIVE", "2, NEUTRAL", "7, NEUTRAL"})
    void notePolarity(int positive, NotePolarity expected) {
        assertThat(RecordParser.polarity(positive)).isEqualTo(expected);
    }

    @Test
    void behaviourDisplayPrefersStandardGradeThenPointsThenShortName() throws Exception {
        assertThat(RecordParser.behaviourDisplay(json("{\"BehaviourGrade\":{\"Id\":1},\"Value\":5}"))).isEqualTo("wz");
        assertThat(RecordParser.behaviourDisplay(json("{\"Value\":-10}"))).isEqualTo("-10");
        assertThat(RecordParser.behaviourDisplay(json("{\"Value\":3}"))).isEqualTo("+3");
        assertThat(RecordParser.behaviourDisplay(json("{\"BehaviourGrade\":{\"Id\":9},\"ShortName\":\"x\"}"))).isEqualTo("x");
        assertThat(RecordParser.behaviourDisplay(json("{}"))).isEmpty();
    }

    @Test
    void timetableStatuses() throws Exception {
        assertThat(TimetableParser.status(DAY, json("{\"IsCanceled\":false,\"IsSubstitutionClass\":false}")))
                .isEqualTo(LessonStatus.NORMAL);
        assertThat(TimetableParser.status(DAY, json("{\"IsCanceled\":true}"))).isEqualTo(LessonStatus.CANCELLED);
        assertThat(TimetableParser.status(DAY, json("{\"IsCanceled\":true,\"IsSubstitutionClass\":true}")))
                .isEqualTo(LessonStatus.MOVED_OUT);
        assertThat(TimetableParser.status(DAY, json(
                "{\"IsSubstitutionClass\":true,\"LessonNo\":\"2\",\"OrgDate\":\"2026-11-23\",\"OrgLessonNo\":\"2\"}")))
                .isEqualTo(LessonStatus.SUBSTITUTION);
        assertThat(TimetableParser.status(DAY, json(
                "{\"IsSubstitutionClass\":true,\"LessonNo\":\"2\",\"OrgDate\":\"2026-11-20\",\"OrgLessonNo\":\"2\"}")))
                .isEqualTo(LessonStatus.MOVED_IN);
        assertThat(TimetableParser.status(DAY, json(
                "{\"IsSubstitutionClass\":true,\"LessonNo\":\"3\",\"OrgDate\":\"2026-11-23\",\"OrgLessonNo\":\"2\"}")))
                .isEqualTo(LessonStatus.MOVED_IN);
    }

    @Test
    void timetableWeekParsesNestedSlotsWithStringIds() throws Exception {
        JsonNode root = json("""
                {"Timetable":{"2026-11-24":[[],[{"LessonNo":"1","HourFrom":"08:00","HourTo":"08:45",
                  "Subject":{"Id":"42005","Name":"Matematyka"},"Teacher":{"Id":"7","FirstName":"Anna","LastName":"Nowak"},
                  "Classroom":{"Id":"2000"},"IsCanceled":true,"IsSubstitutionClass":true,
                  "NewDate":"2026-11-26","NewLessonNo":"4"}]],
                  "2026-11-23":[[{"LessonNo":"0","Subject":{"Id":"1","Name":"Religia"},"Teacher":{}}]],
                  "garbage":[[{}]]}}
                """);

        List<TimetableEntry> week = TimetableParser.week(root);

        assertThat(week).hasSize(2);
        assertThat(week.getFirst().date()).isEqualTo(DAY);
        TimetableEntry moved = week.get(1);
        assertThat(moved.subjectId()).isEqualTo(42005L);
        assertThat(moved.teacherName()).isEqualTo("Anna Nowak");
        assertThat(moved.classroomId()).isEqualTo(2000L);
        assertThat(moved.status()).isEqualTo(LessonStatus.MOVED_OUT);
        assertThat(moved.movedToDate()).isEqualTo(LocalDate.of(2026, 11, 26));
        assertThat(moved.movedToLessonNo()).isEqualTo(4);
        assertThat(moved.originalTeacher()).isNull();
    }

    @Test
    void luckyNumberMissingMeansEmpty() throws Exception {
        assertThat(RecordParser.luckyNumber(json("{}"))).isEmpty();
        assertThat(RecordParser.luckyNumber(json("{\"LuckyNumber\":{\"LuckyNumber\":7}}"))).isEmpty();
    }

    @Test
    void meDetectsParentAccounts() throws Exception {
        var me = DictionaryParser.me(json(
                "{\"Me\":{\"Account\":{\"Id\":1,\"Login\":\"1u\",\"GroupId\":8},\"User\":{\"FirstName\":\"Ola\"}}}"));

        assertThat(me.parentAccount()).isFalse();
        assertThat(me.studentName()).isEqualTo("Ola");
        assertThat(me.studentUserId()).isNull();
    }
}
