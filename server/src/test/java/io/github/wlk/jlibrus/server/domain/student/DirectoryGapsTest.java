package io.github.wlk.jlibrus.server.domain.student;

import io.github.wlk.jlibrus.client.model.Attendance;
import io.github.wlk.jlibrus.client.model.CalendarEvent;
import io.github.wlk.jlibrus.client.model.Grade;
import io.github.wlk.jlibrus.client.model.GradeKind;
import io.github.wlk.jlibrus.client.model.Note;
import io.github.wlk.jlibrus.client.model.NotePolarity;
import io.github.wlk.jlibrus.server.domain.TestStudent;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static io.github.wlk.jlibrus.server.domain.TestStudent.TODAY;
import static org.assertj.core.api.Assertions.assertThat;

class DirectoryGapsTest {

    private final Directory directory = TestStudent.directory();

    private static StudentRecords with(List<Grade> grades, List<Attendance> attendances, List<CalendarEvent> events,
                                       List<Note> notes) {
        return new StudentRecords(grades, List.of(), attendances, events, List.of(), List.of(), List.of(), List.of(),
                List.of(), notes, Optional.empty(), List.of(), List.of(), List.of());
    }

    private static Grade grade(long subject, Long category, Long teacher) {
        return new Grade(1, "5", category, subject, teacher, 1, null, GradeKind.REGULAR, List.of(), null);
    }

    @Test
    void completeDirectoryHasNoGaps() {
        StudentRecords sample = TestStudent.records();
        StudentRecords known = with(sample.grades(), sample.attendances().subList(0, 2), sample.events(), sample.notes());
        assertThat(DirectoryGaps.stale(directory, known)).isFalse();
        assertThat(DirectoryGaps.stale(directory, sample)).isTrue();
        assertThat(DirectoryGaps.stale(directory, StudentRecords.empty())).isFalse();
    }

    @Test
    void unknownGradeReferencesAreGaps() {
        assertThat(DirectoryGaps.stale(directory, with(List.of(grade(999, 1L, 11L)), List.of(), List.of(), List.of()))).isTrue();
        assertThat(DirectoryGaps.stale(directory, with(List.of(grade(100, 77L, 11L)), List.of(), List.of(), List.of()))).isTrue();
        assertThat(DirectoryGaps.stale(directory, with(List.of(grade(100, 1L, 77L)), List.of(), List.of(), List.of()))).isTrue();
        assertThat(DirectoryGaps.stale(directory, with(List.of(grade(100, null, null)), List.of(), List.of(), List.of()))).isFalse();
    }

    @Test
    void unknownAttendanceEventAndNoteReferencesAreGaps() {
        Attendance unknownLesson = new Attendance("a", 777L, TODAY, 1, 1, 100, null, null);
        Attendance unknownType = new Attendance("b", 500L, TODAY, 1, 1, 555, null, null);
        Attendance noLesson = new Attendance("c", null, TODAY, 1, 1, 100, null, null);
        CalendarEvent unknownCategory = new CalendarEvent(1, "x", TODAY, 31L, null, null, null, null, null, null, null);
        CalendarEvent unknownSubject = new CalendarEvent(1, "x", TODAY, null, 31L, null, null, null, null, null, null);
        Note unknownNoteCategory = new Note(1, "x", 99L, null, TODAY, NotePolarity.NEUTRAL);

        assertThat(DirectoryGaps.stale(directory, with(List.of(), List.of(unknownLesson), List.of(), List.of()))).isTrue();
        assertThat(DirectoryGaps.stale(directory, with(List.of(), List.of(unknownType), List.of(), List.of()))).isTrue();
        assertThat(DirectoryGaps.stale(directory, with(List.of(), List.of(noLesson), List.of(), List.of()))).isFalse();
        assertThat(DirectoryGaps.stale(directory, with(List.of(), List.of(), List.of(unknownCategory), List.of()))).isTrue();
        assertThat(DirectoryGaps.stale(directory, with(List.of(), List.of(), List.of(unknownSubject), List.of()))).isTrue();
        assertThat(DirectoryGaps.stale(directory, with(List.of(), List.of(), List.of(), List.of(unknownNoteCategory)))).isTrue();
    }
}
