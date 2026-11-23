package io.github.wlk.jlibrus.server.domain.summary;

import io.github.wlk.jlibrus.server.domain.EmailAddress;
import io.github.wlk.jlibrus.server.domain.EntryType;
import io.github.wlk.jlibrus.server.domain.Subscription;
import io.github.wlk.jlibrus.server.domain.TestStudent;
import io.github.wlk.jlibrus.server.domain.TimelineEntry;
import io.github.wlk.jlibrus.server.domain.agenda.AgendaBuilder;
import io.github.wlk.jlibrus.server.domain.agenda.AgendaItem;
import io.github.wlk.jlibrus.server.domain.ical.When;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import static io.github.wlk.jlibrus.server.domain.TestStudent.CHILD;
import static io.github.wlk.jlibrus.server.domain.TestStudent.TODAY;
import static org.assertj.core.api.Assertions.assertThat;

class SummaryBuilderTest {

    private static final Locale PL = Locale.forLanguageTag("pl");
    private static final ZonedDateTime NOW = TODAY.atTime(18, 0).atZone(ZoneId.of("Europe/Warsaw"));
    private static final Instant RECENT = NOW.minusHours(2).toInstant();

    private final List<AgendaItem> agenda = new AgendaBuilder(CHILD, TestStudent.directory(), PL)
            .build(TestStudent.records(), TestStudent.timetable());
    private final Map<String, String> children = new LinkedHashMap<>();

    {
        children.put(CHILD, "Ola Kowalska");
        children.put("other", "Jan");
    }

    private static TimelineEntry entry(String child, String key, EntryType type, Instant noticed) {
        return new TimelineEntry(child, key, type, "Tytuł " + key, "szczegóły <b>", null, "Anna Nowak", TODAY, null, noticed);
    }

    private static Subscription subscription(Set<EntryType> types, Set<String> childIds) {
        return new Subscription("s1", EmailAddress.parse("mama@example.pl"), Set.of(Frequency.DAILY), types, childIds, true,
                Instant.EPOCH);
    }

    private Summary daily(Subscription subscription, List<TimelineEntry> news) {
        return SummaryBuilder.build(subscription, Frequency.DAILY, SummaryWindow.of(Frequency.DAILY, NOW), PL, children, news,
                Map.of(CHILD, agenda));
    }

    @Test
    void selectsNewEntriesOfWantedTypesChildrenAndWindow() {
        List<TimelineEntry> news = List.of(entry(CHILD, "GRADE:1", EntryType.GRADE, RECENT),
                entry(CHILD, "NOTE:1", EntryType.NOTE, RECENT),
                entry(CHILD, "GRADE:old", EntryType.GRADE, NOW.minusDays(3).toInstant()),
                entry("other", "GRADE:2", EntryType.GRADE, RECENT));

        Summary summary = daily(subscription(Set.of(EntryType.GRADE), Set.of(CHILD)), news);

        assertThat(summary.children()).singleElement().satisfies(section -> {
            assertThat(section.childName()).isEqualTo("Ola Kowalska");
            assertThat(section.news()).extracting(TimelineEntry::key).containsExactly("GRADE:1");
            assertThat(section.upcoming()).isEmpty();
        });
        assertThat(summary.isEmpty()).isFalse();
    }

    @Test
    void dailyUpcomingCoversTodayAndTomorrowIncludingLessons() {
        Summary summary = daily(subscription(Set.of(EntryType.LESSON, EntryType.TIMETABLE_CHANGE, EntryType.EVENT), Set.of()),
                List.of());

        Summary.ChildSection ola = summary.children().getFirst();
        assertThat(ola.upcoming()).extracting(AgendaItem::type)
                .contains(EntryType.LESSON, EntryType.TIMETABLE_CHANGE).doesNotContain(EntryType.EVENT);
        assertThat(summary.children().get(1).isEmpty()).isTrue();
    }

    @Test
    void weeklySummaryOmitsLessons() {
        Summary weekly = SummaryBuilder.build(subscription(Set.of(EntryType.LESSON, EntryType.EVENT), Set.of()),
                Frequency.WEEKLY, SummaryWindow.of(Frequency.WEEKLY, NOW), PL, children, List.of(), Map.of(CHILD, agenda));

        assertThat(weekly.children().getFirst().upcoming()).extracting(AgendaItem::type).containsOnly(EntryType.EVENT);
    }

    @Test
    void multiDayItemsOverlappingTheWindowAreUpcoming() {
        When christmas = new When.AllDay(LocalDate.of(2026, 12, 20), LocalDate.of(2026, 12, 31));

        assertThat(SummaryBuilder.overlaps(christmas, LocalDate.of(2026, 12, 24), LocalDate.of(2026, 12, 25))).isTrue();
        assertThat(SummaryBuilder.overlaps(christmas, LocalDate.of(2027, 1, 1), LocalDate.of(2027, 1, 2))).isFalse();
        assertThat(SummaryBuilder.overlaps(christmas, LocalDate.of(2026, 12, 1), LocalDate.of(2026, 12, 19))).isFalse();
        assertThat(SummaryBuilder.overlaps(christmas, LocalDate.of(2026, 12, 31), LocalDate.of(2026, 12, 31))).isTrue();
    }

    @Test
    void emptySummary() {
        assertThat(daily(subscription(Set.of(EntryType.GRADE), Set.of()), List.of()).isEmpty()).isTrue();
    }

    @Test
    void rendersEscapedHtmlAndPlainText() {
        Summary summary = daily(subscription(Set.of(EntryType.GRADE, EntryType.TIMETABLE_CHANGE, EntryType.FREE_DAY),
                Set.of(CHILD)), List.of(entry(CHILD, "GRADE:1", EntryType.GRADE, RECENT)));

        String html = SummaryRenderer.html(summary);
        String text = SummaryRenderer.text(summary);

        assertThat(SummaryRenderer.subject(summary)).isEqualTo("Librus – Dzienne podsumowanie (2026-11-23)");
        assertThat(html).contains("Ola Kowalska", "Oceny", "Tytuł GRADE:1", "szczegóły &lt;b&gt;", "(Anna Nowak)",
                "Zastępstwo: Historia", "Nadchodzące").doesNotContain("<b>");
        assertThat(text).contains("== Ola Kowalska ==", "[Oceny] Tytuł GRADE:1 – szczegóły <b>", "Odwołana lekcja: Matematyka");
    }

    @Test
    void rendersEmptySectionsInEnglish() {
        Summary summary = SummaryBuilder.build(subscription(Set.of(EntryType.GRADE), Set.of(CHILD)), Frequency.MONTHLY,
                SummaryWindow.of(Frequency.MONTHLY, NOW), Locale.ENGLISH, children, List.of(), Map.of());

        assertThat(SummaryRenderer.html(summary)).contains("Nothing new.", "Nothing scheduled.", "Monthly summary");
        assertThat(SummaryRenderer.text(summary)).contains("Nothing new.", "Nothing scheduled.");
    }

    @Test
    void formatsTiming() {
        assertThat(SummaryRenderer.when(When.AllDay.single(TODAY), PL)).isEqualTo("pon. 23.11, cały dzień");
        assertThat(SummaryRenderer.when(new When.AllDay(TODAY, TODAY.plusDays(1)), Locale.ENGLISH)).isEqualTo("Mon 23.11 – Tue 24.11");
        assertThat(SummaryRenderer.when(new When.Timed(TODAY.atTime(8, 0), TODAY.atTime(8, 45)), Locale.ENGLISH))
                .isEqualTo("Mon 23.11 08:00–08:45");
        assertThat(SummaryRenderer.escape("<a href=\"x\">'&'</a>")).isEqualTo("&lt;a href=&quot;x&quot;&gt;&#39;&amp;&#39;&lt;/a&gt;");
    }
}
