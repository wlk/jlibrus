package io.github.wlk.jlibrus.server.domain.view;

import io.github.wlk.jlibrus.client.grades.GradeScale;
import io.github.wlk.jlibrus.client.model.AttendanceKind;
import io.github.wlk.jlibrus.client.model.GradeKind;
import io.github.wlk.jlibrus.client.model.LessonStatus;
import io.github.wlk.jlibrus.server.domain.TestStudent;
import io.github.wlk.jlibrus.server.domain.agenda.AgendaBuilder;
import io.github.wlk.jlibrus.server.domain.agenda.AgendaItem;
import io.github.wlk.jlibrus.server.domain.student.Directory;
import io.github.wlk.jlibrus.server.domain.student.StudentRecords;
import org.junit.jupiter.api.Test;

import java.time.LocalTime;
import java.util.List;
import java.util.Locale;

import static io.github.wlk.jlibrus.server.domain.TestStudent.TODAY;
import static org.assertj.core.api.Assertions.assertThat;

class ViewsTest {

    private final Directory directory = TestStudent.directory();
    private final StudentRecords records = TestStudent.records();

    @Test
    void gradesAreGroupedBySubjectWithAverages() {
        List<GradeViews.SubjectGrades> subjects = GradeViews.bySubject(directory, records.grades(), GradeScale.DEFAULT);

        assertThat(subjects).extracting(GradeViews.SubjectGrades::subject).containsExactly("Historia", "Matematyka");
        GradeViews.SubjectGrades maths = subjects.get(1);
        assertThat(maths.average()).isEqualTo(5.0);
        assertThat(maths.averageSemester1()).isEqualTo(5.0);
        assertThat(maths.averageSemester2()).isNull();
        assertThat(maths.semester1()).singleElement().satisfies(g -> {
            assertThat(g.category()).isEqualTo("Sprawdzian");
            assertThat(g.weight()).isEqualTo(3);
            assertThat(g.comment()).isEqualTo("Brawo");
            assertThat(g.teacher()).isEqualTo("Anna Nowak");
            assertThat(g.colorHex()).isEqualTo("#FF0000");
        });
        assertThat(maths.summary()).singleElement().satisfies(g -> {
            assertThat(g.kind()).isEqualTo(GradeKind.SEMESTER_PROPOSAL);
            assertThat(g.weight()).isZero();
            assertThat(g.comment()).isNull();
        });
        GradeViews.SubjectGrades history = subjects.getFirst();
        assertThat(history.average()).isNull();
        assertThat(history.semester1().getFirst().weight()).isZero();
    }

    @Test
    void unknownSubjectShowsItsId() {
        var grade = new io.github.wlk.jlibrus.client.model.Grade(9, "3", 1L, 999, null, 2, null, GradeKind.REGULAR,
                List.of(), null);

        assertThat(GradeViews.bySubject(directory, List.of(grade), GradeScale.DEFAULT).getFirst().subject()).isEqualTo("#999");
    }

    @Test
    void attendanceReportCountsKindsAndListsNonPresence() {
        RecordViews.AttendanceReport report = RecordViews.attendance(directory, records.attendances());

        assertThat(report.stats().lessons()).isEqualTo(3);
        assertThat(report.stats().byKind()).containsEntry(AttendanceKind.ABSENT, 1).containsEntry(AttendanceKind.LATE, 1)
                .containsEntry(AttendanceKind.PRESENT, 1).containsEntry(AttendanceKind.EXCUSED, 0);
        assertThat(report.stats().presencePercent()).isEqualTo(66.7);
        assertThat(report.absences()).extracting(RecordViews.AttendanceView::id).containsExactly("t1", "t3");
        assertThat(report.absences().getFirst().subject()).isEqualTo("Matematyka");
        assertThat(report.absences().get(1).subject()).isEmpty();
        assertThat(RecordViews.attendance(directory, List.of()).stats().presencePercent()).isNull();
    }

    @Test
    void lessonsResolveRooms() {
        List<RecordViews.LessonView> lessons = RecordViews.lessons(directory, TestStudent.timetable());

        assertThat(lessons.getFirst().room()).isEqualTo("12 Sala matematyczna");
        assertThat(lessons.get(1).status()).isEqualTo(LessonStatus.SUBSTITUTION);
        assertThat(lessons.get(1).originalTeacher()).isEqualTo("Jan Kowal");
    }

    @Test
    void agendaViewFlattensTiming() {
        List<AgendaItem> items = new AgendaBuilder("c", directory, Locale.ENGLISH).build(records, TestStudent.timetable());
        RecordViews.AgendaView timed = RecordViews.agenda(items.getFirst());
        RecordViews.AgendaView allDay = items.stream().filter(i -> i.uid().startsWith("free_day")).map(RecordViews::agenda)
                .findFirst().orElseThrow();

        assertThat(timed.allDay()).isFalse();
        assertThat(timed.startTime()).isEqualTo(LocalTime.of(8, 0));
        assertThat(allDay.allDay()).isTrue();
        assertThat(allDay.end()).isEqualTo(TODAY.withMonth(12).withDayOfMonth(31));
    }

    @Test
    void listsAreNewestFirstWithNames() {
        assertThat(RecordViews.homework(directory, records.homework()).getFirst().teacher()).isEqualTo("Anna Nowak");
        assertThat(RecordViews.notes(directory, records.notes()).getFirst().category()).isEqualTo("Zachowanie");
        assertThat(RecordViews.behaviour(directory, records.behaviour()).getFirst().teacher()).isEqualTo("Anna Nowak");
        assertThat(RecordViews.announcements(directory, records.announcements()).getFirst().author()).isEqualTo("Jan Kowal");
    }

    @Test
    void paletteFallsBackToBlue() {
        assertThat(Palette.hex(null)).isEqualTo("#2196F3");
        assertThat(Palette.hex(0)).isEqualTo("#2196F3");
        assertThat(Palette.hex(99)).isEqualTo("#2196F3");
        assertThat(Palette.hex(1)).isEqualTo("#F0E68C");
        assertThat(Palette.hex(31)).isEqualTo("#7CFC00");
    }
}
