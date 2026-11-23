package io.github.wlk.jlibrus.server.domain.ical;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ICalendarWriterTest {

    private static final Instant STAMP = Instant.parse("2026-11-23T10:15:30Z");
    private static final ZoneId WARSAW = ZoneId.of("Europe/Warsaw");
    private final ICalendarWriter writer = new ICalendarWriter(WARSAW, () -> STAMP);

    @Test
    void writesCalendarEnvelopeWithCrlfLineEndings() {
        String ics = writer.write("Librus", List.of());

        assertThat(ics).startsWith("BEGIN:VCALENDAR\r\nVERSION:2.0\r\n");
        assertThat(ics).contains("PRODID:-//jlibrus//Librus calendar//EN\r\n");
        assertThat(ics).contains("X-WR-CALNAME:Librus\r\n");
        assertThat(ics).contains("METHOD:PUBLISH\r\n");
        assertThat(ics).endsWith("END:VCALENDAR\r\n");
        assertThat(ics.replace("\r\n", "")).doesNotContain("\n");
    }

    @Test
    void writesAllDayEventWithExclusiveEndDate() {
        CalendarItem item = new CalendarItem("free-1@jlibrus", "Dzień wolny", "Święto",
                null, "FREE_DAY", new When.AllDay(LocalDate.of(2026, 11, 10), LocalDate.of(2026, 11, 11)));

        String ics = writer.write("Librus", List.of(item));

        assertThat(ics).contains("BEGIN:VEVENT\r\nUID:free-1@jlibrus\r\nDTSTAMP:20261123T101530Z\r\n");
        assertThat(ics).contains("DTSTART;VALUE=DATE:20261110\r\n");
        assertThat(ics).contains("DTEND;VALUE=DATE:20261112\r\n");
        assertThat(ics).contains("SUMMARY:Dzień wolny\r\n");
        assertThat(ics).contains("CATEGORIES:FREE_DAY\r\n");
        assertThat(ics).doesNotContain("LOCATION");
    }

    @Test
    void writesTimedEventInUtc() {
        CalendarItem item = new CalendarItem("ev-7@jlibrus", "Sprawdzian", null, "s. 12", "EVENT",
                new When.Timed(LocalDateTime.of(2026, 7, 1, 8, 0), LocalDateTime.of(2026, 7, 1, 8, 45)));

        String ics = writer.write("Librus", List.of(item));

        assertThat(ics).contains("DTSTART:20260701T060000Z\r\n");
        assertThat(ics).contains("DTEND:20260701T064500Z\r\n");
        assertThat(ics).contains("LOCATION:s. 12\r\n");
        assertThat(ics).doesNotContain("DESCRIPTION");
    }

    @Test
    void escapesSpecialCharacters() {
        assertThat(ICalendarWriter.escape("a\\b;c,d\ne\r\nf")).isEqualTo("a\\\\b\\;c\\,d\\ne\\nf");
    }

    @Test
    void foldsLinesLongerThan75Octets() {
        String folded = ICalendarWriter.fold("DESCRIPTION:" + "x".repeat(100));

        String[] lines = folded.split("\r\n");
        assertThat(lines).hasSize(2);
        assertThat(lines[0]).hasSize(75);
        assertThat(lines[1]).startsWith(" ").hasSize(1 + 112 - 75);
    }

    @Test
    void foldingNeverSplitsMultiByteCharacters() {
        String folded = ICalendarWriter.fold("SUMMARY:" + "ż".repeat(60));

        for (String line : folded.split("\r\n")) {
            assertThat(line.getBytes(java.nio.charset.StandardCharsets.UTF_8).length).isLessThanOrEqualTo(75);
        }
        assertThat(folded.replace("\r\n ", "")).isEqualTo("SUMMARY:" + "ż".repeat(60));
    }

    @Test
    void shortLinesAreNotFolded() {
        assertThat(ICalendarWriter.fold("SUMMARY:ok")).isEqualTo("SUMMARY:ok");
    }
}
