package io.github.wlk.jlibrus.server.domain.timeline;

import io.github.wlk.jlibrus.client.messages.Mailbox;
import io.github.wlk.jlibrus.client.messages.MessageSummary;
import io.github.wlk.jlibrus.server.domain.EntryType;
import io.github.wlk.jlibrus.server.domain.TestStudent;
import io.github.wlk.jlibrus.server.domain.TimelineEntry;
import io.github.wlk.jlibrus.server.domain.agenda.AgendaBuilder;
import io.github.wlk.jlibrus.server.domain.agenda.AgendaItem;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static io.github.wlk.jlibrus.server.domain.TestStudent.TODAY;
import static org.assertj.core.api.Assertions.assertThat;

class TimelineExtractorTest {

    private static final ZoneId WARSAW = ZoneId.of("Europe/Warsaw");
    private static final Locale PL = Locale.forLanguageTag("pl");

    private final List<AgendaItem> agenda = new AgendaBuilder(TestStudent.CHILD, TestStudent.directory(), PL)
            .build(TestStudent.records(), TestStudent.timetable());
    private final List<MessageSummary> inbox = List.of(new MessageSummary("7003", Mailbox.INBOX, "Anna Nowak", "Zebranie",
            "Szanowni", LocalDateTime.of(2026, 11, 23, 7, 45), null, true));
    private final Map<String, TimelineEntry> byKey = new TimelineExtractor(TestStudent.CHILD, TestStudent.directory(), WARSAW, PL)
            .extract(TestStudent.records(), agenda, inbox).stream()
            .collect(Collectors.toMap(TimelineEntry::key, Function.identity()));

    @Test
    void gradesResolveSubjectCategoryCommentsAndTeacher() {
        TimelineEntry grade = byKey.get("GRADE:G1");

        assertThat(grade.title()).isEqualTo("Matematyka: 5");
        assertThat(grade.detail()).isEqualTo("Sprawdzian · Brawo");
        assertThat(grade.author()).isEqualTo("Anna Nowak");
        assertThat(grade.occursOn()).isEqualTo(TODAY);
        assertThat(grade.addedAt()).isEqualTo(Instant.parse("2026-11-23T08:30:00Z"));
        assertThat(byKey.get("GRADE:G3").detail()).isNull();
        assertThat(byKey.get("GRADE:G3").addedAt()).isNull();
    }

    @Test
    void onlyNonPresentAttendanceIsRecordedWithKindInKey() {
        assertThat(byKey).containsKey("ATTENDANCE:At1:ABSENT").doesNotContainKey("ATTENDANCE:At2:PRESENT");
        assertThat(byKey.get("ATTENDANCE:At1:ABSENT").title()).isEqualTo("Nieobecność: Matematyka (lekcja 1)");
        assertThat(byKey.get("ATTENDANCE:At3:LATE").title()).isEqualTo("Spóźnienie (lekcja 3)");
    }

    @Test
    void notesAnnouncementsLuckyNumberBehaviourAndMessages() {
        assertThat(byKey.get("NOTE:N50").title()).isEqualTo("Uwaga negatywna – Zachowanie");
        assertThat(byKey.get("NOTE:N50").detail()).isEqualTo("Rozmowy");
        assertThat(byKey.get("ANNOUNCEMENT:Snb-1").author()).isEqualTo("Jan Kowal");
        assertThat(byKey.get("LUCKY_NUMBER:2026-11-23").title()).isEqualTo("Szczęśliwy numerek: 7");
        assertThat(byKey.get("BEHAVIOUR_GRADE:B1").detail()).isEqualTo("Konkurs");
        assertThat(byKey.get("MESSAGE:M7003").author()).isEqualTo("Anna Nowak");
    }

    @Test
    void agendaItemsBecomeEntriesExceptRegularLessons() {
        assertThat(byKey.values()).extracting(TimelineEntry::type)
                .contains(EntryType.EVENT, EntryType.HOMEWORK, EntryType.PARENT_MEETING, EntryType.FREE_DAY,
                        EntryType.TEACHER_ABSENCE, EntryType.TIMETABLE_CHANGE)
                .doesNotContain(EntryType.LESSON);
        assertThat(byKey.get("EVENT:event-1234567u-70@jlibrus").occursOn()).isEqualTo(TODAY.plusDays(2));
    }

    @Test
    void englishWording() {
        var english = new TimelineExtractor("c", TestStudent.directory(), WARSAW, Locale.ENGLISH)
                .extract(TestStudent.records(), List.of(), List.of());

        assertThat(english).extracting(TimelineEntry::title).contains("Absence: Matematyka (lesson 1)", "Lucky number: 7");
    }

    @Test
    void firstSyncUsesLibrusTimestampsLaterSyncsUseNow() {
        Instant now = Instant.parse("2026-11-23T12:00:00Z");
        List<TimelineEntry> entries = List.of(byKey.get("GRADE:G1"), byKey.get("NOTE:N50"),
                byKey.get("GRADE:G1").withNoticedAt(now));

        List<TimelineEntry> first = NoticePolicy.stamp(entries, true, now);
        List<TimelineEntry> later = NoticePolicy.stamp(entries, false, now);

        assertThat(first.get(0).noticedAt()).isEqualTo(Instant.parse("2026-11-23T08:30:00Z"));
        assertThat(first.get(1).noticedAt()).isEqualTo(Instant.EPOCH);
        assertThat(later).extracting(TimelineEntry::noticedAt).containsOnly(now);
    }

    @Test
    void futureLibrusTimestampsAreCappedAtNow() {
        Instant now = Instant.parse("2026-11-23T06:00:00Z");

        assertThat(NoticePolicy.stamp(List.of(byKey.get("GRADE:G1")), true, now).getFirst().noticedAt()).isEqualTo(now);
    }
}
