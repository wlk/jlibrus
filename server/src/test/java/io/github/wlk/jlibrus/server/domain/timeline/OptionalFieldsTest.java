package io.github.wlk.jlibrus.server.domain.timeline;

import io.github.wlk.jlibrus.server.domain.EmailAddress;
import io.github.wlk.jlibrus.server.domain.EntryType;
import io.github.wlk.jlibrus.server.domain.TestStudent;
import io.github.wlk.jlibrus.server.domain.Texts;
import io.github.wlk.jlibrus.server.domain.TimelineEntry;

import io.github.wlk.jlibrus.client.model.Attendance;
import io.github.wlk.jlibrus.client.model.DescriptiveGrade;
import io.github.wlk.jlibrus.client.model.Grade;
import io.github.wlk.jlibrus.client.model.GradeKind;
import io.github.wlk.jlibrus.client.model.Justification;
import io.github.wlk.jlibrus.client.model.JustificationStatus;
import io.github.wlk.jlibrus.client.model.Note;
import io.github.wlk.jlibrus.client.model.NotePolarity;
import io.github.wlk.jlibrus.server.domain.agenda.AgendaItem;
import io.github.wlk.jlibrus.server.domain.ical.CalendarItem;
import io.github.wlk.jlibrus.server.domain.ical.ICalendarWriter;
import io.github.wlk.jlibrus.server.domain.ical.When;
import io.github.wlk.jlibrus.server.domain.summary.Frequency;
import io.github.wlk.jlibrus.server.domain.summary.Summary;
import io.github.wlk.jlibrus.server.domain.summary.SummaryRenderer;
import io.github.wlk.jlibrus.server.domain.summary.SummaryWindow;
import io.github.wlk.jlibrus.server.domain.view.RecordViews;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Locale;

import static io.github.wlk.jlibrus.server.domain.TestStudent.CHILD;
import static io.github.wlk.jlibrus.server.domain.TestStudent.TODAY;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Every optional field missing, present or blank, through timeline, views, iCal and e-mail. */
class OptionalFieldsTest {

    private static final Locale PL = Locale.forLanguageTag("pl");
    private static final ZoneId WARSAW = ZoneId.of("Europe/Warsaw");
    private final TimelineExtractor extractor = new TimelineExtractor(CHILD, TestStudent.directory(), WARSAW, PL);

    private static DescriptiveGrade descriptive(Long subject, String skill, java.time.LocalDate date,
                                                java.time.LocalDateTime added) {
        return new DescriptiveGrade("9", subject, "B", skill, null, date, added, 1, List.of(), List.of(),
                DescriptiveGrade.DescriptiveSystem.PARTIAL);
    }

    @Test
    void attendanceWithoutDateOrLessonNumber() {
        assertThat(extractor.attendance(new Attendance("x", 500L, null, 1, 1, 1, null, null))).isEmpty();
        assertThat(extractor.attendance(new Attendance("y", 500L, TODAY, null, 1, 1, null, null)).orElseThrow().title())
                .isEqualTo("Nieobecność: Matematyka");
    }

    @Test
    void descriptiveTitlesAndDatesFallBack() {
        assertThat(extractor.descriptive(descriptive(null, null, TODAY, null)).title()).isEqualTo("?: B");
        assertThat(extractor.descriptive(descriptive(100L, null, TODAY, null)).title()).isEqualTo("Matematyka: B");
        assertThat(extractor.descriptive(descriptive(100L, "", TODAY, null)).title()).isEqualTo("Matematyka: B");
        assertThat(extractor.descriptive(descriptive(null, null, null, TODAY.atTime(9, 0))).occursOn()).isEqualTo(TODAY);
        assertThat(extractor.descriptive(descriptive(null, null, null, null)).occursOn()).isNull();
    }

    @Test
    void unresolvedGradeCommentsAndUncategorisedNotes() {
        Grade grade = new Grade(4, "3", null, 100, null, 1, null, GradeKind.REGULAR, List.of(999L), null);
        Note note = new Note(5, "x", null, null, TODAY, NotePolarity.POSITIVE);

        assertThat(extractor.grade(grade).detail()).isNull();
        assertThat(extractor.note(note).title()).isEqualTo("Pochwała");
    }

    @Test
    void justificationRanges() {
        assertThat(TimelineExtractor.range(null, TODAY)).isEmpty();
        assertThat(TimelineExtractor.range(TODAY, null)).isEqualTo(": " + TODAY);
        assertThat(TimelineExtractor.range(TODAY, TODAY)).isEqualTo(": " + TODAY);
        assertThat(TimelineExtractor.range(TODAY, TODAY.plusDays(1))).isEqualTo(": " + TODAY + " – " + TODAY.plusDays(1));
        Justification noDates = new Justification(1, JustificationStatus.REJECTED, "", null, null, null, List.of(), 0, false,
                List.of());
        assertThat(extractor.justification(noDates).title()).isEqualTo("Usprawiedliwienie odrzucone");
        assertThat(extractor.justification(noDates).detail()).isNull();
        assertThat(extractor.justification(noDates).author()).isNull();
    }

    @Test
    void attendanceViewWithUnknownTypeAndMissingColour() {
        var unknownType = RecordViews.attendance(TestStudent.directory(),
                List.of(new Attendance("u", 500L, TODAY, null, 1, 777, null, null),
                        new Attendance("v", 500L, TODAY, 2, 1, 1, null, null),
                        new Attendance("w", 500L, TODAY, null, 1, 1, null, null)), List.of());

        assertThat(unknownType.absences()).extracting(RecordViews.AttendanceView::id).containsExactly("w", "v");
        RecordViews.AttendanceView absent = unknownType.absences().getFirst();
        assertThat(absent.colorHex()).isNull();
        assertThat(absent.shortName()).isEqualTo("nb");
        assertThat(unknownType.stats().byKind().get(io.github.wlk.jlibrus.client.model.AttendanceKind.PRESENT)).isEqualTo(1);
    }

    @Test
    void icalWritesEveryStructuralLineAndSkipsBlankOptionalFields() {
        String ics = new ICalendarWriter(WARSAW, () -> Instant.EPOCH).write("Kalendarz", List.of(
                new CalendarItem("u1", "A", " ", "", "EVENT", When.AllDay.single(TODAY))));

        assertThat(ics).contains("CALSCALE:GREGORIAN\r\n", "X-WR-TIMEZONE:Europe/Warsaw\r\n", "BEGIN:VEVENT\r\n",
                "UID:u1\r\n", "SUMMARY:A\r\n", "TRANSP:TRANSPARENT\r\n", "END:VEVENT\r\n", "PRODID:")
                .doesNotContain("DESCRIPTION", "LOCATION");
    }

    @Test
    void summaryWithoutDetailsAuthorsOrLocations() {
        TimelineEntry bare = new TimelineEntry(CHILD, "k", EntryType.GRADE, "Tytuł", null, null, null, TODAY, null,
                Instant.EPOCH);
        AgendaItem located = new AgendaItem("a", CHILD, EntryType.EVENT, "Sprawdzian", null, null, "s. 12",
                When.AllDay.single(TODAY));
        AgendaItem unlocated = new AgendaItem("b", CHILD, EntryType.EVENT, "Kartkówka", null, null, null,
                When.AllDay.single(TODAY));
        Summary summary = new Summary(Frequency.DAILY, SummaryWindow.of(Frequency.DAILY, TODAY.atTime(18, 0).atZone(WARSAW)), PL,
                List.of(new Summary.ChildSection(CHILD, "Ola", List.of(bare), List.of(located, unlocated))));

        String html = SummaryRenderer.html(summary);
        String text = SummaryRenderer.text(summary);

        assertThat(html).contains("<strong>Tytuł</strong></li>", "(s. 12)", "Kartkówka</td>").doesNotContain("<em");
        assertThat(text).contains("[Oceny] Tytuł\n").doesNotContain("Tytuł –").contains("Sprawdzian").contains("Kartkówka");
        assertThat(html).doesNotContain(SummaryRendererHolder.NOTHING_NEW);
    }

    @Test
    void emailLengthBoundaries() {
        String local = "x".repeat(64);
        String domain = String.join(".", List.of("a".repeat(63), "b".repeat(63), "c".repeat(55))) + ".pl";
        String exactly254 = local + "@" + domain.substring(0, 254 - local.length() - 1 - 3) + ".pl";

        assertThat(exactly254).hasSize(254);
        assertThat(EmailAddress.parse(exactly254).toString()).isEqualTo(exactly254);
        assertThatThrownBy(() -> EmailAddress.parse("y" + exactly254)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> EmailAddress.parse("@a.pl")).isInstanceOf(IllegalArgumentException.class);
    }

    /** Polish "nothing new" text, kept here so the assertion reads clearly. */
    private static final class SummaryRendererHolder {
        static final String NOTHING_NEW = Texts.get("summary.nothing", PL);
    }
}
