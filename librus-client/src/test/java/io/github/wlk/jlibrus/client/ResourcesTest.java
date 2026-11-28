package io.github.wlk.jlibrus.client;

import io.github.wlk.jlibrus.client.model.AttendanceKind;
import io.github.wlk.jlibrus.client.model.GradeKind;
import io.github.wlk.jlibrus.client.model.LessonStatus;
import io.github.wlk.jlibrus.client.model.NotePolarity;
import io.github.wlk.jlibrus.client.model.TimetableEntry;
import io.github.wlk.jlibrus.mock.MockAccount;
import io.github.wlk.jlibrus.mock.MockLibrus;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.time.LocalTime;
import java.util.List;

import static io.github.wlk.jlibrus.client.MockedLibrus.TODAY;
import static org.assertj.core.api.Assertions.assertThat;

/** Every resource the facade offers, read end-to-end from the mock. */
class ResourcesTest {

    private static MockLibrus mock;
    private static LibrusAccount account;

    @BeforeAll
    static void start() {
        mock = MockedLibrus.start();
        account = MockedLibrus.account(mock, MockAccount.ola());
    }

    @AfterAll
    static void stop() {
        mock.close();
    }

    @Test
    void dictionaries() {
        assertThat(account.subjects()).extracting(s -> s.name()).contains("Matematyka", "Język polski");
        assertThat(account.users()).extracting(p -> p.fullName()).contains("Anna Nowak");
        assertThat(account.lessons()).isNotEmpty().allSatisfy(l -> assertThat(l.subjectId()).isPositive());
        assertThat(account.classrooms()).extracting(c -> c.displayName()).contains("12 Sala matematyczna");
        assertThat(account.gradeCategories()).anySatisfy(c -> {
            assertThat(c.name()).isEqualTo("Aktywność");
            assertThat(c.weight()).isNull();
            assertThat(c.countsToAverage()).isFalse();
        });
        assertThat(account.eventCategories()).extracting(c -> c.name()).contains("Sprawdzian");
        assertThat(account.noteCategories()).extracting(c -> c.name()).contains("Praca na lekcji");
    }

    @Test
    void schoolAndClass() {
        assertThat(account.school().slot(1)).hasValueSatisfying(s -> assertThat(s.from()).isEqualTo(LocalTime.of(8, 0)));
        assertThat(account.school().slot(0)).isEmpty();
        var schoolClass = account.schoolClass();
        assertThat(schoolClass.name()).isEqualTo("4b");
        assertThat(schoolClass.semesterOf(TODAY)).isEqualTo(1);
        assertThat(schoolClass.tutorId()).isEqualTo(1001L);
    }

    @Test
    void gradesAndComments() {
        var grades = account.grades();
        assertThat(grades).isNotEmpty().allSatisfy(g -> {
            assertThat(g.kind()).isEqualTo(GradeKind.REGULAR);
            assertThat(g.addedAt()).isNotNull();
        });
        assertThat(account.gradeComments()).isNotEmpty().allSatisfy(c -> assertThat(c.text()).isNotBlank());
    }

    @Test
    void attendanceWithTypes() {
        assertThat(account.attendances()).isNotEmpty().allSatisfy(a -> assertThat(a.id()).startsWith("t"));
        assertThat(account.attendanceTypes()).anySatisfy(t -> {
            assertThat(t.shortName()).isEqualTo("wy");
            assertThat(t.kind()).isEqualTo(AttendanceKind.PRESENT);
        }).anySatisfy(t -> {
            assertThat(t.shortName()).isEqualTo("nb");
            assertThat(t.kind()).isEqualTo(AttendanceKind.ABSENT);
        });
    }

    @Test
    void timetableOfTheCurrentWeekShowsChanges() {
        List<TimetableEntry> week = account.timetable(TODAY.plusDays(2));

        assertThat(week).isNotEmpty();
        assertThat(week.getFirst().date()).isEqualTo(TODAY);
        assertThat(week).filteredOn(e -> e.status() == LessonStatus.CANCELLED).hasSize(1);
        assertThat(week).filteredOn(e -> e.status() == LessonStatus.SUBSTITUTION).singleElement()
                .satisfies(e -> {
                    assertThat(e.teacherName()).isEqualTo("Joanna Szymańska");
                    assertThat(e.originalTeacher()).isNotBlank();
                });
    }

    @Test
    void calendarData() {
        assertThat(account.events()).hasSize(5).anySatisfy(e -> assertThat(e.subjectId()).isNull());
        assertThat(account.homework()).anySatisfy(h -> assertThat(h.attachmentNames()).containsExactly("karta-pracy.pdf"));
        assertThat(account.parentMeetings()).singleElement().satisfies(m -> assertThat(m.time()).isEqualTo(LocalTime.of(17, 0)));
        assertThat(account.schoolFreeDays()).hasSize(2);
        assertThat(account.teacherAbsences()).singleElement().satisfies(t -> assertThat(t.timeFrom()).isNull());
    }

    @Test
    void notesAnnouncementsLuckyNumberBehaviour() {
        assertThat(account.notes()).extracting(n -> n.polarity()).containsExactly(NotePolarity.POSITIVE, NotePolarity.NEGATIVE);
        assertThat(account.announcements()).hasSize(3).anySatisfy(a -> assertThat(a.read()).isTrue());
        assertThat(account.luckyNumber()).hasValueSatisfying(l -> assertThat(l.day()).isEqualTo(TODAY));
        assertThat(account.behaviour()).extracting(b -> b.display()).containsExactly("bdb", "+5");
    }

    @Test
    void marksAnnouncementRead() {
        account.markAnnouncementRead("nb-open-day");
    }

    @Test
    void justificationsAndSchoolTrips() {
        assertThat(account.justifications()).extracting(j -> j.status())
                .containsExactly(io.github.wlk.jlibrus.client.model.JustificationStatus.PENDING,
                        io.github.wlk.jlibrus.client.model.JustificationStatus.ACCEPTED);
        assertThat(account.schoolTrips()).extracting(t -> t.destination()).containsExactly("Teatr Lalek", "Zielona szkoła");
        assertThat(account.schoolTrips().get(1).coordinator()).isEqualTo("Ewa Lis");
    }

    @Test
    void descriptiveGradesOfBothSystems() {
        var grades = account.descriptiveGrades();

        assertThat(grades).extracting(g -> g.system()).containsExactly(
                io.github.wlk.jlibrus.client.model.DescriptiveGrade.DescriptiveSystem.CLASSIC,
                io.github.wlk.jlibrus.client.model.DescriptiveGrade.DescriptiveSystem.CLASSIC,
                io.github.wlk.jlibrus.client.model.DescriptiveGrade.DescriptiveSystem.PARTIAL);
        assertThat(grades.getFirst().skill()).isEqualTo("Ekspresja muzyczna. Śpiew");
        assertThat(grades.getFirst().comments()).containsExactly("Pięknie śpiewa i zna słowa piosenek.");
        assertThat(grades.get(2).subjectId()).isEqualTo(2002L);
        assertThat(grades.get(2).requirements()).containsExactly("Czyta teksty dla dzieci");
        assertThat(MockedLibrus.account(mock, io.github.wlk.jlibrus.mock.MockAccount.jan()).descriptiveGrades()).isEmpty();
    }
}
