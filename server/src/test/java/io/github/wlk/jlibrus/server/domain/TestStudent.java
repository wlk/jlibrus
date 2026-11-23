package io.github.wlk.jlibrus.server.domain;

import io.github.wlk.jlibrus.client.model.AccountInfo;
import io.github.wlk.jlibrus.client.model.Announcement;
import io.github.wlk.jlibrus.client.model.Attendance;
import io.github.wlk.jlibrus.client.model.AttendanceKind;
import io.github.wlk.jlibrus.client.model.AttendanceType;
import io.github.wlk.jlibrus.client.model.BehaviourPoint;
import io.github.wlk.jlibrus.client.model.CalendarEvent;
import io.github.wlk.jlibrus.client.model.DescriptiveGrade;
import io.github.wlk.jlibrus.client.model.Justification;
import io.github.wlk.jlibrus.client.model.JustificationStatus;
import io.github.wlk.jlibrus.client.model.JustifiedLesson;
import io.github.wlk.jlibrus.client.model.SchoolTrip;
import io.github.wlk.jlibrus.client.model.Classroom;
import io.github.wlk.jlibrus.client.model.EventCategory;
import io.github.wlk.jlibrus.client.model.FreeDay;
import io.github.wlk.jlibrus.client.model.Grade;
import io.github.wlk.jlibrus.client.model.GradeCategory;
import io.github.wlk.jlibrus.client.model.GradeComment;
import io.github.wlk.jlibrus.client.model.GradeKind;
import io.github.wlk.jlibrus.client.model.Homework;
import io.github.wlk.jlibrus.client.model.LessonAssignment;
import io.github.wlk.jlibrus.client.model.LessonSlot;
import io.github.wlk.jlibrus.client.model.LessonStatus;
import io.github.wlk.jlibrus.client.model.LuckyNumber;
import io.github.wlk.jlibrus.client.model.Note;
import io.github.wlk.jlibrus.client.model.NoteCategory;
import io.github.wlk.jlibrus.client.model.NotePolarity;
import io.github.wlk.jlibrus.client.model.ParentMeeting;
import io.github.wlk.jlibrus.client.model.Person;
import io.github.wlk.jlibrus.client.model.School;
import io.github.wlk.jlibrus.client.model.SchoolClass;
import io.github.wlk.jlibrus.client.model.Subject;
import io.github.wlk.jlibrus.client.model.TeacherAbsence;
import io.github.wlk.jlibrus.client.model.TimetableEntry;
import io.github.wlk.jlibrus.server.domain.student.Directory;
import io.github.wlk.jlibrus.server.domain.student.StudentRecords;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Hand-built child data for pure domain tests (Monday 2026-11-23). */
public final class TestStudent {

    public static final String CHILD = "1234567u";
    public static final LocalDate TODAY = LocalDate.of(2026, 11, 23);

    private TestStudent() {
    }

    public static Directory directory() {
        return new Directory(
                new AccountInfo(1, CHILD, "Małgorzata", "Kowalska", true, "Ola", "Kowalska", 5L),
                new SchoolClass(7, "4b", LocalDate.of(2026, 9, 1), LocalDate.of(2027, 1, 25), LocalDate.of(2027, 6, 26), 11L),
                new School(1, "SP 7", "Przykładowo", List.of(new LessonSlot(1, LocalTime.of(8, 0), LocalTime.of(8, 45)),
                        new LessonSlot(2, LocalTime.of(8, 55), LocalTime.of(9, 40)))),
                Map.of(100L, new Subject(100, "Matematyka", "mat"), 200L, new Subject(200, "Historia", "his")),
                Map.of(11L, new Person(11, "Anna", "Nowak"), 12L, new Person(12, "Jan", "Kowal")),
                Map.of(500L, new LessonAssignment(500, 100, 11L)),
                Map.of(40L, new Classroom(40, "Sala matematyczna", "12")),
                Map.of(1L, new GradeCategory(1, "Sprawdzian", 3, true, 27), 2L, new GradeCategory(2, "Aktywność", null, false, 1)),
                Map.of(9L, new GradeComment(9, "Brawo")),
                Map.of(1L, new AttendanceType(1, "Nieobecność", "nb", AttendanceKind.ABSENT, ""),
                        100L, new AttendanceType(100, "Obecność", "ob", AttendanceKind.PRESENT, ""),
                        2L, new AttendanceType(2, "Spóźnienie", "sp", AttendanceKind.LATE, "")),
                Map.of(30L, new EventCategory(30, "Sprawdzian", 27)),
                Map.of(60L, new NoteCategory(60, "Zachowanie")));
    }

    public static StudentRecords records() {
        LocalDateTime added = TODAY.atTime(9, 30);
        return new StudentRecords(
                List.of(new Grade(1, "5", 1L, 100, 11L, 1, added, GradeKind.REGULAR, List.of(9L), null),
                        new Grade(2, "4+", 2L, 200, 12L, 1, added.minusDays(3), GradeKind.REGULAR, List.of(), null),
                        new Grade(3, "4", null, 100, 11L, 1, null, GradeKind.SEMESTER_PROPOSAL, List.of(), null)),
                List.of(new BehaviourPoint(1, "+5", "Konkurs", null, 11L, 1, added)),
                List.of(new Attendance("t1", 500L, TODAY, 1, 1, 1, 11L, added),
                        new Attendance("t2", 500L, TODAY, 2, 1, 100, 11L, added),
                        new Attendance("t3", 999L, TODAY.minusDays(1), 3, 1, 2, 11L, added.minusDays(1))),
                List.of(new CalendarEvent(70, "Ułamki", TODAY.plusDays(2), 30L, 100L, 11L, 1, null, null, added, null),
                        new CalendarEvent(71, "Wycieczka", TODAY.plusDays(9), null, null, 11L, null,
                                LocalTime.of(8, 0), LocalTime.of(13, 0), added.minusDays(5), "https://x")),
                List.of(new Homework(80, "Zadania", "Str. 42", 11L, TODAY, TODAY.plusDays(2), List.of())),
                List.of(new ParentMeeting(90, "Zebranie", "Wyniki", "14", TODAY.plusDays(6), LocalTime.of(17, 0), 11L),
                        new ParentMeeting(91, "Konsultacje", "", "", TODAY.plusDays(20), null, 12L)),
                List.of(new FreeDay(95, "Przerwa świąteczna", LocalDate.of(2026, 12, 23), LocalDate.of(2026, 12, 31))),
                List.of(new TeacherAbsence(96, 12L, TODAY.plusDays(1), TODAY.plusDays(1), null, null),
                        new TeacherAbsence(97, 11L, TODAY, TODAY, LocalTime.of(10, 0), LocalTime.of(11, 0))),
                List.of(new Announcement("nb-1", "Zbiórka", "Makulatura", TODAY, TODAY.plusDays(30), 12L, added, false)),
                List.of(new Note(50, "Rozmowy", 60L, 12L, TODAY.minusDays(1), NotePolarity.NEGATIVE)),
                Optional.of(new LuckyNumber(7, TODAY)),
                List.of(new DescriptiveGrade("1", 100L, "6", "Liczenie", 11L, TODAY, added, 1, List.of("Brawo"), List.of(),
                                DescriptiveGrade.DescriptiveSystem.CLASSIC),
                        new DescriptiveGrade("p11", null, "B", "Czytanie", null, TODAY.minusDays(1), null, 1, List.of(),
                                List.of("Czyta teksty"), DescriptiveGrade.DescriptiveSystem.PARTIAL)),
                List.of(new Justification(3, JustificationStatus.ACCEPTED, "Choroba", added.minusDays(1), TODAY.minusDays(3),
                        TODAY.minusDays(2), List.of(new JustifiedLesson(TODAY.minusDays(3), 1)), 2, false, List.of("Anna Nowak"))),
                List.of(new SchoolTrip(8, "Teatr Lalek", "Szkoła - Teatr", "autokar", TODAY.plusDays(16), TODAY.plusDays(16),
                        "Nowak Anna")));
    }

    public static List<TimetableEntry> timetable() {
        return List.of(
                new TimetableEntry(TODAY, 1, LocalTime.of(8, 0), LocalTime.of(8, 45), 100L, "Matematyka", 11L,
                        "Anna Nowak", 40L, LessonStatus.NORMAL, null, null, null, null),
                new TimetableEntry(TODAY, 2, LocalTime.of(8, 55), LocalTime.of(9, 40), 200L, "Historia", 11L,
                        "Anna Nowak", null, LessonStatus.SUBSTITUTION, "Historia", "Jan Kowal", null, null),
                new TimetableEntry(TODAY.plusDays(1), 1, null, null, 100L, "Matematyka", 11L, "Anna Nowak", 40L,
                        LessonStatus.CANCELLED, null, null, null, null));
    }
}
