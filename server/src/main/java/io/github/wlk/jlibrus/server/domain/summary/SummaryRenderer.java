package io.github.wlk.jlibrus.server.domain.summary;

import io.github.wlk.jlibrus.server.domain.EntryType;
import io.github.wlk.jlibrus.server.domain.Texts;
import io.github.wlk.jlibrus.server.domain.TimelineEntry;
import io.github.wlk.jlibrus.server.domain.ical.When;

import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

/** Renders a {@link Summary} as an e-mail subject, HTML body (inline styles) and plain-text body. */
public final class SummaryRenderer {

    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("EEE dd.MM");
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");

    private SummaryRenderer() {
    }

    public static String subject(Summary summary) {
        return "Librus – " + Texts.get("summary." + summary.frequency().name(), summary.locale())
                + " (" + summary.window().periodKey() + ")";
    }

    public static String html(Summary summary) {
        StringBuilder out = new StringBuilder("<!doctype html><html><body style=\"font-family:Arial,sans-serif;"
                + "color:#1f2933;max-width:640px;margin:auto\">");
        out.append("<h1 style=\"font-size:20px;color:#1f4e8c\">").append(escape(subject(summary))).append("</h1>");
        summary.children().forEach(section -> section(out, section, summary.locale()));
        out.append("<p style=\"color:#7b8794;font-size:12px\">").append(escape(Texts.get("summary.footer", summary.locale())))
                .append("</p></body></html>");
        return out.toString();
    }

    private static void section(StringBuilder out, Summary.ChildSection section, Locale locale) {
        out.append("<h2 style=\"font-size:18px;border-bottom:1px solid #d9e2ec\">")
                .append(escape(section.childName())).append("</h2>");
        out.append("<h3 style=\"font-size:15px\">").append(escape(Texts.get("summary.new", locale))).append("</h3>");
        if (section.news().isEmpty()) {
            out.append("<p>").append(escape(Texts.get("summary.nothing", locale))).append("</p>");
        }
        grouped(section.news()).forEach((type, entries) -> {
            out.append("<h4 style=\"font-size:14px;margin-bottom:4px\">").append(escape(type.label(locale))).append("</h4><ul>");
            entries.forEach(e -> out.append("<li><strong>").append(escape(e.title())).append("</strong>")
                    .append(e.detail() == null ? "" : " – " + escape(e.detail()).replace("\n", "<br>"))
                    .append(e.author() == null ? "" : " <em style=\"color:#52606d\">(" + escape(e.author()) + ")</em>")
                    .append("</li>"));
            out.append("</ul>");
        });
        out.append("<h3 style=\"font-size:15px\">").append(escape(Texts.get("summary.upcoming", locale))).append("</h3>");
        if (section.upcoming().isEmpty()) {
            out.append("<p>").append(escape(Texts.get("summary.nothingUpcoming", locale))).append("</p>");
            return;
        }
        out.append("<table style=\"border-collapse:collapse;width:100%\">");
        section.upcoming().forEach(item -> out.append("<tr><td style=\"padding:4px 8px;white-space:nowrap;color:#52606d\">")
                .append(escape(when(item.when(), locale))).append("</td><td style=\"padding:4px 8px\">")
                .append(escape(item.title()))
                .append(item.location() == null ? "" : " <span style=\"color:#7b8794\">(" + escape(item.location()) + ")</span>")
                .append("</td></tr>"));
        out.append("</table>");
    }

    public static String text(Summary summary) {
        StringBuilder out = new StringBuilder(subject(summary)).append("\n\n");
        Locale locale = summary.locale();
        for (Summary.ChildSection section : summary.children()) {
            out.append("== ").append(section.childName()).append(" ==\n");
            out.append(Texts.get("summary.new", locale)).append(":\n");
            if (section.news().isEmpty()) {
                out.append("  ").append(Texts.get("summary.nothing", locale)).append('\n');
            }
            grouped(section.news()).forEach((type, entries) -> entries.forEach(e -> out.append("  [")
                    .append(type.label(locale)).append("] ").append(e.title())
                    .append(e.detail() == null ? "" : " – " + e.detail().replace('\n', ' ')).append('\n')));
            out.append(Texts.get("summary.upcoming", locale)).append(":\n");
            if (section.upcoming().isEmpty()) {
                out.append("  ").append(Texts.get("summary.nothingUpcoming", locale)).append('\n');
            }
            section.upcoming().forEach(i -> out.append("  ").append(when(i.when(), locale)).append("  ")
                    .append(i.title()).append('\n'));
            out.append('\n');
        }
        return out.append(Texts.get("summary.footer", locale)).append('\n').toString();
    }

    private static Map<EntryType, List<TimelineEntry>> grouped(List<TimelineEntry> news) {
        return news.stream().collect(Collectors.groupingBy(TimelineEntry::type, TreeMap::new, Collectors.toList()));
    }

    static String when(When when, Locale locale) {
        return switch (when) {
            case When.AllDay allDay -> allDay.from().equals(allDay.toInclusive())
                    ? DAY.withLocale(locale).format(allDay.from()) + ", " + Texts.get("allDay", locale)
                    : DAY.withLocale(locale).format(allDay.from()) + " – " + DAY.withLocale(locale).format(allDay.toInclusive());
            case When.Timed timed -> DAY.withLocale(locale).format(timed.start()) + " " + TIME.format(timed.start())
                    + "–" + TIME.format(timed.end());
        };
    }

    static String escape(String text) {
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;").replace("'", "&#39;");
    }
}
