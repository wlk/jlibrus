package io.github.wlk.jlibrus.server.domain.agenda;

import io.github.wlk.jlibrus.client.model.CalendarEvent;
import io.github.wlk.jlibrus.client.model.FreeDay;
import io.github.wlk.jlibrus.client.model.Homework;
import io.github.wlk.jlibrus.client.model.LessonStatus;
import io.github.wlk.jlibrus.client.model.ParentMeeting;
import io.github.wlk.jlibrus.client.model.TeacherAbsence;
import io.github.wlk.jlibrus.client.model.TimetableEntry;
import io.github.wlk.jlibrus.server.domain.TestStudent;
import io.github.wlk.jlibrus.server.domain.ical.When;
import io.github.wlk.jlibrus.server.domain.student.StudentRecords;
import org.junit.jupiter.api.Test;

import java.time.LocalTime;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import static io.github.wlk.jlibrus.server.domain.TestStudent.TODAY;
import static org.assertj.core.api.Assertions.assertThat;

/** Incomplete or odd Librus data never breaks the agenda. */
class AgendaEdgeCasesTest {

    private final AgendaBuilder builder = new AgendaBuilder("c", TestStudent.directory(), Locale.forLanguageTag("pl"));
    private static final LocalTime EIGHT = LocalTime.of(8, 0);

    private static CalendarEvent event(Integer lessonNo, LocalTime from, LocalTime to) {
        return new CalendarEvent(1, "Kartkówka", TODAY, null, null, null, lessonNo, from, to, null, null);
    }

    @Test
    void itemsWithoutDatesAreSkipped() {
        StudentRecords undated = new StudentRecords(List.of(), List.of(), List.of(),
                List.of(new CalendarEvent(1, "x", null, null, null, null, null, null, null, null, null)),
                List.of(new Homework(2, "t", "x", null, TODAY, null, List.of())),
                List.of(new ParentMeeting(3, "m", "", "", null, null, null)),
                List.of(new FreeDay(4, "f", null, null)),
                List.of(new TeacherAbsence(5, null, null, null, null, null)),
                List.of(), List.of(), Optional.empty());

        assertThat(builder.build(undated, List.of())).isEmpty();
    }

    @Test
    void eventTimingFallsBackFromExplicitTimesToBellScheduleToAllDay() {
        assertThat(builder.event(event(1, EIGHT, null)).when())
                .isEqualTo(new When.Timed(TODAY.atTime(8, 0), TODAY.atTime(8, 45)));
        assertThat(builder.event(event(1, EIGHT, EIGHT)).when())
                .isEqualTo(new When.Timed(TODAY.atTime(8, 0), TODAY.atTime(8, 45)));
        assertThat(builder.event(event(1, null, EIGHT)).when())
                .isEqualTo(new When.Timed(TODAY.atTime(8, 0), TODAY.atTime(8, 45)));
        assertThat(builder.event(event(9, null, null)).when()).isEqualTo(When.AllDay.single(TODAY));
        assertThat(builder.event(event(null, null, null)).when()).isEqualTo(When.AllDay.single(TODAY));
        assertThat(builder.event(event(null, EIGHT, LocalTime.of(9, 0))).when())
                .isEqualTo(new When.Timed(TODAY.atTime(8, 0), TODAY.atTime(9, 0)));
    }

    @Test
    void freeDayWithMissingOrInvertedEndIsOneDay() {
        assertThat(builder.freeDay(new FreeDay(1, "x", TODAY, null)).when()).isEqualTo(When.AllDay.single(TODAY));
        assertThat(builder.freeDay(new FreeDay(1, "x", TODAY, TODAY.minusDays(1))).when()).isEqualTo(When.AllDay.single(TODAY));
    }

    @Test
    void teacherAbsenceTimesOnlyApplyToSingleDaysWithValidRange() {
        LocalTime ten = LocalTime.of(10, 0);
        assertThat(builder.teacherAbsence(new TeacherAbsence(1, 11L, TODAY, TODAY.plusDays(1), EIGHT, ten)).when())
                .isEqualTo(new When.AllDay(TODAY, TODAY.plusDays(1)));
        assertThat(builder.teacherAbsence(new TeacherAbsence(1, 11L, TODAY, TODAY, ten, EIGHT)).when())
                .isEqualTo(When.AllDay.single(TODAY));
        assertThat(builder.teacherAbsence(new TeacherAbsence(1, 11L, TODAY, TODAY, EIGHT, null)).when())
                .isEqualTo(When.AllDay.single(TODAY));
        assertThat(builder.teacherAbsence(new TeacherAbsence(1, 11L, TODAY, TODAY, null, ten)).when())
                .isEqualTo(When.AllDay.single(TODAY));
        assertThat(builder.teacherAbsence(new TeacherAbsence(1, 11L, TODAY, null, null, null)).when())
                .isEqualTo(When.AllDay.single(TODAY));
        assertThat(builder.teacherAbsence(new TeacherAbsence(1, 11L, TODAY, TODAY.minusDays(2), null, null)).when())
                .isEqualTo(When.AllDay.single(TODAY));
    }

    @Test
    void lessonTimingFallsBackToBellSchedule() {
        TimetableEntry noTimes = new TimetableEntry(TODAY, 2, null, null, null, "Plastyka", null, "", null,
                LessonStatus.NORMAL, null, null, null, null);
        TimetableEntry onlyStart = new TimetableEntry(TODAY, 2, EIGHT, null, null, "Plastyka", null, "", null,
                LessonStatus.NORMAL, null, null, null, null);
        TimetableEntry onlyEnd = new TimetableEntry(TODAY, 2, null, EIGHT, null, "Plastyka", null, "", null,
                LessonStatus.NORMAL, null, null, null, null);
        When slot2 = new When.Timed(TODAY.atTime(8, 55), TODAY.atTime(9, 40));

        assertThat(builder.lesson(noTimes).when()).isEqualTo(slot2);
        assertThat(builder.lesson(onlyStart).when()).isEqualTo(slot2);
        assertThat(builder.lesson(onlyEnd).when()).isEqualTo(slot2);
        assertThat(builder.lesson(noTimes).detail()).isNull();
        assertThat(builder.lesson(noTimes).location()).isNull();
    }

    @Test
    void allDayItemsSortBeforeTimedOnesOfTheSameDay() {
        StudentRecords records = new StudentRecords(List.of(), List.of(), List.of(),
                List.of(event(null, EIGHT, LocalTime.of(9, 0))),
                List.of(new Homework(2, "Zadanie", "x", null, TODAY, TODAY, List.of())),
                List.of(), List.of(), List.of(), List.of(), List.of(), Optional.empty());

        assertThat(builder.build(records, List.of())).extracting(AgendaItem::type)
                .containsExactly(io.github.wlk.jlibrus.server.domain.EntryType.HOMEWORK,
                        io.github.wlk.jlibrus.server.domain.EntryType.EVENT);
    }
}
