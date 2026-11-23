package io.github.wlk.jlibrus.server.domain.agenda;

import io.github.wlk.jlibrus.client.model.LessonStatus;
import io.github.wlk.jlibrus.client.model.TimetableEntry;
import io.github.wlk.jlibrus.server.domain.EntryType;
import io.github.wlk.jlibrus.server.domain.TestStudent;
import io.github.wlk.jlibrus.server.domain.ical.When;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Locale;

import static io.github.wlk.jlibrus.server.domain.TestStudent.TODAY;
import static org.assertj.core.api.Assertions.assertThat;

class AgendaBuilderTest {

    private final AgendaBuilder builder = new AgendaBuilder(TestStudent.CHILD, TestStudent.directory(), Locale.forLanguageTag("pl"));
    private final List<AgendaItem> items = builder.build(TestStudent.records(), TestStudent.timetable());

    private AgendaItem find(String uidPrefix) {
        return items.stream().filter(i -> i.uid().startsWith(uidPrefix)).findFirst().orElseThrow();
    }

    @Test
    void itemsAreSortedByDayThenTime() {
        assertThat(items).extracting(i -> i.when().firstDay()).isSorted();
        assertThat(items.getFirst().uid()).isEqualTo("lesson-1234567u-2026-11-23-1@jlibrus");
    }

    @Test
    void eventWithLessonNumberUsesBellSchedule() {
        AgendaItem event = find("event-1234567u-70");

        assertThat(event.title()).isEqualTo("Sprawdzian – Matematyka");
        assertThat(event.detail()).isEqualTo("Ułamki");
        assertThat(event.subject()).isEqualTo("Matematyka");
        assertThat(event.when()).isEqualTo(new When.Timed(TODAY.plusDays(2).atTime(8, 0), TODAY.plusDays(2).atTime(8, 45)));
    }

    @Test
    void eventWithExplicitTimesAndNoCategoryUsesContentAsTitle() {
        AgendaItem trip = find("event-1234567u-71");

        assertThat(trip.title()).isEqualTo("Wycieczka");
        assertThat(trip.subject()).isNull();
        assertThat(trip.when()).isEqualTo(new When.Timed(TODAY.plusDays(9).atTime(8, 0), TODAY.plusDays(9).atTime(13, 0)));
    }

    @Test
    void homeworkIsDueAllDayWithTeacher() {
        AgendaItem homework = find("homework-");

        assertThat(homework.title()).isEqualTo("Zadanie domowe: Zadania");
        assertThat(homework.detail()).isEqualTo("Str. 42\nAnna Nowak");
        assertThat(homework.when()).isEqualTo(When.AllDay.single(TODAY.plusDays(2)));
    }

    @Test
    void meetingsLastAnHourOrAllDay() {
        assertThat(find("parent_meeting-1234567u-90").when())
                .isEqualTo(new When.Timed(TODAY.plusDays(6).atTime(17, 0), TODAY.plusDays(6).atTime(18, 0)));
        assertThat(find("parent_meeting-1234567u-90").location()).isEqualTo("14");
        assertThat(find("parent_meeting-1234567u-91").when()).isEqualTo(When.AllDay.single(TODAY.plusDays(20)));
        assertThat(find("parent_meeting-1234567u-91").location()).isNull();
        assertThat(find("parent_meeting-1234567u-91").detail()).isEqualTo("Jan Kowal");
    }

    @Test
    void freeDaysAndTeacherAbsences() {
        assertThat(find("free_day-").when()).isEqualTo(new When.AllDay(LocalDate.of(2026, 12, 23), LocalDate.of(2026, 12, 31)));
        assertThat(find("teacher_absence-1234567u-96").title()).isEqualTo("Nieobecność nauczyciela: Jan Kowal");
        assertThat(find("teacher_absence-1234567u-96").when()).isEqualTo(When.AllDay.single(TODAY.plusDays(1)));
        assertThat(find("teacher_absence-1234567u-97").when())
                .isEqualTo(new When.Timed(TODAY.atTime(10, 0), TODAY.atTime(11, 0)));
    }

    @Test
    void timetableChangesAndLessons() {
        AgendaItem substitution = find("timetable_change-1234567u-2026-11-23-2-SUBSTITUTION");
        assertThat(substitution.title()).isEqualTo("Zastępstwo: Historia");
        assertThat(substitution.detail()).isEqualTo("Jan Kowal → Anna Nowak");
        AgendaItem cancelled = find("timetable_change-1234567u-2026-11-24-1-CANCELLED");
        assertThat(cancelled.title()).isEqualTo("Odwołana lekcja: Matematyka");
        assertThat(cancelled.location()).isEqualTo("12 Sala matematyczna");
        assertThat(cancelled.when()).isEqualTo(new When.Timed(TODAY.plusDays(1).atTime(8, 0), TODAY.plusDays(1).atTime(8, 45)));
        AgendaItem lesson = find("lesson-");
        assertThat(lesson.type()).isEqualTo(EntryType.LESSON);
        assertThat(lesson.detail()).isEqualTo("Anna Nowak");
    }

    @Test
    void movedLessonsDescribeTarget() {
        TimetableEntry moved = new TimetableEntry(TODAY, 7, null, null, 100L, "Matematyka", null, "", null,
                LessonStatus.MOVED_OUT, null, null, TODAY.plusDays(2), 3);
        TimetableEntry movedIn = new TimetableEntry(TODAY, 1, LocalTime.of(8, 0), LocalTime.of(7, 0), 100L, "Matematyka",
                null, "", null, LessonStatus.MOVED_IN, null, null, null, null);

        assertThat(builder.change(moved).title()).isEqualTo("Lekcja przeniesiona na inny termin: Matematyka");
        assertThat(builder.change(moved).detail()).isEqualTo("2026-11-25 (3)");
        assertThat(builder.change(moved).when()).isEqualTo(When.AllDay.single(TODAY));
        assertThat(builder.change(movedIn).title()).startsWith("Przeniesiona lekcja");
        assertThat(builder.change(movedIn).when()).isEqualTo(
                new When.Timed(LocalDateTime.of(2026, 11, 23, 8, 0), LocalDateTime.of(2026, 11, 23, 8, 45)));
    }

    @Test
    void englishTitles() {
        AgendaBuilder english = new AgendaBuilder("c", TestStudent.directory(), Locale.ENGLISH);

        assertThat(english.build(TestStudent.records(), List.of())).anySatisfy(i ->
                assertThat(i.title()).isEqualTo("Homework due: Zadania"));
    }

    @Test
    void joinSkipsBlanks() {
        assertThat(AgendaBuilder.join(", ", " a ", null, "", "b")).isEqualTo("a, b");
    }
}
