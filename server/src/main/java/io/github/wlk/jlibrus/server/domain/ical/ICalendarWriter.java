package io.github.wlk.jlibrus.server.domain.ical;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.function.Supplier;

/** Serialises {@link CalendarItem}s to RFC 5545 iCalendar text. */
public final class ICalendarWriter {

    private static final String CRLF = "\r\n";
    private static final int MAX_LINE_OCTETS = 75;
    private static final DateTimeFormatter UTC_STAMP =
            DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'").withZone(ZoneOffset.UTC);
    private static final DateTimeFormatter DATE = DateTimeFormatter.BASIC_ISO_DATE;

    private final ZoneId schoolZone;
    private final Supplier<Instant> clock;

    public ICalendarWriter(ZoneId schoolZone, Supplier<Instant> clock) {
        this.schoolZone = schoolZone;
        this.clock = clock;
    }

    public String write(String calendarName, List<CalendarItem> items) {
        StringBuilder out = new StringBuilder();
        line(out, "BEGIN:VCALENDAR");
        line(out, "VERSION:2.0");
        line(out, "PRODID:-//jlibrus//Librus calendar//EN");
        line(out, "CALSCALE:GREGORIAN");
        line(out, "METHOD:PUBLISH");
        line(out, "X-WR-CALNAME:" + escape(calendarName));
        line(out, "X-WR-TIMEZONE:" + schoolZone.getId());
        String stamp = UTC_STAMP.format(clock.get());
        items.forEach(item -> writeEvent(out, item, stamp));
        line(out, "END:VCALENDAR");
        return out.toString();
    }

    private void writeEvent(StringBuilder out, CalendarItem item, String stamp) {
        line(out, "BEGIN:VEVENT");
        line(out, "UID:" + escape(item.uid()));
        line(out, "DTSTAMP:" + stamp);
        writeTiming(out, item.when());
        line(out, "SUMMARY:" + escape(item.summary()));
        optional(out, "DESCRIPTION", item.description());
        optional(out, "LOCATION", item.location());
        line(out, "CATEGORIES:" + escape(item.category()));
        line(out, "TRANSP:TRANSPARENT");
        line(out, "END:VEVENT");
    }

    private void writeTiming(StringBuilder out, When when) {
        switch (when) {
            case When.AllDay allDay -> {
                line(out, "DTSTART;VALUE=DATE:" + DATE.format(allDay.from()));
                line(out, "DTEND;VALUE=DATE:" + DATE.format(allDay.toInclusive().plusDays(1)));
            }
            case When.Timed timed -> {
                line(out, "DTSTART:" + utc(timed.start()));
                line(out, "DTEND:" + utc(timed.end()));
            }
        }
    }

    private String utc(LocalDateTime local) {
        return UTC_STAMP.format(local.atZone(schoolZone).toInstant());
    }

    private static void optional(StringBuilder out, String name, String value) {
        if (value != null && !value.isBlank()) {
            line(out, name + ":" + escape(value));
        }
    }

    private static void line(StringBuilder out, String content) {
        out.append(fold(content)).append(CRLF);
    }

    /** Escapes TEXT values per RFC 5545 section 3.3.11. */
    static String escape(String text) {
        return text.replace("\\", "\\\\")
                .replace(";", "\\;")
                .replace(",", "\\,")
                .replace("\r\n", "\\n")
                .replace("\n", "\\n")
                .replace("\r", "\\n");
    }

    /** Folds a content line so no physical line exceeds 75 octets, never splitting a code point. */
    static String fold(String content) {
        StringBuilder out = new StringBuilder();
        int octets = 0;
        int i = 0;
        while (i < content.length()) {
            int codePoint = content.codePointAt(i);
            int size = new String(Character.toChars(codePoint)).getBytes(StandardCharsets.UTF_8).length;
            if (octets + size > MAX_LINE_OCTETS) {
                out.append(CRLF).append(' ');
                octets = 1;
            }
            out.appendCodePoint(codePoint);
            octets += size;
            i += Character.charCount(codePoint);
        }
        return out.toString();
    }
}
