package io.github.wlk.jlibrus.server.domain.summary;

import io.github.wlk.jlibrus.server.domain.EntryType;
import io.github.wlk.jlibrus.server.domain.Subscription;
import io.github.wlk.jlibrus.server.domain.TimelineEntry;
import io.github.wlk.jlibrus.server.domain.agenda.AgendaItem;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Selects, per subscription, the new entries and upcoming agenda items it asked for. Pure. */
public final class SummaryBuilder {

    private SummaryBuilder() {
    }

    /**
     * Builds the summary one subscription receives.
     *
     * @param children   child id to display name, in display order
     * @param news       timeline entries noticed inside the window (any child, any type)
     * @param agendas    each child's agenda
     */
    public static Summary build(Subscription subscription, Frequency frequency, SummaryWindow window, Locale locale,
                                Map<String, String> children, List<TimelineEntry> news,
                                Map<String, List<AgendaItem>> agendas) {
        List<Summary.ChildSection> sections = children.entrySet().stream()
                .filter(child -> subscription.includesChild(child.getKey()))
                .map(child -> new Summary.ChildSection(child.getKey(), child.getValue(),
                        newsFor(subscription, window, child.getKey(), news),
                        upcomingFor(subscription, frequency, window, agendas.getOrDefault(child.getKey(), List.of()))))
                .toList();
        return new Summary(frequency, window, locale, sections);
    }

    private static List<TimelineEntry> newsFor(Subscription subscription, SummaryWindow window, String childId,
                                               List<TimelineEntry> news) {
        return news.stream()
                .filter(e -> e.childId().equals(childId))
                .filter(e -> subscription.types().contains(e.type()))
                .filter(e -> window.isNew(e.noticedAt()))
                .sorted(Comparator.comparing(TimelineEntry::type).thenComparing(TimelineEntry::noticedAt))
                .toList();
    }

    /** Regular lessons are only listed in daily summaries (tomorrow's plan); a week of lessons is noise. */
    private static List<AgendaItem> upcomingFor(Subscription subscription, Frequency frequency, SummaryWindow window,
                                                List<AgendaItem> agenda) {
        return agenda.stream()
                .filter(i -> subscription.types().contains(i.type()))
                .filter(i -> i.type() != EntryType.LESSON || frequency == Frequency.DAILY)
                .filter(i -> i.when().overlaps(window.upcomingFrom(), window.upcomingTo()))
                .toList();
    }
}
